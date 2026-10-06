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

    // Discord Dependencies
    implementation("net.dv8tion:JDA:6.5.0")
}
