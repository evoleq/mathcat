plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { nodejs() }
    android {
        namespace = "org.evoleq.mathcat.serialization"
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
            api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
            api(project(":mathcat-result"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}
