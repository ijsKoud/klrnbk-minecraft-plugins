package nl.klrnbk.minecraft.connect.discord.services.config.models

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.objectmapping.meta.Setting

@ConfigSerializable
class RegistrationConfigModel {
    @Setting("reset-limit")
    @Comment("The time in seconds after which a user can reset their registration. For example 1 month -> 2528000 seconds.")
    val resetLimit: Int = 2628000 // 1 month

    @Setting("booster-role")
    @Comment("The Discord role Id for users who are boosters.")
    val boosterRole: Long = 0

    @Setting("discord-bot-token")
    @Comment("The token for the Discord bot.")
    val discordBotToken: String = ""
}
