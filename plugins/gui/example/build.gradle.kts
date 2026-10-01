plugins {
    id("mcplugin.velocity-plugin")
}

// Shows how another KLRNBK Velocity plugin consumes the GUI framework: it compiles
// against :plugins:gui:api only and looks the implementation up through GuiProvider.
dependencies {
    compileOnly(project(":plugins:gui:api"))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)

    testImplementation(project(":plugins:gui:api"))
    // The real implementation (with a fake protocol) so the example is tested against the actual final API.
    testImplementation(project(":plugins:gui:common"))
    testImplementation(testFixtures(project(":plugins:gui:common")))
    testImplementation(libs.velocity.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
}

kotlin {
    compilerOptions {
        optIn.add("nl.klrnbk.minecraft.plugins.gui.api.InternalGuiApi")
    }
}
