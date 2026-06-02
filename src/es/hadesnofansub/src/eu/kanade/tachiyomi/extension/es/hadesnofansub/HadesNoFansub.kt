package eu.kanade.tachiyomi.extension.es.hadesnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HadesNoFansub :
    Madara(
        "Hades no Fansub",
        "https://lectorhades.latamtoon.com",
        "es",
    ) {
    override val useNewChapterEndpoint = true

    override val mangaSubString = "tmo"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorStatus = "div.post-content_item:has(h5:contains(Status)) div.summary-content"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator

}
