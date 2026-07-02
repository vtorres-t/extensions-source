plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luscious"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "es", "other", "all").forEach {
        source {
            lang = it
            baseUrl("https://www.luscious.net") {
                mirrors = listOf("https://members.luscious.net")
            }
        }
    }

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}
