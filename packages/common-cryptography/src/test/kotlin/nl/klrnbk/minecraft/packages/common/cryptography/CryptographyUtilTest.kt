package nl.klrnbk.minecraft.packages.common.cryptography

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.AEADBadTagException

class CryptographyUtilTest {
    @Test
    fun `getRandomSecretKey returns AES 256 bit key`() {
        val key = CryptographyUtil.getRandomSecretKey()

        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size) // 256 bits = 32 bytes
    }

    @Test
    fun `getSecretKeyFromString round-trips base64 encoded key`() {
        val original = CryptographyUtil.getRandomSecretKey()
        val encoded = Base64.getEncoder().encodeToString(original.encoded)

        val restored = CryptographyUtil.getSecretKeyFromString(encoded)

        assertEquals("AES", restored.algorithm)
        assertArrayEquals(original.encoded, restored.encoded)
    }

    @Test
    fun `encrypt and decrypt round-trip`() {
        val key = CryptographyUtil.getRandomSecretKey()
        val plain = "The quick brown fox jumps over the lazy dog"

        val cipherText = CryptographyUtil.encrypt(plain, key)
        val decrypted = CryptographyUtil.decrypt(cipherText, key)

        assertEquals(plain, decrypted)
    }

    @Test
    fun `encrypt produces different ciphertexts for same plaintext (random IV)`() {
        val key = CryptographyUtil.getRandomSecretKey()
        val plain = "same-plaintext"

        val c1 = CryptographyUtil.encrypt(plain, key)
        val c2 = CryptographyUtil.encrypt(plain, key)

        assertNotEquals(c1, c2)
    }

    @Test
    fun `decrypt with wrong key fails`() {
        val key1 = CryptographyUtil.getRandomSecretKey()
        val key2 = CryptographyUtil.getRandomSecretKey()
        val plain = "secret-message"

        val cipherText = CryptographyUtil.encrypt(plain, key1)

        val thrown =
            assertThrows(Exception::class.java) {
                CryptographyUtil.decrypt(cipherText, key2)
            }

        assertTrue(
            thrown is AEADBadTagException || thrown.message?.contains("tag") == true || thrown.message?.contains("auth") == true,
            "Expected authentication failure when decrypting with wrong key",
        )
    }
}
