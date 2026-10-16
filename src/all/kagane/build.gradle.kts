import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 34
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es", "es-419").forEach {
        source {
            lang = it
            baseUrl {
                mirrors(
                    "https://kagane.si",
                    "https://kagane.cx",
                    "https://kagane.moe",
                    "https://kagane.net",
                )
            }
        }
    }

    deeplink {
        path("/series/..*")
    }
}
