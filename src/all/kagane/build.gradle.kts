import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 28
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf("en", "es", "es-419").forEach {
        source {
            lang = it
            baseUrl = "https://kagane.to"
        }
    }
}
