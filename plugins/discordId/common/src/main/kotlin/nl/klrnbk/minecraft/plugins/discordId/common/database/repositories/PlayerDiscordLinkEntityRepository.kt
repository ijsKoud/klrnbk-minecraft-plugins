package nl.klrnbk.minecraft.plugins.discordId.common.database.repositories

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.discordId.common.database.models.PlayerDiscordLinkEntity
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Singleton
class PlayerDiscordLinkEntityRepository
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        fun findByEntityId(entityId: Uuid): PlayerDiscordLinkEntity? =
            execute {
                PlayerDiscordLinkEntity
                    .findById(entityId)
            }

        fun upsert(
            entityId: Uuid,
            discordId: String?,
            isBooster: Boolean,
        ): PlayerDiscordLinkEntity =
            execute {
                val playerEntity = findByEntityId(entityId)
                if (playerEntity != null) {
                    playerEntity.discordId = discordId
                    playerEntity.isBooster = isBooster
                    playerEntity.lastUpdatedAt = Clock.System.now()
                    playerEntity
                } else {
                    PlayerDiscordLinkEntity.new(entityId) {
                        this.discordId = discordId
                        this.isBooster = isBooster
                        this.lastUpdatedAt = Clock.System.now()
                    }
                }
            }
    }
