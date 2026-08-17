package nl.klrnbk.minecraft.connect.discord.utils

import java.security.SecureRandom

object RegistrationCodeGenerator {
    private val random = SecureRandom()

    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(length: Int = 8): String =
        buildString(length) {
            repeat(length) {
                append(CHARSET[random.nextInt(CHARSET.length)])
            }
        }
}

fun getCryptographicRandomText(length: Int): String = RegistrationCodeGenerator.generate(length)
