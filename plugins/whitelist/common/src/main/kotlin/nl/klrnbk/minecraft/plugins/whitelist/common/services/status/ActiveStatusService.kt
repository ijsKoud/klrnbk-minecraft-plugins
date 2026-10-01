package nl.klrnbk.minecraft.plugins.whitelist.common.services.status

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.plugins.whitelist.common.WHITELIST_ACTIVE_STATUS_FILE_NAME
import nl.klrnbk.minecraft.plugins.whitelist.common.providers.toggle.StatusFileToggleProvider
import nl.klrnbk.minecraft.plugins.whitelist.common.services.logs.WhitelistLogsService
import java.nio.file.Path
import kotlin.uuid.Uuid

@Singleton
class ActiveStatusService
    @Inject
    constructor(
        private val whitelistLogsService: WhitelistLogsService,
    ) {
        private val toggleProvider = StatusFileToggleProvider()
        private lateinit var dataDirectory: Path

        /**
         * Loads the stored status, also used to reload it from disk.
         */
        fun start(dataDirectory: Path) {
            this.dataDirectory = dataDirectory
            toggleProvider.load(dataDirectory, WHITELIST_ACTIVE_STATUS_FILE_NAME)
        }

        fun isWhitelistEnabled(): Boolean = toggleProvider.parse()

        /**
         * Enables or disables the whitelist and logs who did it.
         *
         * @return true if the status changed, false if the whitelist already was in the requested state.
         */
        @Synchronized
        fun setWhitelistEnabled(
            enabled: Boolean,
            actorIdentityId: Uuid,
        ): Boolean {
            if (isWhitelistEnabled() == enabled) return false

            // Log first: when the database is unavailable the toggle fails instead of happening unlogged.
            whitelistLogsService.logWhitelistToggle(actorIdentityId, enabled)
            toggleProvider.write(dataDirectory, WHITELIST_ACTIVE_STATUS_FILE_NAME, enabled)
            return true
        }
    }
