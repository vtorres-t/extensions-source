package eu.kanade.tachiyomi.extension.es.emperorscan

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class EmperorScan :
    Madara(
        "Emperor Scan",
        "https://imperiomanhua.com",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ),
    ConfigurableSource {

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .setRandomUserAgent()

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override val mangaDetailsSelectorDescription = "div.summary_content div.post-content_item:has(h5:contains(Sinopsis)) div"

    override val mangaDetailsSelectorStatus = "div.post-content_item:has(h5:contains(Estado)) div.summary-content"

    override fun chapterListParse(response: Response): List<SChapter> {
        // 1. Obtiene la lista completa procesada originalmente por el CMS Madara
        val chapters = super.chapterListParse(response)

        // 2. Filtra y limpia la lista
        return chapters
            // Descarta los que contienen la palabra "soberano"
            .filterNot { chapter ->
                chapter.name.contains("Vip", ignoreCase = true)
            }
            // Elimina nombres duplicados, conservando SIEMPRE el primero encontrado
            .distinctBy { chapter ->
                chapter.name.trim()
            }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }
}
