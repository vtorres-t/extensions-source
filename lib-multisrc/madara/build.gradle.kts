import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 51

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
    compileOnly("org.jspecify:jspecify:1.0.0")
}
