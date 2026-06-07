package eu.kanade.tachiyomi.extension.es.esmi2manga

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.utils.getPreferences
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EsMi2Manga :
    Madara(
        "Es.Mi2Manga",
        "https://es.mi2manga.com",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ),
    ConfigurableSource {

    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun popularMangaSelector() = "div.site-content div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"
    override fun searchMangaSelector() = "div.site-content div.c-tabs-item__content"

    private val preferences: SharedPreferences = getPreferences()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)
        return filterMangasPage(mangasPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangasPage = super.latestUpdatesParse(response)
        return filterMangasPage(mangasPage)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = super.searchMangaParse(response)
        return filterMangasPage(mangasPage)
    }

    private fun filterMangasPage(mangasPage: MangasPage): MangasPage {
        val regexString = preferences.getString(REGEX_FILTER_KEY, REGEX_FILTER_DEFAULT) ?: ""

        if (regexString.isNotBlank() && mangasPage.mangas.isNotEmpty()) {
            try {
                val regexFiltro = Regex(regexString, RegexOption.IGNORE_CASE)

                val mangasFiltrados = mangasPage.mangas.filter { manga ->
                    val generosDelManga = manga.genre

                    if (generosDelManga != null) {
                        !regexFiltro.containsMatchIn(generosDelManga)
                    } else {
                        true
                    }
                }

                return MangasPage(mangasFiltrados, mangasPage.hasNextPage)
            } catch (e: Exception) {
                // Captura fallos si el usuario escribe un patrón Regex incorrecto en los ajustes
            }
        }
        return mangasPage
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
        private const val REGEX_FILTER_KEY = "genre_regex_filter"
        private const val REGEX_FILTER_DEFAULT = "BL|Boy|Smut|Yaoi|Adult"
    }
}
