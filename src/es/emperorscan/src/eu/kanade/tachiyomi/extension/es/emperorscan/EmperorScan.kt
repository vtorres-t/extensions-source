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
class EmperorScan(
    override val id: Long,
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
) : KeiSource(),
    ConfigurableSource {

    private val baseHttpUrl get() = baseUrl.toHttpUrl()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3) { it.host == baseHttpUrl.host }

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

    override fun getMangaUrl(manga: SManga): String {
        val path = manga.memo["path"]?.stringOrNull?.takeIf { manga.url.all(Char::isDigit) } ?: manga.url
        return baseUrl + path
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)

        // 1. Descargamos el HTML de Imperio Manhwa de forma nativa por HTTP puro
        val document = client.get(chapterUrl).asJsoup()

        // 2. Localizamos el contenedor de hidratación de Inertia donde viaja el JSON de datos
        val pageElement = document.selectFirst("div[data-page], script[data-page=app]")
            ?: throw IOException("No se encontró el contenedor de datos del lector en el HTML")

        val rawJsonText = pageElement.attr("data-page").ifEmpty { pageElement.data() }

        // 3. EXPRESIÓN REGULAR AVANZADA: Captura tanto "src" como "srcset" dentro del JSON crudo
        val imageRegex = """"(?:src|srcset)"\s*:\s*"([^"]+)"""".toRegex()
        val matches = imageRegex.findAll(rawJsonText)

        val cleanUrls = mutableListOf<String>()

        for (match in matches) {
            val rawValue = match.groupValues[1].replace("\\/", "/") // Limpiamos barras escapadas del JSON (\/)

            if (rawValue.isBlank()) continue

            // 4. Si es un string de tipo srcset, extraemos la URL de mayor resolución (la última de la lista)
            if (rawValue.contains(" ")) {
                val candidate = rawValue.split(",")
                    .map { it.trim() }
                    .lastOrNull { it.isNotEmpty() }
                    ?.substringBefore(" ") // Removemos el descriptor de ancho (ej: 768w)

                if (candidate != null && (candidate.contains("/storage/") || candidate.contains("/uploads/"))) {
                    cleanUrls.add(candidate)
                }
            } else {
                // Es un src directo de una sola imagen (común para la primera página)
                if (rawValue.contains("/storage/") || rawValue.contains("/uploads/")) {
                    cleanUrls.add(rawValue)
                }
            }
        }

        // 5. Removemos duplicados manteniendo el orden de lectura original de las páginas
        val finalUrls = cleanUrls.distinct().toList()

        if (finalUrls.isEmpty()) {
            throw IOException("No se pudieron extraer las imágenes del capítulo mediante el extractor adaptativo")
        }

        // 6. Mapeamos al objeto Page final de Tachiyomi/Mihon verificando el host de destino
        return finalUrls.mapIndexed { index, imageUrlString ->
            val imageUrl = imageUrlString.toHttpUrl()
            if (imageUrl.host != baseHttpUrl.host) throw IOException("Host de imagen inesperado: ${imageUrl.host}")
            Page(index, imageUrl = imageUrl.toString())
        }
    }

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
