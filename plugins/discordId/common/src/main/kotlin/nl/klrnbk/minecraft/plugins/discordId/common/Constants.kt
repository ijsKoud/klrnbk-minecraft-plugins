package nl.klrnbk.minecraft.plugins.discordId.common

import kotlin.time.Duration.Companion.minutes

val LINK_CODE_VALIDITY_DURATION = 20.minutes

object LanguageKeys {
    const val LINK_CODE_DETAILS = "discordId.linkCode.details"
    const val LINK_CODE_INVALID = "Invalid code"
    const val LINK_CODE_SUCCESS = "Account linked successfully"
    const val LINK_CODE_FAILED = "discordId.linkCode.failed"
    const val LINK_CODE_ALREADY_LINKED = "Discord or Minecraft account is already linked"
    const val LINK_CODE_UNLINK_SUCCESS = "discordId.unlink.success"
    const val LINK_CODE_UNLINK_FAILED = "discordId.unlink.failed"
    const val LINK_CODE_LOOKUP_FAILED = "discordId.lookup.failed"
    const val LINK_CODE_LOOKUP_SUCCESS = "discordId.lookup.success"
    const val LINK_CODE_EXPORT_SUCCESS = "discordId.admin.export.success"
    const val LINK_CODE_IMPORT_SUCCESS = "discordId.admin.import.success"
    const val LINK_CODE_TRANSFER_FAILED = "discordId.admin.transfer.failed"
    const val LINK_CODE_RELOAD_SUCCESS = "discordId.admin.reload.success"
}

object Permissions {
    const val LINK = "klrnbk.discordId.link"
    const val UNLINK = "klrnbk.discordId.unlink"
    const val UNLINK_BYPASS = "klrnbk.discordId.unlink.bypass"
    const val UNLINK_FORCED = "klrnbk.discordId.unlink.force"
    const val LOOKUP = "klrnbk.discordId.lookup"
    const val ADMIN_EXPORT = "klrnbk.discordId.admin.export"
    const val ADMIN_IMPORT = "klrnbk.discordId.admin.import"
    const val ADMIN_RELOAD = "klrnbk.discordId.admin.reload"
}
