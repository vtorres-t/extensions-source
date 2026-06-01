package eu.kanade.tachiyomi.extension.all.xkcd

import eu.kanade.tachiyomi.extension.all.xkcd.translations.XkcdES
import eu.kanade.tachiyomi.source.SourceFactory

class XkcdFactory : SourceFactory {
    override fun createSources() = listOf(
        Xkcd("https://xkcd.com", "en"),
        XkcdES(),
    )
}
