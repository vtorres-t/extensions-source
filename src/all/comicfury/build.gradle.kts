import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Fury"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    val comicFuryUrl = "https://comicfury.com"

    listOf("all", "en", "es", "other").forEach {
        source {
            lang = it
            baseUrl = comicFuryUrl
        }
    }
    source {
        name = "Comic Fury (No Text)"
        lang = "other"
        baseUrl = comicFuryUrl
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}
