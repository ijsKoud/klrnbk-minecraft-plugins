package nl.klrnbk.minecraft.plugins.gui.api

import java.util.concurrent.ConcurrentHashMap

/** A typed key for [GuiState]. Keys are compared by identity, so keep them in a `val`. */
public class StateKey<T : Any>(
    public val name: String,
) {
    override fun toString(): String = "StateKey($name)"
}

/**
 * Mutable per-viewer state of a [Gui] (page numbers, selected tabs, ...).
 *
 * The state lives exactly as long as the viewer's session: it is created when the GUI
 * opens for the player and discarded when it closes, so it cannot leak. It is
 * thread-safe.
 */
public class GuiState {
    private val values = ConcurrentHashMap<StateKey<*>, Any>()

    @Suppress("UNCHECKED_CAST")
    public operator fun <T : Any> get(key: StateKey<T>): T? = values[key] as T?

    public operator fun <T : Any> set(
        key: StateKey<T>,
        value: T,
    ) {
        values[key] = value
    }

    public fun remove(key: StateKey<*>) {
        values.remove(key)
    }

    /** Returns the current value, or atomically stores and returns [default]'s result. */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getOrPut(
        key: StateKey<T>,
        default: () -> T,
    ): T = values.computeIfAbsent(key) { default() } as T

    /** Atomically replaces the value with `transform(current)`. */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> update(
        key: StateKey<T>,
        initial: T,
        transform: (T) -> T,
    ): T = values.compute(key) { _, current -> transform((current as T?) ?: initial) } as T
}
