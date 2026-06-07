package eu.kanade.tachiyomi.extension.es.mhscans

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MHScans :
    Madara(
        "MHScans",
        "https://mhscans.com",
        "es",
        dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
    ),
    ConfigurableSource {
    override val mangaSubString = "series"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 3, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.AutoDetect

    private val preferences: SharedPreferences = getPreferences()

    override fun chapterListSelector(): String {
        val baseSelector = super.chapterListSelector()
        val removePremium = preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

        if (!removePremium) {
            return baseSelector
        }

        return "$baseSelector:not(.premium)"
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

    override fun getFilterList(): FilterList {
        val regexString = preferences.getString(REGEX_FILTER_KEY, REGEX_FILTER_DEFAULT) ?: ""

        if (regexString.isNotBlank() && genresList.isNotEmpty()) {
            try {
                val regexFiltro = Regex(regexString, RegexOption.IGNORE_CASE)

                genresList = genresList.filter { genre ->
                    !regexFiltro.containsMatchIn(genre.name)
                }
            } catch (e: Exception) {
                // Captura fallos si el usuario escribe un patrón Regex incorrecto en los ajustes
            }
        }

        return super.getFilterList()
    }

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

        EditTextPreference(screen.context).apply {
            key = REGEX_FILTER_KEY
            title = "Filtrar géneros por Expresión Regular"
            summary = "Escribe los géneros que deseas ocultar separados por |. Ejemplo: BL|18|Yaoi"
            dialogTitle = "Expresión Regular de Exclusión"
            dialogMessage = "Sintaxis estándar de Regex (case-insensitive)"
            setDefaultValue(REGEX_FILTER_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val stringValue = newValue as String
                try {
                    Regex(stringValue)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
        private const val REGEX_FILTER_KEY = "genre_regex_filter"
        private const val REGEX_FILTER_DEFAULT = ""
    }
}
