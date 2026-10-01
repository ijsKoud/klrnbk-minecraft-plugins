package nl.klrnbk.minecraft.packages.common.cryptography

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptographyUtil {
    private val random = SecureRandom()

    fun getRandomSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)

        return keyGenerator.generateKey()
    }

    fun getSecretKeyFromString(keyString: String): SecretKey {
        val decodedKey = Base64.getDecoder().decode(keyString)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
    }

    fun encrypt(
        plainText: String,
        secretKey: SecretKey,
    ): String {
        val iv = ByteArray(12)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            GCMParameterSpec(128, iv),
        )

        val encrypted =
            cipher.doFinal(
                plainText.toByteArray(StandardCharsets.UTF_8),
            )

        val buffer = ByteBuffer.allocate(iv.size + encrypted.size)
        buffer.put(iv)
        buffer.put(encrypted)

        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decrypt(
        cipherText: String,
        secretKey: SecretKey,
    ): String {
        val bytes = Base64.getDecoder().decode(cipherText)

        val buffer = ByteBuffer.wrap(bytes)

        val iv = ByteArray(12)
        buffer.get(iv)

        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(128, iv),
        )

        return String(
            cipher.doFinal(encrypted),
            StandardCharsets.UTF_8,
        )
    }
}
