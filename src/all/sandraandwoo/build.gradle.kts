import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sandra and Woo"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://www.sandraandwoo.com"
        }
    }
}
