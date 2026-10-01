package net.william278.papiproxybridge.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Test stand-in for PAPIProxyBridge's real API class. */
class PlaceholderAPI {
    fun formatPlaceholders(
        text: String,
        player: UUID,
    ): CompletableFuture<String> = CompletableFuture.completedFuture(text.replace("%player_name%", "§aAlex").replace("%balance%", "100"))

    companion object {
        @JvmStatic
        fun createInstance(): PlaceholderAPI = PlaceholderAPI()
    }
}
