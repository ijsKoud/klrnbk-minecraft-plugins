plugins {
    id("mcplugin.library-conventions")
    `java-test-fixtures`
}

dependencies {
    // `api` because the internal implementation is consumed by :plugins:gui:velocity
    // and the public types appear in its signatures.
    api(project(":plugins:gui:api"))

    compileOnly(libs.velocity.api)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.kotlinx.coroutines.core)
    compileOnly("org.slf4j:slf4j-api:2.0.16")
    // Jackson is provided at runtime by the shared runtime plugin (excluded from shaded jars).
    compileOnly(libs.jackson.dataformat.yaml)

    testImplementation(libs.velocity.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.mockk)
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation(libs.jackson.dataformat.yaml)
}

dependencies {
    testFixturesApi(project(":plugins:gui:api"))
    testFixturesImplementation(libs.velocity.api)
    testFixturesImplementation(libs.adventure.api)
    testFixturesImplementation(libs.mockk)
}

// The implementation modules are the intended users of the API's @InternalGuiApi hooks
// (event constructors, provider registration).
kotlin {
    compilerOptions {
        optIn.add("nl.klrnbk.minecraft.plugins.gui.api.InternalGuiApi")
    }
}
