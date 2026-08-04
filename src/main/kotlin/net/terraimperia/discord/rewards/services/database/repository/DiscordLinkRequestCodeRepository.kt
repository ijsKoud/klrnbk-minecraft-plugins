package net.terraimperia.discord.rewards.services.database.repository

import com.google.inject.Inject
import com.google.inject.Singleton
import net.terraimperia.discord.rewards.services.database.DatabaseService
import net.terraimperia.discord.rewards.services.database.tables.DiscordLinkRequestCode
import net.terraimperia.discord.rewards.services.database.tables.DiscordLinkRequestCodeTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Singleton
class DiscordLinkRequestCodeRepository
    @Inject
    constructor(
        private val database: DatabaseService,
    ) {
        fun findById(uuid: UUID): DiscordLinkRequestCode? =
            database.query {
                DiscordLinkRequestCodeTable
                    .selectAll()
                    .where { DiscordLinkRequestCodeTable.id eq uuid }
                    .singleOrNull()
                    ?.let {
                        DiscordLinkRequestCode(
                            uuid = it[DiscordLinkRequestCodeTable.id].value,
                            code = it[DiscordLinkRequestCodeTable.requestCode],
                        )
                    }
            }

        fun create(link: DiscordLinkRequestCode) =
            database.query {
                DiscordLinkRequestCodeTable
                    .insert {
                        it[id] = link.uuid
                        it[requestCode] = link.code
                    }
            }

        fun deleteById(uuid: UUID) =
            database.query {
                DiscordLinkRequestCodeTable
                    .deleteWhere { DiscordLinkRequestCodeTable.id eq uuid }
            }
    }
