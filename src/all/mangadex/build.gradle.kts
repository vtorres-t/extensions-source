import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDex"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "en",
        "es-419",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangadex.org"
        }
    }

    deeplink {
        host("mangadex.org")
        host("canary.mangadex.dev")
        path("/title/..*")
        path("/manga/..*")
        path("/chapter/..*")
        path("/group/..*")
        path("/author/..*")
        path("/user/..*")
        path("/list/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
