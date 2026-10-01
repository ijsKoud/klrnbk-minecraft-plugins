package org.geysermc.geyser.api

import java.util.UUID

/** Test stand-in for Geyser's real API class. */
class GeyserApi {
    fun isBedrockPlayer(uuid: UUID): Boolean = uuid in bedrockUuids

    companion object {
        @JvmField
        var bedrockUuids: Set<UUID> = emptySet()

        @JvmStatic
        fun api(): GeyserApi = GeyserApi()
    }
}
