package nl.klrnbk.minecraft.connect.discord.commands

import com.google.inject.Inject
import com.google.inject.Singleton
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import nl.klrnbk.minecraft.connect.discord.facade.AdminFacade
import java.util.concurrent.CompletableFuture

@Singleton
class AdminReloadCommand
    @Inject
    constructor(
        private val adminFacade: AdminFacade,
    ) : SimpleCommand {
        override fun execute(invocation: SimpleCommand.Invocation) {
            val source = invocation.source()
            source.sendMessage(Component.text("[KLRNBK Connect / Discord]: Config reloading...", NamedTextColor.AQUA))

            val configVersion = adminFacade.reload()
            source.sendMessage(
                Component.text("[KLRNBK Connect / Discord]: Config reloaded (config version $configVersion)", NamedTextColor.AQUA),
            )
        }

        // This method allows you to control who can execute the command.
        // If the executor does not have the required permission,
        // the execution of the command and the control of its autocompletion
        // will be sent directly to the server on which the sender is located
        override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
            invocation.source().hasPermission("klrnbk.connect.discord.admin.*") ||
                invocation.source().hasPermission("klrnbk.connect.discord.admin.reload")

        // With this method you can control the suggestions to send
        // to the CommandSource according to the arguments
        // it has already written or other requirements you need
        override fun suggest(invocation: SimpleCommand.Invocation?): MutableList<String?> = mutableListOf<String?>()

        // Here you can offer argument suggestions in the same way as the previous method,
        // but asynchronously. It is recommended to use this method instead of the previous one
        // especially in cases where you make a more extensive logic to provide the suggestions
        override fun suggestAsync(invocation: SimpleCommand.Invocation?): CompletableFuture<MutableList<String?>?> =
            CompletableFuture.completedFuture<MutableList<String?>?>(mutableListOf<String?>())
    }
