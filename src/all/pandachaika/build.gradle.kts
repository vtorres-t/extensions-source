import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PandaChaika"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf(
        "all",
        "en",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://panda.chaika.moe"
        }
    }

    deeplink {
        host("panda.chaika.moe")
        path("/archive/..*")
    }
}
