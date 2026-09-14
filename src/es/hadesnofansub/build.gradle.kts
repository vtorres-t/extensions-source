import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hades no Fansub"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "es"
        baseUrl = "https://lectorhades.latamtoon.com"
    }
}
