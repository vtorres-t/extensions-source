import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Hentai"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://www.simply-hentai.com"
            versionId = 2
        }
    }
}
