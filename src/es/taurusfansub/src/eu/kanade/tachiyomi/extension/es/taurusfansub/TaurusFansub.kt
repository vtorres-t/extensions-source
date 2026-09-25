package eu.kanade.tachiyomi.extension.es.taurusfansub

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
abstract class TaurusFansub :
    Madara(),
    ConfigurableSource {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2, 1.seconds)

    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.manga-status span:last-child"
    override val mangaDetailsSelectorDescription = "div.summary__content p"

<<<<<<< HEAD
    override fun parseGenres(document: Document): List<Genre> = document.select(".genres-filter .options a")
        .mapNotNull { element ->
            val name = element.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = element.absUrl("href").toHttpUrlOrNull()?.queryParameter("genre")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Genre(name, id)
        }

    private val preferences: SharedPreferences by lazy { getPreferences() }
=======
    private val preferences: SharedPreferences = getPreferences()
>>>>>>> upstream/main

    override fun chapterListSelector(): String {
        val base = super.chapterListSelector()
        return if (preferences.removePremium) "$base:not(.scheduled)" else base
    }
    override val filterGenresSelector = ".manga-genres"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos de pago"
            summary = "Oculta automáticamente los capítulos que requieren pago."
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    private val SharedPreferences.removePremium
        get() = getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    companion object {
        private val DATE_FORMATTER = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
