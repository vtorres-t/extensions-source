package eu.kanade.tachiyomi.extension.es.mhscans

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MHScans :
    Madara(),
    ConfigurableSource {

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))

    override val mangaSubString = "series"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 3.seconds)

    override val chapterMode = ChapterMode.MangaAjax

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val baseSelector = super.chapterListSelector()
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        if (!removePremium) {
            return baseSelector
        }

        return "$baseSelector:not(.premium)"
    }

<<<<<<< HEAD
    override fun chapterListParse(response: okhttp3.Response): List<eu.kanade.tachiyomi.source.model.SChapter> {
        val chapters = super.chapterListParse(response)
        chapters.forEach { chapter ->
            chapter.name = limpiarTextoCapitulo(chapter.name)
        }
        return chapters
    }

    override fun chapterFromElement(element: org.jsoup.nodes.Element): eu.kanade.tachiyomi.source.model.SChapter {
        val chapter = super.chapterFromElement(element)
        chapter.name = limpiarTextoCapitulo(chapter.name)
        return chapter
    }

    private fun limpiarTextoCapitulo(name: String): String {
        val regex = Regex(
            """\s*(?:-\s*)?\[?\s*(?:AL D[IÍ]A(?: CON LA RAW)?|Promo Especial|PACK DE CAP[IÍ]TULOS|PACK|Marat[oó]n de Caps|SUPER PACK|AVISO IMPORTANTE \\(No es cap[ií]tulo como tal\\)|Versi[oó]n pocha|Versi[oó]n con calidad|Versi[oó]n Definitiva|TEMPORAL|Sin Censura|ESTRENO)\s*\]?\s*""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.CANON_EQ),
        )

        return name.replace(regex, "").trim().replace(Regex("\\s+"), " ")
    }

    override fun pageListParse(document: Document): List<Page> {
        super.pageListParse(document).also {
            if (it.isNotEmpty()) return it
        }

        document.selectFirst("form#rk_madara_redirect[method=post]")?.let { form ->
            val url = form.attr("action")
            val headers = headersBuilder().set("Referer", document.location()).build()
            val body = FormBody.Builder()
            form.select("input").forEach {
                body.add(it.attr("name"), it.attr("value"))
            }
            return pageListParse(client.newCall(POST(url, headers, body.build())).execute().asJsoup())
        }

        return document.select("div.rk-page-wrap img, img.rk-img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") })
        }
    }

=======
>>>>>>> upstream/main
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos de pago"
            summary = "Oculta automáticamente los capítulos que requieren Taels."
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
