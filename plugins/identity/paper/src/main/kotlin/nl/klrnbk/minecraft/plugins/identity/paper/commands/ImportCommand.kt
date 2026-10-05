package nl.klrnbk.minecraft.plugins.identity.paper.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.packages.paper.commands.PaperCommand
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.Permissions
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

@Singleton
class ImportCommand
    @Inject
    constructor(
        private val dataTransferService: DataTransferService,
        private val plugin: JavaPlugin,
    ) : PaperCommand("identityimport", listOf("identity-import"), Permissions.IMPORT_DATA) {
        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): Boolean {
            val fileName = args.firstOrNull() ?: return false

            // The import writes whole tables, so it runs off the main thread.
            plugin.server.scheduler.runTaskAsynchronously(
                plugin,
                Runnable {
                    val message =
                        try {
                            val result = dataTransferService.importData(plugin.dataFolder.toPath(), fileName)
                            MessageFactory
                                .factory()
                                .appendAndParseWithTranslatable(LanguageKeys.IMPORT_SUCCESS, Component.text(result.totalRows))
                                .build()
                        } catch (exception: Exception) {
                            failureMessage(exception)
                        }

                    sender.sendMessage(message)
                },
            )
            return true
        }

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>,
        ): List<String> =
            if (args.size == 1) {
                dataTransferService
                    .getExportSuggestions(plugin.dataFolder.toPath())
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            } else {
                emptyList()
            }

        private fun failureMessage(exception: Exception): Component {
            // Anything but a rejected input is unexpected: keep the details in the log, not in chat.
            val reason =
                if (exception is DatabaseTransferException) {
                    exception.message.orEmpty()
                } else {
                    plugin.logger.log(Level.SEVERE, "Importing data failed", exception)
                    "Unexpected error, see the console."
                }

            return MessageFactory
                .factory()
                .appendAndParseWithTranslatable(LanguageKeys.TRANSFER_FAILED, Component.text(reason))
                .build()
        }
    }
