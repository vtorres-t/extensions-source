import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NamiComi"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

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
        host("namicomi.com")
        path("/.*/title/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
