package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EmperorScan :
    Madara(),
    ConfigurableSource {

    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

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

        // Extracción robusta de géneros en la nueva disposición de elementos en ImperioManhua
        val genres = mutableListOf<String>()
        document.select("div.hchips--genres a.chip, div.genres-content a").forEach { element ->
            val genre = element.text()
            if (genre.isNotBlank()) {
                genres.add(genre)
            }
        }

        if (genres.isNotEmpty()) {
            manga.genre = genres.joinToString(", ")
        }

        return manga
    }

    // ================================================================================
    // FILTRADO DE CAPÍTULOS PREMIUM
    // ================================================================================

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)
        return if (removePremium) {
            "li.wp-manga-chapter:not(:has(.required-login)):not(:has(.vip-icon)):not(:has(.premium-block))"
        } else {
            "li.wp-manga-chapter"
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        val filteredChapters = if (removePremium) {
            chapters.filterNot { chapter ->
                chapter.name.contains("Vip", ignoreCase = true) ||
                    chapter.name.contains("Soberano", ignoreCase = true) ||
                    chapter.name.contains("Premium", ignoreCase = true)
            }
        } else {
            chapters
        }

        return filteredChapters.distinctBy { it.name.trim() }
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
