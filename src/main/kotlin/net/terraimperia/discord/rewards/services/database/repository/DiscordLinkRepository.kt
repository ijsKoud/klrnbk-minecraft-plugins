package net.terraimperia.discord.rewards.services.database.repository

import com.google.inject.Inject
import com.google.inject.Singleton
import net.terraimperia.discord.rewards.services.database.DatabaseService
import net.terraimperia.discord.rewards.services.database.tables.DiscordLink
import net.terraimperia.discord.rewards.services.database.tables.DiscordLinkTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Singleton
class DiscordLinkRepository
    @Inject
    constructor(
        private val database: DatabaseService,
    ) {
        fun findById(uuid: UUID): DiscordLink? =
            database.query {
                DiscordLinkTable
                    .selectAll()
                    .where { DiscordLinkTable.id eq uuid }
                    .singleOrNull()
                    ?.let {
                        DiscordLink(
                            uuid = it[DiscordLinkTable.id].value,
                            discordId = it[DiscordLinkTable.discordId],
                        )
                    }
            }

        fun create(link: DiscordLink) =
            database.query {
                DiscordLinkTable
                    .insert {
                        it[id] = link.uuid
                        it[discordId] = link.discordId
                    }
            }

        fun deleteById(uuid: UUID) =
            database.query {
                DiscordLinkTable
                    .deleteWhere { DiscordLinkTable.id eq uuid }
            }
    }
