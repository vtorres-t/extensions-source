package eu.kanade.tachiyomi.extension.all.akuma

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class AkumaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Akuma("all", "all"),
        Akuma("en", "english"),
        Akuma("es", "spanish"),
    )
}
