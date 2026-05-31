package eu.kanade.tachiyomi.extension.all.mangaball

import eu.kanade.tachiyomi.source.SourceFactory

class MangaBallFactory : SourceFactory {
    override fun createSources() = listOf(
        MangaBall("en", "en"),
        MangaBall("es", "es", "es-ar", "es-mx", "es-es", "es-la", "es-419"),
    )
}
