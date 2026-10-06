# KLRNBK DiscordId

Lets players link their Minecraft account to their Discord account. A Discord bot handles the Discord side, the
plugin the Minecraft side. Linked accounts can be looked up from either side, and the plugin keeps the stored Discord
username and server-booster status up to date.

Available for Velocity and Paper.

| Module | Contents |
|---|---|
| `common` | Services, repositories, the Discord bot (commands and events), facades and the database. |
| `velocity`, `paper` | The platform plugins: the `/discordid` command, `config.yml`, `lang/en_us.yml`. |

## Requirements

- **KLRNBK Identity** (required): links are stored against the Identity ID, and the plugin reads players through
  `IdentityProvider`. Only Identity registers it, DiscordId must not and does not bundle the Identity API. On Paper
  `paper-plugin.yml` declares Identity as a required dependency that loads before DiscordId; on Velocity the plugin
  declares `klrnbk-identity`.
- **KLRNBK Shared Runtime** (required): provides the shared libraries (Guice, Exposed, ...) at runtime.
- A Discord bot application and token (see below).

## Linking an account

1. In Minecraft run `/discordid link`. You get a code that is valid for 20 minutes. Click it to copy.
2. In Discord run `/link code:<code>`.
3. The accounts are linked. The code is used up.

Rules:

- A Minecraft account and a Discord account can only be linked to each other once.
- After unlinking you can't request a new code until `unlink-cooldown` has passed, and you can't unlink before the
  cooldown since the link was made either (default 30 days). Players with `klrnbk.discord-id.unlink.bypass` and admins
  skip this.
- `/lookup user:<user>` in Discord answers with the Minecraft name of a linked user. `/discordid lookup <player>` in
  Minecraft shows the Discord account of a player.

## Commands

All commands are subcommands of `/discordid` (aliases: `/klrnbk-discordid`, on Velocity also `/discordid`).

| Command | Permission | Description |
|---|---|---|
| `/discordid link` | `klrnbk.discord-id.link` | Get a link code. Players only. |
| `/discordid unlink` | `klrnbk.discord-id.unlink` | Unlink your Discord account (after the cooldown). Players only. |
| `/discordid lookup <player>` | `klrnbk.discord-id.lookup` | Show the Discord account of a player. |
| `/discordid adminunlink <player>` | `klrnbk.discord-id.unlink.force` | Unlink a player, ignoring the cooldown. |
| `/discordid reload` | `klrnbk.discord-id.admin.reload` | Reload the config, the database connection and the bot. |
| `/discordid export` | `klrnbk.discord-id.admin.export` | Export all links to a new zip in the plugin's `exports` folder. |
| `/discordid import <file>` | `klrnbk.discord-id.admin.import` | Import a file from the `exports` folder. Only works on an empty database, all or nothing. |

Other permission: `klrnbk.discord-id.unlink.bypass` lets a player unlink during the cooldown.

To migrate to another database: export, switch `database` in `config.yml` and `/discordid reload`, copy the zip into
the `exports` folder of the new setup if needed, then import.

## Configuration

`config.yml` is created on first start.

| Option | Default | Description |
|---|---|---|
| `use-proxy` | `false` (Paper), `true` (Velocity file) | Paper only: when `true` the bot and the commands are not started on Paper because the proxy runs them (a bot token can only be connected once). The database connection must be the same on both. Velocity ignores it. |
| `discord.bot-token` | none | The token of your Discord bot. Required. |
| `discord.booster-role` | none | ID of the server booster role. Linked players get `is_booster` set from it. Leave unset to disable. |
| `discord.unlink-cooldown` | `2592000000` | Milliseconds (30 days) before a player may unlink, or request a new code after unlinking. |
| `discord.status-message` | `Discord & Minecraft players` | Text of the bot's presence. |
| `discord.status-type` | `3` | `0` Playing, `1` Streaming, `2` Listening, `3` Watching, `4` Custom, `5` Competing. |
| `database.type` | `SQLITE` | `SQLITE`, `MYSQL` (MariaDB) or `POSTGRESQL`. |
| `database.host` / `port` / `database` / `username` / `password` | `localhost` / `3306` / `database.db` / `root` / `password` | Connection details. For `SQLITE`, `database` is a file in the plugin's data folder and the rest is ignored. |
| `database.maximum-pool-size` | `10` | Size of the connection pool. |

If the database connection is lost, the plugin reconnects in the background with an increasing delay. Expired link
codes are deleted every minute.

### Discord bot setup

1. Create an application at the [Discord Developer Portal](https://discord.com/developers/applications) and add a bot.
2. Copy the bot token into `discord.bot-token`.
3. Invite the bot to your server with the `bot` and `applications.commands` scopes. It needs no special permissions.
4. Start the server. On startup the bot registers its global slash commands `/link` and `/lookup`, which can take a
   little while to show up in Discord.

The bot requests the privileged **Server Members** gateway intent so it receives role changes and username updates
of linked players. Enable **Server Members Intent** under *Bot -> Privileged Gateway Intents* in the Developer Portal,
or Discord refuses the connection. A check every 4 hours (first one after 10 minutes) also repairs differing Discord
usernames and booster statuses of linked players, for example after downtime.

If the bot can't log in (for example an invalid `discord.bot-token`), the error is logged and the plugin keeps
running without the bot.

## Messages

Message keys must start with `discord-id.` (see `packages/i18n`). Texts in `lang/en_us.yml`.

## Building and running

```bash
./gradlew :plugins:discordId:velocity:shadowJar   # build/libs/klrnbk-discordId-velocity-<version>.jar
./gradlew :plugins:discordId:paper:shadowJar      # build/libs/klrnbk-discordId-paper-<version>.jar
```

Put the jar in `plugins/` next to KLRNBK Identity and KLRNBK Shared Runtime. To try it locally:

```bash
./gradlew :plugins:discordId:velocity:runVelocity
./gradlew :plugins:discordId:paper:runServer
```

The development servers start with only this plugin, so copy the Identity and Runtime jars into the run folder's
`plugins/` first. Set `discord.bot-token` before the first real start, with the placeholder token the bot can't log in.

## Development

```bash
./gradlew :plugins:discordId:common:test :plugins:discordId:velocity:test :plugins:discordId:paper:test
```

The tests use the real services on a temporary SQLite database (`common`'s test fixtures), mocked JDA events and
MockBukkit for Paper. The Discord bot itself is never started in tests.
