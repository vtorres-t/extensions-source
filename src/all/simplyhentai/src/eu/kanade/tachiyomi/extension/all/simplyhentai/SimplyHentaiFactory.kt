package eu.kanade.tachiyomi.extension.all.simplyhentai

import eu.kanade.tachiyomi.source.SourceFactory

class SimplyHentaiFactory : SourceFactory {
    override fun createSources() = listOf(
        SimplyHentai("en", "english"),
        SimplyHentai("es", "spanish"),
    )
}
