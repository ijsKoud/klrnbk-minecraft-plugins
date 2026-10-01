plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(project(":plugins:gui:common"))
    implementation(project(":plugins:gui:api"))

    // PacketEvents is NOT shaded: the PacketEvents Velocity plugin must be installed
    // on the proxy (declared as a required dependency in VelocityPlugin) so that a
    // single instance owns the netty injection for every plugin.
    compileOnly(libs.packetevents.velocity)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    // Provided at runtime by the shared runtime plugin (excluded from the shaded jar).
    compileOnly(libs.jackson.dataformat.yaml)
    testImplementation(libs.jackson.dataformat.yaml)
    testImplementation(libs.packetevents.velocity)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(testFixtures(project(":plugins:gui:common")))
    // Real byte buffers for the wire-format tests (Velocity provides netty at runtime).
    testImplementation("io.netty:netty-buffer:4.1.118.Final")
    // Velocity provides these at runtime; PacketEvents needs them to initialise its item registries.
    testImplementation("net.kyori:adventure-nbt:5.2.0")
}

// The implementation modules are the intended users of the API's @InternalGuiApi hooks
// (event constructors, provider registration).
kotlin {
    compilerOptions {
        optIn.add("nl.klrnbk.minecraft.plugins.gui.api.InternalGuiApi")
    }
}
