package eu.kanade.tachiyomi.extension.all.kagane

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KaganeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KaganeEnglish(),
        KaganeSpanish(),
        KaganeSpanishLatAm(),
    )
}

class KaganeEnglish : Kagane("en", listOf("en"))
class KaganeSpanish : Kagane("es", listOf("es"))
class KaganeSpanishLatAm : Kagane("es-419", listOf("es-419"))
