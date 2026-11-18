pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mathcat"

include(":mathcat-type-algebra")
include(":mathcat-core")
include(":mathcat-store")
include(":mathcat-actions")
include(":mathcat-api")
