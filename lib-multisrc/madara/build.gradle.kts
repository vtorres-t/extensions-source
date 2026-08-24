plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:i18n"))
    compileOnly("org.jspecify:jspecify:1.0.0")
}

keiyoushi {
    baseVersionCode = 53
    libVersion = "1.6"

    deeplink {
        path("/.*/..*")
    }
}
