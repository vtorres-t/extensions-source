import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webtoons.com"
    versionCode = 57
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://www.webtoons.com"
        }
    }

    deeplink {
        host("webtoons.com")
        host("www.webtoons.com")
        host("m.webtoons.com")
        path("/.*/.*/.*/..*")
        path("/.*/.*/.*/.*/..*")
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}
