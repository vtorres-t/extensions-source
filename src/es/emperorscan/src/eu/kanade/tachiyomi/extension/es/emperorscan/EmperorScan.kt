package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.utils.getPreferences
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

    private val preferences: SharedPreferences = getPreferences()

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
        private const val REGEX_FILTER_DEFAULT = "Boys Love|Novela"
    }
}
