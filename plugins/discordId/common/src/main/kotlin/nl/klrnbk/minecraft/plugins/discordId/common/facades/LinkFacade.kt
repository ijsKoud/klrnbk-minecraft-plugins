package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkCodeService
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkService
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.pkgs.i18n.utils.instantToComponentText
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Singleton
class LinkFacade
    @Inject
    constructor(
        private val playerDiscordLinkService: PlayerLinkService,
        private val playerLinkCodeService: PlayerLinkCodeService,
    ) {
        private val identityApi = IdentityProvider.get()

        fun unlinkPlayer(playerId: Uuid): TextComponent {
            val identityPlayer =
                identityApi.getPlayerFromUuid(playerId.toJavaUuid())
                    ?: throw IllegalArgumentException("Player does not exist on identity but should")
            val identityId = identityPlayer.id.toKotlinUuid()

            val canUnlink = playerDiscordLinkService.canUnlinkDiscordFromPlayer(identityId)
            if (!canUnlink) return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_FAILED).build()

            playerDiscordLinkService.unlinkDiscordFromPlayer(identityId)
            return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_SUCCESS).build()
        }

        fun linkPlayer(
            linkCode: String,
            discordId: String,
            isBooster: Boolean,
        ): TextComponent {
            val codeDetails =
                playerLinkCodeService.getCodeDetailsForPlayerByCode(linkCode) ?: return MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_INVALID)
                    .build()

            val identityPlayer =
                identityApi.getPlayerFromId(codeDetails.playerEntityId.toJavaUuid())
                    ?: throw IllegalArgumentException("Player does not exist on identity but should")
            val identityId = identityPlayer.id.toKotlinUuid()

            val canLink = playerDiscordLinkService.canLinkDiscordToPlayer(identityId, discordId)
            if (!canLink) return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_ALREADY_LINKED).build()

            playerDiscordLinkService.linkDiscordWithPlayer(identityId, discordId, isBooster)
            playerLinkCodeService.deleteCodeDetailsForPlayer(codeDetails.playerEntityId)
            return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_SUCCESS).build()
        }

        fun getLinkCodeForPlayer(playerId: Uuid): TextComponent {
            val identityPlayer =
                identityApi.getPlayerFromUuid(playerId.toJavaUuid())
                    ?: throw IllegalArgumentException("Player does not exist on identity but should")
            val identityId = identityPlayer.id.toKotlinUuid()

            val canRequestLinkCode = playerDiscordLinkService.canRequestLinkCode(identityId)
            if (!canRequestLinkCode) return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_FAILED).build()

            val linkCodeDetails = playerLinkCodeService.getOrCreateCodeDetailsForPlayer(identityId)
            return MessageFactory
                .factory()
                .appendAndParseWithTranslatable(
                    LanguageKeys.LINK_CODE_DETAILS,
                    Component.text(linkCodeDetails.code),
                    instantToComponentText(MessageFactory.factory().miniMessage, linkCodeDetails.validUntil),
                ).build()
        }
    }
