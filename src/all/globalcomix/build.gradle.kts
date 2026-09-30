import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GlobalComix"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "en",
        "es",
    ).forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://globalcomix.com"
        }
    }

    deeplink {
        host("globalcomix.com")
        path("/c/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
