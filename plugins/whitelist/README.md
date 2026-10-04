# KLRNBK Whitelist

A proxy-wide whitelist for Velocity. Players are identified through the
[Identity](../identity/README.md) plugin, so the whitelist keeps working when a player renames
themselves. Every change is logged, and other plugins can use the whitelist through a small API.

Requires the `klrnbk-runtime-velocity` and `klrnbk-identity` plugins.

| Module | Contents |
|---|---|
| `api` | `WhitelistApi`, `WhitelistProvider` and the log/list models. Compile against this from other plugins. |
| `common` | Services, repositories, facades and the database. Platform independent. |
| `velocity` | The Velocity plugin: commands, join listener, `config.yml`, `lang/en_us.yml`. |

## Commands

All commands are subcommands of `/whitelist` (alias `/klrnbk-whitelist`). A subcommand only shows up for
players that have its permission.

| Command | Permission | Description |
|---|---|---|
| `/whitelist on` / `off` | `klrnbk.whitelist.toggle` | Enable or disable the whitelist. |
| `/whitelist add <player>` | `klrnbk.whitelist.add` | Add a player. Suggestions come from every player Identity knows. |
| `/whitelist remove <player>` | `klrnbk.whitelist.remove` | Remove a player. Suggestions only contain whitelisted players. |
| `/whitelist list [page]` | `klrnbk.whitelist.list` | Show the whitelisted players, newest first, with who added them and when. |
| `/whitelist logs settings [page]` | `klrnbk.whitelist.logs` | Show when the whitelist was toggled, and by whom. |
| `/whitelist logs player <player> [page]` | `klrnbk.whitelist.logs` | Show when a player was added or removed, and by whom. |
| `/whitelist reload` | `klrnbk.whitelist.reload` | Reload the config, the on/off state and the database connection. |

Players have to have joined the network at least once (so Identity knows them) before they can be added.
The console can run every command; its actions are logged as the console.

## How joining works

When the whitelist is on, a connecting player is checked in `PreLoginEvent`:

1. The connecting UUID is resolved to the player's Identity ID. Clients older than 1.20.2 send no UUID and are
   looked up by name instead.
2. Players Identity doesn't know, and players that aren't whitelisted, are denied with the configured `kick-message`.
3. **If the check itself fails (for example the database is down), the player is denied** (fail closed). The error
   is logged.

Every denial is logged with the name, UUID and reason. The listener runs last and only ever denies: it never
overrides a login mode or denial set by another plugin.

## Logging

Every action is stored in the database (`logs.enabled`, on by default): who added or removed a player, and who
toggled the whitelist, with a timestamp. A change and its log entry are written in a single transaction, and a
toggle is only applied once its log entry is stored. Logs older than `logs.purge-logs-after-days` are removed daily.

The actor of an action is the Identity ID of the sender. Chat output always shows names (`Console` for the console,
`Unknown player` when Identity doesn't know the actor), never IDs.

## Configuration

`plugins/klrnbk-whitelist/config.yml`:

```yaml
use-proxy: true
kick-message: "<gold>You are not whitelisted.</gold>"   # MiniMessage
logs:
  enabled: true
  purge-logs-after-days: 90
database:
  type: SQLITE        # MYSQL, POSTGRESQL or SQLITE
  host: localhost
  port: 3306
  database: database.db
  username: root
  password: password
```

The on/off state is kept in `whitelist_enabled.txt` in the plugin folder (off by default).

### Database connection

If the connection is lost, or the database is unreachable when the proxy starts, the plugin reconnects in the
background: it checks every 5 seconds and waits 5 seconds after a failed attempt, doubling up to 5 minutes, so an
offline database isn't hammered. The same logic (`DatabaseConnectionMonitor` in `packages/database`) is used by Identity.

## Messages

Messages live in `lang/en_us.yml` and are MiniMessage. Every key has to start with `whitelist.`: translations of
all plugins end up in one shared translator on the proxy, and the first plugin that has a key wins. See
`packages/i18n`.

## API

```kotlin
val whitelist = WhitelistProvider.get()          // available once the plugin has started

whitelist.isWhitelistEnabled()
whitelist.isPlayerWhitelisted(identityId)        // Identity ID, NOT the Mojang UUID

whitelist.addPlayerToWhitelist(identityId, actorIdentityId)       // true if something changed
whitelist.removePlayerFromWhitelist(identityId, actorIdentityId)
whitelist.setWhitelistEnabled(true, WhitelistApi.CONSOLE_ACTOR_ID)

whitelist.getWhitelistedPlayers(page = 0, itemsPerPage = 25)      // newest first
whitelist.getWhitelistedPlayersCount()
whitelist.getPlayerLogs(identityId, page = 0, itemsPerPage = 25)  // newest first
whitelist.getPlayerLogsCount(identityId)
whitelist.getSettingsLogs(page = 0, itemsPerPage = 25)
whitelist.getSettingsLogsCount()
```

The API only uses `java.util.UUID` and `java.time.Instant`. Pages start at 0 and `itemsPerPage` is capped at 100.
Use `IdentityApi.getPlayersFromIds` to turn the IDs in the results into names in one query.

## Development

```bash
./gradlew :plugins:whitelist:api:test :plugins:whitelist:common:test :plugins:whitelist:velocity:test
```

The services are final classes, which MockK can't mock here, so most tests run the real services on a temporary
SQLite database. That setup (`WhitelistTestEnvironment`, `FakeIdentityApi`) is shared with the `velocity` module
through Gradle test fixtures in `common`.
