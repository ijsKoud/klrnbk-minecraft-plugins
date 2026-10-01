package nl.klrnbk.minecraft.plugins.whitelist.api

object WhitelistProvider {
    @Volatile
    private var instance: WhitelistApi? = null

    fun get(): WhitelistApi =
        instance ?: throw IllegalStateException(
            """
            The Whitelist PLUGIN isn't loaded yet!
            This could be because:
             1. KLRNBK Whitelist is not installed
             2. KLRNBK Whitelist failed to enable
             3. Your plugin is accessing the API too early (e.g. during plugin load instead of in the onEnable event)
            """.trimIndent(),
        )

    fun register(api: WhitelistApi) {
        instance = api
    }

    fun unregister() {
        instance = null
    }
}
