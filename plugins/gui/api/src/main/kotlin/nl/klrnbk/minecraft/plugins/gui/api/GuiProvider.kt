package nl.klrnbk.minecraft.plugins.gui.api

/**
 * Static access point for the [GuiApi], mirroring the other KLRNBK APIs (`IdentityProvider`, ...).
 */
public object GuiProvider {
    @Volatile
    private var instance: GuiApi? = null

    public fun get(): GuiApi =
        instance ?: throw IllegalStateException(
            """
            The GUI PLUGIN isn't loaded yet!
            This could be because:
             1. KLRNBK GUI is not installed
             2. KLRNBK GUI failed to enable (is the PacketEvents Velocity plugin installed?)
             3. Your plugin is accessing the API too early (e.g. during plugin construction instead of in ProxyInitializeEvent)
            """.trimIndent(),
        )

    /** Like [get] but `null` when the GUI plugin is unavailable, for soft dependencies. */
    public fun getOrNull(): GuiApi? = instance

    @InternalGuiApi
    public fun register(api: GuiApi) {
        instance = api
    }

    @InternalGuiApi
    public fun unregister() {
        instance = null
    }
}
