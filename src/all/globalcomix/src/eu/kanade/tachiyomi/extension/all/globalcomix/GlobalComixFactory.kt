package eu.kanade.tachiyomi.extension.all.globalcomix

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GlobalComixFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        GlobalComixEnglish(),
        GlobalComixSpanish(),
    )
}
class GlobalComixEnglish : GlobalComix("en")
class GlobalComixSpanish : GlobalComix("es")
