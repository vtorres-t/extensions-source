plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHand"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "hentaihand"

    val languages = listOf(
        "all",
        "en",
        "other",
        "es",
    )

    languages.forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentaihand.com"

            when (language) {
                "all" -> id = 1235047015955289468L
                "other" -> id = 7302549142935671434L
            }
        }
    }
}
