package nl.klrnbk.minecraft.packages.common.cryptography

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

fun secretKeyToString(secretKey: SecretKey): String = Base64.encode(secretKey.encoded)

fun stringToSecretKey(string: String): SecretKey = SecretKeySpec(Base64.decode(string), "AES")
