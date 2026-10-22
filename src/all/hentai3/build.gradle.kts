import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3Hentai"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
<<<<<<< HEAD
        "all",
        "en",
        "es",
=======
        "all", "en", "ja", "ko", "zh", "es", "pt", "id", "jv",
        "tl", "vi", "th", "my", "tr", "ru", "uk", "pl", "de",
        "it", "fr", "nl", "cs", "hu", "bg", "is", "la", "ar",
>>>>>>> upstream/main
    ).forEach {
        source {
            lang = it
            baseUrl = "https://3hentai.net"
        }
    }

    deeplink {
        path("/d/..*")
    }
}
