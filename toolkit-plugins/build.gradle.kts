plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
        browser()
    }
    wasmJs { nodejs() }
    android {
        namespace = "evoleq.toolkit.plugins"
        compileSdk = 36
        minSdk = 24
        withHostTestBuilder {}.configure {}
    }
    linuxX64()
    mingwX64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":toolkit-type-algebra"))
            api(project(":toolkit-core"))
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
