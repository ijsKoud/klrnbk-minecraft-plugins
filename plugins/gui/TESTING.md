# Testing

## Automated

```bash
./gradlew :plugins:gui:api:test :plugins:gui:common:test :plugins:gui:velocity:test :plugins:gui:example:test
./gradlew :plugins:gui:velocity:build          # also produces the shaded plugin jar
```

No Minecraft server, proxy or client is needed. JUnit 5 + MockK (the repo's stack); Velocity objects are mocks because
Velocity has no plugin test harness.

| Suite | What it proves |
|---|---|
| `api` — `GuiLayoutTest`, `GuiItemTest`, `GuiActionTest`, `GuiStateTest`, `MaterialTest`, `GuiClickTypeTest`, `GuiPlatformTest`, `GuiProviderTest` | invalid sizes rejected; item builder (material, amount 1–99, MiniMessage name/lore, enchantments, model data, tooltip, raw components, per-platform variants); **action validation** (blank / control-character / overlong commands, blank servers, bad channels, bad URLs); typed state is atomic |
| `api` — `ApiSurfaceTest` | **no packet-library, Geyser, netty or implementation type is reachable from any public signature** of the API; only JDK/Kotlin/Velocity/Adventure types |
| `common` — `ClickInterpreterTest`, `DragTrackerTest` | every click mode × button of Minecraft 26.x → `GuiClickType`: left/right, shift, number keys 0–8, offhand (40), middle, drop / ctrl-drop, double click, outside clicks, drag start/add/end for all three drag kinds, malformed packets; area classification (GUI / player inventory / outside) for several GUI sizes |
| `common` — `GuiImplTest` | creation, all layouts, item place / replace / remove / fill / fillEmpty, out-of-range slots everywhere, dynamic slots, throwing providers, platform variants, atomic `update`, concurrent writers |
| `common` — `GuiManagerTest` | opening (results for every failure, protocol failure leaves nothing behind, replaced GUI keeps state consistent), **events** (open/close/click/drag, cancellation, ordering global → GUI → item, exceptions contained), **multiple viewers**, **per-player state**, refresh diffing (only changed slots, full resync above 8, title change re-opens), close reasons (client, plugin, replaced, backend container, server switch, disconnect, shutdown), state-id wrapping, consumed / passed-through packets, late clicks for closed windows |
| `common` — `ProxyActionExecutorTest` | each action reaches the right place: player command (proxy vs backend), proxy command as player / console, server command (current server, other server with delayed execution, failure, expiry, wrong server), connect, plugin messages go to the **backend** connection, clickable messages carry the right click event |
| `common` — `GuiConcurrencyTest`, `SerialExecutorTest`, `GuiEventBusTest`, `GuiLeakTest` | 64 players hammering one GUI; 16 GUIs racing to open for one player (exactly one session survives); disconnects racing with clicks and refreshes; close events exactly once per session; per-player ordering with parallelism across players; **no leaked sessions, viewers, platform-cache entries or GUIs (weak-reference GC check)** |
| `common` — `PlatformDetectorTest` | chain order, throwing detectors skipped, caching, Floodgate/Geyser reflection paths (with test stand-ins for their classes), UUID heuristic |
| `velocity` — `WireFormatTest` | **protocol tests**: the packets are serialised by PacketEvents exactly as for a client and read back byte for byte, for client versions 1.21.5, 1.21.11, 26.1, 26.2, 26.3: open-screen (container id, menu type per layout, title), full content (GUI slots + 36 player slots + empty cursor), set-slot, close, item name / lore round trip |
| `velocity` — `PeItemConverterTest` | Minecraft 26.x **data components**: `custom_name`, `lore` (italics off by default), glint, enchantments, `custom_model_data` (list form), `item_model`, `tooltip_display`, supported raw components, unknown material / enchantment / component degrade gracefully and log once |
| `velocity` — `MenuTypesTest` | pins the `minecraft:menu` ids (the one table to re-verify on each Minecraft update) |
| `velocity` — `PacketEventsGuiProtocolTest`, `PlayerInventoryMirrorTest` | each GUI operation → expected packets (open, resync, slot updates, close); inbound clicks decoded and consumed / passed; backend inventory packets feed the mirror |
| `velocity` — `VelocityPluginTest` | plugin metadata (required `packetevents`, optional `floodgate`/`geyser`), start/stop registers/unregisters the API, a missing PacketEvents fails with an actionable message, lifecycle events are safe |
| `common` — `MenuParserTest`, `MenuServiceTest` | **YAML menus**: full-featured file, every action kind, slot syntax (numbers, lists, ranges, bounds), every validation message with file + path, bad items skipped but the menu survives, bad menu-level settings drop it, invalid YAML / bad file names / duplicate ids, first-start seeding of the example (which must load without problems), rendering into real GUIs, filler and slot precedence, placeholders (dynamic items, click-time substitution), platform overrides, permissions, `open_menu`, reload semantics (atomic, failing menu dropped, old viewers unaffected) |
| `common` — `ExternalPlaceholdersTest` | PAPIProxyBridge route (non-blocking format, blanks before the first answer, re-render only when the value changed, TTL refetch, dedupe of in-flight requests, errors logged once and retried, per-player cache and forget, legacy `§` colours), MiniPlaceholders route (tags resolve for the viewer / stay literal when absent), reflective lookup with stand-in API classes, and an end-to-end YAML menu that updates when the async answer arrives |
| `velocity` — command tests in `VelocityPluginTest` | the example menu loads on first start; `/klrnbkgui` and menu commands register and unregister; a broken file never blocks startup; `reload` needs the admin permission |
| `example` — `ExampleMenuTest` | the example plugin runs against the real implementation with a fake protocol |

**What the automated tests cannot cover:** a real netty pipeline, PacketEvents' Velocity injection, the actual bytes a
Geyser instance or a 26.2 client accepts, and rendering. That is what the manual matrix is for.

## Manual compatibility matrix

Setup, once:

1. Velocity 4.x proxy with `runtime-velocity`, **PacketEvents-Velocity 2.14.0**, `gui-velocity-1.0.0.jar`,
   `gui-example-1.0.0.jar`; any 26.2 backend behind it.
2. For Bedrock: Geyser-Velocity (+ Floodgate) on the proxy, a Bedrock client.
3. Start the proxy: the log must say `GUI framework ready. Protocol: PacketEvents 2.14.0 …`. If it says PacketEvents is not
   initialised, fix that first.
4. `/guiexample` as a player. The menu row layout:
   slot 10 spawn (server command + close) · 11 proxy console `/glist` · 12 connect / connect+command ·
   13 clickable message · 14 dynamic info (platform, online count, last click) · 15 per-viewer counter ·
   16 platform-specific model · 22 click test (records the click type in slot 14).

Mark each cell ✅ / ❌ / ➖ (not applicable) and note the client version. **Everything below starts unchecked: nothing in
this table has been run.** The *Expectation* column is what the research (RESEARCH.md §7, Geyser source) predicts, so a
deviation is a finding, not a surprise; "expected ❌" means Geyser very likely cannot do it.

| Feature | Java | Bedrock/Geyser | Expectation / how to test |
|---|:-:|:-:|---|
| Open GUI | ⬜ | ⬜ | `/guiexample`. Java ✅. Bedrock: chest appears (Geyser places a fake chest block). |
| Menu type is right (3 rows, not 6) | ⬜ | ⬜ | Guards the `MenuTypes` table. Also run with a hopper and a 6-row GUI. |
| Title with colours | ⬜ | ⬜ | Bedrock: colour codes survive, other formatting flattened. |
| Close GUI (Esc / X) | ⬜ | ⬜ | `GuiCloseEvent.reason = CLIENT` in the proxy console. |
| Close GUI from code (item 10) | ⬜ | ⬜ | screen closes, no ghost cursor item. |
| Click item | ⬜ | ⬜ | item 10 runs `/spawn` on the backend. |
| Left click | ⬜ | ⬜ | item 22 → slot 14 shows `LEFT`. |
| Right click | ⬜ | ⬜ | `RIGHT`. |
| Shift click | ⬜ | ⬜ | `SHIFT_LEFT` / `SHIFT_RIGHT`. Bedrock expected ✅ (Geyser emits QUICK_MOVE). |
| Number key | ⬜ | ⬜ | `NUMBER_KEY (hotbar n)`. Bedrock: expected ❌ / device dependent. |
| Offhand swap (F) | ⬜ | ⬜ | `OFFHAND_SWAP`. Bedrock expected ❌. |
| Drop (Q / Ctrl-Q) | ⬜ | ⬜ | `DROP`, `CONTROL_DROP`. Bedrock: Geyser emits drop clicks → maybe ✅. |
| Middle click (creative) | ⬜ | ⬜ | `MIDDLE`. Bedrock expected ❌. |
| Double click | ⬜ | ⬜ | `DOUBLE_CLICK`. Bedrock expected ❌. |
| Drag over slots | ⬜ | ⬜ | drop a stack across several slots: `GuiDragEvent` in console, items snap back. Bedrock expected ❌ (becomes single clicks — check they are still reverted). |
| Click outside the window | ⬜ | ⬜ | `OUTSIDE_LEFT/RIGHT`, no item dropped in the world. |
| Item cannot be taken / moved | ⬜ | ⬜ | grab a GUI item, put items in: everything snaps back; **nothing** appears in the real inventory. |
| Player's own inventory shown below GUI | ⬜ | ⬜ | matches the real inventory; change it on the backend while open (`/give`) and re-open. |
| Display name & colours | ⬜ | ⬜ | |
| Lore (multi-line, blank line) | ⬜ | ⬜ | no purple italics on Java. |
| Tooltip hide (`hideTooltip`) | ⬜ | ⬜ | the filler panes show no tooltip. Bedrock: unknown. |
| Enchantment glint (`glow()`) | ⬜ | ⬜ | item 16. |
| Custom model data | ⬜ | ⬜ | item 16 (Java needs a resource pack; Bedrock expected ❌ without a Geyser custom item mapping — the platform variant then shows a plain star). |
| Raw data component / `item_model` | ⬜ | ⬜ | Bedrock expected ❌. |
| Dynamic updates | ⬜ | ⬜ | item 15: click changes the number without closing or flicker. |
| Title change | ⬜ | ⬜ | `gui.title("…"); gui.refresh()` re-opens in place. Bedrock: unverified. |
| Server command action | ⬜ | ⬜ | item 10. |
| Server command on another server | ⬜ | ⬜ | item 12 right click: connects, then runs `/spawn` there after join. |
| Proxy command action | ⬜ | ⬜ | item 11 (output in proxy console). |
| Connect action | ⬜ | ⬜ | item 12 left click. GUI closes on the server switch. |
| Clickable message (client side) | ⬜ | ⬜ | item 13; the chat line puts `/warp` into the chat box **only after you click it**. Bedrock: click events on chat are rendered by Geyser: unverified. |
| Multiple viewers | ⬜ | ⬜ | two players open it; each counter is independent; `gui.refresh()` reaches both. |
| Mixed Java + Bedrock viewers | ⬜ | ⬜ | same GUI instance, both platforms; item 14 shows the right platform. |
| Reopen after close | ⬜ | ⬜ | no stale state; counter restarts (state is per session). |
| Disconnect while open | ⬜ | ⬜ | no exception; `DISCONNECT` close event. |
| Server switch while open | ⬜ | ⬜ | GUI is gone, client not kicked. |
| Backend opens a chest while GUI is open | ⬜ | ⬜ | `BACKEND_CONTAINER`, the chest is usable. |
| Backend inventory keeps working after the GUI | ⬜ | ⬜ | no lost items, no desync (a stack cursor bug would show here). |
| 26.3 client | ⬜ | ➖ | supported by PacketEvents 2.14.0; confirm. |
| Client older than 1.21.5 (ViaVersion) | ⬜ | ➖ | `UNSUPPORTED_CLIENT`, no kick. |

### YAML menus (manual)

1. First start: `plugins/klrnbk-gui/menus/example.yml` appears; `/klrnbkgui list` shows `example`; `/guiexample` opens it (needs `klrnbk.gui.example`).
2. Edit a title, `/klrnbkgui reload`: the change shows on the next open; a typo in a file is reported in chat and in the log, the other menus keep working.
3. `{online}` / `{player}` in lore update every `refresh_seconds` without closing the menu (Java and Bedrock).
4. Each action type in a real click: server_command, proxy_command (console), connect, `server_command` with `server:`, clickable_message, open_menu.

### Placeholders (manual — needs the real plugins)

1. With PAPIProxyBridge (proxy + backend) and PlaceholderAPI (+ the `Player` expansion) installed: an item with `lore: ["%player_name%"]` shows blank for an instant, then the name; check the log line lists PAPIProxyBridge as found.
2. With MiniPlaceholders and any expansion: an item name `<your_tag>` shows the value; change the underlying value and wait for `refresh_seconds`.
3. Remove each plugin again: menus still load, the raw `%…%` / `<…>` text is shown, nothing errors.
4. Confirm the plugin ids `miniplaceholders` / `papiproxybridge` (the optional dependencies) against the installed jars.

### Known / expected Geyser limits (from the source, not from a run)

* **Unsupported container types are closed by Geyser** (`JavaOpenScreenTranslator`): this is why only chest 1–6 rows, hopper and 3x3 exist.
* **Drag, double click, middle click, offhand swap** — no evidence Geyser generates them. Expect ❌.
* **Custom models and most non-basic components** need Geyser-side mappings; expect a plain item.
* **Bedrock UI differences** (tap-based item movement, the chest being a placed block) are not something the framework can change.

## Reporting

When the matrix is filled in, put the results in this file (client versions, Geyser and PacketEvents versions, date) and
change `GuiPlatform.BEDROCK`'s capability set if reality differs from the conservative default.
