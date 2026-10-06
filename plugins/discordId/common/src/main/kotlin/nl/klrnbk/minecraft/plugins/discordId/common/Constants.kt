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
}
