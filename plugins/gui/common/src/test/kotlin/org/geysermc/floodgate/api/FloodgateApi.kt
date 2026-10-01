package org.geysermc.floodgate.api

import java.util.UUID

/** Test stand-in for Floodgate's real API class (the real artifact is snapshot-only and deliberately not a dependency). */
class FloodgateApi {
    fun isFloodgatePlayer(uuid: UUID): Boolean = uuid in bedrockUuids

    companion object {
        @JvmField
        var bedrockUuids: Set<UUID> = emptySet()

        @JvmStatic
        fun getInstance(): FloodgateApi = FloodgateApi()
    }
}
