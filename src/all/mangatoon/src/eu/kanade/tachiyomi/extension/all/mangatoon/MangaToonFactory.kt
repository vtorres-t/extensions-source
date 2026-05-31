package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaToonFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        MangaToonEn(),
        MangaToonEs(),
    )
}

class MangaToonEn : MangaToon("en")
class MangaToonEs : MangaToon("es")
