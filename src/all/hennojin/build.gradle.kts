plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hennojin"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en").forEach {
        source {
            lang = it
            baseUrl = "https://hennojin.com"
        }
    }
}
