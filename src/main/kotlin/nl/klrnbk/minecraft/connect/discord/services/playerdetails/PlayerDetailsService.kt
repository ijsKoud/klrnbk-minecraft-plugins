package nl.klrnbk.minecraft.connect.discord.services.playerdetails

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.services.database.repository.PlayerRegistryDetailsRepository
import java.util.UUID

@Singleton
class PlayerDetailsService
    @Inject
    constructor(
        private val playerRegistryDetailsRepository: PlayerRegistryDetailsRepository,
    ) {
        fun updatePlayerUsername(
            id: UUID,
            username: String,
        ) {
            val existingPlayerDetails = playerRegistryDetailsRepository.findById(id) ?: return
            if (existingPlayerDetails.username == username) return

            existingPlayerDetails.username = username
            playerRegistryDetailsRepository.update(existingPlayerDetails)
        }

        fun getPlayerIdByUsername(username: String): UUID? = playerRegistryDetailsRepository.findIdByUsername(username)
    }
