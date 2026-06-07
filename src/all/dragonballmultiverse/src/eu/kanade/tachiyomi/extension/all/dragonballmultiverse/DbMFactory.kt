@file:Suppress("ClassName")

package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DbMFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DbMultiverseEN(),
        DbMultiverseES(),
        DbMultiverseES_CO(),
    )
}

class DbMultiverseEN : DbMultiverse("en", "en")
class DbMultiverseES : DbMultiverse("es", "es")
class DbMultiverseES_CO : DbMultiverse("es-419", "es_CO")

