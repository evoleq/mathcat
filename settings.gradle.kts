pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Kotlin 2.3.20's commonMain KMP metadata resolution can request
        // kotlin-stdlib's 'all' classifier. Keep this targeted workaround
        // for publication modes only; remove when upstream resolution is fixed.
        val publicationBuild =
            providers.environmentVariable("JITPACK").orNull.equals("true", ignoreCase = true) ||
            providers.gradleProperty("centralPublishing").orNull.equals("true", ignoreCase = true) ||
            providers.environmentVariable("CENTRAL_PUBLISHING").orNull.equals("true", ignoreCase = true)
        if (publicationBuild) {
            exclusiveContent {
                forRepository {
                    maven { url = uri("https://packages.jetbrains.team/maven/p/kt/dev") }
                }
                filter { includeModule("org.jetbrains.kotlin", "kotlin-stdlib") }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "toolkit"

include(":toolkit-type-algebra")
include(":toolkit-core")
include(":toolkit-store")
include(":toolkit-actions")
include(":toolkit-api")
include(":toolkit-result")
include(":toolkit-serialization")
include(":toolkit-ktorx")
include(":toolkit-middleware")
include(":toolkit-plugins")
include(":toolkit-plugins-actions")
include(":toolkit-context")
include(":toolkit-transform")
include(":toolkit-serialization-ksp")
include(":toolkit-serialization-ksp-fixture")
include(":toolkit-routing")
include(":toolkit-evolution")
include(":toolkit-evolution-runtime")
