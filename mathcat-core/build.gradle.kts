plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()

    js(IR) {
        nodejs()
    }

    wasmJs {
        nodejs()
    }

    android {
        namespace = "org.evoleq.mathcat.core"
        compileSdk = 36
        minSdk = 24

        // Android unit tests for the same portable commonTest suite.
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
