plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(project(":plugins:whitelist:common"))
    implementation(project(":plugins:whitelist:api"))
    compileOnly(project(":plugins:identity:api"))
    implementation(project(":packages:i18n"))
    implementation(project(":packages:velocity-commands"))

    testImplementation(project(":plugins:identity:api"))
    testImplementation(testFixtures(project(":plugins:whitelist:common")))
}
