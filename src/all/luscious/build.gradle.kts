import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Luscious"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "es", "other", "all").forEach {
        source {
            lang = it
            baseUrl {
                mirrors(
                    "https://www.luscious.net",
                    "https://members.luscious.net",
                )
            }
        }
    }

    deeplink {
        host("www.luscious.net")
        host("members.luscious.net")
        path("/albums/..*")
    }
}
