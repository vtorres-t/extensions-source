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
<<<<<<< HEAD
            baseUrl("https://www.luscious.net") {
                mirrors = listOf("https://members.luscious.net")
=======
            if (it == "pt-BR") id = 5826725746643311801L
            baseUrl {
                mirrors(
                    "https://www.luscious.net",
                    "https://members.luscious.net",
                )
>>>>>>> upstream/main
            }
        }
    }

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}
