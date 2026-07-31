import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 31
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es", "es-419").forEach {
        source {
            lang = it
            baseUrl = "https://kagane.to"
        }
    }

    deeplink {
        path("/series/..*")
    }
}
