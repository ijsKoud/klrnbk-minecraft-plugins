package net.terraimperia.discord.rewards.services.link.models

data class LinkStatusModel(
    val isLinked: Boolean,
    val lastLinkDate: String? = null,
    val discordId: String? = null,
)
