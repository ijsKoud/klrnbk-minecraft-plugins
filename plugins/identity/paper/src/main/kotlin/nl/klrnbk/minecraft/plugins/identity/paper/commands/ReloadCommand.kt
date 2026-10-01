package nl.klrnbk.minecraft.plugins.identity.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys.RELOAD_SUCCESS
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

@Singleton
class ReloadCommand
    @Inject
    constructor(
        private val adminCommandsFacade: AdminCommandsFacade,
        private val plugin: JavaPlugin,
    ) : PaperCommand("identityreload", listOf("identity-reload"), Permissions.RELOAD_PLUGIN) {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            adminCommandsFacade.reload(plugin.dataFolder.toPath())

            val message =
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(RELOAD_SUCCESS)
                    .build()
            sender.sendMessage(message)

            return true
        }
    }
