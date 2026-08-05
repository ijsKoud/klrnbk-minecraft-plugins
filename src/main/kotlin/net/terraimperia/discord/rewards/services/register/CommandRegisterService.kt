package net.terraimperia.discord.rewards.services.register

import net.terraimperia.discord.rewards.Plugin

interface CommandRegisterService {
    fun register(plugin: Plugin)
}
