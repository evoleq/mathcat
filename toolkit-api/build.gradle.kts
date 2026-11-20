plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { nodejs() }
    android {
        namespace = "evoleq.toolkit.api"
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
            api(project(":toolkit-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
