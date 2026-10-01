plugins {
    id("mcplugin.library-conventions")
}

dependencies {
    implementation(libs.jackson.dataformat.yaml)
    api(libs.jackson.module.kotlin)

    testImplementation(project(":packages:database"))
    implementation(project(":packages:common-cryptography"))
}
