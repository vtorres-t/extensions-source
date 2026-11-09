import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 42
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es", "es-419").forEach {
        source {
            lang = it
            baseUrl {
                mirrors("https://kagane.moe")
            }
        }
    }

    deeplink {
        host("kagane.cx")
        host("kagane.moe")
        host("kagane.net")
        path("/series/..*")
    }
}
