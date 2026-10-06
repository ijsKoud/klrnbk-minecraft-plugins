package nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkTable
import org.jetbrains.exposed.v1.core.eq
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Singleton
class PlayerDiscordLinkEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun findAll(): List<PlayerDiscordLinkEntity> =
            execute {
                PlayerDiscordLinkEntity.all().toList()
            }

        fun findByIdentityId(identityId: Uuid): PlayerDiscordLinkEntity? =
            execute {
                PlayerDiscordLinkEntity
                    .findById(identityId)
            }

        fun findByDiscordId(discordId: String): PlayerDiscordLinkEntity? =
            execute {
                PlayerDiscordLinkEntity
                    .find { PlayerDiscordLinkTable.discordId eq discordId }
                    .firstOrNull()
            }

        fun create(
            identityId: Uuid,
            discordId: String,
            discordName: String,
            isBooster: Boolean,
        ): PlayerDiscordLinkEntity =
            execute {
                val playerEntity = findByIdentityId(identityId)
                if (playerEntity != null) throw IllegalArgumentException("Player already exists")

                PlayerDiscordLinkEntity.new(identityId) {
                    this.discordId = discordId
                    this.discordName = discordName
                    this.isBooster = isBooster
                    this.lastUpdatedAt = Clock.System.now()
                }
            }

        fun update(
            identityId: Uuid,
            discordId: String?,
            discordName: String?,
            isBooster: Boolean,
            updateLastUpdatedAt: Boolean,
        ): PlayerDiscordLinkEntity =
            execute {
                val playerEntity = findByIdentityId(identityId) ?: throw IllegalArgumentException("Player does not exist")
                if (updateLastUpdatedAt) playerEntity.lastUpdatedAt = Clock.System.now()

                playerEntity.discordId = discordId
                playerEntity.discordName = discordName
                playerEntity.isBooster = isBooster
                playerEntity
            }
    }
