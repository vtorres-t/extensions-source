package eu.kanade.tachiyomi.extension.es.dragontranslationorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DragonTranslationOrg : Madara() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("es"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // === SOLUCIÓN DEFINITIVA: SALTARSE EL AJAX ANTIGUO DE MADARA ===
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga/page/$page/?m_orderby=views"
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseNewMangaLayout(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga/page/$page/?m_orderby=latest"
        val document = client.newCall(GET(url, headers)).execute().asJsoup()
        return parseNewMangaLayout(document)
    }

    // Extractor manual adaptado a la nueva interfaz visual de DragonTranslation
    private fun parseNewMangaLayout(document: Document): MangasPage {
        val mangas = document.select("div.grid-items > div, div.grid-chapters > div").map { element ->
            SManga.create().apply {
                val anchor = element.select("a[href*=/manga/]").first() ?: element.select("a").first()
                anchor?.let { setUrlWithoutDomain(it.attr("href")) }

                title = element.select("h3, h2, h4, .title").text().trim().takeIf { it.isNotBlank() }
                    ?: element.select("img").attr("alt").trim()

                val img = element.select("img").first()
                thumbnail_url = img?.let {
                    val dataSrc = it.attr("data-src")
                    if (dataSrc.isNotBlank()) it.absUrl("data-src") else it.absUrl("src")
                }
            }
        }
        // Identificar si existe una página siguiente buscando las variantes del botón nativo
        val hasNextPage = document.selectFirst("div.navigation a.next, a.next, .pagination a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // === SELECTORES ADICIONALES (TU CÓDIGO ORIGINAL) ===
    override val filterGenresSelector = ".filters"
    override fun archiveSelector() = "div.grid-items > div, div.grid-chapters > div"
    override val archiveUrlSelector = "a"

    override val mangaDetailsSelectorTitle = "div.hero h1"
    override val mangaDetailsSelectorStatus = "div.htags > span.htag"
    override val mangaDetailsSelectorDescription = "div.syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter img"
    override val mangaDetailsSelectorGenre = "div.hchips a.chip"

    override val pageListParseSelector = "div.reading-content img.wp-manga-chapter-img"

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        element.attr("title").takeIf(String::isNotBlank)?.let { title = it }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("#mkChapters script[type=application/json]")!!.data()
        .parseAs<ChapterListDto>().items.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }
}
