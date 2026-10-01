plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.jetbrains.exposed:exposed-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.4.0")
    implementation("org.jetbrains.exposed:exposed-dao:1.4.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:1.4.0")
    implementation("org.jetbrains.exposed:exposed-migration-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:1.4.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
}
