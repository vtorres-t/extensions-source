package eu.kanade.tachiyomi.extension.es.esmi2manga

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EsMi2Manga :
    Madara(
        "Es.Mi2Manga",
        "https://es.mi2manga.com",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun popularMangaSelector() = "div.site-content div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"
    override fun searchMangaSelector() = "div.site-content div.c-tabs-item__content"

    private val preferences: SharedPreferences = getPreferences()

    override fun getFilterList(): FilterList {
        val filtros = super.getFilterList()
        val regexString = preferences.getString(REGEX_FILTER_KEY, REGEX_FILTER_DEFAULT) ?: ""

        if (regexString.isNotBlank()) {
            try {
                val regexFiltro = Regex(regexString, RegexOption.IGNORE_CASE)
                val fltGen = filtros.filterIsInstance<GenreList>().firstOrNull()

                if (fltGen != null) {
                    fltGen.vals = fltGen.vals.filter { genre ->
                        !regexFiltro.containsMatchIn(genre.name)
                    }.toTypedArray()
                }
            } catch (e: Exception) {
            }
        }

        return filtros
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val regexPreference = EditTextPreference(screen.context).apply {
            key = REGEX_FILTER_KEY
            title = "Filtrar géneros por Expresión Regular"
            summary = "Escribe los géneros que deseas ocultar separados por una barra vertical |. Ejemplo: hentai|adulto|gore"
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
        }

        screen.addPreference(regexPreference)
    }

    companion object {
        private const val REGEX_FILTER_KEY = "genre_regex_filter"
        private const val REGEX_FILTER_DEFAULT = "BL|Boy|Smut|Yaoi|Adult"
    }
}
