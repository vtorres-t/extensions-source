package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import okhttp3.OkHttpClient

class HentaiHandFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        // https://hentaihand.com/api/languages?per_page=50
        HentaiHandOther(),
        HentaiHandEn(),
        HentaiHandEs(),
    )
}
abstract class HentaiHandCommon(
    override val lang: String,
    hhLangId: List<Int> = emptyList(),
    // altLangId: Int? = null
) : HentaiHand("HentaiHand", "https://hentaihand.com", lang, false, hhLangId) {
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}

class HentaiHandOther : HentaiHandCommon("all") {
    override val id: Long = 1235047015955289468
}
class HentaiHandEn : HentaiHandCommon("en", listOf(2, 27))
class HentaiHandNoText : HentaiHandCommon("other", listOf(6)) {
    override val id: Long = 7302549142935671434
}
class HentaiHandEs : HentaiHandCommon("es", listOf(33, 37))

