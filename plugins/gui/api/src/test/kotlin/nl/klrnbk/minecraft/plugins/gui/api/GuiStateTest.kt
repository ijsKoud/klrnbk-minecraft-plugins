package nl.klrnbk.minecraft.plugins.gui.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GuiStateTest {
    private val page = StateKey<Int>("page")
    private val tab = StateKey<String>("tab")

    @Test
    fun `values are typed per key`() {
        val state = GuiState()
        assertNull(state[page])
        state[page] = 2
        state[tab] = "stats"
        assertEquals(2, state[page])
        assertEquals("stats", state[tab])
        state.remove(page)
        assertNull(state[page])
    }

    @Test
    fun `getOrPut stores the default once`() {
        val state = GuiState()
        assertEquals(1, state.getOrPut(page) { 1 })
        assertEquals(1, state.getOrPut(page) { 99 })
    }

    @Test
    fun `update is atomic under contention`() {
        val state = GuiState()
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { pool.execute { repeat(1000) { state.update(page, 0) { it + 1 } } } }
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(8000, state[page])
    }

    @Test
    fun `keys with the same name are different keys`() {
        val state = GuiState()
        state[StateKey<Int>("x")] = 1
        assertNull(state[StateKey<Int>("x")])
    }
}
