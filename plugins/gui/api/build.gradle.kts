plugins {
    id("mcplugin.library-conventions")
}

// The API is consumed by other KLRNBK plugins, so every declaration must
// state its visibility and type explicitly — nothing becomes public by accident.
kotlin {
    explicitApi()
}

dependencies {
    // Provided by the proxy at runtime. The API deliberately depends on nothing
    // else: no protocol library, no Geyser/Floodgate, no packet types.
    compileOnly(libs.velocity.api)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(libs.velocity.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.mockk)
}
