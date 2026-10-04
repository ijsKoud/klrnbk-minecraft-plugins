# KLRNBK Identity

Keeps track of every player that has joined the network and gives each of them a stable **Identity ID**. Other
KLRNBK plugins build on it: they store the Identity ID instead of the Minecraft UUID or the name.

Available for Velocity and Paper.

| Module | Contents |
|---|---|
| `api` | `IdentityApi`, `IdentityProvider`, `IdentityPlayer`, `IdentityPlayerLogs`. Compile against this from other plugins. |
| `common` | Services, repositories, command facades and the database. |
| `velocity`, `paper` | The platform plugins: commands, listeners, `config.yml`, `lang/en_us.yml`. |

## Commands

| Command | Alias | Permission | Description |
|---|---|---|---|
| `/playerlist [page]` | `/identity-playerlist` | `klrnbk.identity.view.players` | Paginated list of all players. |
| `/player <player>` | `/identity-player` | `klrnbk.identity.view.player-info` | Details of a player. |
| `/playerlogs <player> [page]` | `/identity-playerlogs` | `klrnbk.identity.view.logs` | Connection logs of a player. IPs are only shown with `klrnbk.identity.view.ips`. |
| `/identityreload` | `/identity-reload` | `klrnbk.identity.reload` | Reload the config and the database connection. |

## Configuration

`config.yml` has `logs` (`enabled`, `log-ips`, `purge-logs-after-days`), the `database` section
(`MYSQL`, `POSTGRESQL` or `SQLITE`) and an `encryption-key` that is generated on first start and used to encrypt
the stored IP addresses. If the database connection is lost, Identity reconnects in the background with an
increasing delay (see the Whitelist README).

## API

```kotlin
val identity = IdentityProvider.get()

identity.getPlayerFromUuid(minecraftUuid)     // by Minecraft UUID
identity.getPlayerFromId(identityId)          // by Identity ID
identity.getPlayerFromName("alice")           // case-insensitive; if several players ever had the name, the last one to join

identity.getPlayersFromIds(listOf(a, b, c))   // many at once, one query
identity.getPlayerNames(prefix = "al", limit = 100)   // sorted, case-insensitive prefix search, for suggestions
identity.getAllPlayers(page = 0, itemsPerPage = 25)   // sorted by name
identity.getPlayerCount()
```

`IdentityPlayer.id` is the Identity ID, `IdentityPlayer.playerId` is the Minecraft UUID. Don't mix them up: passing
a Minecraft UUID to `getPlayerFromId` finds nothing.

## Messages

Message keys must start with `identity.` (see `packages/i18n`).

## Development

```bash
./gradlew :plugins:identity:common:test :plugins:identity:velocity:test :plugins:identity:paper:test
```
