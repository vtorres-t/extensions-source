plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dragon Ball Multiverse"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    val dbmUrl = "https://www.dragonball-multiverse.com"

    listOf(
        "en", "es",
        "es-419",
    ).forEach {
        source {
            lang = it
            baseUrl = dbmUrl
        }
    }
    source {
        name = "Dragon Ball Multiverse Parody"
        lang = "fr"
        baseUrl = dbmUrl
    }
}
