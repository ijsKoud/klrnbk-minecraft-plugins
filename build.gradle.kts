// Root project: only hosts release-packaging helpers. Plugin modules get their
// configuration from the build-logic convention plugins.

/**
 * Collects the final, shaded plugin jars (the `shadowJar` output of every
 * module that applies the Shadow plugin, i.e. every paper/velocity plugin
 * module) into build/dist. Libraries, thin jars and test output are NOT
 * included, so this directory is exactly what gets released. Example/template
 * plugins are excluded.
 */
// Resolved lazily (at task-graph time) because the child modules register
// their shadowJar tasks after this root script has been configured. Output
// file collections (not Task objects) are used so the configuration cache can
// serialize them; they carry the dependency on the producing task.
val shadowJarTasks =
    provider {
        subprojects
            .filterNot { it.path.startsWith(":plugins:example-plugin") || it.path == ":plugins:gui:example" }
            .mapNotNull { it.tasks.findByName("shadowJar")?.outputs?.files }
            .also { check(it.isNotEmpty()) { "No shadowJar tasks found; nothing to collect" } }
    }

tasks.register<Sync>("collectPluginJars") {
    group = "distribution"
    description = "Copies all final plugin jars into build/dist."
    dependsOn(shadowJarTasks)
    from(shadowJarTasks)
    into(layout.buildDirectory.dir("dist"))
}
