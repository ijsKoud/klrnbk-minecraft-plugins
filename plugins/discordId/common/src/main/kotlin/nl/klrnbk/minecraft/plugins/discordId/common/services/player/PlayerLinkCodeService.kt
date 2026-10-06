package nl.klrnbk.minecraft.plugins.discordId.common.services.player

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.PlayerDiscordLinkCodeEntityRepository
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.models.PlayerLinkCode
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.transformers.fromEntity
import kotlin.uuid.Uuid

@Singleton
class PlayerLinkCodeService
    @Inject
    constructor(
        private val playerDiscordLinkCodeEntityRepository: PlayerDiscordLinkCodeEntityRepository,
    ) {
        fun getOrCreateCodeDetailsForPlayer(playerIdentityId: Uuid): PlayerLinkCode {
            val entity =
                playerDiscordLinkCodeEntityRepository.findByEntityId(playerIdentityId)
                    ?: playerDiscordLinkCodeEntityRepository.create(playerIdentityId)

            return PlayerLinkCode.fromEntity(entity)
        }

        fun getCodeDetailsForPlayerByCode(linkCode: String): PlayerLinkCode? {
            val entity = playerDiscordLinkCodeEntityRepository.findByCode(linkCode) ?: return null
            return PlayerLinkCode.fromEntity(entity)
        }

        fun deleteCodeDetailsForPlayer(playerIdentityId: Uuid): Boolean = playerDiscordLinkCodeEntityRepository.delete(playerIdentityId)
    }
