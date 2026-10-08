import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Emperor Scan"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://imperiomanhwa.com"
    }
}
