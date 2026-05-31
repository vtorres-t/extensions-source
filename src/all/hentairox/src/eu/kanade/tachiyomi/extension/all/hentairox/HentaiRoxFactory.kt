package eu.kanade.tachiyomi.extension.all.hentairox

import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HentaiRoxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HentaiRox("en", GalleryAdults.LANGUAGE_ENGLISH),
        HentaiRox("all", GalleryAdults.LANGUAGE_MULTI),
    )
}
