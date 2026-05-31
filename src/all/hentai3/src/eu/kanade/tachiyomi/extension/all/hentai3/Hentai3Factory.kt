package eu.kanade.tachiyomi.extension.all.hentai3

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class Hentai3Factory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Hentai3("all", ""),
        Hentai3("en", "english"),
        Hentai3("es", "spanish"),
    )
}
