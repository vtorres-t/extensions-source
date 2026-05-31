package eu.kanade.tachiyomi.extension.all.myreadingmanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MyReadingMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map { MyReadingManga(it.tachiLang, it.siteLang, it.latestLang) }
}

private data class Source(val tachiLang: String, val siteLang: String, val latestLang: String = siteLang)

// These should all be valid. Add a language code and uncomment to enable
private val languageList = listOf(
    Source("en", "English"),
    Source("es", "Spanish"),
)
