package net.terraimperia.discord.rewards.services.link

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import net.terraimperia.discord.rewards.services.database.repository.DiscordLinkRepository
import net.terraimperia.discord.rewards.services.database.repository.PlayerRegistryDetailsRepository
import net.terraimperia.discord.rewards.services.link.models.LinkStatusModel
import java.util.UUID

@Singleton
class LinkService
    @Inject
    constructor(
        private val playerRegistryDetailsRepository: PlayerRegistryDetailsRepository,
        private val discordLinkRepository: DiscordLinkRepository,
    ) {
        fun getStatus(id: UUID): LinkStatusModel {
            val details = playerRegistryDetailsRepository.findById(id) ?: return LinkStatusModel(isLinked = false)
            val discordLink = discordLinkRepository.findById(id) ?: return LinkStatusModel(isLinked = false)

            return LinkStatusModel(
                isLinked = details.isRegistered,
                lastLinkDate = details.lastRegistryDate.format(LocalDateTime.Formats.ISO),
                discordId = discordLink.discordId.toString(),
            )
        }
    }
