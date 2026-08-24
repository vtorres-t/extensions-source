import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaClub.net"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "madara"

    listOf("en").forEach {
        source {
            lang = it
            baseUrl = "https://manhwaclub.net"
        }
    }
}
