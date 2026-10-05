# klrnbk-minecraft-plugins

A Kotlin Gradle monorepo for **multiple** Minecraft plugins — any mix of
PaperMC backend plugins and Velocity proxy plugins — sharing code through a
`common` module and shared Gradle build logic. Targets Minecraft/Paper 26.2
and Velocity 4.x (Java 25, Gradle 9.7.1).

```
klrnbk-minecraft-plugins/
├── settings.gradle.kts          # includeBuild("build-logic") + registers every module
├── gradle/libs.versions.toml    # single source of truth for every dependency version
├── build-logic/                 # convention plugins — see "Why build-logic" below
│   └── src/main/kotlin/
│       ├── mcplugin.kotlin-conventions.gradle.kts       # base: toolchain, JUnit5
│       ├── mcplugin.library-conventions.gradle.kts      # plain Kotlin lib (→ common)
│       ├── mcplugin.serialization-conventions.gradle.kts
│       ├── mcplugin.paper-plugin.gradle.kts             # shading, paper-plugin.yml, MockBukkit, run-paper
│       └── mcplugin.velocity-plugin.gradle.kts          # shading, MockK, run-velocity
├── scripts/
│   └── new-plugin.sh            # scaffolds a new plugin — see "Adding a plugin" below
└── src/                         # everything that's actual source code lives here
    ├── common/                  # pure Kotlin, shared by EVERY plugin in the repo
    │   └── .../common/{config,messaging,logging}/...
    ├── pkgs/                    # standalone shared packages, independent of any one plugin
    └── plugins/
        └── example-plugin/      # worked example — Paper + Velocity + per-plugin common
            ├── paper/
            ├── velocity/
            └── common/           # shared between THIS plugin's paper/ and velocity/ only
```

**Gradle project paths deliberately don't have a `:src` segment** — every
module's *task-invocation path* stays flat (`:common`,
`:plugins:example-plugin:paper`), even though its files physically live
under `src/`. `settings.gradle.kts` does this by setting each project's
`projectDir` explicitly after `include(...)`, rather than nesting `src`
into the include path itself. That's deliberate, not just tidiness:
nesting a `:src` segment into the path (`include(":src:common")`) would
make `:src` a real Gradle project node with no build script of its own,
which breaks IDE sync — IntelliJ's Kotlin-DSL support can't prepare a
build-script model for a project that has nothing to prepare it from,
which shows up as `Task 'prepareKotlinBuildScriptModel' not found in
project ':src'`. Keeping paths flat and only redirecting `projectDir`
sidesteps that entirely, and keeps every `./gradlew :plugins:...` command
unchanged regardless of how the files are organized on disk.

Each plugin lives under `src/plugins/<plugin-id>/`, with a `paper/`
subfolder, a `velocity/` subfolder, or both — whichever it actually needs.
Nothing requires a plugin to have a matching pair on the other platform.

**A plugin that supports both platforms also gets its own `common/`
module** — distinct from the root `:common`. The root `:common` is for code
every plugin in the repo might use (config models, shared messaging channel
constants); a plugin's own `common/` is for logic specific to *that plugin*
that its `paper/` and `velocity/` modules both need (e.g. its plugin id, a
shared message format, protocol constants for talking between its own two
halves). Paper-only or Velocity-only plugins don't get one — there's nothing
to share between platforms within a single-platform plugin.

## GUI framework (`plugins/gui`)

A reusable, protocol-independent inventory GUI framework for Velocity plugins (Java + Bedrock via Geyser). Other plugins
compile against `:plugins:gui:api` and obtain `GuiApi` from `GuiProvider`. Needs the PacketEvents Velocity plugin on the
proxy. Start with [`plugins/gui/README.md`](plugins/gui/README.md); background in
[`RESEARCH.md`](plugins/gui/RESEARCH.md), [`IMPLEMENTATION.md`](plugins/gui/IMPLEMENTATION.md) and
[`TESTING.md`](plugins/gui/TESTING.md).

