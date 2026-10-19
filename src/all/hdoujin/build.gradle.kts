import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HDoujin"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://hdoujin.org"
        }
    }
}
