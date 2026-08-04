package net.terraimperia.discord.rewards.services.config.models

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class PluginConfigModel {
    @Comment("Config version, DO NOT CHANGE THIS!!!")
    val version: Int = 1

    @Comment("Database configuration for the plugin. This is where all linked data will be stored.")
    val database: DatabaseConfigModel = DatabaseConfigModel()
}
