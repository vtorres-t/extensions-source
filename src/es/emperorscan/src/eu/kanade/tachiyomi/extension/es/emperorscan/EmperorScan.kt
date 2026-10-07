package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

@Source
class EmperorScan(
    override val id: Long,
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(6) { it.host == baseUrlHost }
        .build()

    private val preferences: SharedPreferences = getPreferences()

    private val removePremium
        get() =
            preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")

        if (page > 1) {
            url.addPathSegment("page")
                .addPathSegment(page.toString())
        }

        url.addQueryParameter("orden", "valoradas")

        return GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("main .grid > div, main .grid > article, ul.grid li article").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("a[href*='/manga/']")
                setUrlWithoutDomain(anchor?.attr("href") ?: "")

                title = element.select("h2 a, h2, h3").text().trim()

                val img = element.selectFirst("img")
                thumbnail_url = img?.attr("abs:src")?.ifEmpty {
                    img.attr("abs:data-src")?.ifEmpty {
                        img.attr("abs:srcset")?.substringBefore(" ") ?: ""
                    }
                } ?: ""
            }
        }.filter { it.url.isNotEmpty() && it.title.isNotEmpty() }

        val segments = response.request.url.pathSegments
        val pageIdx = segments.indexOf("page")
        val currentPage = if (pageIdx != -1 && pageIdx + 1 < segments.size) {
            segments[pageIdx + 1].toIntOrNull() ?: 1
        } else {
            1
        }
        val paginationText = document.select("p:contains(Página), div:contains(Página), span:contains(Página)").text()

        val maxPage = try {
            val regexMax = """Página\s+\d+\s+de\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)
            regexMax.find(paginationText)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        } catch (e: Exception) {
            1
        }

        val hasNextPage = if (paginationText.isEmpty()) {
            mangas.size >= 24
        } else {
            currentPage < maxPage
        }

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")

        if (page > 1) {
            url.addPathSegment("page")
                .addPathSegment(page.toString())
        }

        url.addQueryParameter("orden", "latest")

        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty() && filters.isEmpty()) return popularMangaRequest(page)

        val url = "$baseUrl/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.bg-surface-2 img, div.hposter__card img")?.attr("abs:src") ?: ""
            description = document.select("p.col-span-2.max-w-3xl").text()
                .replace("HAZ CLICK AQUÍ PARA UNIRTE A NUESTRO DISCORD", "", ignoreCase = true)
                .trim()
            status = when (document.select("span.text-muted:contains(En curso), span:contains(Publicándose)").size > 0) {
                true -> SManga.ONGOING
                false -> SManga.UNKNOWN
            }

            val genres = document.select("a.chip").map { it.text().trim() }
            genre = genres.filterNot { item ->
                removePremium && (item.contains("Vip", ignoreCase = true) || item.contains("Premium", ignoreCase = true))
            }.joinToString(", ")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = manga.url.removeSuffix("/")
        return GET("$baseUrl$mangaUrl/capitulos.json", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonString = response.body.string()
        val chapters = mutableListOf<SChapter>()

        try {
            val jsonObject = JSONObject(jsonString)
            val itemsArray = jsonObject.getJSONArray("items")

            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)

                val isVip = item.optString("access", "").contains("vip", ignoreCase = true) ||
                    item.optBoolean("locked", false)

                if (removePremium && isVip) continue

                val chapter = SChapter.create().apply {
                    val chapterSlug = item.getString("slug")
                    url = "${response.request.url.encodedPath.replace("/capitulos.json", "")}/$chapterSlug"
                    name = item.getString("label").trim()

                    // val dateText = item.optString("published_label", "")
                    // date_upload = parseChapterDate(dateText)
                }
                chapters.add(chapter)
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return chapters
    }

    private fun parseChapterDate(dateText: String): Long {
        if (dateText.isEmpty()) return 0L
        return try {
            val cleaned = dateText.replace("Z", "+00:00")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            format.parse(cleaned)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val cleaned = dateText.substringBefore("T").trim()
                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                format.parse(cleaned)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imgElements = document.select("main.reader-pages div.w-full img")
        if (imgElements.isEmpty()) return emptyList()

        return imgElements.mapIndexed { index, element ->
            val srcsetAttr: String? = element.attr("srcset")
            val dataSrcsetAttr: String? = element.attr("data-srcset")

            var srcsetText = srcsetAttr.orEmpty().trim()
            if (srcsetText.isEmpty()) {
                srcsetText = dataSrcsetAttr.orEmpty().trim()
            }

            val rawUrl = if (srcsetText.contains(",")) {
                srcsetText.substringBefore(",")
            } else {
                srcsetText
            }

            val imageUrl = rawUrl.substringBefore(" ").trim()

            if (imageUrl.isEmpty() || imageUrl.startsWith("data:")) {
                return@mapIndexed Page(index, "", "")
            }

            Page(index, "", imageUrl)
        }.filter { it.imageUrl?.isNotEmpty() == true }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP del feed"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
