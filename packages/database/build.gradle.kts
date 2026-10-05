plugins {
    `java-library`
    id("mcplugin.serialization-conventions")
}

dependencies {
    // Database Dependencies
    api("com.zaxxer:HikariCP:6.3.0")

    // Base Exposed ORM
    api("org.jetbrains.exposed:exposed-core:1.4.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.4.0")
    api("org.jetbrains.exposed:exposed-dao:1.4.0")

    // Exposed ORM Extensions
    api("org.jetbrains.exposed:exposed-kotlin-datetime:1.4.0")
    api("org.jetbrains.exposed:exposed-migration-core:1.4.0")
    api("org.jetbrains.exposed:exposed-migration-jdbc:1.4.0")

    // Export/import file format
    implementation(libs.kotlinx.serialization.json)

    // Database Drivers
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
}

dependencies {
    testImplementation(libs.mockk)
}
