package eu.kanade.tachiyomi.extension.es.capibaratraductor

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class CapibaraTraductor :
    KeiSource(),
    ConfigurableSource {

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3) { it.host == baseUrlHost }
        addInterceptor(::authorizationInterceptor)
    }

    private fun authorizationInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "token" }

        if (token == null) return chain.proceed(request)

        val newRequest = request.newBuilder().apply {
            header("Authorization", "Bearer ${token.value}")
        }.build()

        return chain.proceed(newRequest)
    }

    private fun getScanHeaders(organizationSlug: String): Headers = headers.newBuilder()
        .add("x-organization", organizationSlug)
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=popular&nsfw=${(!hide18Plus)}".toHttpUrl(), page, headers)

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=latestr&nsfw=${(!hide18Plus)}".toHttpUrl(), page, headers)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/manga-custom".toHttpUrl().newBuilder()

        url.setQueryParameter("page", page.toString())
        url.setQueryParameter("limit", PAGE_LIMIT.toString())
        url.setQueryParameter("nsfw", (!hide18Plus).toString())

        val headers = headers.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> url.setQueryParameter("order", filter.toUriPart())
                is ScanlatorFilter -> if (filter.state != 0) headers["x-organization"] = filter.toUriPart()
                else -> {}
            }
        }

        if (query.isNotBlank()) url.setQueryParameter("search", query)

        return parseMangaList(url.build(), page, headers.build())
    }

    private suspend fun parseMangaList(url: HttpUrl, page: Int, headers: Headers): MangasPage {
        val result = client.get(url, headers).parseAs<Data<SeriesListDataDto>>()

        val mangas = result.data.series.map { it.toSManga() }
        val hasNextPage = page < result.data.maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val sfwScans = fetchAllScans(includeNsfw = false)
        val nsfwScans = fetchAllScans(includeNsfw = true)

        return buildList {
            add("Todos" to "")
            addAll(
                (sfwScans + nsfwScans)
                    .distinctBy { it.second }
                    .sortedBy { it.first },
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(ScanlatorFilter("Scanlator", it.toTypedArray()))
        }

        filters.add(SortByFilter("Ordenar por", getSortList()))

        return FilterList(filters)
    }

    private fun getSortList() = arrayOf(
        Pair("Recientes", "latest"),
        Pair("Popularidad", "popular"),
        Pair("A-Z", "alphabetical"),
    )

    private suspend fun fetchAllScans(includeNsfw: Boolean): List<Pair<String, String>> {
        val scans = mutableListOf<Pair<String, String>>()
        var page = 1

        while (true) {
            val url = "$baseUrl/api/landing/scans".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "name")
                .addQueryParameter("limit", "100")
                .apply {
                    if (includeNsfw) {
                        addQueryParameter("includeNSFW", "true")
                    }
                }
                .build()

            val response = client.get(url)
                .parseAs<Data<ScanListDto>>()
                .data

            scans += response.items.map { it.name to it.id }

            if (!response.hasNextPage()) {
                break
            }

            page++
        }

        return scans
    }

    override fun getMangaUrl(manga: SManga): String {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        return "$baseUrl/$organizationSlug/manga/$seriesSlug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        val seriesPath = if (manga.memo["jointSlug"] != null) "joint" else "manga-custom"
        val result = client.get("$baseUrl/api/$seriesPath/$seriesSlug", getScanHeaders(organizationSlug))
            .parseAs<Data<SeriesDto>>()
            .data

        val chapterList = result.chapters
            ?.filter { it.isUnreleased.not() }
            ?.map { it.toSChapter(seriesSlug, organizationSlug) }
            ?.filter { it.date_upload < System.currentTimeMillis() }
            ?: emptyList()

        return SMangaUpdate(result.toSMangaDetails(organizationSlug), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return "$baseUrl/$organizationSlug/manga/$seriesSlug/chapters/$chapterSlug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        val seriesPath = if (chapter.memo["jointId"] != null) "joint" else "manga-custom"

        val result = client.get("$baseUrl/api/$seriesPath/$seriesSlug/chapter/$chapterSlug/pages", getScanHeaders(organizationSlug))
            .parseAs<Data<List<PageDto>>>()
        return result.data
            .filterNot { "/promo/" in it.imageUrl }
            .mapIndexed { i, page ->
                Page(i, imageUrl = page.imageUrl)
            }
    }

    private val preferences by getPreferencesLazy()

    private val hide18Plus get() = preferences.getBoolean(HIDE_18_PLUS, HIDE_18_PLUS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = eu.kanade.tachiyomi.extension.es.capibaratraductor.CapibaraTraductor.Companion.HIDE_18_PLUS
            title = "Filtrar contenido +18"
            summary = "Ocultar mangas para adultos en las búsquedas y listados"
            setDefaultValue(eu.kanade.tachiyomi.extension.es.capibaratraductor.CapibaraTraductor.Companion.HIDE_18_PLUS_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_LIMIT = 36

        private const val HIDE_18_PLUS = "hide18Plus"
        private const val HIDE_18_PLUS_DEFAULT = true
    }
}
