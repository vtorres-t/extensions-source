package eu.kanade.tachiyomi.extension.es.hadesnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.ParseException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HadesNoFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "tmo"

    override val mangaDetailsSelectorStatus = "div.summary_content > div.post-content div.post-content_item:has(div.summary-heading:contains(Status)) div.summary-content"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)

        val dateElement = element.selectFirst("span.chapter-release-date span.timediff i")
        if (dateElement != null) {
            val dateText = dateElement.text().trim()
            chapter.date_upload = parsearFechaManual(dateText)
        } else {
            chapter.date_upload = parsearFechaManual(element.selectFirst("span.chapter-release-date")?.text())
        }

        return chapter
    }

    private fun parsearFechaManual(date: String?): Long {
        if (date.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }
}
