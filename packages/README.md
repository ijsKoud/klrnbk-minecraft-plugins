# packages

Standalone shared packages, for code that isn't tied to a single plugin.

| Package | Purpose |
|---|---|
| `database` | HikariCP + Exposed setup shared by the plugins: `BaseDatasource`, `BaseRepository`, `DatasourceConfig` and the connection monitor below. |
| `i18n` | MiniMessage language files and the `TranslationService`. |
| `config-yaml` | Loading and writing the plugins' `config.yml`. |
| `velocity-commands` | The `Command` interface and registry used by the Velocity plugins. |
| `common-constants`, `common-cryptography`, `paper-commands` | Small shared helpers. |

## database

### Automatic reconnect

`DatabaseConnectionMonitor` checks the connection every 5 seconds and reconnects when it was lost. After a failed
attempt it waits 5 seconds, doubling after every consecutive failure up to 5 minutes (`ReconnectBackoff`), so a
database that is down isn't flooded with connection attempts. A plugin's `DatabaseService` starts it on `start()`
and stops it first on `stop()`, and survives a database that is offline at startup.

### Connection checks

`BaseDatasource.isConnected()` is true when the pool is open and hands out a valid connection. The connection
timeout is 5 seconds so a dead database fails fast instead of blocking player logins.

## i18n

`TranslationService` registers a plugin's language files in Adventure's `GlobalTranslator`, which is **shared by
every plugin on the server** and answers with the first store that has the key. To stop plugins from showing each
other's text, every key has to start with the namespace of the `Key` the service was created with:
`TranslationService(Key.key("whitelist", "velocity"))` only accepts keys starting with `whitelist.`. In a language
file, wrap everything in a top-level section named after the plugin. Loading a file with other keys fails with an
`IllegalArgumentException` that lists them.
