// Completely separate consumer build: no project() references to ToolKit.
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
rootProject.name = "toolkit-publishing-smoke"
