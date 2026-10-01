# Implementation

## Architecture

```
 consumer plugins ── compile against ──► api                      (public, semver, explicit-API mode)
                                            ▲
 velocity ── VelocityPlugin, PluginFacade   │ implements
   │        PacketEventsGuiProtocol ───────┐│
   │                                       ▼│
   └─ depends on ──► common ── GuiManager, GuiImpl, ViewerSession, ClickInterpreter,
                              ProxyActionExecutor, GuiEventBus, PlatformDetectors
                                   │
                                   └── GuiProtocol   ← the port. No packet types in its signature.
```

* **`api`** — `Gui`, `GuiApi`, `GuiProvider`, `GuiItem`/`guiItem {}`, `Material`, `GuiAction`, events, `GuiLayout`,
  `GuiPlatform`, `GuiState`. Depends on Velocity API + Adventure (both `compileOnly`) and nothing else.
* **`common`** — everything that is logic rather than wiring. It compiles against Velocity but *not* against any packet
  library, so it is testable with a fake `GuiProtocol` (`FakeGuiProtocol` in `testFixtures`).
* **`velocity`** — the plugin. Contains the only PacketEvents code, in `protocol/packetevents`.
* **`example`** — a consumer plugin, also the manual test harness.

Classes in `common`/`velocity` are public only because Kotlin `internal` is module-wide; nothing in `api` refers to them
(`ApiSurfaceTest`). The few `api` types that only the implementation may construct (event constructors,
`GuiProvider.register`) carry `@InternalGuiApi`, an error-level opt-in.

## Protocol abstraction

`GuiProtocol` (in `common`) is the only thing the rest of the code knows about the wire:

```kotlin
check(player): ProtocolCheck                     // can we open a window right now? (play phase, supported client)
open(player, WindowSpec)                         // open screen + full content
resync(player, WindowSpec)                       // full content, empty cursor (undo prediction)
updateSlots(player, containerId, stateId, map)   // some slots
close(player, containerId)
bind(InboundHandler) / unbind()                  // deliver decoded clicks / closes / "backend opened a container"
forget(uuid)
```

`WindowSpec` carries `GuiItem`s — the same immutable values the consumer built — so *rendering an item to bytes is the
protocol layer's job*, done for the receiving client's version. Inbound, the protocol layer decodes a packet into
`RawClick(containerId, stateId, slot, button, RawClickMode, cursor)`; `RawClickMode` mirrors vanilla's `ClickType` and is
independent of how any version encodes it. The verdict (`PASS` / `CONSUME`) tells the layer whether to cancel the packet.

`PacketEventsGuiProtocol` splits further so that each part can be tested alone:

| Class | Job |
|---|---|
| `PacketFactory` | pure: `WindowSpec`/stacks → `WrapperPlayServer…` |
| `PeItemConverter` | `GuiItem` → `ItemStack` for a `ClientVersion` (data components) |
| `MenuTypes` | `GuiLayout` → `minecraft:menu` registry id; minimum client version |
| `PeClickDecoder` | `WrapperPlayClientClickWindow` → `RawClick` (hashed stacks, both layouts) |
| `PlayerInventoryMirror` | copy of the backend's container 0 |
| `PacketTransport` | seam over PacketEvents' connection manager (faked in tests) |
| `Listener` (inner) | ~25 lines: decode → delegate → apply verdict |

### Protocol flow

```
open:   → OpenScreen(id, menuType, title)  → SetContent(id, state, [gui slots + 36 player slots], EMPTY cursor)
click:  ← Click(id, …)   consumed at once;  → SetContent(id, state+1, …, EMPTY cursor)   (revert prediction)
                          then, on the player's serial queue: GuiClickEvent → handlers → item actions
drag:   ← Click(QUICK_CRAFT start/add…/end) consumed; no resync mid-drag; at "end": → SetContent, then GuiDragEvent
update: → SetSlot(id, state, slot, item) per changed slot (≤ 8 slots) or SetContent
title:  → OpenScreen(same id, new title) → SetContent              (client swaps the title in place)
close:  ← Close(id) consumed → GuiCloseEvent(CLIENT)      /      → Close(id) → GuiCloseEvent(PLUGIN)
```

### Container ids

`101..127`, cycling per player. Vanilla servers use 1–100, so no collision, and 127 fits the signed byte older protocols use.
Packets addressed to any id in that range are **always consumed**, even without a session, so a click that was in
flight when a GUI closed can never reach the backend (which would otherwise log a bad container id).

### Player inventory mirror

A window's content packet includes the player's 36 inventory slots, and the client overwrites its real inventory
slots from it. The proxy does not own the inventory, so it listens to the backend's `WindowItems(0)` / `SetSlot(0|-2)` on the way
through and keeps the last known container-0 content per player (main inventory `9..35`, hotbar `36..44`). This costs one
wrapper decode per inventory packet of every player, for the lifetime of the connection — inventory packets are rare compared with
entity or chunk traffic. Until the backend has sent its inventory (e.g. the plugin started after the player joined) the lower half
of a GUI is empty until the next inventory packet. Entries are removed on disconnect.

