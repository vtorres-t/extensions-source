package eu.kanade.tachiyomi.extension.all.leagueoflegends

import eu.kanade.tachiyomi.source.SourceFactory

class LOLFactory : SourceFactory {
    override fun createSources() = listOf(
        LOLUniverse("en_us"),
        // LOLUniverse("en_gb"),
        LOLUniverse("es_es"),
        LOLUniverse("es_mx", "es-419"),
        // LOLUniverse("es_ar", "es-419"),
    )
}
