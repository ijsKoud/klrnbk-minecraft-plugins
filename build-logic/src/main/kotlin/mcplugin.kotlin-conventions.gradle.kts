import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Every project sharing one flat group is what caused the collision this
// comment used to warn about a project-name-rename "fix" for — that
// approach was wrong (renaming a project's .name changes its PATH too,
// since path is computed from parent-path + name, breaking every
// project(":...") reference to it). Group, unlike name, has no effect on a
// project's path or any task/project reference anywhere — so it's what
// actually disambiguates safely here instead.
//
// Two DIFFERENT plugins each having a "paper" (or "velocity", or "common")
// module would otherwise all share identical group:name:version
// coordinates (every project here has the same group + version), and
// Gradle would silently collapse same-coordinate project dependencies into
// one — exactly what happened with :common vs
// :plugins:example-plugin:common. Suffixing the group with the owning
// plugin's id for any project nested under plugins/<id>/ keeps every
// module's identity unique, automatically, for every current and future
// plugin — no per-plugin settings.gradle.kts bookkeeping required.
group =
    run {
        val baseGroup = "nl.klrnbk.minecraft.plugins"
        val segments = project.path.removePrefix(":").split(":")
        if (segments.size >= 2 && segments[0] == "plugins") {
            val pluginId = segments[1].replace("-", "")
            "$baseGroup.plugin.$pluginId"
        } else {
            baseGroup
        }
    }
version = "1.0.0"

// Every module's jar otherwise defaults to just its directory name —
// paper-1.0.0.jar, velocity-1.0.0.jar, common-1.0.0.jar — identical across
// EVERY plugin, since that name only ever reflects the platform/role
// folder, never which plugin it is. Harmless while jars stay in their own
// per-module build/libs/, but collecting several plugins' jars into one
// server's plugins/ folder by hand silently collides. Same fix shape as
// the group suffixing above: derive a per-plugin prefix from the project
// path (keeping hyphens here, unlike group, since this ends up in a
// filename a human reads — "example-plugin-paper", not
// "exampleplugin-paper") so every current and future plugin gets a unique,
// descriptive jar name for free.
extensions.configure<BasePluginExtension> {
    archivesName.set(
        run {
            val segments = project.path.removePrefix(":").split(":")
            if (segments.size >= 2 && segments[0] == "plugins") {
                "${segments[1]}-${project.name}"
            } else {
                project.name
            }
        },
    )
}

// No repositories{} block here: repositoriesMode is PREFER_SETTINGS (set in
// the root settings.gradle.kts), so a project-level repositories{} block —
// including one added via this convention plugin — would be silently
// ignored. Every repository this build needs (mavenCentral, papermc,
// Velocity's Sonatype snapshots) is declared once in settings.gradle.kts's
// dependencyResolutionManagement instead.

// The type-safe `libs.xxx` accessor that works in a normal build.gradle.kts
// is NOT available inside precompiled script plugins (a long-standing
// Gradle limitation: https://github.com/gradle/gradle/issues/24908) —
// hence "Unresolved reference: libs" if you try it here. This queries the
// same catalog through its raw runtime API instead, so the version still
// comes from the one place: ../gradle/libs.versions.toml, nowhere else.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = catalog.findLibrary(alias).get()

fun ver(alias: String) = catalog.findVersion(alias).get().requiredVersion

dependencies {
    testImplementation(lib("junit-jupiter"))
testImplementation("org.jetbrains.kotlin:kotlin-reflect:${ver("kotlin")}")
// Required explicitly since Gradle 9 — see the version comment in
// libs.versions.toml. Without this, every test task fails at
// execution time with "Failed to load JUnit Platform", even though
// compilation succeeds fine (it's a runtime-classpath gap, not a
// compile-time one).
testRuntimeOnly(lib("junit-platform-launcher"))
implementation("com.google.inject:guice:7.0.0")
}

kotlin {
    jvmToolchain(ver("jvmTarget").toInt())

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // NOTE: JvmTarget.fromTarget("25") requires Kotlin 2.3.20's
        // JvmTarget enum to actually have a JVM_25 entry — unverified here
        // (no ability to run a real build). If this throws
        // IllegalArgumentException at configuration time, either Kotlin
        // doesn't support bytecode target 25 yet, or the jvmTarget compiler
        // setting simply needs to stay at a lower value (e.g. 21) than the
        // jvmToolchain above — that's a legal combination.
        jvmTarget.set(JvmTarget.fromTarget(ver("jvmTarget")))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
