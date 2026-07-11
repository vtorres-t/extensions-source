import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toomics"
    versionCode = 10
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "es-419", "es").forEach { langCode ->
        source {
            name = "Toomics (Only free chapters)"
            lang = langCode
            baseUrl = "https://global.toomics.com"
            when (langCode) {
                "es-419" -> id = 7362369816539610504L
            }
        }
    }
}
