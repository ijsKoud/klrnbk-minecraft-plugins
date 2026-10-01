
plugins {
    id("mcplugin.serialization-conventions")
}

dependencies {
    api(project(":packages:database"))
    compileOnly(project(":packages:i18n"))
    implementation(project(":plugins:identity:api"))
    implementation(project(":packages:common-constants"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:config-yaml"))

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(project(":packages:i18n"))
    testImplementation(libs.mockk)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
}
