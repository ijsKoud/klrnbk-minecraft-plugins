package nl.klrnbk.minecraft.plugins.discordId.common.services.player

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.PlayerDiscordLinkEntityRepository
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.models.PlayerDiscordLinkDetails
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.transformers.fromEntity
import kotlin.time.Clock
import kotlin.time.toDuration
import kotlin.uuid.Uuid

@Singleton
class PlayerLinkService
    @Inject
    constructor(
        private val playerDiscordLinkEntityRepository: PlayerDiscordLinkEntityRepository,
        private val configProvider: ConfigProvider,
    ) {
        fun getLinkDetailsByIdentityId(identityId: Uuid): PlayerDiscordLinkDetails? {
            val entity = playerDiscordLinkEntityRepository.findByIdentityId(identityId) ?: return null
            return PlayerDiscordLinkDetails.fromEntity(entity)
        }

        fun getLinkDetailsByDiscordId(discordId: String): PlayerDiscordLinkDetails? {
            val entity = playerDiscordLinkEntityRepository.findByDiscordId(discordId) ?: return null
            return PlayerDiscordLinkDetails.fromEntity(entity)
        }

        fun linkDiscordWithPlayer(
            identityId: Uuid,
            discordId: String,
            discordName: String,
            isBooster: Boolean,
        ): PlayerDiscordLinkDetails {
            val entity = playerDiscordLinkEntityRepository.findByIdentityId(identityId)
            if (entity == null) {
                val newEntity = playerDiscordLinkEntityRepository.create(identityId, discordId, discordName, isBooster)
                return PlayerDiscordLinkDetails.fromEntity(newEntity)
            }

            if (!entity.discordId.isNullOrEmpty()) throw IllegalArgumentException("Player is already linked to a Discord account")
            val updatedEntity = playerDiscordLinkEntityRepository.update(identityId, discordId, discordName, isBooster)
            return PlayerDiscordLinkDetails.fromEntity(updatedEntity)
        }

        fun unlinkDiscordFromPlayer(identityId: Uuid): PlayerDiscordLinkDetails {
            playerDiscordLinkEntityRepository.findByIdentityId(identityId)
                ?: throw IllegalArgumentException("Player is not linked to a Discord account")

            val updatedEntity = playerDiscordLinkEntityRepository.update(identityId, null, null, false)
            return PlayerDiscordLinkDetails.fromEntity(updatedEntity)
        }

        fun updateDiscordUsernameForLinkedPlayer(
            identityId: Uuid,
            discordName: String,
        ): PlayerDiscordLinkDetails {
            val entity =
                playerDiscordLinkEntityRepository.findByIdentityId(identityId)
                    ?: throw IllegalArgumentException("Player is not linked to a Discord account")

            val updatedEntity = playerDiscordLinkEntityRepository.update(identityId, entity.discordId, discordName, entity.isBooster)
            return PlayerDiscordLinkDetails.fromEntity(updatedEntity)
        }

        fun updateBoosterStatusForLinkedPlayer(
            identityId: Uuid,
            isBooster: Boolean,
        ): PlayerDiscordLinkDetails {
            val entity =
                playerDiscordLinkEntityRepository.findByIdentityId(identityId)
                    ?: throw IllegalArgumentException("Player is not linked to a Discord account")

            val updatedEntity = playerDiscordLinkEntityRepository.update(identityId, entity.discordId, entity.discordName, isBooster)
            return PlayerDiscordLinkDetails.fromEntity(updatedEntity)
        }

        fun getAllLinkedPlayers(): List<PlayerDiscordLinkDetails> {
            val entities = playerDiscordLinkEntityRepository.findAll()
            return entities.map { PlayerDiscordLinkDetails.fromEntity(it) }
        }

        fun canLinkDiscordToPlayer(
            identityId: Uuid,
            discordId: String,
        ): Boolean {
            if (playerDiscordLinkEntityRepository.findByDiscordId(discordId) != null) return false
            val entity = playerDiscordLinkEntityRepository.findByIdentityId(identityId) ?: return true
            return entity.discordId.isNullOrEmpty()
        }

        fun canUnlinkDiscordFromPlayer(identityId: Uuid): Boolean {
            val entity =
                playerDiscordLinkEntityRepository.findByIdentityId(identityId)
                    ?: throw IllegalArgumentException("Player is not linked to a Discord account")

            return entity.lastUpdatedAt +
                configProvider.config.discord.unlinkCooldown
                    .toDuration(kotlin.time.DurationUnit.MILLISECONDS) <
                Clock.System.now()
        }

        fun canRequestLinkCode(identityId: Uuid): Boolean {
            val entity = playerDiscordLinkEntityRepository.findByIdentityId(identityId) ?: return true
            return entity.discordId.isNullOrEmpty() && entity.lastUpdatedAt +
                configProvider.config.discord.unlinkCooldown
                    .toDuration(kotlin.time.DurationUnit.MILLISECONDS) <
                Clock.System.now()
        }
    }
