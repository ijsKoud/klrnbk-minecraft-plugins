package nl.klrnbk.minecraft.packages.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DatabaseConnectionMonitorTest {
    private var now = 0L
    private var healthy = true
    private var reconnectCalls = 0
    private var reconnectFails = false

    private fun monitor(backoff: ReconnectBackoff = ReconnectBackoff(initialDelay = 5.seconds, maxDelay = 40.seconds)) =
        DatabaseConnectionMonitor(
            isHealthy = { healthy },
            reconnect = {
                reconnectCalls++
                if (reconnectFails) throw IllegalStateException("database offline")
                healthy = true
            },
            logger = NOPLogger.NOP_LOGGER,
            backoff = backoff,
            nanoTime = { now },
        )

    private fun advance(seconds: Long) {
        now += seconds.seconds.inWholeNanoseconds
    }

    @Test
    fun `does not reconnect while the connection is healthy`() {
        val monitor = monitor()

        repeat(3) { monitor.check() }

        assertEquals(0, reconnectCalls)
    }

    @Test
    fun `reconnects when the connection was lost`() {
        val monitor = monitor()
        healthy = false

        monitor.check()

        assertEquals(1, reconnectCalls)
        assertEquals(0, monitor.failedAttempts)
    }

    @Test
    fun `waits for the backoff after a failed attempt`() {
        val monitor = monitor()
        healthy = false
        reconnectFails = true

        monitor.check()
        assertEquals(1, reconnectCalls)

        // Still inside the 5 second backoff, no new attempt.
        advance(4)
        monitor.check()
        assertEquals(1, reconnectCalls)

        advance(1)
        monitor.check()
        assertEquals(2, reconnectCalls)
        assertEquals(2, monitor.failedAttempts)
    }

    @Test
    fun `backoff grows with consecutive failures`() {
        val monitor = monitor()
        healthy = false
        reconnectFails = true

        monitor.check() // fails, next attempt in 5s
        advance(5)
        monitor.check() // fails, next attempt in 10s
        assertEquals(2, reconnectCalls)

        advance(9)
        monitor.check()
        assertEquals(2, reconnectCalls)

        advance(1)
        monitor.check()
        assertEquals(3, reconnectCalls)
    }

    @Test
    fun `reconnects after the database comes back and resets the backoff`() {
        val monitor = monitor()
        healthy = false
        reconnectFails = true
        monitor.check()
        advance(5)

        reconnectFails = false
        monitor.check()

        assertEquals(2, reconnectCalls)
        assertEquals(0, monitor.failedAttempts)

        // A new outage starts over with the initial delay and is retried immediately.
        healthy = false
        reconnectFails = true
        monitor.check()
        assertEquals(3, reconnectCalls)
        advance(5)
        monitor.check()
        assertEquals(4, reconnectCalls)
    }

    @Test
    fun `works when nanoTime is negative`() {
        now = -10.minutes.inWholeNanoseconds
        val monitor = monitor()
        healthy = false
        reconnectFails = true

        monitor.check()
        monitor.check()
        assertEquals(1, reconnectCalls)

        advance(5)
        monitor.check()
        assertEquals(2, reconnectCalls)
    }

    @Test
    fun `a throwing health check counts as a failed attempt instead of escaping`() {
        val monitor =
            DatabaseConnectionMonitor(
                isHealthy = { throw IllegalStateException("boom") },
                reconnect = { reconnectCalls++ },
                logger = NOPLogger.NOP_LOGGER,
                nanoTime = { now },
            )

        monitor.check()

        assertEquals(1, monitor.failedAttempts)
        assertEquals(0, reconnectCalls)
    }

    @Test
    fun `backoff doubles up to the maximum`() {
        val backoff = ReconnectBackoff(initialDelay = 5.seconds, maxDelay = 30.seconds)

        assertEquals(5.seconds, backoff.delayAfter(1))
        assertEquals(10.seconds, backoff.delayAfter(2))
        assertEquals(20.seconds, backoff.delayAfter(3))
        assertEquals(30.seconds, backoff.delayAfter(4))
        assertEquals(30.seconds, backoff.delayAfter(500))
    }
}
