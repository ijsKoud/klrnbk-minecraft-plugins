package nl.klrnbk.minecraft.packages.database

import org.slf4j.Logger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Delay between failed reconnect attempts: [initialDelay] after the first failure,
 * doubling with every consecutive failure up to [maxDelay].
 */
data class ReconnectBackoff(
    val initialDelay: Duration = 5.seconds,
    val maxDelay: Duration = 5.minutes,
) {
    fun delayAfter(consecutiveFailures: Int): Duration {
        require(consecutiveFailures >= 1) { "consecutiveFailures must be at least 1" }

        var delay = initialDelay
        repeat(consecutiveFailures - 1) {
            if (delay >= maxDelay) return maxDelay
            delay *= 2
        }

        return minOf(delay, maxDelay)
    }
}

/**
 * Periodically checks the database connection and reconnects when it was lost.
 * Failed reconnect attempts are spaced out by [backoff] so an offline database isn't hammered.
 */
class DatabaseConnectionMonitor(
    private val isHealthy: () -> Boolean,
    private val reconnect: () -> Unit,
    private val logger: Logger,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val checkInterval: Duration = 5.seconds,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var executor: ScheduledExecutorService? = null
    private var consecutiveFailures = 0
    private var nextAttemptAtNanos: Long? = null

    val failedAttempts: Int
        @Synchronized get() = consecutiveFailures

    @Synchronized
    fun start() {
        if (executor != null) return

        consecutiveFailures = 0
        nextAttemptAtNanos = null
        executor =
            Executors
                .newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "database-connection-monitor").apply { isDaemon = true }
                }.also {
                    it.scheduleWithFixedDelay(
                        ::check,
                        checkInterval.inWholeMilliseconds,
                        checkInterval.inWholeMilliseconds,
                        TimeUnit.MILLISECONDS,
                    )
                }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    /**
     * Runs one health check and, when the connection is down and the backoff has elapsed, one reconnect attempt.
     */
    @Synchronized
    fun check() {
        try {
            if (isHealthy()) {
                if (consecutiveFailures > 0) logger.info("Database connection is healthy again.")
                consecutiveFailures = 0
                nextAttemptAtNanos = null
                return
            }

            // nanoTime() may be negative, so compare by difference instead of directly.
            val notBefore = nextAttemptAtNanos
            if (notBefore != null && nanoTime() - notBefore < 0) return

            logger.warn("Database connection lost, attempting to reconnect...")
            reconnect()

            if (consecutiveFailures > 0) logger.info("Reconnected to the database after $consecutiveFailures failed attempt(s).")
            else logger.info("Reconnected to the database.")
            consecutiveFailures = 0
            nextAttemptAtNanos = null
        } catch (exception: Exception) {
            consecutiveFailures++
            val delay = backoff.delayAfter(consecutiveFailures)
            nextAttemptAtNanos = nanoTime() + delay.inWholeNanoseconds
            logger.error("Failed to reconnect to the database (attempt $consecutiveFailures), retrying in $delay.", exception)
        }
    }
}
