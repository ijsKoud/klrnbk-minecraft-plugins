package nl.klrnbk.minecraft.plugins.identity.common.providers.player

import kotlin.uuid.Uuid

interface PlayerOnlineStatusProvider {
    fun isPlayerOnline(playerId: Uuid): Boolean
}
