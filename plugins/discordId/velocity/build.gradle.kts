plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(project(":plugins:discordId:common"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:i18n"))
    implementation(project(":packages:velocity-commands"))
    compileOnly(project(":plugins:identity:api"))

    testImplementation(project(":packages:config-yaml"))
    testImplementation(project(":plugins:identity:api"))
    testImplementation(testFixtures(project(":plugins:discordId:common")))
}
