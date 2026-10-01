// Root project: only hosts release-packaging helpers. Plugin modules get their
// configuration from the build-logic convention plugins.

// Make sure every module (and its shadowJar task) exists before the task
// wiring below looks at it.
evaluationDependsOnChildren()

/**
 * Collects the final, shaded plugin jars (the `shadowJar` output of every
 * module that applies the Shadow plugin, i.e. every paper/velocity plugin
 * module) into build/dist. Libraries, thin jars and test output are NOT
 * included, so this directory is exactly what gets released. Example/template
 * plugins are excluded.
 */
val shadowJarTasks =
    subprojects.filterNot { it.path.startsWith(":plugins:example-plugin") || it.path == ":plugins:gui:example" }
        .mapNotNull { it.tasks.findByName("shadowJar") }

check(shadowJarTasks.isNotEmpty()) { "No shadowJar tasks found; nothing to collect" }

tasks.register<Sync>("collectPluginJars") {
    group = "distribution"
    description = "Copies all final plugin jars into build/dist."
    dependsOn(shadowJarTasks)
    shadowJarTasks.forEach { from(it) }
    into(layout.buildDirectory.dir("dist"))
}
