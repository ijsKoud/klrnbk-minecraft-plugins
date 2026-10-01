package nl.klrnbk.minecraft.plugins.identity.api

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class IdentityApiAbiTest {
    @Test
    fun `uses stable JDK UUID signatures`() {
        assertNotNull(IdentityApi::class.java.getMethod("getPlayerFromUuid", UUID::class.java))
        assertNotNull(IdentityApi::class.java.getMethod("getPlayerFromId", UUID::class.java))
    }
}
