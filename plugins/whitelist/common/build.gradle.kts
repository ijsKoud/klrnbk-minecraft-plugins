plugins {
    `java-test-fixtures`
    id("mcplugin.serialization-conventions")
}

dependencies {
    api(project(":packages:database"))
    compileOnly(project(":packages:i18n"))
    compileOnly(project(":plugins:whitelist:api"))
    compileOnly(project(":plugins:identity:api"))
    implementation(project(":packages:common-constants"))
    implementation(project(":packages:config-yaml"))

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    // The real services on a temporary SQLite database, shared with the velocity module's tests.
    // (The services are final classes, which mockk can't mock in this setup.)
    testFixturesApi(project(":packages:database"))
    testFixturesApi(project(":plugins:whitelist:api"))
    testFixturesApi(project(":plugins:identity:api"))
    testFixturesImplementation(project(":packages:config-yaml"))
    testFixturesImplementation(libs.adventure.api)
    testFixturesImplementation(libs.adventure.minimessage)
    testFixturesImplementation(project(":packages:i18n"))

    testImplementation(project(":plugins:whitelist:api"))
    testImplementation(project(":plugins:identity:api"))
    testImplementation(project(":packages:i18n"))
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
}
