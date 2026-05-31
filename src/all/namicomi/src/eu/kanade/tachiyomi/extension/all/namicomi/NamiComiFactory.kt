package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NamiComiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NamiComiEnglish(),
        NamiComiSpanishLatinAmerica(),
        NamiComiSpanishSpain(),
    )
}

class NamiComiEnglish : NamiComi("en")
class NamiComiSpanishLatinAmerica : NamiComi("es-419")
class NamiComiSpanishSpain : NamiComi("es", "es-es")
