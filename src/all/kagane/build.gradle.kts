plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kagane"
    versionCode = 26
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    listOf("en", "es", "es-419").forEach {
        source {
            lang = it
            baseUrl = "https://kagane.to"
        }
    }
}

dependencies {

    compileOnly("com.squareup.okhttp3:okhttp-brotli:5.0.0-alpha.11")
}
