package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Source
abstract class EmperorScan :
    Madara(),
    ConfigurableSource {

    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    // ================================================================================
    // PETICIONES DE NAVEGACIÓN Y BÚSQUEDA FLUIDAS (EVITA ADMIN-AJAX)
    // ================================================================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("page")
            addPathSegment(page.toString())
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")
        }.build()
        return GET(url, headers)
    }

    // ================================================================================
    // PARSEO ROBUSTO DE LA REJILLA HTML DE IMPERIOMANHUA
    // ================================================================================

    override fun popularMangaSelector() = "div.agrid a.acard"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("div.ac-t")?.text() ?: element.attr("title")

        val imgElement = element.selectFirst("img.ac-cover")
        thumbnail_url = imgElement?.attr("abs:src")
            ?.takeIf { it.isNotEmpty() }
            ?: imgElement?.attr("abs:data-src")
            ?: imgElement?.attr("abs:srcset")?.substringBefore(" ")
    }

    override fun popularMangaNextPageSelector() = "div.pagination a.next, a.next-page, li.next a, a:contains(»)"

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ================================================================================
    // SELECTORES NUEVOS PARA LA VISTA DETALLADA (HERO LAYOUT DE LA WEB)
    // ================================================================================

    override val mangaDetailsSelectorTitle = "h1.htitle"
    override val mangaDetailsSelectorDescription = "div.syn, div.syn p, div.description-p, div.summary_content div.post-content_item div:has(p)"
    override val mangaDetailsSelectorStatus = "span.htag--status, div.sir:has(.l:contains(Estado)) span.v, div.post-content_item:contains(Estado) div.summary-content"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        val genresAndTags = mutableListOf<String>()

        document.select("div.hchips--genres a.chip").forEach { element ->
            val genre = element.text().trim()
            if (genre.isNotBlank() && !genre.equals("Vip", ignoreCase = true)) {
                genresAndTags.add(genre)
            }
        }

        document.select("div.hchips--tags a.chip, a.chip--tag").forEach { element ->
            val tag = element.text().trim()
            if (tag.isNotBlank() && !tag.equals("Emperor scan", ignoreCase = true) && !genresAndTags.contains(tag)) {
                genresAndTags.add(tag)
            }
        }

        if (genresAndTags.isNotEmpty()) {
            manga.genre = genresAndTags.joinToString(", ")
        }

        return manga
    }

    // ================================================================================
    // FILTRADO DE CAPÍTULOS PREMIUM
    // ================================================================================

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector() = "div.clist a.crow, li.wp-manga-chapter, .crow"

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        if (document.select("div.clist a.crow").isEmpty()) {
            val postId = document.select("input.rating-post-id, input#wp-manga-action-button").attr("value")
                .takeIf { it.isNotEmpty() }
                ?: document.select("div.add-bookmark a[data-post], div.hact a[data-post]").attr("data-post")

            if (!postId.isNullOrBlank()) {
                val formBody = FormBody.Builder()
                    .add("action", "manga_get_chapters")
                    .add("manga", postId)
                    .build()

                val ajaxHeaders = headersBuilder()
                    .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .add("X-Requested-With", "XMLHttpRequest")
                    .build()

                val ajaxResponse = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, formBody)).execute()
                if (ajaxResponse.isSuccessful) {
                    document = ajaxResponse.asJsoup()
                }
            }
        }

        val chapters = document.select(chapterListSelector()).map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))

                val titleElement = element.selectFirst("span.ctitle") ?: element.selectFirst("a")
                name = titleElement?.text() ?: "Capítulo"

                val dateElement = element.selectFirst("span.cmeta") ?: element.selectFirst("span.chapter-release-date")
                val dateText = dateElement?.text()?.trim() ?: ""

                date_upload = if (dateText.isNotBlank()) {
                    parseCustomRelativeDate(dateText) ?: parseChapterDate(dateText)
                } else {
                    0L
                }
            }
        }

        val filteredChapters = if (removePremium) {
            chapters.filterNot { chapter ->
                chapter.url.contains("/membership-levels/") ||
                    chapter.name.contains("Vip", ignoreCase = true) ||
                    chapter.name.contains("Soberano", ignoreCase = true) ||
                    chapter.name.contains("Premium", ignoreCase = true)
            }
        } else {
            chapters
        }

        return filteredChapters.distinctBy { it.name.trim() }
    }

    private fun parseCustomRelativeDate(dateString: String): Long? {
        val trimmed = dateString.lowercase()
        val calendar = Calendar.getInstance()

        return try {
            val number = trimmed.substringBefore(" ").toInt()
            when {
                "minuto" in trimmed || "minutos" in trimmed -> {
                    calendar.add(Calendar.MINUTE, -number)
                    calendar.timeInMillis
                }
                "hora" in trimmed || "horas" in trimmed -> {
                    calendar.add(Calendar.HOUR_OF_DAY, -number)
                    calendar.timeInMillis
                }
                "día" in trimmed || "días" in trimmed || "dia" in trimmed -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -number)
                    calendar.timeInMillis
                }
                "semana" in trimmed || "semanas" in trimmed -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, -number)
                    calendar.timeInMillis
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()

        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP"
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
