// Completely separate consumer build: no project() references to MathCat.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
rootProject.name = "mathcat-publishing-smoke"
