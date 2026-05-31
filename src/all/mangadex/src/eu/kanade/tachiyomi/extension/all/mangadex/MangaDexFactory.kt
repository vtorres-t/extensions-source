package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaDexFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaDexEnglish(),
        MangaDexSpanishLatinAmerica(),
        MangaDexSpanishSpain(),
    )
}

class MangaDexEnglish : MangaDex("en")
class MangaDexSpanishLatinAmerica : MangaDex("es-419", "es-la")
class MangaDexSpanishSpain : MangaDex("es")

