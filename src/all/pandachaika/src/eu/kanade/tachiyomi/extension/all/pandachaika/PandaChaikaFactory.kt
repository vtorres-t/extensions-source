package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PandaChaikaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        PandaChaika(),
        PandaChaika("en", "english"),
        PandaChaika("es", "spanish"),
    )
}
