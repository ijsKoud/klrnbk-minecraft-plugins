package nl.klrnbk.minecraft.plugins.identity.api

object IdentityProvider {
    @Volatile
    private var instance: IdentityApi? = null

    fun get(): IdentityApi =
        instance ?: throw IllegalStateException(
            """
            The Identity PLUGIN isn't loaded yet!
            This could be because:
             1. KLRNBK Identity is not installed
             2. KLRNBK Identity failed to enable
             3. Your plugin is accessing the API too early (e.g. during plugin load instead of in the onEnable event)
            """.trimIndent(),
        )

    fun register(api: IdentityApi) {
        instance = api
    }

    fun unregister() {
        instance = null
    }
}
