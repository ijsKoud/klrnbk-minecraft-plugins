package nl.klrnbk.minecraft.connect.discord.services.database.repository

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.database.DatabaseService
import nl.klrnbk.minecraft.connect.discord.services.database.tables.DiscordLink
import nl.klrnbk.minecraft.connect.discord.services.database.tables.DiscordLinkTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
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
                            discordUsername = it[DiscordLinkTable.discordUsername],
                            isServerBooster = it[DiscordLinkTable.booster],
                        )
                    }
            }

        fun findByDiscordId(id: Long): DiscordLink? =
            database.query {
                DiscordLinkTable
                    .selectAll()
                    .where { DiscordLinkTable.discordId eq id }
                    .singleOrNull()
                    ?.let {
                        DiscordLink(
                            uuid = it[DiscordLinkTable.id].value,
                            discordId = it[DiscordLinkTable.discordId],
                            discordUsername = it[DiscordLinkTable.discordUsername],
                            isServerBooster = it[DiscordLinkTable.booster],
                        )
                    }
            }

        fun create(link: DiscordLink) =
            database.query {
                DiscordLinkTable
                    .insert {
                        it[id] = link.uuid
                        it[discordId] = link.discordId
                        it[discordUsername] = link.discordUsername
                        it[booster] = link.isServerBooster
                    }
            }

        fun deleteById(uuid: UUID) =
            database.query {
                DiscordLinkTable
                    .deleteWhere { DiscordLinkTable.id eq uuid }
            }
    }
