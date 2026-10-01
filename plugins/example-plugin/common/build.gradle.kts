plugins {
    id("mcplugin.serialization-conventions")
}

dependencies {
    // Cross-plugin shared code (config models, messaging channel constants)
    // still comes from the root :common — this module is only for logic
    // specific to THIS plugin that its own paper/ and velocity/ modules
    // both need.
//    implementation(project(":common"))
}
