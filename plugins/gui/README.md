# KLRNBK GUI

A Kotlin-first inventory GUI framework for **Velocity** proxies, for use by other KLRNBK plugins.
It opens chest-style menus on the *proxy*, in front of whatever backend server the player is on,
for **Java** players and — through Geyser — **Bedrock** players.

Other plugins depend on a small, protocol-free API (`Gui`, `GuiItem`, `GuiAction`, `GuiClickEvent`, `GuiApi`).
Everything that knows about Minecraft packets lives behind an internal interface, so a Minecraft update is a
change inside `klrnbk-gui`, not in every plugin that shows a menu.

| | |
|---|---|
| Minecraft | **26.2** (protocol 776). PacketEvents 2.14.0 also supports 26.3 (the wire-format tests cover it); not separately live-tested. Clients older than 1.21.5 are refused with `GuiOpenResult.UNSUPPORTED_CLIENT`. |
| Velocity | API `4.1.0-SNAPSHOT` (what the repo builds against) |
| Java | 25 (repo toolchain) |
| Kotlin | 2.4.20-Beta2 (repo toolchain) |
| Protocol layer | [PacketEvents](https://github.com/retrooper/packetevents) **2.14.0** — installed as its own Velocity plugin |
| Bedrock | Geyser **on the proxy** (Geyser-Velocity); Floodgate optional |

> **Status.** The framework is fully unit-tested (API, session logic, wire format of every packet it sends,
> concurrency, leaks) but has **not yet been run against a live proxy with a real Java or Bedrock client** —
> there was no way to do that while writing it. [TESTING.md](TESTING.md) is the procedure and the checklist for
> that first live run; treat everything Bedrock-related as *researched, not verified*.

## Modules

```
plugins/gui/
├── api/       public API — the only module other plugins compile against
├── common/    internal: sessions, events, actions, click logic, the GuiProtocol port (no packet library)
├── velocity/  the plugin: PacketEvents implementation of GuiProtocol, wiring, Velocity listeners
└── example/   a real consumer plugin: /guiexample opens a menu that doubles as the manual test harness
```

```
 your plugin ──► api ──► (Gui, GuiItem, GuiAction, events)          ← stable, semver'd
                          │
                 common: GuiManager, sessions, click interpretation   ← internal
                          │  GuiProtocol (port)
                 velocity: PacketEventsGuiProtocol                    ← the only version-specific code
                          │
                     PacketEvents ──► Velocity ──► Java client / Geyser ──► Bedrock client
```

See [IMPLEMENTATION.md](IMPLEMENTATION.md) for the details and [RESEARCH.md](RESEARCH.md) for the evidence behind
the decisions.

## Installation

On the proxy, in `plugins/`:

1. **KLRNBK Shared Runtime** (`runtime-velocity`) — the repo's shared Kotlin runtime, as for every KLRNBK plugin.
2. **PacketEvents for Velocity** — the *release jar* `packetevents-velocity-2.14.0.jar` (or newer 2.x) from the
   [PacketEvents releases](https://github.com/retrooper/packetevents/releases). Not the thin Maven artifact.
   `klrnbk-gui` refuses to start without it and says so in the log.
3. **`gui-velocity-1.0.1.jar`** (this plugin, id `klrnbk-gui`).
4. Optional, for Bedrock: **Geyser-Velocity** (and Floodgate if you use it) on the *proxy*. Geyser installed only on a
   backend server does not help: those Bedrock players never pass through the proxy, so the proxy cannot open a GUI for them.

A consuming plugin adds to its `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly(project(":plugins:gui:api"))   // in this repo
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
}
```

and to its `@Plugin`: `dependencies = [Dependency(id = "klrnbk-gui")]`. Obtain the API in `ProxyInitializeEvent`
(never in the constructor) with `GuiProvider.get()`.

## Dependencies

| Dependency | Where | Why |
|---|---|---|
| Velocity API | `compileOnly` | players, commands, events |
| Adventure (+ MiniMessage) | `compileOnly` | text; provided by Velocity |
| PacketEvents 2.14.0 | `compileOnly`, **runtime plugin** | container packets, item component encoding per client version |
| Jackson YAML | `compileOnly`, provided by the shared runtime plugin | reading menu files |
| Floodgate / Geyser | **none** (reflection at runtime) | recognise Bedrock players without a compile-time coupling |

Nothing is shaded except this repo's own modules. GPL note: PacketEvents is **GPL-3.0**; see RESEARCH.md §6.

## Basic usage

```kotlin
class SpawnMenu(private val guiApi: GuiApi) {
    fun open(player: Player) {
        val gui = guiApi.create(title = "<gold>Server Menu", rows = 3)

        gui.setItem(
            13,
            guiItem {
                material = Material.DIAMOND
                name = "<aqua>Spawn"
                lore(
                    "<gray>Teleport to spawn",
                    "",
                    "<yellow>Click to teleport",
                )
                onClick {
                    executeServerCommand("spawn")
                }
            },
        )

        gui.open(player)
    }
}
```

A complete, tested example is in [`example/`](example/src/main/kotlin/nl/klrnbk/minecraft/plugins/gui/example/ExampleMenu.kt).

### GUIs

```kotlin
val gui = guiApi.create("<gold>Title", rows = 6)                 // chest 1..6 rows
val hopper = guiApi.create("<gold>Title", GuiLayout.Hopper)      // 5 slots; also GuiLayout.Dispenser (3x3)
val quick = guiApi.gui("<gold>Title", rows = 3) { setItem(0, item) }

gui.setItem(4, item)                     // place / replace
gui.setItem(5) { ctx -> viewerItem(ctx) } // dynamic: rendered per viewer
gui.removeItem(4); gui.clear(); gui.fill(pane); gui.fillEmpty(pane)
gui.update { setItem(1, a); setItem(2, b) }   // atomic batch + one refresh
gui.title("<red>New title"); gui.refresh()    // re-sends only what changed

gui.open(player)      // -> GuiOpenResult (OPENED, CANCELLED, UNSUPPORTED_CLIENT, ...)
gui.close(player); gui.closeAll()
gui.viewers; gui.isViewedBy(player); gui.state(player)
```

* A `Gui` is a **shared** definition: many players can view the same instance and `refresh()` updates all of them.
  What differs per viewer comes from dynamic slots (`GuiItemProvider`) and the per-viewer `GuiState`.
* A player views **one** GUI at a time; opening another replaces it.
* State (`StateKey<T>`) lives exactly as long as the viewing session, so it cannot leak.
* Window titles cannot change while open in place: changing `title` re-opens the window at the next `refresh()`.

### Items

```kotlin
guiItem {
    material = Material.DIAMOND_SWORD        // or Material.of("klrnbk:token"); validated against the client's registry
    amount = 1                               // 1..99
    name = "<aqua>Server Information"        // MiniMessage; or nameComponent = Component...
    lore("<gray>Online players: <white>42", "<gray>TPS: <green>20.0")
    glow()                                   // enchantment glint
    enchant("sharpness", 5)
    customModelData = 7                      // needs a resource pack (Java)
    itemModel("klrnbk:menu/spawn")
    hideTooltipComponents("attribute_modifiers")   // or hideTooltip = true
    component("minecraft:custom_data", GuiData.compound("gui" to GuiData.text("spawn")))
    forPlatform(GuiPlatform.BEDROCK) { customModelData = null }
    onClick { ... }
}
```

* Items are immutable; share them freely.
* Names and lore are Adventure components rendered **without vanilla's default italics** unless you ask for `<italic>`.
* Minecraft ≥ 1.20.5 uses *data components*, not NBT: the framework builds components (`custom_name`, `lore`,
  `enchantments`, `custom_model_data`, `item_model`, `tooltip_display`, …) and PacketEvents writes them in the receiving
  client's format. `component(id, GuiData)` is an escape hatch that supports a documented list of ids (see `GuiData`);
  an unsupported one is skipped with a one-time log warning, never silently mis-sent.
* An unknown `Material` is shown as a barrier and logged once.

### Events

```kotlin
guiApi.events.on<GuiClickEvent> { event -> if (onCooldown(event.player)) event.cancelled = true }
gui.onOpen { event -> ... }       // GuiOpenEvent: cancellable, runs on the calling thread
gui.onClose { event -> ... }      // GuiCloseEvent.reason: CLIENT, PLUGIN, REPLACED, BACKEND_CONTAINER, SERVER_SWITCH, DISCONNECT, SHUTDOWN
gui.onClick { event -> ... }      // player, gui, slot, area, item, clickType, hotbarSlot, cursorItem, cancelled
gui.onDrag { event -> ... }       // once per finished drag: guiSlots, inventorySlots
```

Click types (`GuiClickType`): `LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE, NUMBER_KEY, OFFHAND_SWAP, DROP,
CONTROL_DROP, DOUBLE_CLICK, OUTSIDE_LEFT, OUTSIDE_RIGHT, DRAG_*, UNKNOWN` — one per Minecraft 26.x
container click mode/button combination.

**`cancelled` semantics — read this.** The GUI is a virtual container owned by the proxy, so items can never really
move in or out. Whatever the client predicted is **always** reverted with a fresh synchronisation, cancelled or not.
`cancelled = true` suppresses the item's *action* (its `onClick` handlers). Global handlers run first, then the GUI's,
then the item's; a plain `item.onClick { }` only fires for the four primary clicks (left, right, shift-left,
shift-right) — ask for drop / number-key / offhand explicitly with `onClick(GuiClickType.DROP) { }`, so a stray Q
cannot teleport somebody.

The click event's `cursorItem` has only **type and amount**: since 1.21.5 the client sends hashes of item
components, not the components.

### Actions

`GuiAction` names *where* something runs, because a proxy has several places to run a command:

| Action | Runs where | As whom |
|---|---|---|
| `ExecutePlayerCommand("spawn")` | the proxy if it has such a command, otherwise the backend | the player, exactly like typing it |
| `ExecuteProxyCommand("glist", PLAYER \| CONSOLE)` | Velocity's command manager only | player or proxy console |
| `ExecuteServerCommand("spawn")` / `("spawn", server = "lobby")` | the player's backend, bypassing proxy commands; with `server`, connect first, run after join completes (expires after 15 s) | the player |
| `ConnectToServer("lobby")` | — | — |
| `SendPluginMessage("klrnbk:x", bytes)` | a companion plugin on the backend | (its business) |
| `SendMessage`, `OpenGui`, `Close`, `Refresh`, `Run`, `Composite` | | |
| `SendClickableMessage(text, ClientClick.…)` | the player's chat | see below |

In click handlers the same actions are available as shortcuts: `executeServerCommand("spawn")`, `executePlayerCommand`,
`executeProxyCommand`, `connect`, `sendMessage`, `close`, `refresh`. Commands are validated when the action is
created (blank, over 255 characters, or containing control characters — which could smuggle a second command — are rejected).

**Client-side commands are impossible, so there is no `executeClientCommand`.** A server or proxy cannot make a
Minecraft client run a client-side command on its own. The protocol offers chat messages whose text carries a click
event (`suggest_command`, `run_command`, `open_url`, `copy_to_clipboard`) — the player has to click. That is
`SendClickableMessage`, and it is named for what it does. (Dialogs, added in 1.21.6, are a second real mechanism; not
implemented — see RESEARCH.md §5.)

**Running a command as the backend *console* is also impossible from a proxy.** Use `SendPluginMessage` with a
companion plugin on the backend.

### Platforms

```kotlin
guiApi.platformOf(player)                    // JAVA or BEDROCK, cached per connection
GuiPlatform.BEDROCK.supports(GuiCapability.DRAG)   // informational, conservative
```

Detection order: Floodgate API → Geyser API (both via reflection, no hard dependency) → Floodgate's UUID shape
(`00000000-0000-0000-…`) → `JAVA`. Every event carries the `platform`. Use `forPlatform(BEDROCK) { … }` on an item to
give Bedrock players a different variant. The layouts offered (chest 1–6 rows, hopper, dispenser) are the container
types Geyser has translators for.

## YAML menus (no plugin code needed)

Drop `.yml` files into `plugins/klrnbk-gui/menus/`; the file name is the menu id. On the very first start (empty folder)
the plugin writes [`example.yml`](common/src/main/resources/menus/example.yml) there. Menus are built with the same public
API as plugin code — a YAML menu is a normal `Gui`, so other plugins can open it too:
`guiApi.menus.open("shop", player)` / `guiApi.menus.get("shop")`.

```yaml
title: "<gold>Shop"              # MiniMessage. Required.
rows: 3                          # 1-6. Or `type: hopper` / `type: dispenser` instead of rows.
commands: [shop, buy]            # optional: commands that open the menu (registered on the proxy)
permission: myserver.shop        # optional: required to open it (via command, API or open_menu)
refresh_seconds: 10              # optional: re-render every N seconds while somebody views it
filler: {material: gray_stained_glass_pane, name: " ", hide_tooltip: true}   # optional, fills unclaimed slots

items:
  spawn:                         # any id
    slot: 13                     # or slots: [0, 1, "3-5"]  (a later item wins on the same slot)
    material: diamond            # required. minecraft: namespace optional
    amount: 1                    # 1-99
    name: "<aqua>Spawn"          # placeholders: {player} {uuid} {online} {server} {platform}
    lore: ["<gray>Online: <white>{online}", "", "<yellow>Click"]
    glow: true
    enchantments: {sharpness: 5}
    custom_model_data: 7
    item_model: "myserver:menu/spawn"
    hide_tooltip: false
    hide_tooltip_components: [attribute_modifiers]
    components: {"minecraft:max_stack_size": 16}   # only the ids GuiData lists
    platform:
      bedrock: {custom_model_data: 0, lore: ["<gray>Plain on Bedrock"]}   # any item field; 0 removes model data
    actions:                     # left / right / shift-left / shift-right
      - server_command: spawn
      - close
    on:                          # per click type: left right shift_left shift_right middle
      drop:                      #   number_key offhand drop control_drop double_click
        - message: "<red>You dropped it"
```

Actions (each list entry is a mapping with one key, or the words `close` / `refresh`):

| YAML | Meaning |
|---|---|
| `server_command: spawn` / `{command: spawn, server: lobby}` | command on the player's backend as the player; with `server`, connect first |
| `player_command: help` | as if typed: proxy command if one exists, else backend |
| `proxy_command: glist` / `{command: "send {player} lobby", as: console}` | Velocity command as the player or the proxy console |
| `connect: lobby` | connect to a server |
| `message: "<green>Hi {player}"` | chat message |
| `open_menu: other` | switch to another YAML menu |
| `plugin_message: {channel: "ns:name", text: "go"}` | plugin message to the backend |
| `clickable_message: {text: "[click]", suggest_command: warp}` | chat line; also `run_command`, `open_url`, `copy_to_clipboard` (the player must click it — there is no client-side execution) |
| `close`, `refresh` | |

Rules and behaviour:

* **Validation is at load time.** A bad action (blank command, control characters, bad URL, unknown action or click type),
  bad slot, unknown material syntax or unknown `open_menu` target is reported with `file: path: message` and that item is
  skipped; a bad title/size skips the menu; an invalid YAML file skips only that file. Nothing stops the plugin from starting.
  (Whether a material or enchantment *exists* in the client's registry is only known when it is sent; unknown ones show
  a barrier / are skipped and logged once.)
* **Placeholders** are substituted per viewer into names, lore and action text — and only there (not in `title`, which is shared).
  Items whose name or lore use them are re-rendered per viewer on every refresh; use `refresh_seconds` to keep `{online}` current.
* **Commands:** `/klrnbkgui list | open <menu> | reload` (alias `/guimenu`). `reload` and `list` need `klrnbk.gui.admin`;
  `open` and menu commands use the menu's own `permission`. Menu `commands:` that clash with an existing command are skipped with a warning.
* **Reload** swaps the whole set atomically. Players looking at the old version keep a working (old) menu until they close it; a menu
  that fails after an edit is dropped rather than silently kept stale.
* **PlaceholderAPI & friends** — see the next section.
* The YAML is read with Jackson (from the shared runtime plugin), the same library the fleet's configs use.

### Placeholders from other plugins (PlaceholderAPI, MiniPlaceholders)

PlaceholderAPI itself is a Bukkit/Paper plugin and cannot run on Velocity. Two proxy-side projects fill the gap, and
`klrnbk-gui` uses whichever is installed (both optional, neither is a dependency; the log line
`Menu placeholders: … MiniPlaceholders found/not installed; PAPIProxyBridge found/not installed` tells you what it saw):

| Route | Syntax in `name:` / `lore:` | Install | Behaviour |
|---|---|---|---|
| [**PAPIProxyBridge**](https://github.com/WiIIiam278/PAPIProxyBridge) | `%vault_eco_balance%`, `%player_name%` — real PlaceholderAPI syntax | PAPIProxyBridge on the proxy **and** on the backend(s), plus PlaceholderAPI (and the expansions you use) on the backend | Answers come from the player's *backend* over plugin messages, so they are **asynchronous**: the menu opens with the placeholder blank, then re-renders when the answer arrives. Values are cached ~5 s here and ~30 s by the bridge; they update when the menu re-renders (`refresh_seconds`, or any click). Legacy `§` colours in answers are converted. The player must be on a backend that has PAPIProxyBridge. |
| [**MiniPlaceholders**](https://github.com/MiniPlaceholders/MiniPlaceholders) | `<luckperms_prefix>`, any MiniPlaceholders tag | MiniPlaceholders on the proxy with the expansions you want | Native to Velocity and synchronous. Evaluated per viewer on every render. |

Limits: external placeholders work in item **names and lore only**. They are not available in `title` (shared between viewers)
or in action text (`server_command: give %player_name% …`) — use the built-in `{player}`, `{uuid}`, `{online}`, `{server}`,
`{platform}` there. With MiniPlaceholders installed, every item whose name or lore contains a `<` is rendered per viewer (a
MiniPlaceholders tag looks like any MiniMessage tag); with PAPIProxyBridge, every item containing `%word%`. Code-defined
`guiItem { }` items are not touched: pass a `Component` you built with your own resolver.

## Limitations

* Clients older than **1.21.5** are refused (`UNSUPPORTED_CLIENT`).
* **Bedrock is researched, not verified.** Expected gaps: no drag events, custom models need a Geyser custom-item
  mapping, only a subset of item components is translated. See TESTING.md.
* The player's own inventory is shown below the GUI from a copy of the backend's container-0 packets. A player who
  joined before the plugin started (or whose backend has not sent its inventory yet) sees an empty inventory
  below the menu until the backend sends it.
* Items cannot be moved into or out of a proxy GUI (see *cancelled semantics*).
* One GUI per player. GUIs close on server switch and disconnect; they do not persist.
* `cursorItem` is type + amount only.
* Titles cannot change in place; a title change re-opens the window (fine on Java; unverified on Bedrock).
* If a backend opens its own container while a GUI is open, the GUI closes (`BACKEND_CONTAINER`).

## Compatibility promise

`api` follows semantic versioning. Its public surface mentions only the JDK, Kotlin, Velocity API and Adventure types
(enforced by `ApiSurfaceTest`), is compiled in Kotlin *explicit API* mode, and hides implementation hooks behind
`@InternalGuiApi` (an opt-in that is an error to use). Interface members are added only in minor releases; an addition
to an interface that consumers *implement* would break them, so `Gui`, `GuiApi` and friends are documented as
**implemented only by the framework**. A Minecraft protocol change does not change `api` at all.
