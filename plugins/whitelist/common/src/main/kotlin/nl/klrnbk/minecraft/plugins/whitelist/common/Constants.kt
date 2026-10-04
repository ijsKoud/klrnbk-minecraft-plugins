package nl.klrnbk.minecraft.plugins.whitelist.common

import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

val LOGS_CLEANUP_INTERVAL = 1.days.toJavaDuration()

const val CHAT_PREFIX = "<gray>[</gray><color:#F97316>K</color><gray>]</gray>"

const val WHITELIST_ACTIVE_STATUS_FILE_NAME = "whitelist_enabled.txt"

object Permissions {
    const val RELOAD_PLUGIN = "klrnbk.whitelist.reload"
    const val TOGGLE_WHITELIST = "klrnbk.whitelist.toggle"
    const val ADD_PLAYER = "klrnbk.whitelist.add"
    const val REMOVE_PLAYER = "klrnbk.whitelist.remove"
    const val VIEW_LOGS = "klrnbk.whitelist.logs"
    const val VIEW_LIST = "klrnbk.whitelist.list"
}

object LanguageKeys {
    const val RELOAD_SUCCESS = "whitelist.admin.reload.success"
    const val RELOAD_FAILED = "whitelist.admin.reload.failed"
    const val WHITELIST_ENABLED = "whitelist.toggle.enabled"
    const val WHITELIST_DISABLED = "whitelist.toggle.disabled"
    const val WHITELIST_ALREADY_ENABLED = "whitelist.toggle.already_enabled"
    const val WHITELIST_ALREADY_DISABLED = "whitelist.toggle.already_disabled"
    const val PLAYER_ADDED = "whitelist.player.added"
    const val PLAYER_ALREADY_WHITELISTED = "whitelist.player.already_whitelisted"
    const val PLAYER_REMOVED = "whitelist.player.removed"
    const val PLAYER_NOT_WHITELISTED = "whitelist.player.not_whitelisted"
    const val PLAYER_NOT_FOUND = "whitelist.player.not_found"
    const val ACTOR_UNKNOWN = "whitelist.actor.unknown"
    const val ACTION_FAILED = "whitelist.action.failed"
    const val LOGS_HEADER_PLAYER = "whitelist.logs.header.player"
    const val LOGS_HEADER_SETTINGS = "whitelist.logs.header.settings"
    const val LOGS_ENTRY_ADDED = "whitelist.logs.entry.added"
    const val LOGS_ENTRY_REMOVED = "whitelist.logs.entry.removed"
    const val LOGS_ENTRY_ENABLED = "whitelist.logs.entry.enabled"
    const val LOGS_ENTRY_DISABLED = "whitelist.logs.entry.disabled"
    const val LOGS_EMPTY = "whitelist.logs.empty"
    const val LOGS_PAGE = "whitelist.logs.page"
    const val LOGS_FOOTER_PREVIOUS = "whitelist.logs.footer_previous"
    const val LOGS_FOOTER_NEXT = "whitelist.logs.footer_next"
    const val LOGS_ACTOR_CONSOLE = "whitelist.logs.actor_console"
    const val LOGS_ACTOR_UNKNOWN = "whitelist.logs.actor_unknown"
    const val LIST_HEADER = "whitelist.list.header"
    const val LIST_ENTRY = "whitelist.list.entry"
    const val LIST_EMPTY = "whitelist.list.empty"
    const val LIST_PAGE = "whitelist.list.page"
    const val LIST_FOOTER_PREVIOUS = "whitelist.list.footer_previous"
    const val LIST_FOOTER_NEXT = "whitelist.list.footer_next"
}
