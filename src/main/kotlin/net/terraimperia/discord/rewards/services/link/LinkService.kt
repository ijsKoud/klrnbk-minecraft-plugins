package net.terraimperia.discord.rewards.services.link

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import net.terraimperia.discord.rewards.services.database.repository.DiscordLinkRepository
import net.terraimperia.discord.rewards.services.database.repository.DiscordLinkRequestCodeRepository
import net.terraimperia.discord.rewards.services.database.repository.PlayerRegistryDetailsRepository
import net.terraimperia.discord.rewards.services.database.tables.DiscordLinkRequestCode
import net.terraimperia.discord.rewards.services.link.models.LinkStatusModel
import net.terraimperia.discord.rewards.utils.getCryptographicRandomText
import java.util.UUID

@Singleton
class LinkService
    @Inject
    constructor(
        private val playerRegistryDetailsRepository: PlayerRegistryDetailsRepository,
        private val discordLinkRepository: DiscordLinkRepository,
        private val discordLinkRequestCodeRepository: DiscordLinkRequestCodeRepository,
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

        fun getExistingRegistrationCode(id: UUID): String? {
            val details = discordLinkRequestCodeRepository.findById(id) ?: return null
            return details.code
        }

        fun createRegistrationCode(id: UUID): String {
            val existingCode = getExistingRegistrationCode(id)
            if (existingCode != null) return existingCode

            val code = getCryptographicRandomText(16)
            val details = DiscordLinkRequestCode(id, code)

            discordLinkRequestCodeRepository.create(details)
            return code
        }
    }
