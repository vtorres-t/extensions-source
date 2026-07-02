plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf(
        "en", "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangaball.net"
        }
    }

    deeplink {
        host("mangaball.net")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}
