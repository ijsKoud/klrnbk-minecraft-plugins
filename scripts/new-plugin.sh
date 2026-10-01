#!/usr/bin/env bash
#
# Scaffolds a new plugin module under src/plugins/<plugin-id>/{paper,velocity},
# wired up to the shared build-logic conventions, and registers it in
# settings.gradle.kts (as a flat Gradle project path with its projectDir
# redirected into src/ — see the comment in settings.gradle.kts for why).
#
# Usage:
#   scripts/new-plugin.sh <plugin-id> <paper|velocity|both>
#
# Example:
#   scripts/new-plugin.sh cool-plugin both
#
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <plugin-id> <paper|velocity|both>" >&2
  exit 1
fi

PLUGIN_ID="$1"
PLATFORM="$2"

if [[ ! "$PLUGIN_ID" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "error: plugin-id must be lowercase kebab-case (e.g. 'cool-plugin'), got: $PLUGIN_ID" >&2
  exit 1
fi

if [[ "$PLATFORM" != "paper" && "$PLATFORM" != "velocity" && "$PLATFORM" != "both" ]]; then
  echo "error: platform must be one of: paper, velocity, both — got: $PLATFORM" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_DIR="$ROOT_DIR/src/plugins/$PLUGIN_ID"

if [[ -e "$PLUGIN_DIR" ]]; then
  echo "error: $PLUGIN_DIR already exists" >&2
  exit 1
fi

# --- derive names -----------------------------------------------------------
PACKAGE_SEG="$(echo "$PLUGIN_ID" | tr -d '-')"                 # cool-plugin -> coolplugin
PACKAGE_PATH="nl/klrnbk/minecraft/plugins/$PACKAGE_SEG"
PACKAGE_NAME="nl.klrnbk.minecraft.plugins.$PACKAGE_SEG"

# cool-plugin -> CoolPlugin
CLASS_PREFIX="$(echo "$PLUGIN_ID" | awk -F- '{ out=""; for (i=1;i<=NF;i++) out = out toupper(substr($i,1,1)) substr($i,2); print out }')"

echo "Scaffolding '$PLUGIN_ID' ($PLATFORM) as $CLASS_PREFIX in package $PACKAGE_NAME ..."

# --- per-plugin common (only for plugins that span both platforms) ---------
# A plugin that's paper-only or velocity-only has nothing to share between
# platforms, so no common/ module is created for it — see README.
COMMON_DEP_LINE=""
if [[ "$PLATFORM" == "both" ]]; then
  COMMON_MODULE_DIR="$PLUGIN_DIR/common"
  KOTLIN_DIR="$COMMON_MODULE_DIR/src/main/kotlin/$PACKAGE_PATH/common"
  mkdir -p "$KOTLIN_DIR"

  cat > "$COMMON_MODULE_DIR/build.gradle.kts" << EOF
plugins {
    id("mcplugin.serialization-conventions")
}

dependencies {
    // Cross-plugin shared code still comes from the root :common — this
    // module is only for logic specific to THIS plugin that its own
    // paper/ and velocity/ modules both need.
    implementation(project(":common"))
}
EOF

  cat > "$KOTLIN_DIR/${CLASS_PREFIX}Info.kt" << EOF
package $PACKAGE_NAME.common

/**
 * Logic specific to THIS plugin, shared between its Paper and Velocity
 * modules (e.g. the plugin id, a shared config shape, message formatting).
 */
object ${CLASS_PREFIX}Info {
    const val PLUGIN_ID = "$PLUGIN_ID"
}
EOF

  COMMON_DEP_LINE="    implementation(project(\":plugins:$PLUGIN_ID:common\"))
"

  echo "  created src/plugins/$PLUGIN_ID/common"
fi

# --- paper -------------------------------------------------------------------
if [[ "$PLATFORM" == "paper" || "$PLATFORM" == "both" ]]; then
  PAPER_DIR="$PLUGIN_DIR/paper"
  KOTLIN_DIR="$PAPER_DIR/src/main/kotlin/$PACKAGE_PATH/paper"
  RES_DIR="$PAPER_DIR/src/main/resources"
  mkdir -p "$KOTLIN_DIR" "$RES_DIR"

  cat > "$PAPER_DIR/build.gradle.kts" << EOF
plugins {
    id("mcplugin.paper-plugin")
}

dependencies {
    implementation(project(":common"))
$COMMON_DEP_LINE}
EOF

  cat > "$RES_DIR/paper-plugin.yml" << EOF
name: $CLASS_PREFIX
version: '@version@'
main: $PACKAGE_NAME.paper.${CLASS_PREFIX}Main
bootstrapper: $PACKAGE_NAME.paper.${CLASS_PREFIX}Bootstrap

api-version: '26.2'
EOF

  cat > "$KOTLIN_DIR/${CLASS_PREFIX}Bootstrap.kt" << EOF
package $PACKAGE_NAME.paper

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext

class ${CLASS_PREFIX}Bootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.logger.info("$CLASS_PREFIX bootstrapping...")
    }

    override fun createPlugin(context: PluginProviderContext): ${CLASS_PREFIX}Main =
        ${CLASS_PREFIX}Main()
}
EOF

  cat > "$KOTLIN_DIR/${CLASS_PREFIX}Main.kt" << EOF
