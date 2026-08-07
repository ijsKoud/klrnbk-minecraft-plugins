package net.terraimperia.discord.rewards.services.config.models

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

    @Setting("check-duration")
    @Comment(
        "The time in seconds between each automated check for changes on Discord (such as booster changes). For example 1 hour -> 3600 seconds.",
    )
    val checkDuration: Int = 3600 // 1 hour
}
