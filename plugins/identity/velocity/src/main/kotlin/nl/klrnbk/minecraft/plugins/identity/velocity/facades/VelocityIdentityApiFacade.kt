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
    }