package $PACKAGE_NAME.paper

import org.bukkit.plugin.java.JavaPlugin

// open is required — MockBukkit's plugin loader creates a ByteBuddy proxy
// subclass of this class when loading it for tests; Kotlin classes are
// final by default, which breaks that.
open class ${CLASS_PREFIX}Main : JavaPlugin() {

    override fun onEnable() {
        logger.info("$CLASS_PREFIX enabled.")
    }

    override fun onDisable() {
        logger.info("$CLASS_PREFIX disabled.")
    }
}
EOF

  echo "  created src/plugins/$PLUGIN_ID/paper"
fi

# --- velocity ------------------------------------------------------------
if [[ "$PLATFORM" == "velocity" || "$PLATFORM" == "both" ]]; then
  VELOCITY_DIR="$PLUGIN_DIR/velocity"
  KOTLIN_DIR="$VELOCITY_DIR/src/main/kotlin/$PACKAGE_PATH/velocity"
  mkdir -p "$KOTLIN_DIR"

  cat > "$VELOCITY_DIR/build.gradle.kts" << EOF
plugins {
    id("mcplugin.velocity-plugin")
}

dependencies {
    implementation(project(":common"))
$COMMON_DEP_LINE}
EOF

  cat > "$KOTLIN_DIR/${CLASS_PREFIX}VelocityPlugin.kt" << EOF
package $PACKAGE_NAME.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "$PLUGIN_ID",
    name = "$CLASS_PREFIX",
    version = "1.0.0",
)
class ${CLASS_PREFIX}VelocityPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path,
) {

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("$CLASS_PREFIX (Velocity) initialized.")
    }
}
EOF

  echo "  created src/plugins/$PLUGIN_ID/velocity"
fi

# --- register in settings.gradle.kts ----------------------------------------
SETTINGS_FILE="$ROOT_DIR/settings.gradle.kts"
MARKER="// === end plugins ==="

INSERT_LINES=""
if [[ "$PLATFORM" == "paper" || "$PLATFORM" == "both" ]]; then
  INSERT_LINES="${INSERT_LINES}include(\":plugins:$PLUGIN_ID:paper\")\n"
  INSERT_LINES="${INSERT_LINES}project(\":plugins:$PLUGIN_ID:paper\").projectDir = file(\"src/plugins/$PLUGIN_ID/paper\")\n"
fi
if [[ "$PLATFORM" == "velocity" || "$PLATFORM" == "both" ]]; then
  INSERT_LINES="${INSERT_LINES}include(\":plugins:$PLUGIN_ID:velocity\")\n"
  INSERT_LINES="${INSERT_LINES}project(\":plugins:$PLUGIN_ID:velocity\").projectDir = file(\"src/plugins/$PLUGIN_ID/velocity\")\n"
fi
if [[ "$PLATFORM" == "both" ]]; then
  INSERT_LINES="${INSERT_LINES}include(\":plugins:$PLUGIN_ID:common\")\n"
  INSERT_LINES="${INSERT_LINES}project(\":plugins:$PLUGIN_ID:common\").projectDir = file(\"src/plugins/$PLUGIN_ID/common\")\n"
fi

if ! grep -qF "$MARKER" "$SETTINGS_FILE"; then
  echo "error: could not find marker '$MARKER' in settings.gradle.kts — add the include(...) lines above manually:" >&2
  echo -e "$INSERT_LINES" >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
awk -v marker="$MARKER" -v insert="$INSERT_LINES" '
  index($0, marker) == 1 { printf "%s", insert }
  { print }
' "$SETTINGS_FILE" > "$TMP_FILE"
mv "$TMP_FILE" "$SETTINGS_FILE"

echo "  registered in settings.gradle.kts"
echo ""
echo "Done. Next steps:"
echo "  1. Review src/plugins/$PLUGIN_ID/"
echo "  2. ./gradlew :plugins:$PLUGIN_ID:paper:build      (if applicable)"
echo "  3. ./gradlew :plugins:$PLUGIN_ID:velocity:build   (if applicable)"
