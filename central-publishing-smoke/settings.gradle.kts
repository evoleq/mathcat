// This is a separate consumer: no project(...) and no Maven Local fallback.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven(url = uri("../build/central-staging"))
        google()
        mavenCentral()
    }
}
rootProject.name = "toolkit-central-consumer"
