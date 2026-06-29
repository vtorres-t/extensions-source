plugins {
    alias(kei.plugins.multisrc)
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
    compileOnly("org.jspecify:jspecify:1.0.0")
}

keiyoushi {
    baseVersionCode = 51
    libVersion = "1.4"

    deeplink {
        path("/.*/..*")
    }
}
