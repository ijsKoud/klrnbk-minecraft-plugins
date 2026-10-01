package nl.klrnbk.minecraft.plugins.gui.api

/**
 * The kind of client a player is playing on. Detected automatically (see `GuiApi.platformOf`).
 */
public enum class GuiPlatform {
    /** A Minecraft: Java Edition client connected directly to the proxy. */
    JAVA,

    /** A Minecraft: Bedrock Edition client connected through Geyser. */
    BEDROCK,
    ;

    /**
     * Whether this platform is *expected* to support [capability].
     *
     * This is informational: the framework never blocks a feature based on it. The
     * Bedrock answers are derived from reading Geyser's translators and are
     * intentionally conservative — see TESTING.md for the manual verification matrix.
     */
    public fun supports(capability: GuiCapability): Boolean =
        when (this) {
            JAVA -> true
            BEDROCK -> capability in BEDROCK_CAPABILITIES
        }

    private companion object {
        val BEDROCK_CAPABILITIES =
            setOf(
                GuiCapability.LEFT_RIGHT_CLICK,
                GuiCapability.SHIFT_CLICK,
                GuiCapability.DROP_CLICK,
                GuiCapability.RICH_TOOLTIP,
            )
    }
}

/** Features whose availability differs between [GuiPlatform]s. */
public enum class GuiCapability {
    LEFT_RIGHT_CLICK,
    SHIFT_CLICK,
    MIDDLE_CLICK,
    NUMBER_KEY_CLICK,
    OFFHAND_SWAP,
    DOUBLE_CLICK,
    DROP_CLICK,
    DRAG,

    /** Display name and lore (including colours and formatting). */
    RICH_TOOLTIP,

    /** `custom_model_data` / `item_model` — needs a resource pack (Java) or custom item mapping (Bedrock). */
    CUSTOM_MODELS,

    /** Arbitrary data components set through `GuiItemBuilder.component`. */
    RAW_COMPONENTS,
}
