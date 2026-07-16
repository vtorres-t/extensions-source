import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhuarm"
    versionCode = 25
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madara"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://manhuarmtl.com"
        }
    }
}
