package nl.klrnbk.minecraft.packages.common.cryptography

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class SerializationTest {
    @Test
    fun `secretKeyToString and stringToSecretKey round-trip key bytes`() {
        val key = CryptographyUtil.getRandomSecretKey()

        val encoded = secretKeyToString(key)
        val restored = stringToSecretKey(encoded)

        assertEquals("AES", restored.algorithm)
        assertArrayEquals(key.encoded, restored.encoded)
    }

    @Test
    fun `secretKeyToString matches standard base64 encoding`() {
        val bytes = ByteArray(32) { index -> index.toByte() }
        val key = SecretKeySpec(bytes, "AES")

        val actual = secretKeyToString(key)
        val expected = Base64.getEncoder().encodeToString(bytes)

        assertEquals(expected, actual)
    }

    @Test
    fun `stringToSecretKey rejects invalid base64`() {
        assertThrows(IllegalArgumentException::class.java) {
            stringToSecretKey("not-a-valid-base64-@@@")
        }
    }
}
