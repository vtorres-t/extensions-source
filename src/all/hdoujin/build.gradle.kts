plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HDoujin"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("all", "en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://hdoujin.org"
        }
    }
}
