plugins {
    `java-test-fixtures`
    id("mcplugin.serialization-conventions")
}

dependencies {
    api(project(":packages:database"))
    compileOnly(project(":packages:i18n"))
    compileOnly(project(":plugins:identity:api"))
    implementation(project(":packages:common-constants"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:config-yaml"))

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    // Discord Dependencies
    implementation("net.dv8tion:JDA:6.5.0")

    // The real services on a temporary SQLite database, shared with the velocity and paper modules' tests.
    testFixturesApi(project(":packages:database"))
    testFixturesApi(project(":plugins:identity:api"))
    testFixturesApi(libs.mockk)
    testFixturesImplementation(project(":packages:config-yaml"))
    testFixturesImplementation(project(":packages:i18n"))
    testFixturesImplementation(libs.adventure.api)
    testFixturesImplementation(libs.adventure.minimessage)
    testFixturesImplementation("net.dv8tion:JDA:6.5.0")

    testImplementation(project(":plugins:identity:api"))
    testImplementation(project(":packages:i18n"))
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.mockk)
}
