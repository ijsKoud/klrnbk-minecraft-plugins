package nl.klrnbk.minecraft.connect.discord.services.register

import nl.klrnbk.minecraft.connect.discord.Plugin

interface CommandRegisterService {
    fun register(plugin: Plugin)
}
