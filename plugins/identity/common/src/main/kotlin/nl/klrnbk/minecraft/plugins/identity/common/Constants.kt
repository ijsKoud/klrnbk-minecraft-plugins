package nl.klrnbk.minecraft.plugins.identity.common

import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

val LOGS_CLEANUP_INTERVAL = 1.days.toJavaDuration()

const val CHAT_PREFIX = "<gray>[</gray><color:#F97316>K</color><gray>]</gray>"
const val LOGS_PREFIX = "[KLRNBK Identity]"

object Permissions {
    const val RELOAD_PLUGIN = "klrnbk.identity.reload"
    const val EXPORT_DATA = "klrnbk.identity.export"
    const val IMPORT_DATA = "klrnbk.identity.import"
    const val VIEW_PLAYERS = "klrnbk.identity.view.players"
    const val VIEW_PLAYER_INFO = "klrnbk.identity.view.player-info"
    const val VIEW_LOGS = "klrnbk.identity.view.logs"
    const val VIEW_IPS = "klrnbk.identity.view.ips"
}

object LanguageKeys {
    const val RELOAD_SUCCESS = "identity.admin.reload.success"
    const val EXPORT_SUCCESS = "identity.admin.export.success"
    const val IMPORT_SUCCESS = "identity.admin.import.success"
    const val TRANSFER_FAILED = "identity.admin.transfer.failed"
    const val PLAYER_LIST_HEADER = "identity.player.list.header"
    const val PLAYER_LIST_PAGE = "identity.player.list.page"
    const val PLAYER_LIST_PLAYER_HOVER_TEXT = "identity.player.list.player_hover_text"
    const val PLAYER_LIST_PLAYER_ENTRY = "identity.player.list.player_entry"
    const val PLAYER_LIST_FOOTER_PREVIOUS = "identity.player.list.footer_previous"
    const val PLAYER_LIST_FOOTER_NEXT = "identity.player.list.footer_next"
    const val PLAYER_ONLINE = "identity.player.common.online"
    const val PLAYER_OFFLINE = "identity.player.common.offline"
    const val PLAYER_AVAILABLE = "identity.player.common.available"
    const val PLAYER_UNAVAILABLE = "identity.player.common.unavailable"
    const val PLAYER_INFO_HEADER = "identity.player.info.header"
    const val PLAYER_NAME = "identity.player.common.name"
    const val PLAYER_INFO_PLAYER_UUID = "identity.player.info.player_uuid"
    const val PLAYER_INFO_INTERNAL_UUID = "identity.player.info.internal_uuid"
    const val PLAYER_INFO_FIRST_JOIN = "identity.player.info.first_join"
    const val PLAYER_INFO_STATUS = "identity.player.info.status"
    const val PLAYER_INFO_LOGS = "identity.player.info.logs"
    const val PLAYER_INFO_NOT_FOUND = "identity.player.info.not_found"
    const val PLAYER_LOGS_HEADER = "identity.player.logs.header"
    const val PLAYER_LOGS_IP_ADDRESS = "identity.player.logs.ip"
    const val PLAYER_LOGS_SERVER = "identity.player.logs.server"
    const val PLAYER_LOGS_ACTION = "identity.player.logs.action"
}
