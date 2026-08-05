plugins {
    kotlin("jvm") version "2.4.20-Beta2"
    kotlin("kapt") version "2.4.20-Beta2"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-velocity") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Velocity Dependencies
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    kapt("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    // Config Dependencies
    implementation("org.spongepowered:configurate-hocon:4.2.0")
    implementation("org.spongepowered:configurate-extra-kotlin:4.2.0")

    // Database Dependencies
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.jetbrains.exposed:exposed-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.4.0")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.4.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runVelocity {
        // Configure the Velocity version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        velocityVersion("3.5.0-SNAPSHOT")
    }

    processResources {
        val props =
            mapOf(
                "version" to version,
            )

        filesMatching(
            listOf(
                "plugin.properties",
            ),
        ) {
            expand(props)
        }
    }
}
