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
        namespace = "evoleq.toolkit.ktorx"
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
            api(project(":toolkit-api"))
            api(project(":toolkit-result"))
            api(project(":toolkit-serialization"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            api(libs.ktor.server.core)
            implementation(libs.ktor.server.auth.jwt)
            api(project(":toolkit-transform"))
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.test.host)
        }
    }
}
