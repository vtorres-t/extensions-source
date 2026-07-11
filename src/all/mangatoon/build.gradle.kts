import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaToon (Limited)"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://mangatoon.mobi"
        }
    }
}
