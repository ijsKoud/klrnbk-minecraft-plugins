package nl.klrnbk.minecraft.plugins.gui.common.platform

import com.velocitypowered.api.proxy.Player
import nl.klrnbk.minecraft.plugins.gui.api.GuiPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides whether a player is on Java or Bedrock.
 *
 * Detectors return `null` when they cannot tell (their backing plugin is not installed), so a
 * [ChainedPlatformDetector] can fall through to the next one.
 */
fun interface PlatformDetector {
    fun detect(player: Player): GuiPlatform?
}

/** Asks each detector in order; the first non-null answer wins, and [fallback] is used if nobody knows. */
class ChainedPlatformDetector(
    private val detectors: List<PlatformDetector>,
    private val fallback: GuiPlatform = GuiPlatform.JAVA,
) : PlatformDetector {
    override fun detect(player: Player): GuiPlatform = detectors.firstNotNullOfOrNull { runCatching { it.detect(player) }.getOrNull() } ?: fallback
}

/** Caches the answer of [delegate] for the length of a player's connection. */
class CachingPlatformDetector(
    private val delegate: PlatformDetector,
) {
    private val cache = ConcurrentHashMap<UUID, GuiPlatform>()

    fun detect(player: Player): GuiPlatform = cache.computeIfAbsent(player.uniqueId) { delegate.detect(player) ?: GuiPlatform.JAVA }

    fun forget(player: UUID) {
        cache.remove(player)
    }
}

/**
 * Asks Floodgate (`FloodgateApi.getInstance().isFloodgatePlayer(uuid)`) through reflection, so the
 * framework needs neither a compile-time dependency on Floodgate's snapshot-only API artifact nor
 * Floodgate to be installed. Requires `floodgate` to be an (optional) dependency of the plugin so
 * Velocity lets our class loader see its classes.
 */
class FloodgatePlatformDetector(
    private val classLoader: ClassLoader = FloodgatePlatformDetector::class.java.classLoader,
) : PlatformDetector {
    override fun detect(player: Player): GuiPlatform? {
        val apiClass = runCatching { Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, classLoader) }.getOrNull() ?: return null
        val api = apiClass.getMethod("getInstance").invoke(null) ?: return null
        val isBedrock = apiClass.getMethod("isFloodgatePlayer", UUID::class.java).invoke(api, player.uniqueId) as? Boolean ?: return null
        return if (isBedrock) GuiPlatform.BEDROCK else GuiPlatform.JAVA
    }
}

/** Asks the Geyser API (`GeyserApi.api().isBedrockPlayer(uuid)`), for setups that run Geyser without Floodgate. */
class GeyserPlatformDetector(
    private val classLoader: ClassLoader = GeyserPlatformDetector::class.java.classLoader,
) : PlatformDetector {
    override fun detect(player: Player): GuiPlatform? {
        val apiClass = runCatching { Class.forName("org.geysermc.geyser.api.GeyserApi", true, classLoader) }.getOrNull() ?: return null
        val api = apiClass.getMethod("api").invoke(null) ?: return null
        val isBedrock = apiClass.getMethod("isBedrockPlayer", UUID::class.java).invoke(api, player.uniqueId) as? Boolean ?: return null
        // Geyser only knows Bedrock players; "false" for an unknown UUID must not override later detectors.
        return if (isBedrock) GuiPlatform.BEDROCK else null
    }
}

/**
 * Last resort: Floodgate derives every Bedrock player's UUID from their XUID with the most-significant
 * 64 bits set to zero (`00000000-0000-0000-....`). Real Java UUIDs (v3/v4) never have that shape.
 * This works even when Floodgate's API is unavailable to us, as long as Floodgate is what assigned the UUID.
 */
class FloodgateUuidPlatformDetector : PlatformDetector {
    override fun detect(player: Player): GuiPlatform? = if (player.uniqueId.mostSignificantBits == 0L) GuiPlatform.BEDROCK else null
}
