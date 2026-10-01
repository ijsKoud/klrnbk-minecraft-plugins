plugins {
    id("mcplugin.library-conventions")
}

dependencies {
    compileOnly(libs.paper.api)

    // compileOnly isn't visible to the test source set.
    testImplementation(libs.paper.api)
}
