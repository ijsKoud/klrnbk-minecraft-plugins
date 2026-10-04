package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.whitelist.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerWhitelistFacade
    @Inject
    constructor(
        private val playerWhitelistService: PlayerWhitelistService,
        private val activeStatusService: ActiveStatusService,
        private val configService: ConfigService,
    ) {
        fun isPlayerWhitelisted(playerIdentityId: Uuid): Boolean = playerWhitelistService.isPlayerWhitelisted(playerIdentityId)

        fun isServerWhitelistActive(): Boolean = activeStatusService.isWhitelistEnabled()

        /**
         * Decides whether a connecting player may join.
         *
         * @param minecraftId The UUID the client connects with. Clients older than 1.20.2 don't send one, those are looked up by [playerName].
         */
        fun checkJoin(
            minecraftId: Uuid?,
            playerName: String,
        ): JoinCheckResult {
            // Checked first so a disabled whitelist never depends on Identity or the database.
            if (isServerWhitelistActive().not()) return JoinCheckResult.WHITELIST_DISABLED

            val identity = IdentityProvider.get()
            val player =
                if (minecraftId != null) identity.getPlayerFromUuid(minecraftId.toJavaUuid()) else identity.getPlayerFromName(playerName)

            // Whitelisting goes through Identity, so a player Identity doesn't know can't be whitelisted.
            if (player == null) return JoinCheckResult.UNKNOWN_PLAYER

            return if (isPlayerWhitelisted(player.id.toKotlinUuid())) JoinCheckResult.WHITELISTED else JoinCheckResult.NOT_WHITELISTED
        }

        fun isPlayerAllowedToJoinServer(
            minecraftId: Uuid?,
            playerName: String,
        ): Boolean = checkJoin(minecraftId, playerName).isAllowed

        fun getDenialMessage(): Component {
            val message = configService.getConfig().kickMessage
            return MessageFactory.factory().miniMessage.deserialize(message)
        }

        enum class JoinCheckResult(
            val isAllowed: Boolean,
        ) {
            WHITELIST_DISABLED(true),
            WHITELISTED(true),
            NOT_WHITELISTED(false),
            UNKNOWN_PLAYER(false),
        }
    }
