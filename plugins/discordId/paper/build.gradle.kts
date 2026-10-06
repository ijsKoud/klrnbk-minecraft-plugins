plugins {
    id("mcplugin.paper-plugin")
}

dependencies {
    implementation(project(":plugins:discordId:common"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:i18n"))
    implementation(project(":packages:paper-commands"))

    // Provided at runtime by the Identity plugin (which registers the IdentityProvider instance);
    // bundling a copy here would give this plugin an IdentityProvider that never receives that instance.
    compileOnly(project(":plugins:identity:api"))

    testImplementation(libs.mockk)
    testImplementation(project(":packages:config-yaml"))
    testImplementation(project(":plugins:identity:api"))
    testImplementation(testFixtures(project(":plugins:discordId:common")))
}
