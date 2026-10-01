rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        // Reuse the SAME toml as the main build, so a version only ever
        // needs to be bumped in one place (../gradle/libs.versions.toml)
        // even though build-logic is technically a separate build.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
