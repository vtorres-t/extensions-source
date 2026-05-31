package eu.kanade.tachiyomi.extension.all.comicfury

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ComicFuryFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ComicFury("all"),
        ComicFury("en"),
        ComicFury("es"),
        ComicFury("other"),
        ComicFury("other", "notext", " (No Text)"),
    )
}
