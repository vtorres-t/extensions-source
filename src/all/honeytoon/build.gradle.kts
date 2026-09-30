import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Honeytoon"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://honeytoon.com"
        }
    }

    deeplink {
        host("honeytoon.com")
        path("/comic/..*")
        path("/de/comic/..*")
        path("/es/comic/..*")
        path("/fr/comic/..*")
        path("/it/comic/..*")
        path("/pt/comic/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