## Version abstraction

Three separate axes, each isolated:

1. **Wire layout of packets** (field order, hashed vs. full stacks, container-id width, component encoding) →
   PacketEvents, selected by the connection's `ClientVersion` at write time. Not our code.
2. **Registry ids** (`minecraft:menu`) → `MenuTypes`, one table, pinned by `MenuTypesTest`.
3. **Item semantics** (which component means "name", "lore", "tooltip") → `PeItemConverter`. It already branches where
   the format changed inside the supported range (`custom_model_data` list form from 1.21.4).

The public API contains none of the three.

## Geyser integration

Geyser makes a Bedrock player look like a Java client of the proxy, so **the same packets are sent to both platforms** —
there is no Bedrock-specific packet path. What differs is expressed above the protocol layer:

* `PlatformDetector` chain (`Floodgate` → `Geyser` reflection → Floodgate UUID shape → `JAVA`), cached per connection and
  cleared on disconnect. Reflection keeps Floodgate's snapshot-only artifact and Geyser off the compile classpath; the plugin declares both
  as *optional* dependencies so Velocity lets our class loader see them.
* Each session records the platform; items are rendered through `GuiItem.forPlatform(platform)`, so a consumer can supply a
  Bedrock variant; events carry `platform`.
* Only layouts Geyser translates are offered. See RESEARCH.md §7 for what is and is not known about Bedrock — including the
  capability flags on `GuiPlatform`, which are conservative and informational.

## YAML menus

`common/menu`: `MenuParser` (Jackson `JsonNode` → `MenuDef`/`ItemSpec`, collecting `MenuIssue`s with file + YAML path, never
throwing for bad content) → `MenuFactory` (`MenuDef` → `Gui`, using only the public API: `setItem`, dynamic providers,
`forPlatform`, `onClick`) → `MenuService` (folder scan, atomic swap of an immutable `Map<String, LoadedMenu>` in an
`AtomicReference`, reload serialised, `GuiMenus` implementation). Actions are parsed into `ActionTemplate`s — a function from
a placeholder substitutor to a `GuiAction` — validated once at load with neutral placeholder values (so the same constructor
checks that protect code-defined actions protect YAML ones), and built again at click time with the clicker's real values.
External placeholders (`ExternalPlaceholders`) sit between the YAML text and the MiniMessage parse: PAPIProxyBridge values are
substituted into the raw string first (`PapiPlaceholders`: non-blocking, per-(player, text) cache, refetch after a TTL, a re-render
callback only when a value actually changed, so an unchanged answer cannot loop), then the string is deserialised with the viewer as
audience and MiniPlaceholders' `audienceGlobalPlaceholders()` resolver. Both are found by reflection on every use, so neither is a
compile-time dependency and a plugin that loads later is still picked up. Because their output can change without the menu changing,
`MenuFactory` renders such items per viewer (dynamic slots) instead of once.
`velocity/commands/MenuCommands` owns the proxy-facing parts: per-menu commands (unregistered and re-registered on every sync),
`/klrnbkgui`, and the `refresh_seconds` timers (skipped while nobody views the menu). Jackson comes from the shared runtime plugin.

## Threading model

Velocity runs each connection on a netty event loop and plugin events on its own executor; plugins call in from anywhere.

| State | Guard |
|---|---|
| `GuiImpl` slots | immutable array in an `AtomicReference`, copy-on-write. Readers (rendering, `getItem`) never lock and see a consistent snapshot. Writers take a `ReentrantLock`, which also makes `update { }` atomic (edits a private copy, publishes once). |
| `GuiImpl.viewerSessions`, `GuiManager.sessions` | `ConcurrentHashMap`. A player's session is replaced inside `compute`, so two concurrent `open`s cannot both win; `closeSession` uses `remove(key, value)` so exactly one of several racing close paths fires the close event. |
| Outbound packets of one session | `ViewerSession.lock` while sending: keeps state ids in order. Sending is non-blocking (netty enqueues), so it is held for microseconds. |
| Refresh | `ViewerSession.refreshLock` serialises whole refreshes (they run user `GuiItemProvider`s); the netty thread never takes it. |
| Inbound packets | run on the player's event loop and do the minimum: decode, re-sync from the *last rendered snapshot* (no user code), enqueue. |
| Event handlers | `SerialExecutor` per player over a shared virtual-thread executor: in order, one at a time per player, parallel across players, no thread parked per player. `GuiOpenEvent` is the exception: it runs synchronously on the caller of `open`, because it can veto. |
| `GuiEventBus` | copy-on-write listener list; a handler may (un)register listeners during dispatch. |
| `GuiState` | `ConcurrentHashMap` with atomic `update`. Scoped to the session, so it cannot outlive the viewer. |

