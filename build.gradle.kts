plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    id("com.android.kotlin.multiplatform.library") version "9.0.0" apply false
}

// All MathCat modules currently share one verified repository release version.
// Passing a release version is explicit; ordinary local builds are snapshots.
val artifactVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("RELEASE_VERSION"))
    .orElse("0.0.0-SNAPSHOT")

allprojects {
    group = "org.evoleq"
    version = artifactVersion.get()
}

tasks.register<Exec>("verifyReleaseCompatibility") {
    group = "verification"
    description = "Verify the module compatibility manifest against Gradle declarations."
    commandLine("python3", "scripts/release.py", "validate")
}

tasks.register<Exec>("planRelease") {
    group = "release"
    description = "Plan a SemVer source release without creating tags or publishing anything."
    dependsOn("verifyReleaseCompatibility")
    // Git tags and commit history are external mutable inputs to this task.
    doNotTrackState("Git tags and commit history are not Gradle inputs")
    commandLine(
        "python3", "scripts/release.py", "plan", "--output",
        layout.buildDirectory.file("release/plan.json").get().asFile.absolutePath,
    )
}
