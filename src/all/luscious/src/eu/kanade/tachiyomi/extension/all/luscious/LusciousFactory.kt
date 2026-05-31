package eu.kanade.tachiyomi.extension.all.luscious

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LusciousFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LusciousEN(),
        LusciousES(),
        LusciousOTHER(),
        LusciousALL(),
    )
}

class LusciousEN : Luscious("en")
class LusciousES : Luscious("es")
class LusciousOTHER : Luscious("other")
