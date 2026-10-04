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

### Pagination without OFFSET

`LIMIT n OFFSET m` makes the database walk over and discard `m` rows for every request, so page 100 costs a
hundred times as much as page 1. `KeysetPaginator` pages by *seeking* instead: it asks for the rows after the last
row of the previous page (`WHERE sort > :last OR (sort = :last AND id > :lastId) ORDER BY sort, id LIMIT n`), which an
index answers directly at any depth. The id breaks ties between rows with the same sort value, so no row is skipped
or repeated, and rows inserted while somebody is paging don't shift the next page.

```kotlin
private val paginator =
    KeysetPaginator(
        entityClass = WhitelistLogEntity,
        table = WhitelistLogEntityTable,
        sort = WhitelistLogEntityTable.timestamp,
        order = SortOrder.DESC,
        sortValueOf = { it.timestamp },
        encode = Instant::toString,
        decode = Instant::parse,
    )

// inside a transaction (BaseRepository.execute):
paginator.findPage(scope = "player:$id", filter = Table.identityId eq id, page = 3, size = 10)
```

Callers still use page numbers. When a page is read, `PageCursorCache` remembers where the next one starts (per
`scope`, for 5 minutes, least recently used entries are dropped), so going to the next page, back to a visited page
or reloading a page never needs an offset. Only a page whose position is unknown (first request after a restart,
or typing page 100 right away) looks its position up once, with an offset over just the sort and id columns, which
the index covers; the rows themselves are still read by seeking. Consequences worth knowing:

- Page boundaries are fixed when a page is first read and then reused for up to 5 minutes, so pages stay stable
  while rows are added, but a cached page 3 can differ slightly from what a fresh page 3 would be.
- Give every distinct filter its own `scope`, and add an index over the filter columns followed by the sort column
  (for example `index(false, identityId, timestamp)`) so the seek is an index range scan.
- Use it for ordered, user-facing lists. Plain `findAll(...).limit(...)` is fine for small tables.

Used by the Identity player list and connection logs, and the Whitelist list and logs.

## i18n

`TranslationService` registers a plugin's language files in Adventure's `GlobalTranslator`, which is **shared by
every plugin on the server** and answers with the first store that has the key. To stop plugins from showing each
other's text, every key has to start with the namespace of the `Key` the service was created with:
`TranslationService(Key.key("whitelist", "velocity"))` only accepts keys starting with `whitelist.`. In a language
file, wrap everything in a top-level section named after the plugin. Loading a file with other keys fails with an
`IllegalArgumentException` that lists them.
