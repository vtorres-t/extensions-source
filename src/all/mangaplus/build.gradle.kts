import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
<<<<<<< HEAD
    name = "MANGA Plus"
    versionCode = 63
=======
    name = "MANGA Plus by SHUEISHA"
    versionCode = 64
>>>>>>> upstream/main
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    listOf("en", "es").forEach {
        source {
            lang = it
            baseUrl = "https://mangaplus.shueisha.co.jp"
        }
    }

    deeplink {
        host("mangaplus.shueisha.co.jp")
        host("www.mangaplus.shueisha.co.jp")
        host("jumpg-webapi.tokyo-cdn.com")
        host("www.jumpg-webapi.tokyo-cdn.com")
        path("/titles/..*")
        path("/viewer/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
