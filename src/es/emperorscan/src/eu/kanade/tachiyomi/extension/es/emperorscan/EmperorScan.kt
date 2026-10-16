package eu.kanade.tachiyomi.extension.es.emperorscan

import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class EmperorScan :
    KeiSource(),
    ConfigurableSource {

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseHttpUrl.host }

    override suspend fun getPopularManga(page: Int) = library(page, "vistas", null)

    override suspend fun getLatestUpdates(page: Int) = library(page, "reciente", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            return library(page, filters.firstInstance<SortFilter>().value, filters.firstInstanceOrNull<GenreFilter>()?.slug)
        }
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("buscar")
            .addPathSegment("")
            .addQueryParameter("q", query)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return listing(url)
    }

    private suspend fun library(page: Int, order: String, genre: String?): MangasPage {
        val url = baseHttpUrl.newBuilder().apply {
            if (genre == null) addPathSegment("manga") else addPathSegment("manga-genre").addPathSegment(genre)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
            addPathSegment("")
            if (order != "reciente") addQueryParameter("orden", order)
        }.build()
        return listing(url)
    }

    private suspend fun listing(url: HttpUrl): MangasPage {
        val props = client.get(url).asJsoup().inertiaPage<ListingProps>().props
        return MangasPage(props.series.map { it.toSManga() }, props.pagination.next != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHttpUrl.host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it != "page" } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val props = client.get(getMangaUrl(manga)).asJsoup().inertiaPage<SeriesProps>().props
        props.series.applyTo(manga)
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        // Dates from the series page, the rest from capitulos.json
        val all = if (props.chaptersPagination.last > 1) {
            client.get(baseHttpUrl.resolve(props.series.url)!!.resolve("capitulos.json")!!).parseAs<ChapterListDto>().items
        } else {
            props.chapters
        }
        val dated = props.chapters.associateBy { it.slug }
        return SMangaUpdate(
            manga,
            all.filterNot { hideLocked && it.locked }
                .map { (dated[it.slug] ?: it).toSChapter(props.series.url) },
        )
    }

    // Madara-era search results: a post id, with the path in memo
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.memo["path"]?.stringOrNull?.takeIf { manga.url.all(Char::isDigit) } ?: manga.url
        return baseUrl + path
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)

        // 1. Solicitamos el HTML inicial usando Jsoup para obtener los datos base (Inertia Props)
        val document = client.get(chapterUrl).asJsoup()
        val page = document.inertiaPage<JsonElement>()
        if (page.component == "Reader/Locked") return emptyList()

        val props = page.props.parseAs<ReaderProps>()
        val pagesList = props.pages.toMutableList()

        // 2. Si existen páginas selladas ('dims'), pedimos el manifiesto directamente por HTTP
        if (props.dims.isNotEmpty()) {
            val inlineCount = props.pages.size

            // Hacemos la llamada HTTP nativa simulando la petición de red que haría fetchManifest
            val sealedPages = fetchManifestNativamente(chapterUrl, props.reader, props.chapter.id)
            val sealedMap = sealedPages.associateBy { it.n }

            for (i in (inlineCount + 1)..(inlineCount + props.dims.size)) {
                val pageDto = sealedMap[i] ?: throw IOException("Falta la página del lector: $i")
                pagesList.add(pageDto)
            }
        }

        // 3. Devolvemos la lista final validando que los hosts correspondan al dominio
        return pagesList.mapIndexed { index, pageDto ->
            val imageUrl = pageDto.imageUrl.toHttpUrl()
            if (imageUrl.host != baseHttpUrl.host) throw IOException("Host de imagen inesperado: ${imageUrl.host}")
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    private suspend fun fetchManifestNativamente(chapterUrl: String, readerKey: String, chapterId: Int): List<PageDto> {
        // 1. Configuramos las cabeceras requeridas por el framework Inertia para respuestas JSON
        val manifestHeaders = headersBuilder()
            .add("Accept", "application/json")
            .add("X-Inertia", "true") // Fuerza al servidor a responder con JSON puro en lugar de HTML
            .add("X-Inertia-Version", "1.0") // Ajusta según la versión que use el sitio si da error
            .add("Referer", chapterUrl)
            .build()

        // 2. Apuntamos al endpoint que procesa el lector (usualmente reusando la misma URL del capítulo con parámetros de consulta)
        val apiUrl = chapterUrl.toHttpUrl().newBuilder()
            .addQueryParameter("reader", readerKey)
            .addQueryParameter("id", chapterId.toString())
            .build()

        val request = GET(apiUrl, manifestHeaders)

        // 3. Ejecutamos y deserializamos el payload directamente
        return try {
            val response = client.newCall(request).execute()
            val jsonResponse = response.body.string().parseAs<InertiaResponseDto>()

            // Extraemos las páginas calculadas que devolvió el endpoint
            jsonResponse.props.pages
        } catch (e: Exception) {
            throw IOException("Error al intentar obtener el manifiesto de páginas sin WebView: ${e.message}", e)
        }
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup().inertiaPage<HomeProps>().props.genres.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<NamedDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                genres.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            ),
        )
    }

    private inline fun <reified T> Document.inertiaPage(): InertiaPage<T> = selectFirst("script[data-page=app]")!!.data().parseAs()

    private val preferences by getPreferencesLazy()

    private val hideLocked get() = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
