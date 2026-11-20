plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// All MathCat modules currently share one verified repository release version.
// Passing a release version is explicit; ordinary local builds are snapshots.
// JitPack exposes individual modules under com.github.<owner>.<repository>.
// Match the group on every publication so internal dependencies resolve.
val isJitPack = providers.environmentVariable("JITPACK")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val artifactVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("RELEASE_VERSION"))
    .orElse(
        if (isJitPack) providers.environmentVariable("VERSION")
        else providers.provider { "0.0.0-SNAPSHOT" }
    )

if (isJitPack && !artifactVersion.isPresent) {
    error("JITPACK=true requires VERSION (Git tag) or an explicit releaseVersion")
}

allprojects {
    group = if (isJitPack) "com.github.evoleq.mathcat" else "org.evoleq"
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
