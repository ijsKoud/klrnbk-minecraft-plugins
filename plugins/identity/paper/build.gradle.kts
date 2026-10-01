plugins {
    id("mcplugin.paper-plugin")
}

dependencies {
    implementation(project(":plugins:identity:common"))
    implementation(project(":plugins:identity:api"))
    implementation(project(":packages:common-cryptography"))
    implementation(project(":packages:i18n"))
    implementation(project(":packages:paper-commands"))
}