## Whitelist and Identity

[`plugins/identity`](plugins/identity/README.md) assigns every player a stable ID and exposes an API to look
players up. [`plugins/whitelist`](plugins/whitelist/README.md) is a proxy-wide whitelist on top of it, with
commands, logging and its own API.

## Why `build-logic`

With one plugin, each `build.gradle.kts` having its own shading/manifest/
testing boilerplate is fine. With five (or more), copy-pasting that
boilerplate five times means a bug fix or version bump has to be applied five
times too. So the shared logic — Kotlin toolchain setup, Shadow relocation
rules, `compileOnly` server APIs, MockBukkit/MockK test deps, `run-paper`/
`run-velocity` — lives once in `build-logic/` as **convention plugins**, and
every actual plugin module applies one line:

```kotlin
// src/plugins/example-plugin/paper/build.gradle.kts
plugins {
    id("mcplugin.paper-plugin")
}

dependencies {
    implementation(project(":common"))
}
```

`build-logic` is technically a separate Gradle build (pulled in via
`includeBuild` in `settings.gradle.kts`), but its own
`build-logic/settings.gradle.kts` points at the exact same
`gradle/libs.versions.toml` as the main build — so there is still only one
place to bump a version, never two.

**One Gradle wrinkle worth knowing:** the type-safe `libs.xxx` accessor
that works in a normal `build.gradle.kts` is *not* available inside
precompiled script plugins themselves (the files directly under
`build-logic/src/main/kotlin/`) — a long-standing Gradle limitation
([gradle/gradle#24908](https://github.com/gradle/gradle/issues/24908)).
Using `libs.xxx` there fails to compile with `Unresolved reference: libs`.
Every convention file that needs a dependency from the catalog works around
this with a small local helper instead:

```kotlin
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).get()
```

This queries the same catalog through its raw runtime API, so the version
still comes from `gradle/libs.versions.toml` and nowhere else — it's just a
different way of asking for it. `build-logic/build.gradle.kts` itself
(build-logic's own top-level build script, not a precompiled script plugin)
doesn't have this problem and uses `libs.xxx` directly, as does every
regular per-plugin `build.gradle.kts` under `src/plugins/` — the limitation is
specific to files that get compiled into Gradle plugins.

## Adding a new plugin

**Recommended — the scaffold script:**

```bash
scripts/new-plugin.sh cool-plugin both       # paper + velocity
scripts/new-plugin.sh cool-plugin paper      # paper only
scripts/new-plugin.sh cool-plugin velocity   # velocity only
```

This creates `src/plugins/cool-plugin/...` with a working `build.gradle.kts`,
main class, manifest (for Paper), and registers the new module(s) in
`settings.gradle.kts` automatically. When you pick `both`, it also creates
`src/plugins/cool-plugin/common/` (shared between that plugin's own paper/ and
velocity/ modules) and wires both platform modules to depend on it. It
refuses to run if `src/plugins/cool-plugin` already exists, and won't touch
`settings.gradle.kts` if it can't find the insertion marker — so it fails
loudly instead of leaving things half-wired.

**Manually:** copy `src/plugins/example-plugin/` to `src/plugins/<new-id>/`, rename
the package/class names, delete the parts you don't need (e.g. the
`velocity/` folder for a Paper-only plugin), and add the corresponding
`include(":plugins:<new-id>:paper")` line(s) **and** a matching
`project(":plugins:<new-id>:paper").projectDir = file("src/plugins/<new-id>/paper")`
line for each, to `settings.gradle.kts`.

Either way, each plugin module can depend on `:common` for shared code —
config models, plugin-messaging channel constants, etc. — and add its own
extra dependencies in its own `build.gradle.kts` as needed.

## Why every plugin's modules get their own Gradle group

Every project in this repo shares the same `version` (set once, by
`mcplugin.kotlin-conventions.gradle.kts`), and each per-plugin module's
*name* defaults to its directory's last path segment — `paper`, `velocity`,
`common`. Gradle identifies a project dependency by `group:name:version`,
so without something to disambiguate them, `src/plugins/example-plugin/paper`
and a second plugin's `src/plugins/cool-plugin/paper` would have identical
coordinates. Gradle doesn't error on that — it silently collapses them into
a single dependency, and whichever one "loses" never actually ends up on
any classpath. The symptom looks exactly like a missing/unresolved import
for the losing module's code, even though every `include(...)` and every
`implementation(project(...))` looks correct. (This project hit that exact
bug during development with `:common` vs `:plugins:example-plugin:common`.)

The fix is **not** to rename the projects — a project's *path* is computed
from its parent's path plus its own name, so changing `.name` in
`settings.gradle.kts` changes the path too, breaking every
`project(":...")` reference to it elsewhere (this project also briefly
shipped that broken fix before landing on this one). Renaming would also
mean every `./gradlew :plugins:<id>:<platform>:...` command in this README
and printed by the scaffold script would need the renamed path too.

Instead, `mcplugin.kotlin-conventions.gradle.kts` gives every project
nested under the `:plugins:<id>:` Gradle path (unchanged by the src/
filesystem reorganization above — see `project.path.split(":")` in the
file itself) a **group** suffixed with its own plugin id
(e.g. `nl.klrnbk.minecraft.plugins.plugin.exampleplugin` instead of the
plain `nl.klrnbk.minecraft.plugins` root `:common` gets) — computed
automatically from `project.path`, not hand-maintained anywhere. Group has
no effect on a project's path or on any task/`project(...)` reference, so
this disambiguates the dependency graph without changing anything else.
Every current and future plugin gets this for free; there's nothing to
remember when adding a plugin, by script or by hand.

## Building

```bash
./gradlew build                              # every module
./gradlew :plugins:example-plugin:paper:build       # just one
```

Each plugin produces its own shaded jar at
`src/plugins/<id>/<platform>/build/libs/<id>-<platform>-1.0.0.jar` — e.g.
`src/plugins/example-plugin/paper/build/libs/example-plugin-paper-1.0.0.jar`
— drop it into the matching server's/proxy's `plugins/` folder.
`mcplugin.kotlin-conventions.gradle.kts` sets this `archivesName` for every
module automatically (same `project.path` derivation as the `group` fix
above, just keeping hyphens since this ends up in a filename a human
reads), so two different plugins' Paper jars never collide even collected
into one server's `plugins/` folder by hand. Each platform module also
produces a `..-thin.jar` alongside it — the plain, dependency-less jar
Gradle builds before shading; not the distributable artifact, just build
output, safe to ignore.

### First-time setup: the Gradle wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.properties` are
all included — but `gradle/wrapper/gradle-wrapper.jar` (a compiled binary)
isn't, since it can't be produced in the environment this repo was built in.
**Running `./gradlew` before the step below will fail** with something like
`Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain`
or `Unable to access jarfile gradle/wrapper/gradle-wrapper.jar` — that's
expected, not a sign anything else is broken.

Fix it once, from any machine with Gradle already installed (any version —
this doesn't need to match 9.7.1 itself, it just needs to exist so it can
bootstrap the real one):

```bash
gradle wrapper --gradle-version 9.7.1 --distribution-type bin
```

**The `--gradle-version` flag matters** — running bare `gradle wrapper`
without it uses whatever Gradle version is already on your machine instead
of 9.7.1, silently overwriting `gradle-wrapper.properties` with that
version. Since `run-paper`/`run-velocity` require Gradle 9.7+ (see
"Bumping versions" below), landing on an older version this way will
reproduce the exact "no matching variant" dependency error this project hit
during development if the version picked up is below 9.7.

Verify it took effect:

```bash
./gradlew --version   # should print Gradle 9.7.1
```

From then on, always invoke `./gradlew` (never bare `gradle`) so you
consistently get the pinned version. Opening the project in IntelliJ IDEA
should also regenerate the wrapper jar automatically, as an alternative to
the command above.

### Running a plugin locally

Every Paper module gets `runServer` (via `run-paper`), every Velocity module
gets `runVelocity` (via `run-velocity`), both wired in by the convention
plugins:

```bash
./gradlew :plugins:example-plugin:paper:runServer
./gradlew :plugins:example-plugin:velocity:runVelocity
```

## Running multiple Kotlin plugins on one server

Shading the Kotlin stdlib into every plugin is fine for one or two plugins,
but with five on the same Paper server it means five copies of the same
~6 MB stdlib, all needing to agree on a Kotlin version. Consider a shared
Kotlin loader (e.g. the community
[`paper-kotlin`](https://github.com/DevSrSouza/paper-kotlin) approach, or
Paper's `PluginLoader` API to pull Kotlin from a shared library plugin)
instead. `kotlinx.coroutines`/`kotlinx.serialization` are still relocated
per-plugin by the convention plugins (namespaced under each plugin's own
package), so only the stdlib itself is the shared-copy concern.

## Testing

Each module gets the right framework for what it's testing:

| Module            | Framework                          | Why                                                                      |
|--------------------|------------------------------------|---------------------------------------------------------------------------|
| `common`           | JUnit5                             | Plain Kotlin — no server/proxy types to fake.                            |
| `src/plugins/*/paper`    | JUnit5 + [MockBukkit](https://docs.mockbukkit.org/) | Spins up a fake in-process Bukkit/Paper server so listeners/commands run for real. |
| `src/plugins/*/velocity` | JUnit5 + [MockK](https://mockk.io/) | No "MockVelocity" exists, so `ProxyServer`/`Logger` are mocked directly. |

All three are wired in by the convention plugins in `build-logic/` — a new
plugin scaffolded via `scripts/new-plugin.sh` (or copied from
`example-plugin/`) gets test dependencies for free, no extra setup.

Run everything:

```bash
./gradlew test
```

Run one module:

```bash
./gradlew :plugins:example-plugin:paper:test
```

Example tests live in `src/plugins/example-plugin/*/src/test/kotlin` and
`src/common/src/test/kotlin`:
- `common/.../config/PluginConfigTest.kt` — defaults + JSON round-trip.
- `paper/.../MCPluginMainTest.kt` — loads the real plugin into a `ServerMock`,
  simulates a player joining, asserts on the resulting Adventure message.
- `velocity/.../MCVelocityPluginTest.kt` — mocks `ProxyServer`/`Logger`,
  fires `ProxyInitializeEvent`, verifies the log call.

A few things worth knowing:
- **`compileOnly` dependencies aren't visible to tests.** `paper-api` and
  `velocity-api` are `compileOnly` in the respective convention plugin, but
  re-declared as `testImplementation` there too — otherwise MockBukkit/your
  test code can't even compile against Bukkit/Velocity types.
- **A Paper plugin's main class needs to be `open`.** MockBukkit's plugin
  loader creates a ByteBuddy proxy subclass of whatever class you pass to
  `MockBukkit.load(...)`, and Kotlin classes are `final` by default (unlike
  Java's) — a `final` main class fails with `Cannot subclass primitive,
  array or final types` the moment a test tries to load it. `scripts/new-
  plugin.sh` generates every plugin's main class as `open` already; if you
  write one by hand instead, don't forget the modifier.
- **The example plugin's config is hardcoded in `onEnable()`** in this
  template, so tests can exercise the *default* config but can't inject a
  different one. If you want to unit test debug-mode behavior in a real
  plugin, add a way to pass in (or reload) config rather than constructing
  it inline.
- **If MockK/MockBukkit throw `InaccessibleObjectException` on JDK 17+**,
  add `--add-opens java.base/java.lang=ALL-UNNAMED` (and similar) to the
  `test` task's `jvmArgs` in `mcplugin.kotlin-conventions.gradle.kts` — one
  place, applies to every module — it's a module-system restriction some
  reflection-based mocking hits, not specific to this project.
- **Integration testing** (an actual server booting your actual shaded jar)
  is covered by `runServer`/`runVelocity` rather than the `test` task —
  MockBukkit/MockK and `run-paper`/`run-velocity` solve different problems:
  fast in-process unit tests vs. "does this really work end to end."

CI (`.github/workflows/ci.yml`) runs `./gradlew test` then `./gradlew build`
on every push and PR, and uploads test reports as an artifact — this covers
every module automatically as new plugins are added, no workflow edits
needed.

## Bumping versions

Everything version-related — Kotlin, Paper API, Velocity API, Shadow,
run-paper/run-velocity, kotlinx libs, test libs — lives in
`gradle/libs.versions.toml`, and nowhere else (including inside
`build-logic`, which reads the same file). Check
`https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/`
and Velocity's Sonatype snapshots repo for the latest builds; the versions
pinned here are known-good but will age.

**Current target stack:** Gradle 9.7.1, Kotlin 2.3.20, Java 25 toolchain
(auto-provisioned via the `foojay-resolver-convention` plugin in
`settings.gradle.kts`, so you don't need JDK 25 pre-installed), Paper 26.2,
Velocity 4.1.0-SNAPSHOT, Shadow 9.2.2. This is a recent, fast-moving corner
of the ecosystem — Paper dropped its old `1.21.x-R0.1-SNAPSHOT` versioning
for a year-based scheme (`26.2.build.<n>-<channel>`) partway through 2026,
and both Paper and Velocity bumped their Java requirement to 25 in the same
wave. A few pins here are best-effort rather than fully verified against a
real build (this repo was assembled without the ability to run `./gradlew
build` end to end):
- **`mockbukkit` version** (`gradle/libs.versions.toml`) — MockBukkit's
  artifact for the 26.x line is `mockbukkit-v26.1.2`, but the exact release
  number pinned may be stale. Check
  `https://mvnrepository.com/artifact/org.mockbukkit.mockbukkit/mockbukkit-v26.1.2`
  before relying on the Paper test setup.
- **`velocityVersion(...)` in `mcplugin.velocity-plugin.gradle.kts`** — the
  build number `run-velocity` downloads for `runVelocity`/local testing.
  Check `https://papermc.io/downloads/velocity` for the current 4.x build.
- **Kotlin 2.3.20 + Gradle 9.7.1** — Kotlin's own compatibility notes
  confirm Gradle 9.3+ support as of 2.3.20; 9.7.1 specifically hasn't been
  independently confirmed here.

If any of these are stale by the time you build, the fix is usually just
bumping that one version in `gradle/libs.versions.toml` — the rest of the
stack (Shadow relocation, paper-plugin.yml templating, etc.) doesn't care
what exact patch version these are pinned to.

## CI/CD and releases

- **CI** (`.github/workflows/ci.yml`) runs on every push and pull request: `./gradlew clean check build collectPluginJars`, then verifies the jars. Nothing is published; jars are uploaded as short-lived `plugin-build-<sha>` Actions artifacts.
- **Release** (`.github/workflows/release.yml`) runs when a tag `vMAJOR.MINOR.PATCH` is pushed from `main`. It builds with `-Pversion=<tag without v>`, validates, generates `CHANGELOG.md` (`scripts/generate-changelog.sh`, grouped by Conventional Commit type) and publishes a GitHub Release with the `klrnbk-<plugin>-<module>-<version>.jar` files plus the changelog.

Create a release (the first one is identical; with no earlier `v*` tag the changelog covers all history):

```bash
git checkout main && git pull
git tag v1.0.0
git push origin v1.0.0
```

Plugin jars are built with `./gradlew collectPluginJars` into `build/dist`. Local builds default to version `1.0.1`; override with `-Pversion=x.y.z`.
