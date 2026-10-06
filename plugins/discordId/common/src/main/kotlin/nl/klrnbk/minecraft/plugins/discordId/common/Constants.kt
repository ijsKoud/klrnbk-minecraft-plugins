package nl.klrnbk.minecraft.plugins.discordId.common

import kotlin.time.Duration.Companion.minutes

val LINK_CODE_VALIDITY_DURATION = 20.minutes

object LanguageKeys {
    const val LINK_CODE_DETAILS = "discordId.linkCode.details"
    const val LINK_CODE_INVALID = "discordId.linkCode.invalid"
    const val LINK_CODE_SUCCESS = "discordId.linkCode.success"
    const val LINK_CODE_FAILED = "discordId.linkCode.failed"
    const val LINK_CODE_ALREADY_LINKED = "discordId.linkCode.alreadyLinked"
    const val LINK_CODE_UNLINK_SUCCESS = "discordId.unlink.success"
    const val LINK_CODE_UNLINK_FAILED = "discordId.unlink.failed"
}
