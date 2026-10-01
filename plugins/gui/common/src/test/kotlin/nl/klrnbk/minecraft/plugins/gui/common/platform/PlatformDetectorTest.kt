package nl.klrnbk.minecraft.plugins.gui.common.platform

import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import nl.klrnbk.minecraft.plugins.gui.common.testPlayer
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.geyser.api.GeyserApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private fun det(block: () -> GuiPlatform?) = PlatformDetector { block() }

class PlatformDetectorTest {
    private val java = testPlayer("Java", UUID.fromString("4b1f6c3a-0000-4000-8000-000000000001"))
    private val bedrock = testPlayer("Bedrock", UUID(0L, 1234567L))

    @Test
    fun `the uuid heuristic recognises floodgate uuids only`() {
        val detector = FloodgateUuidPlatformDetector()
        assertEquals(GuiPlatform.BEDROCK, detector.detect(bedrock))
        assertNull(detector.detect(java))
    }

    @Test
    fun `chained detectors ask in order and fall back to java`() {
        val chain = ChainedPlatformDetector(listOf(det { null }, det { GuiPlatform.BEDROCK }, det { GuiPlatform.JAVA }))
        assertEquals(GuiPlatform.BEDROCK, chain.detect(java))
        assertEquals(GuiPlatform.JAVA, ChainedPlatformDetector(listOf(det { null })).detect(java))
        assertEquals(GuiPlatform.JAVA, ChainedPlatformDetector(emptyList()).detect(java))
    }

    @Test
    fun `a detector that throws is skipped`() {
        val chain = ChainedPlatformDetector(listOf(det { error("floodgate exploded") }, det { GuiPlatform.BEDROCK }))
        assertEquals(GuiPlatform.BEDROCK, chain.detect(java))
    }

    @Test
    fun `caching detects once per player until forgotten`() {
        val calls = AtomicInteger()
        val cache = CachingPlatformDetector({ calls.incrementAndGet(); GuiPlatform.BEDROCK })
        repeat(5) { cache.detect(java) }
        assertEquals(1, calls.get())
        cache.forget(java.uniqueId)
        cache.detect(java)
        assertEquals(2, calls.get())
    }

    // A class loader that can see nothing but the JDK stands in for "Floodgate/Geyser are not installed".
    private val noPlugins = object : ClassLoader(null) {}

    @AfterEach
    fun resetStubs() {
        FloodgateApi.bedrockUuids = emptySet()
        GeyserApi.bedrockUuids = emptySet()
    }

    @Test
    fun `floodgate and geyser detectors report unknown when their api is not installed`() {
        assertNull(FloodgatePlatformDetector(noPlugins).detect(java))
        assertNull(GeyserPlatformDetector(noPlugins).detect(java))
    }

    @Test
    fun `the floodgate detector uses the floodgate api through reflection`() {
        FloodgateApi.bedrockUuids = setOf(bedrock.uniqueId)
        val detector = FloodgatePlatformDetector()
        assertEquals(GuiPlatform.BEDROCK, detector.detect(bedrock))
        assertEquals(GuiPlatform.JAVA, detector.detect(java))
    }

    @Test
    fun `the geyser detector only ever answers bedrock so it cannot veto later detectors`() {
        GeyserApi.bedrockUuids = setOf(bedrock.uniqueId)
        val detector = GeyserPlatformDetector()
        assertEquals(GuiPlatform.BEDROCK, detector.detect(bedrock))
        assertNull(detector.detect(java))
    }
}
