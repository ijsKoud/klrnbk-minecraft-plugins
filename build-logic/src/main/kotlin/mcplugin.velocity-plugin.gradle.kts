import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.Jar
import xyz.jpenilla.runvelocity.task.RunVelocity

plugins {
    id("mcplugin.kotlin-conventions")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-velocity")
    id("org.jetbrains.kotlin.kapt")
}

// See mcplugin.kotlin-conventions.gradle.kts for why this isn't `libs.xxx`.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencies {
    compileOnly(lib("velocity-api"))
    kapt(lib("velocity-api"))

    // compileOnly isn't visible to the test source set, so it's
    // re-declared here.
    testImplementation(lib("velocity-api"))
    testImplementation(lib("mockk"))
}

tasks {
    named<Jar>("jar") {
        // See mcplugin.paper-plugin.gradle.kts for why: the plain jar isn't
        // the distributable artifact, and now that archivesName bakes the
        // platform in too, it needs its own classifier so it can't
        // collide with shadowJar's output.
        archiveClassifier.set("thin")
    }

    named<ShadowJar>("shadowJar") {
        // Empty classifier — the actual distributable jar gets the clean
        // "<pluginId>-<platform>-<version>.jar" name instead of a
        // redundant "...-velocity-velocity.jar".
        archiveClassifier.set("")

        relocate("kotlinx.coroutines", "${project.group}.${project.name}.libs.kotlinx.coroutines")
        relocate("kotlinx.serialization", "${project.group}.${project.name}.libs.kotlinx.serialization")

        if (!project.path.startsWith(":plugins:runtime:")) {
            exclude("kotlin/**")
            exclude("kotlinx/**")
            exclude("com/fasterxml/**")
            exclude("com/google/inject/**")
            // Guava's real package root is com/google/common (there is no com/google/guava) — the old
            // exclude matched nothing and every jar carried ~3 MB of Guava plus its annotation jars. The
            // platform (Velocity/Paper) provides Guava; the shared runtime plugin provides Guice.
            exclude("com/google/common/**")
            exclude("com/google/thirdparty/**")
            exclude("com/google/errorprone/**")
            exclude("com/google/j2objc/**")
            exclude("org/checkerframework/**")
            exclude("org/aopalliance/**")
            exclude("javax/inject/**")
            exclude("jakarta/inject/**")
            exclude("javax/annotation/**")
            exclude("org/intellij/lang/annotations/**")
            exclude("org/jetbrains/annotations/**")
            exclude("org/yaml/snakeyaml/**")
            exclude("com/zaxxer/**")
            exclude("org/jetbrains/exposed/**")
            exclude("org/postgresql/**")
            exclude("org/mariadb/**")
            exclude("org/sqlite/**")
            exclude("org/xerial/**")
        }
    }

    build {
        dependsOn(named("shadowJar"))
    }

    named<RunVelocity>("runVelocity") {
        // Velocity moved to a 4.x API generation alongside the MC 26.2 work
        // (velocity-api above is 4.1.0-SNAPSHOT). This targets the latest
        // stable 4.x proxy build for local testing — check
        // https://papermc.io/downloads/velocity for the current one before
        // relying on this.
        velocityVersion("4.0.0")
    }
}
