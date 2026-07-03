plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "GlobalComix"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

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
