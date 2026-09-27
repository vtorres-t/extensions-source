import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf(
        "en",
        "es-419",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://namicomi.com"
        }
    }

    deeplink {
        path("/.*/title/..*")
    }
}
