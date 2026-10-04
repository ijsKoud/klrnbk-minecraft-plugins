@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    // Auto-provisions JDK toolchains (e.g. JDK 25, which most machines won't
    // already have) instead of failing the build when one isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    // Project-level repositories{} blocks (including ones added via a
    // build-logic convention plugin) are IGNORED under PREFER_SETTINGS —
    // every repository a module needs has to be declared here instead.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // paper-api / velocity-api (compileOnly in every paper/velocity
        // module) resolve from here.
        maven("https://repo.papermc.io/repository/maven-public/")
        // velocity-api is currently a SNAPSHOT build (4.1.0-SNAPSHOT).
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        // PacketEvents (compileOnly in plugins:gui:velocity) is published here only.
        maven("https://repo.codemc.io/repository/maven-releases/") {
            content { includeGroup("com.github.retrooper") }
        }
    }
}

rootProject.name = "klrnbk-minecraft-plugins"

// === plugins (managed by scripts/new-plugin.sh — do not hand-edit the
// markers below; the script inserts new include(...) + projectDir lines
// just above the closing marker) ===
include(":packages:common-constants")
include(":packages:common-cryptography")
include(":packages:i18n")
include(":packages:velocity-commands")
include(":packages:paper-commands")
include(":packages:database")
include(":packages:config-yaml")

// TODO: example-plugin is a template that still references the removed root
// :common module (PluginConfig/PluginLogger no longer exist), so it does not
// compile. Fix it, then re-enable.
// include(":plugins:example-plugin:paper")
// include(":plugins:example-plugin:velocity")
// include(":plugins:example-plugin:common")

include(":plugins:identity:paper")
include(":plugins:identity:velocity")
include(":plugins:identity:common")

include(":plugins:whitelist:velocity")
include(":plugins:whitelist:api")
include(":plugins:whitelist:common")

// TODO: moderation is being rewritten; re-enable once it is added back
// include(":plugins:moderation:paper")
// include(":plugins:moderation:velocity")
// include(":plugins:moderation:common")

include(":plugins:gui:api")
include(":plugins:gui:common")
include(":plugins:gui:velocity")
include(":plugins:gui:example")

include(":plugins:runtime:paper")
include(":plugins:runtime:velocity")
// === end plugins ===

include("plugins:identity:api")
// TODO: moderation is being rewritten; re-enable once it is added back
// include("plugins:moderation:api")
