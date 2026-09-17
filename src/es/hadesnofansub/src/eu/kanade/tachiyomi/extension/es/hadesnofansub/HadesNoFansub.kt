package eu.kanade.tachiyomi.extension.es.hadesnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HadesNoFansub : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "tmo"

    override val mangaDetailsSelectorStatus = "div.summary_content > div.post-content div.post-content_item:has(div.summary-heading:contains(Status)) div.summary-content"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
        val chapter = super.chapterFromElement(element, mangaPath) ?: return null

        val dateElement = element.selectFirst("span.chapter-release-date span.timediff i")
        if (dateElement != null) {
            val dateText = dateElement.text().trim()
            chapter.date_upload = parseChapterDate(dateText)
        } else {
            val backupText = element.selectFirst("span.chapter-release-date")?.text()
            chapter.date_upload = parseChapterDate(backupText)
        }

        return chapter
    }
}
