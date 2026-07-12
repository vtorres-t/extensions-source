import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pixiv"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf("en").forEach {
        source {
            lang = it
            baseUrl = "https://www.pixiv.net"
        }
    }

    deeplink {
        host("pixiv.net")
        host("www.pixiv.net")
        path("/en/artworks/..*")
        path("/artworks/..*")
        path("/en/users/..*")
        path("/users/..*")
        path("/user/..*/series/..*")
    }
}
