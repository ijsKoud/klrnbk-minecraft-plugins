plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Each of these lets a convention script below do id("...") without
    // redeclaring a version — the version lives in ../gradle/libs.versions.toml
    // and nowhere else.
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
    implementation(libs.run.paper.gradle.plugin)
    implementation(libs.run.velocity.gradle.plugin)
}
