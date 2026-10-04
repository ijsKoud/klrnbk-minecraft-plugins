package nl.klrnbk.minecraft.plugins.identity.velocity.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import kotlin.uuid.toKotlinUuid
import java.util.UUID

@Singleton
class VelocityIdentityApiFacade
    @Inject
    constructor(
        private val playerDetailsService: PlayerDetailsService,
    ) : IdentityApi {
        override fun getPlayerFromUuid(uuid: UUID): IdentityPlayer? = playerDetailsService.getPlayerDetailsByPlayerId(uuid.toKotlinUuid())

        override fun getPlayerFromId(id: UUID): IdentityPlayer? = playerDetailsService.getPlayerDetailsById(id.toKotlinUuid())

        override fun getPlayerFromName(name: String): IdentityPlayer? = playerDetailsService.getPlayerDetailsByName(name)

        override fun getPlayersFromIds(ids: Collection<UUID>): List<IdentityPlayer> =
            playerDetailsService.getPlayerDetailsByIds(ids.map { it.toKotlinUuid() })

        override fun getPlayerNames(
            prefix: String,
            limit: Int,
        ): List<String> {
            require(limit > 0) { "limit must be positive" }
            return playerDetailsService.getPlayerNamesByPrefix(prefix, limit)
        }

        override fun getAllPlayers(
            page: Int,
            itemsPerPage: Int,
        ): List<IdentityPlayer> {
            require(page >= 0) { "page must not be negative" }
            require(itemsPerPage > 0) { "itemsPerPage must be positive" }
            // getAllPlayerDetails takes 1-based pages, like the player list command.
            return playerDetailsService.getAllPlayerDetails(page + 1, itemsPerPage)
        }

        override fun getPlayerCount(): Long = playerDetailsService.getTotalPlayerCount()
    }
