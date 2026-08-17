package nl.klrnbk.minecraft.connect.discord.services.link.models

data class LinkStatusModel(
    val isLinked: Boolean,
    val lastLinkDate: String? = null,
    val discordId: String? = null,
)
