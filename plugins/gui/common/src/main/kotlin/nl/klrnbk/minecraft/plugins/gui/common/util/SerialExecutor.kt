package nl.klrnbk.minecraft.plugins.gui.common.util

import org.slf4j.Logger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs tasks one at a time, in submission order, on top of a shared [delegate] pool.
 *
 * One instance per viewer gives the guarantee documented on `GuiClickEvent`: events of one player are
 * never handled concurrently and never reordered, while different players still run in parallel and no
 * thread is ever parked per player. There is no lock and no blocking: a submitter either enqueues (and
 * returns) or, if the queue was idle, schedules the drain loop on the delegate.
 */
class SerialExecutor(
    private val delegate: Executor,
    private val logger: Logger,
) : Executor {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private val running = AtomicBoolean(false)

    override fun execute(command: Runnable) {
        queue.add(command)
        schedule()
    }

    private fun schedule() {
        if (queue.isNotEmpty() && running.compareAndSet(false, true)) {
            try {
                delegate.execute(::drain)
            } catch (t: Throwable) {
                running.set(false)
                throw t
            }
        }
    }

    private fun drain() {
        try {
            while (true) {
                val task = queue.poll() ?: break
                try {
                    task.run()
                } catch (t: Throwable) {
                    logger.error("Uncaught exception in a GUI event handler", t)
                }
            }
        } finally {
            running.set(false)
            // A task may have been enqueued between the last poll() and the flag reset.
            schedule()
        }
    }
}
