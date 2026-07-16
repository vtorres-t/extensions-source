import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comick (Unoriginal)"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf(
        "en",
        "es",
    ).forEach {
        source {
            lang = it
            baseUrl {
                mirrors(
                    "https://comick.live",
                    "https://comick.art",
                )
            }
        }
    }
}
