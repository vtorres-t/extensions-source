package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class EmperorScan :
    Madara(),
    ConfigurableSource {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimit(2) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override val mangaDetailsSelectorDescription = "div#syn"

    override val mangaDetailsSelectorStatus = "div.sir:has(span.l:contains(Estado)) span.v"

    override fun chapterDateSelector() = "span.cmeta"

    override val chapterUrlSelector = "span.ctitle"

    override fun popularMangaSelector() = "div.agrid a.acard"
    override fun latestUpdatesSelector() = "div.agrid a.acard"
    override val popularMangaUrlSelector = "div.ac-t"
    override val popularMangaUrlSelectorImg = "img.ac-cover"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("abs:href"))

        manga.title = element.selectFirst("div.ac-t")?.text()?.trim() ?: ""

        element.selectFirst(popularMangaUrlSelectorImg)?.let {
            manga.thumbnail_url = processThumbnail(imageFromElement(it), true)
        }

        return manga
    }

    override fun searchMangaSelector() = "div.agrid a.acard"
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)
        return if (removePremium) {
            "div.clist.list a.crow:not(.is-locked)"
        } else {
            "div.clist.list a.crow"
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.url = element.attr("abs:href").let {
            it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
        }

        chapter.name = element.selectFirst(chapterUrlSelector)?.text() ?: ""

        chapter.date_upload = parseChapterDate(element.selectFirst(chapterDateSelector())?.text())

        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        return chapters
            .let { list ->
                if (removePremium) {
                    list.filterNot { chapter ->
                        chapter.name.contains("Vip", ignoreCase = true) ||
                            chapter.name.contains("Soberano", ignoreCase = true)
                    }
                } else {
                    list
                }
            }
            .distinctBy { chapter ->
                chapter.name.trim()
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
