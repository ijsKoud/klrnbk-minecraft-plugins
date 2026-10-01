# Research (done before any code was written)

Date of research: **2026-09-30**. Minecraft **26.2** was released 2026-06-16 (protocol **776**); **26.3** (protocol
**777**) is already out, PacketEvents 2.14.0 (2026-09-23) supports it. My training knowledge ends before 26.x, so
everything below comes from the sources listed, and each finding says how well it is verified:

* **[verified]** — I inspected the artifact/source/test result myself (jar contents via `javap`, Maven metadata, unit tests).
* **[source]** — read from primary documentation or source, but through a page-summarising fetch tool; not re-checked byte for byte.
* **[unverified]** — reasoning or secondary evidence; needs the live test in TESTING.md.

## 1. Repository conventions the framework follows [verified]

Inspected the whole repository first. What was adopted:

* Layout `plugins/<id>/{api,common,velocity,paper}`, Gradle project paths `:plugins:<id>:<module>`, convention plugins
  (`mcplugin.library-conventions`, `mcplugin.velocity-plugin`) from `build-logic`, versions in `gradle/libs.versions.toml`.
* Java 25 / Kotlin 2.4.20-Beta2 / Velocity API `4.1.0-SNAPSHOT` / Adventure 5.2.0 / JUnit 5.11 / MockK 1.13.
* `api` modules expose a `Xyz` interface and an `XyzProvider` singleton (`IdentityProvider`, `WhitelistProvider`) that the
  plugin registers in `ProxyInitializeEvent` — copied as `GuiApi`/`GuiProvider`.
* Guice for wiring (`PluginModule` + `PluginFacade`), `@Plugin` with `Dependency(id = "klrnbk-runtime-velocity")` (the shared
  runtime provides Kotlin; `shadowJar` excludes `kotlin/**`), MockK for Velocity objects (there is no Velocity test harness),
  `java-test-fixtures` for shared test code (`whitelist:common`).
* Not adopted, deliberately: the repo's `i18n` and `config-yaml` packages — this plugin has no user-facing strings and
  nothing to configure, so they would be dependencies without a job.
* One addition to shared build files: a CodeMC Maven repository (restricted to group `com.github.retrooper`) in
  `settings.gradle.kts`, because PacketEvents is only published there; a `packetevents` version and library alias in
  `libs.versions.toml`.
* Pre-existing, unrelated build failures at the time of writing: `:plugins:example-plugin:paper`,
  `:plugins:example-plugin:velocity` (unresolved references) and `:plugins:moderation:common:compileTestKotlin`. They
  do not depend on, and are not touched by, the GUI modules.

## 2. What Velocity can and cannot do [verified / source]

