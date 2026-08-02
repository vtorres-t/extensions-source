package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient

@Source
abstract class HentaiHand : HentaiHand() {

    override val chapters = false

    override val hhLangId = when (lang) {
        "all" -> emptyList()
        "en" -> listOf(2, 27)
        "other" -> listOf(6)
        "uk" -> listOf(12, 46)
        "es" -> listOf(33, 37)
        else -> emptyList()
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}
