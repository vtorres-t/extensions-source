import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiRox"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "galleryadults"

<<<<<<< HEAD
    listOf("en", "all").forEach { language ->
=======
    listOf("en", "ja", "all").forEach { language ->
>>>>>>> upstream/main
        source {
            lang = language
            baseUrl = "https://hentairox.com"
        }
    }
}
