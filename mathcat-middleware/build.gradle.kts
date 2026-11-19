plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { nodejs() }
    android {
        namespace = "org.evoleq.mathcat.middleware"
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
            api(project(":mathcat-actions"))
            api(project(":mathcat-ktorx"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}
