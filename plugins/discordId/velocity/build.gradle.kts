plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(project(":plugins:discordId:common"))
    testImplementation(project(":packages:config-yaml"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:i18n"))
    implementation(project(":packages:velocity-commands"))
    implementation(project(":plugins:identity:api"))
}
