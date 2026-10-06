package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.discordId.common.LINK_CODE_VALIDITY_DURATION
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkCodeEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkCodeTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerDiscordLinkCodeEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun findByEntityId(entityId: Uuid): PlayerDiscordLinkCodeEntity? =
            execute {
                PlayerDiscordLinkCodeEntity
                    .findById(entityId)
            }

        fun findByCode(code: String): PlayerDiscordLinkCodeEntity? =
            execute {
                PlayerDiscordLinkCodeEntity
                    .find { PlayerDiscordLinkCodeTable.code eq code }
                    .firstOrNull()
            }

        fun create(entityId: Uuid): PlayerDiscordLinkCodeEntity =
            execute {
                val playerEntity = findByEntityId(entityId)
                if (playerEntity != null) {
                    throw IllegalStateException("PlayerDiscordLinkCodeEntity with entityId $entityId already exists.")
                }

                PlayerDiscordLinkCodeEntity.new(entityId) {
                    this.validUntil = Clock.System.now().plus(LINK_CODE_VALIDITY_DURATION)
                    this.code = Uuid.random().toString()
                }
            }

        fun delete(entityId: Uuid): Boolean =
            execute {
                val playerEntity = findByEntityId(entityId)
                if (playerEntity != null) {
                    playerEntity.delete()
                    true
                } else {
                    false
                }
            }

        fun deleteExpired(): Int =
            execute {
                val now = Clock.System.now()
                PlayerDiscordLinkCodeEntity
                    .find { PlayerDiscordLinkCodeTable.validUntil lessEq now }
                    .toList()
                    .onEach { it.delete() }
                    .size
            }
    }
