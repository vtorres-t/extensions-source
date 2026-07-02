plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Akuma"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf(
        "all", "en", "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://akuma.moe"
        }
    }

    deeplink {
        host("akuma.moe")
        path("/g/..*")
    }
}
