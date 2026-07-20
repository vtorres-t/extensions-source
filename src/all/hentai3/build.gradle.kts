import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3Hentai"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

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

    deeplink {
        path("/d/..*")
    }
}
