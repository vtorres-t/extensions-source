plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3Hentai"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf(
        "all",
        "en",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://3hentai.net"
        }
    }
}