No `synchronized` block covers I/O or user code except `refreshLock`, which exists to cover user code and is per session.
Guarantees: at most one session per player; a close event exactly once per session; click events for a player are
delivered in the order the client sent them. Click processing is skipped once a session is marked closed (best effort: a
handler that is already running when the session closes is allowed to finish).

Verified by `GuiConcurrencyTest` (64 players on one GUI, 16 GUIs racing to open for one player, disconnect racing with clicks and
refreshes, close-path races) and `SerialExecutorTest`.

## Lifecycle and leaks

`DisconnectEvent` → `GuiManager.onDisconnect`: closes the session (no packet to a dead connection), and asks the protocol
(mirror), platform cache and action executor (queued server commands) to forget the player. `ServerConnectedEvent` with a previous
server → `onServerSwitch`: the client discards screens on world change, so the session is dropped without sending
anything (a packet during a phase change would be a protocol error). Plugin shutdown closes all GUIs, unregisters the API, unbinds from
PacketEvents and stops the executor. `GuiLeakTest` checks with weak references that a closed GUI and its player are collectable.

## API design

* Kotlin idioms: DSL (`guiItem { }`, `onClick { }` with a receiver), named/default arguments where an interface method is
  not involved, extension `GuiApi.gui(title, rows) { }`, `inline reified` `events.on<T> { }`, sealed `GuiAction` / `ClientClick` / `GuiLayout`.
* Immutability: `GuiItem`, actions, layouts, `Material`, events' data are immutable; `Gui` and `GuiState` are the mutable, thread-safe
  objects.
* Interfaces have no default parameters (default arguments in interfaces compile to a synthetic `DefaultImpls` that is a binary-compat trap);
  overloads are used instead.
* `Material` is a value wrapping a namespaced key, not an enum, so a new Minecraft item needs no API release.
* Validation at construction: bad commands, channels, URLs, amounts, rows fail where they are written, not when a player clicks.
* Explicit API mode is on for `api`.

## Dependency decisions

See RESEARCH.md §6. In short: PacketEvents (required plugin, `compileOnly`), Velocity + Adventure (`compileOnly`), nothing else at
runtime. Test-only: MockK, JUnit (repo standard), `netty-buffer` and `adventure-nbt` (PacketEvents needs them to initialise its item
registries in unit tests; Velocity provides them at runtime).

## Upgrading to a new Minecraft version

1. Bump `packetevents` in `gradle/libs.versions.toml` to a release that supports the version. Run the tests: `WireFormatTest` /
   `PeItemConverterTest` exercise the new `ClientVersion` (add it to the `@EnumSource`).
2. Check whether the `minecraft:menu` registry changed (compare with a server's `reports/registries.json`, or MCProtocolLib's
   `ContainerType`). Update `MenuTypes` if so; `MenuTypesTest` is a tripwire — update the *table*, then the test.
3. Check whether the container click packet or its modes changed (protocol page, "Click Container"). New mode → add to `RawClickMode`,
   `PeClickDecoder.mode`, `ClickInterpreter` (and a `GuiClickType` value, which is an additive API change).
4. If a component was renamed / split (as happened with `hide_tooltip` → `tooltip_display`), adjust `PeItemConverter`. Add a
   branch on `ClientVersion` rather than replacing the old one while old clients are still supported.
5. If PacketEvents stops being viable, implement `GuiProtocol` differently (own netty handler, another library) and switch it in
   `PluginFacade`. **No consumer changes** — that is the point of the port.
6. Re-run the manual matrix in TESTING.md.

`MenuTypes.MINIMUM_CLIENT` (1.21.5: hashed stacks, `tooltip_display`) is the lower bound of what the converter supports; raise it when
you drop a branch.

## Known limitations

* Not run against a live proxy or client (see TESTING.md). PacketEvents' Velocity injection, the `packetevents`/`floodgate`/`geyser`
  plugin ids, and the menu-type table are the assumptions with the most risk.
* Bedrock behaviour is from reading Geyser's source. Capability flags are conservative and informational.
* Items can never move in or out of a proxy GUI; clicks are always reverted.
* One GUI per player; GUIs do not survive a server switch.
* `GuiClickEvent.cursorItem` is type + amount (the client sends hashes).
* Raw data components: an explicit list, not arbitrary (PacketEvents' component codecs cannot be built from a generic tree).
* Titles cannot change in place on an open window except by re-sending the open packet.
* The lower (player-inventory) half is a mirror; it can be stale if the backend changes the inventory without sending a packet.
* Minimum client 1.21.5.
* PacketEvents is GPL-3.0 (RESEARCH.md §6).
* The shaded plugin jar contains Guava (~3 MB) because the shared `mcplugin.kotlin-conventions` adds Guice as an
  `implementation` dependency and the shared `shadowJar` excludes the wrong Guava package path (`com/google/guava/**`
  instead of `com/google/common/**`). Pre-existing, fleet-wide, harmless (Velocity ships its own Guava), left alone.
