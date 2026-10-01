package nl.klrnbk.minecraft.plugins.gui.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SerialExecutorTest {
    private val pool = Executors.newFixedThreadPool(8)

    @Test
    fun `tasks run in submission order and never concurrently`() {
        val serial = SerialExecutor(pool, NOPLogger.NOP_LOGGER)
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val done = CountDownLatch(500)
        repeat(500) { i ->
            serial.execute {
                val now = running.incrementAndGet()
                maxRunning.accumulateAndGet(now, ::maxOf)
                order += i
                running.decrementAndGet()
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals((0 until 500).toList(), order)
        assertEquals(1, maxRunning.get())
    }

    @Test
    fun `concurrent producers lose no tasks`() {
        val serial = SerialExecutor(pool, NOPLogger.NOP_LOGGER)
        val count = AtomicInteger()
        val done = CountDownLatch(8 * 1000)
        repeat(8) {
            pool.execute {
                repeat(1000) {
                    serial.execute {
                        count.incrementAndGet()
                        done.countDown()
                    }
                }
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(8000, count.get())
    }

    @Test
    fun `a throwing task does not stop later tasks`() {
        val serial = SerialExecutor(pool, NOPLogger.NOP_LOGGER)
        val latch = CountDownLatch(1)
        serial.execute { error("boom") }
        serial.execute { latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `different executors run in parallel`() {
        val a = SerialExecutor(pool, NOPLogger.NOP_LOGGER)
        val b = SerialExecutor(pool, NOPLogger.NOP_LOGGER)
        val bothStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        listOf(a, b).forEach {
            it.execute {
                bothStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(bothStarted.await(5, TimeUnit.SECONDS), "a blocked player must not block another player")
        release.countDown()
    }

    @Test
    fun `a rejecting delegate propagates the rejection and the executor recovers once the delegate accepts again`() {
        var reject = true
        val flaky = java.util.concurrent.Executor { task -> if (reject) throw java.util.concurrent.RejectedExecutionException("full") else task.run() }
        val serial = SerialExecutor(flaky, NOPLogger.NOP_LOGGER)
        assertTrue(runCatching { serial.execute { } }.isFailure)
        reject = false
        var ran = false
        serial.execute { ran = true }
        assertTrue(ran, "the running flag must have been reset after the rejection")
    }
}
