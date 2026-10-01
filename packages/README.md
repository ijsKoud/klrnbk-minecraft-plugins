# pkgs

Standalone shared packages — for code that isn't tied to any single plugin
the way `src/common` (cross-plugin) or a plugin's own `common/`
(single-plugin) is.

Nothing here yet. When you add a module, follow the same pattern the rest
of the repo uses: a flat Gradle project path (`include(":pkgs:<name>")`)
with its `projectDir` explicitly redirected into `src/pkgs/<name>` in the
root `settings.gradle.kts` — see the comment above the `:common` include
there for why it's done that way instead of nesting `src` into the path.
