package nl.klrnbk.minecraft.connect.discord.facade

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.connect.discord.Plugin
import nl.klrnbk.minecraft.connect.discord.services.config.ConfigService
import nl.klrnbk.minecraft.connect.discord.services.config.VersionService
import nl.klrnbk.minecraft.connect.discord.services.database.DatabaseService
import nl.klrnbk.minecraft.connect.discord.services.register.AdminCommandsRegisterService
import nl.klrnbk.minecraft.connect.discord.services.register.LinkCommandRegistryService
import nl.klrnbk.minecraft.connect.discord.services.register.UnlinkCommandRegistryService
import org.slf4j.Logger

@Singleton
class PluginFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val versionService: VersionService,
        private val databaseService: DatabaseService,
        private val adminCommandsRegisterService: AdminCommandsRegisterService,
        private val linkCommandRegistryService: LinkCommandRegistryService,
        private val unlinkCommandRegistryService: UnlinkCommandRegistryService,
    ) {
        fun start(plugin: Plugin) {
            val config = configService.load()
            val version = versionService.version

            databaseService.start(config.database)

            adminCommandsRegisterService.register(plugin)
            linkCommandRegistryService.register(plugin)
            unlinkCommandRegistryService.register(plugin)

            logger.info("Plugin version: $version")
            logger.info("Plugin is ready.")
        }
    }
