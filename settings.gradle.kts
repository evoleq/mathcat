pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Kotlin 2.3.20's commonMain metadata resolution requests the
        // kotlin-stdlib 'all' classifier. It is present in JetBrains'
        // repository but not available from the Maven Central variant
        // selected by Gradle during KMP root publication.
        // Limit this workaround to publication smoke/JitPack builds.
        if (providers.environmentVariable("JITPACK").orNull.equals("true", ignoreCase = true)) {
            exclusiveContent {
                forRepository {
                    maven {
                        url = uri("https://packages.jetbrains.team/maven/p/kt/dev")
                    }
                }
                filter {
                    includeModule("org.jetbrains.kotlin", "kotlin-stdlib")
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "mathcat"

include(":mathcat-type-algebra")
include(":mathcat-core")
include(":mathcat-store")
include(":mathcat-actions")
