import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Olympus Scanlation"
<<<<<<< HEAD
    versionCode = 22
=======
    versionCode = 0
>>>>>>> upstream/main
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl {
            custom("https://olympusxyz.com")
        }
        versionId = 3
    }
}
