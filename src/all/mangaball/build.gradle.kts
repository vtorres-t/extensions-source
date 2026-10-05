import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Ball"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "en",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangaball.com"
        }
    }

    deeplink {
        host("mangaball.com")
        host("mangaball.net")
        path("/title-detail/..*")
        path("/chapter-detail/..*")
    }
}
