plugins {
    id("mcplugin.library-conventions")
}

dependencies {
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.kotlin)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(project(":packages:common-constants"))
}

dependencies {
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
}
