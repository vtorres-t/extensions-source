import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lunar Manga"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    val languages = listOf(
        "all",
        "en",
        "es",
        "es-419",
    )

    languages.forEach { language ->
        source {
            baseUrl = "https://lunaranime.ru"
            lang = language
        }
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