* The Velocity API (4.1.0-SNAPSHOT jar, inspected with `javap`) has **no inventory or container API**. Its low-level
  surface for a player is plugin messaging (`ChannelMessageSink`), `spoofChatInput`, connection requests and events. The
  API javadoc overview describes plugin messages as the low-level interface ([jd.papermc.io/velocity/4.1.0](https://jd.papermc.io/velocity/4.1.0/)).
  Velocity decodes only the play-state packets it needs and forwards the rest untouched.
* Consequence: opening an inventory on a proxy means **writing container packets yourself** — either via a packet library
  that hooks the netty pipeline, or by implementing that hook directly. "Velocity API only" is not an option.
* `Player.spoofChatInput(String)`: implementation read in
  [`ConnectedPlayer.java`](https://raw.githubusercontent.com/PaperMC/Velocity/dev/3.0.0/proxy/src/main/java/com/velocitypowered/proxy/connection/client/ConnectedPlayer.java)
  [source] — it builds a chat/command packet and writes it **to the backend connection**; the proxy's `CommandManager` is
  not consulted. This is what makes "run on the backend" and "run on the proxy" distinguishable (§4).
* `Player.sendPluginMessage` sends to the **client**; `ServerConnection.sendPluginMessage` sends to the **backend** [verified via `javap`].
  The `SendPluginMessage` action deliberately uses the latter.
* `ServerConnectedEvent` (with `previousServer`), `ServerPostConnectEvent`, `DisconnectEvent`: all exist in 4.1.0-SNAPSHOT [verified];
  used for cleanup and for delayed commands after a server switch.

## 3. Minecraft 26.2 container protocol [source + verified through PacketEvents]

Sources: [Java Edition protocol/Packets](https://minecraft.wiki/w/Java_Edition_protocol/Packets),
[Java Edition 26.2](https://minecraft.wiki/w/Java_Edition_26.2), MCProtocolLib `ContainerType`, and — most usefully —
the PacketEvents 2.14.0 wrappers, which I inspected and exercised in unit tests.

* **Open Screen** `(containerId, menuType, title)`. Title is a text component (NBT-encoded on the wire since 1.20.3 — the
  reason the old Protocolize/Velocity combination broke for 1.20.3+ clients; see §6). `menuType` is a **registry id** of
  `minecraft:menu`: `generic_9x1..9x6 = 0..5`, `generic_3x3 = 6`, `hopper = 16` (order confirmed against MCProtocolLib's
  `ContainerType`; the registry has been stable since `crafter_3x3` was inserted in 1.20.3). PacketEvents has no helper for
  it, so the ids live in one table (`MenuTypes`) pinned by a test. **[source]** — the one item in this document whose
  correctness the live test must confirm on a real 26.2 client (a wrong id opens the wrong menu type).
* **Set Container Content** `(containerId, stateId, slots[], carriedItem)` — *all* slots of the window, GUI slots first and
  then the player's 36 inventory slots. Consequence: the proxy must know the player's inventory to render the lower half
  (IMPLEMENTATION.md, "Player inventory mirror").
* **Set Container Slot** `(containerId, stateId, slot, item)`.
* **Container Close**, both directions. Proxy-initiated close is not echoed by the client; client-initiated close is
  `ServerboundContainerClose(id)` and must be swallowed (the backend never heard of the window).
* **Container Click** `(containerId, stateId, slot, button, mode, changedSlots, carried)` — **since 1.21.5 the stacks in
  it are *hashed*** (`HashedStack`: item type, count, per-component hash). Verified in
  `WrapperPlayClientClickWindow` (`getHashedSlots`, `getCarriedHashedStack`). So a proxy learns the cursor's item type and
  count, not its components. `GuiClickEvent.cursorItem` is documented accordingly.
* **Click modes in 26.2** [verified: `WrapperPlayClientClickWindow.WindowClickType`]: `PICKUP, QUICK_MOVE, SWAP, CLONE,
  THROW, QUICK_CRAFT, PICKUP_ALL` (+ `UNKNOWN` in PacketEvents). The same seven as always; no new mode exists in 26.2. The
  button meaning: `PICKUP` 0/1 = left/right (slot −999 = outside); `QUICK_MOVE` 0/1 = shift-left/right; `SWAP` 0–8 = hotbar
  number key, 40 = offhand; `CLONE` = middle; `THROW` 0/1 = drop / ctrl-drop; `PICKUP_ALL` = double-click; `QUICK_CRAFT` packs
  stage (bits 0–1: start/add/end) and type (bits 2–3: left/right/middle) — that is `ClickInterpreter`, tested exhaustively.
* **State ids** are 15-bit counters the server increments for each container update; the client echoes the last one. Because
  the proxy re-synchronises the whole window after every click it does not need to validate them.
* **Window ids.** Vanilla servers count 1–100. The framework uses **101–127**, cycling, so a proxy window can never collide
  with a backend window and always fits a signed byte (older protocols).
* **Item components.** Since 1.20.5 items are `(count, item id, added components, removed components)`. Display name =
  `minecraft:custom_name`, lore = `minecraft:lore`, glint = `minecraft:enchantment_glint_override`, model data =
  `custom_model_data` (a single int before 1.21.4, `{floats, flags, strings, colors}` after), `item_model` (1.21.2+), and since
  1.21.5 the tooltip is controlled by `minecraft:tooltip_display {hide_tooltip, hidden_components}` (which replaced
  `hide_tooltip`/`hide_additional_tooltip`). All of these exist in PacketEvents 2.14.0's `ComponentTypes` [verified]. **No NBT
  item format (`tag`, `display.Name`, `Lore`) is used anywhere.** The vanilla default italics on custom names/lore are
  switched off unless requested.
* **Client versions.** PacketEvents' `ClientVersion` has `V_26_1, V_26_2, V_26_3` [verified]; the wire format is chosen per
  connection, so a 26.2 and a 26.3 player on the same proxy both work. Wire-format tests run for 1.21.5, 1.21.11, 26.1, 26.2, 26.3.

## 4. Commands: what "server", "proxy", "player" and "client" mean

| Wanted | Possible? | How |
|---|---|---|
| Run a command as the player, like typing it | yes | proxy `CommandManager` if it knows the command, else `spoofChatInput` → `ExecutePlayerCommand` |
| Run a **Velocity** command | yes | `CommandManager.executeAsync(player \| console, line)` → `ExecuteProxyCommand` |
| Run a command **on the player's backend** as the player | yes | `spoofChatInput("/cmd")`, bypasses proxy commands → `ExecuteServerCommand` |
| … on **another** server | yes | connect first, send after `ServerPostConnectEvent`, expire after 15 s → `ExecuteServerCommand(server = …)` |
| Run a command as the **backend console** | **no**, not from a proxy | needs a companion plugin: `SendPluginMessage` |
| Make the **client** run a command | **no** | see §5 |

## 5. Client-side commands [source / unverified in detail]

A Minecraft server (or proxy) cannot execute a client command on a client's behalf; there is no packet that does so.
What the protocol *does* offer:

* **Chat text components with a click event** — `suggest_command` (fills the chat box), `run_command` (runs it as the
  player), `open_url`, `copy_to_clipboard`. The player must click. Recent clients can show a confirmation for some of these;
  that is client behaviour. → `GuiAction.SendClickableMessage` + `ClientClick`, named for what happens.
* **Dialogs** (since 1.21.6): a `ShowDialog` packet and click actions `show_dialog` / `custom`. Adventure 5.2's
  `ClickEvent.Action` has `SHOW_DIALOG` and `CUSTOM` [verified], and PacketEvents has `PacketType.Play.Server.SHOW_DIALOG`
  [verified]. It is a legitimate future mechanism (buttons that run commands); not implemented because the container GUI was
  the requirement and Geyser's support for dialogs is unresearched.
* Client-side *mods* can register their own channels, but that needs a mod.

There is deliberately no `executeClientCommand`.

## 6. Dependency candidates

| Candidate | MC 26.2 on Velocity | Kotlin | Maintenance | Verdict |
|---|---|---|---|---|
| **PacketEvents** (retrooper) | **yes** — `packetevents-velocity` 2.13.0 (26.2), 2.14.0 (26.3), on CodeMC [verified: Maven metadata `lastUpdated 2026-09-23`; wrappers for every container packet, `ClientVersion.V_26_2`] | plain Java API, unproblematic from Kotlin | active; a release per Minecraft version | **chosen** |
| Protocolize | no evidence found. Search results only mention 1.20/1.21-era releases; a known issue says inventory titles broke on Velocity for 1.20.3+ (NBT titles) [source: GitHub issue tracker, search result]. I did not fetch its release page, so "no 26.x support" is *not proven* | Java | slower | rejected: cannot show it works on 26.2 |
| Direct protocol implementation | possible | — | ours | rejected: would re-implement item-component encoding for every client version (the hard part), plus netty injection — exactly what PacketEvents maintains. The `GuiProtocol` port keeps this option open |
| Velocity API only | impossible (§2) | — | — | rejected |
| ViaVersion / ViaProxy | irrelevant | — | — | a *translator*, not a GUI/protocol-writing library; may sit in front of us but is not needed |
| Geyser API | not a protocol library | — | — | only for `isBedrockPlayer`; accessed by reflection (its artifact is not on Maven Central; Floodgate's `api` is published as snapshots only — verified from Maven metadata) |
| Adventure | already in the fleet | — | — | text (MiniMessage) |

**PacketEvents specifics that shaped the design** [verified by decompiling the Velocity module]:

* The Maven artifact `packetevents-velocity` is a *thin* jar (55 KB). The runnable plugin is the release jar from GitHub/Modrinth.
  → `compileOnly` + a required `Dependency(id = "packetevents")`. One PacketEvents instance owns the netty injection for all plugins;
  shading a second relocated copy would inject twice.
* `PlayerManagerImpl` accepts a Velocity `Player` and resolves the channel by uuid, so the public `Player` type is enough.
* Sending with `User.sendPacketSilently` skips PacketEvents' own listeners, so the framework's packets are not fed back into
  its inbound hooks.
* **Licence: PacketEvents is GPL-3.0** (POM `<license>`, and the repository's `LICENSE`). The framework links against it at
  runtime as a separately installed plugin. Whether that is acceptable for the KLRNBK plugins' licence is the repository owner's
  decision, not something I can resolve; if it is not, the `GuiProtocol` port is the seam for an own implementation.

## 7. Geyser and Bedrock [source; nothing here has been run]

Read from Geyser's source (`GeyserMC/Geyser`, `master`) and the [Geyser wiki](https://geysermc.org/wiki/geyser/supported-versions/):

* **Where Geyser must run.** Geyser translates a Bedrock client into a *Java* client of the proxy. It must therefore run in front of the
  Velocity proxy — Geyser-Velocity, or standalone connecting to the proxy. Geyser on a backend server would connect Bedrock players
  directly to that backend; the proxy would never see them. The wiki also states that, on Velocity, every destination server must accept
  the Java version Geyser emulates (26.2), or use ViaVersion on the backend.
* **Supported container types** (`InventoryTranslator`): generic 9x1…9x6, generic 3x3, hopper, plus anvil, beacon, furnaces, brewing stand,
  cartography, crafter, crafting, enchanting, grindstone, loom, merchant, shulker box, smithing, stonecutter, lectern. **For an unsupported
  type Geyser closes all windows and tells the server to close it** (`JavaOpenScreenTranslator`). So the framework only offers the layouts
  Geyser demonstrably translates: chest 1–6 rows, hopper, 3x3.
* **How it renders them.** Chest-like windows are done with **fake blocks placed in the world** near the player
  (`SingleChestInventoryTranslator` places a chest block; 9x4–9x6 are double chests). The Java side of that is transparent to us, but it means
  Bedrock GUIs depend on Geyser's world tracking and that a Bedrock client shows a *chest*, not "a generic window".
* **Container id 0 is ignored** by Geyser's open-screen translator (it is the player inventory), which is why 101–127 ids are safe.
* **Titles** go through `MessageTranslator.convertMessage(title, locale)`: components are flattened to Bedrock text.
* **Clicks.** Bedrock sends item-stack *requests*; Geyser simulates them and emits Java `ServerboundContainerClick`s using `LEFT`, `RIGHT`,
  `LEFT_OUTSIDE`, `RIGHT_OUTSIDE`, `SWAP`/hotbar swap, `DROP_ALL`/`DROP_ONE`, `QUICK_MOVE` (shift), and crafting variants. **I found no
  evidence that Geyser ever emits `QUICK_CRAFT` (drag) or `PICKUP_ALL` (double-click)** — Bedrock's drag-split becomes individual place actions.
  Hence `GuiPlatform.BEDROCK` reports `DRAG`, `DOUBLE_CLICK`, `MIDDLE_CLICK`, `NUMBER_KEY_CLICK` and `OFFHAND_SWAP` as *not expected*.
  These flags are informational; nothing is blocked.
* **Container synchronisation.** Geyser applies its own prediction and then trusts the Java server's `SetContent`/`SetSlot`. The framework's
  behaviour — always answer a click with a full re-sync and an empty cursor — is the protocol-level contract a vanilla server also follows, so it
  should hold for Geyser. **[unverified]**
* **Items on Bedrock.** Display name and lore translate. `custom_model_data` / `item_model` only show if Geyser has a custom-item mapping for them
  (Geyser custom-item API / mapping files); arbitrary other components are not translated. `GuiItemBuilder.forPlatform(BEDROCK)` exists to give Bedrock a
  variant. **[unverified — exact per-component behaviour]**
* **Floodgate.** Optional. `FloodgateApi.getInstance().isFloodgatePlayer(uuid)` works "in pre-login events" and needs no player to be online
  ([Floodgate API wiki](https://geysermc.org/wiki/floodgate/api/)); Floodgate UUIDs have a zero most-significant half, the last-resort
  heuristic. Geyser without Floodgate cannot be told apart by UUID; `GeyserApi.api().isBedrockPlayer` covers it when reachable.
  Plugin ids (`geyser`, `floodgate`) for the optional `Dependency` entries are from memory — **verify** against the installed jars.

## 8. Decisions

1. PacketEvents, as a required Velocity plugin, behind the internal `GuiProtocol` port. (§6)
2. Container ids 101–127, one GUI per player, always-consumed clicks with full re-sync. (§3)
3. Layouts limited to chest 1–6 rows, hopper, 3x3 — what Geyser translates. (§7)
4. Player-inventory mirror fed from the backend's container-0 packets. (Set Container Content covers the player's inventory too.)
5. Actions named by execution site; no client-command action. (§4, §5)
6. Detection of Bedrock by reflection + UUID shape, never a compile-time dependency. (§7)
7. Items as immutable values; conversion to `ItemStack`s per client version at send time. (§3)
