package eu.kanade.tachiyomi.extension.all.comicklive

import eu.kanade.tachiyomi.source.SourceFactory

class ComickFactory : SourceFactory {
    // as of 2025-10-15, the commented languages have 0 chapters uploaded
    // from: /api/languages
    override fun createSources() = listOf(
        Comick("en"),
        // Comick("es-419", "es-la"),
        Comick("es"),
    )
}
