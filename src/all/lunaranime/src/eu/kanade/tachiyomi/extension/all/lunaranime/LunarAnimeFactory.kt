package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LunarAnimeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LunarAnime("all"),
        LunarAnime("en"),
        LunarAnime("es"),
        LunarAnime("es-419"),
    )
}
