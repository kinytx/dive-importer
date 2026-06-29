// android-dive-importer / root build script
//
// 跟 ble probe 同款 Gradle 8.13.x + AGP 8 + Kotlin 2.x 体系。
// 所有 plugin 版本在 gradle/libs.versions.toml 统一管控。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
