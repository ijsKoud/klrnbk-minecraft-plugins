package nl.klrnbk.minecraft.plugins.discordId.common

import kotlin.time.Duration.Companion.minutes

val LINK_CODE_VALIDITY_DURATION = 20.minutes

object LanguageKeys {
    const val LINK_CODE_DETAILS = "discord-id.linkCode.details"
    const val LINK_CODE_INVALID = "Invalid code"
    const val LINK_CODE_SUCCESS = "Account linked successfully"
    const val LINK_CODE_FAILED = "discord-id.linkCode.failed"
    const val LINK_CODE_ALREADY_LINKED = "Discord or Minecraft account is already linked"
    const val LINK_CODE_UNLINK_SUCCESS = "discord-id.unlink.success"
    const val LINK_CODE_UNLINK_FAILED = "discord-id.unlink.failed"
    const val LINK_CODE_LOOKUP_FAILED = "discord-id.lookup.failed"
    const val LINK_CODE_LOOKUP_SUCCESS = "discord-id.lookup.success"
    const val LINK_CODE_EXPORT_SUCCESS = "discord-id.admin.export.success"
    const val LINK_CODE_IMPORT_SUCCESS = "discord-id.admin.import.success"
    const val LINK_CODE_TRANSFER_FAILED = "discord-id.admin.transfer.failed"
    const val LINK_CODE_RELOAD_SUCCESS = "discord-id.admin.reload.success"
}

object Permissions {
    const val LINK = "klrnbk.discord-id.link"
    const val UNLINK = "klrnbk.discord-id.unlink"
    const val UNLINK_BYPASS = "klrnbk.discord-id.unlink.bypass"
    const val UNLINK_FORCED = "klrnbk.discord-id.unlink.force"
    const val LOOKUP = "klrnbk.discord-id.lookup"
    const val ADMIN_EXPORT = "klrnbk.discord-id.admin.export"
    const val ADMIN_IMPORT = "klrnbk.discord-id.admin.import"
    const val ADMIN_RELOAD = "klrnbk.discord-id.admin.reload"
}
