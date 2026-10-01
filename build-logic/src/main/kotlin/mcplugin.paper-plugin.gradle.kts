import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.bundling.Jar
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("mcplugin.kotlin-conventions")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

// See mcplugin.kotlin-conventions.gradle.kts for why this isn't `libs.xxx`.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = catalog.findLibrary(alias).get()

dependencies {
    compileOnly(lib("paper-api"))

    // compileOnly isn't visible to the test source set, so it's
    // re-declared here — MockBukkit needs real Paper types to compile
    // against.
    testImplementation(lib("paper-api"))
    testImplementation(lib("mockbukkit"))
}

tasks {
    processResources {
        // paper-plugin.yml uses Ant/Maven-style @token@ placeholders, NOT
        // Groovy's ${property} — that mismatch (this used to call
        // expand(), which only understands ${property}) is why @version@
        // was landing in the built jar completely unsubstituted.
        //
        // project.version is read here, at configuration time, into a
        // plain local val — NOT inside the filesMatching{} block below.
        // filesMatching's action runs at task EXECUTION time, and the
        // configuration cache forbids `Task.project` access then
        // ("invocation of 'Task.project' at execution time is unsupported
        // with the configuration cache"); referencing project.version
        // directly inside that block silently produced a no-op filter
        // instead of a hard failure. Capturing the value up here avoids
        // touching `project` from the execution-time action at all.
        val versionString = project.version.toString()
        inputs.property("version", versionString)
        filesMatching("paper-plugin.yml") {
            filter<ReplaceTokens>("tokens" to mapOf("version" to versionString))
        }
    }

    named<Jar>("jar") {
        // The plain jar (no dependencies bundled) isn't the distributable
        // artifact and nothing depends on it as a library — give it an
        // unambiguous classifier so it can't collide with/overwrite
        // shadowJar's output in the same build/libs/ directory now that
        // both would otherwise share the same base name.
        archiveClassifier.set("thin")
    }

    named<ShadowJar>("shadowJar") {
        // Empty classifier — this is the actual distributable jar an admin
        // drops into a server's plugins/ folder, so it gets the clean
        // "<pluginId>-<platform>-<version>.jar" name (from the archivesName
        // set in mcplugin.kotlin-conventions.gradle.kts) rather than a
        // redundant "...-paper-paper.jar".
        archiveClassifier.set("")
        // Relocate shaded libraries so two Paper plugins on the same server
        // that each shade a different version of kotlinx don't collide.
        // Namespaced per-project so plugin A and plugin B don't collide with
        // EACH OTHER either.
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

    named<RunServer>("runServer") {
        minecraftVersion("26.2")
    }
}
