package nl.klrnbk.minecraft.packages.velocity.commands.models

import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta

interface Command {
    fun configure(): BrigadierCommand

    fun meta(
        manager: CommandManager,
        plugin: Any,
    ): CommandMeta
}
