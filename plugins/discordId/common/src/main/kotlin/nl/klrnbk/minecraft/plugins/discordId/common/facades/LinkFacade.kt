package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
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

        fun lookupPlayer(playerName: String): TextComponent {
            val identityPlayer =
                identityApi.getPlayerFromName(playerName)
                    ?: return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_FAILED).build()

            val identityId = identityPlayer.id.toKotlinUuid()

            val linkDetails =
                playerDiscordLinkService.getLinkDetailsByIdentityId(identityId) ?: return MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_LOOKUP_FAILED, Component.text(identityPlayer.name))
                    .build()

            return MessageFactory
                .factory()
                .appendAndParseWithTranslatable(
                    LanguageKeys.LINK_CODE_LOOKUP_SUCCESS,
                    Component.text(identityPlayer.name),
                    Component.text(linkDetails.discordName ?: "N/A"),
                    Component.text(linkDetails.discordId ?: "N/A"),
                ).build()
        }

        fun forceUnlinkPlayer(playerName: String): TextComponent {
            val identityPlayer =
                identityApi.getPlayerFromName(playerName)
                    ?: return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_FAILED).build()

            return unlinkPlayer(identityPlayer.playerId.toKotlinUuid(), isForced = true, isBypassed = false)
        }

        fun unlinkPlayer(
            playerId: Uuid,
            isForced: Boolean,
            isBypassed: Boolean,
        ): TextComponent {
            val identityPlayer =
                identityApi.getPlayerFromUuid(playerId.toJavaUuid())
                    ?: throw IllegalArgumentException("Player does not exist on identity but should")
            val identityId = identityPlayer.id.toKotlinUuid()

            val canUnlink = playerDiscordLinkService.canUnlinkDiscordFromPlayer(identityId)
            if (!canUnlink && !isForced && !isBypassed) {
                return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_FAILED).build()
            }

            playerDiscordLinkService.unlinkDiscordFromPlayer(identityId)
            return MessageFactory.factory().appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_UNLINK_SUCCESS).build()
        }

        fun linkPlayer(
            linkCode: String,
            discordId: String,
            discordName: String,
            isBooster: Boolean,
        ): String {
            val codeDetails =
                playerLinkCodeService.getCodeDetailsForPlayerByCode(linkCode) ?: return LanguageKeys.LINK_CODE_INVALID

            val identityPlayer =
                identityApi.getPlayerFromId(codeDetails.playerEntityId.toJavaUuid())
                    ?: throw IllegalArgumentException("Player does not exist on identity but should")
            val identityId = identityPlayer.id.toKotlinUuid()

            val canLink = playerDiscordLinkService.canLinkDiscordToPlayer(identityId, discordId)
            if (!canLink) return LanguageKeys.LINK_CODE_ALREADY_LINKED

            playerDiscordLinkService.linkDiscordWithPlayer(identityId, discordId, discordName, isBooster)
            playerLinkCodeService.deleteCodeDetailsForPlayer(codeDetails.playerEntityId)
            return LanguageKeys.LINK_CODE_SUCCESS
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
                    Component
                        .text(linkCodeDetails.code)
                        .clickEvent(ClickEvent.copyToClipboard(linkCodeDetails.code))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))),
                    instantToComponentText(MessageFactory.factory().miniMessage, linkCodeDetails.validUntil),
                ).build()
        }

        fun getMinecraftUsernameOfDiscordUser(discordId: String): String? {
            val identityId = playerDiscordLinkService.getLinkDetailsByDiscordId(discordId)?.identityId ?: return null
            val identityPlayer = identityApi.getPlayerFromId(identityId.toJavaUuid()) ?: return null

            return identityPlayer.name
        }

        fun updateDiscordNameForLinkedPlayer(
            discordId: String,
            discordName: String,
        ) {
            val identityId = playerDiscordLinkService.getLinkDetailsByDiscordId(discordId)?.identityId ?: return
            playerDiscordLinkService.updateDiscordUsernameForLinkedPlayer(identityId, discordName)
        }

        fun updateDiscordBoosterStatusForLinkedPlayer(
            discordId: String,
            isBooster: Boolean,
        ) {
            val identityId = playerDiscordLinkService.getLinkDetailsByDiscordId(discordId)?.identityId ?: return
            playerDiscordLinkService.updateBoosterStatusForLinkedPlayer(identityId, isBooster)
        }
    }
