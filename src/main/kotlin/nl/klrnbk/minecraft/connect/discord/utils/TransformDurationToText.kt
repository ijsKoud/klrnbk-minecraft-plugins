package nl.klrnbk.minecraft.connect.discord.utils

import kotlin.time.Duration

fun transformDurationToText(duration: Duration): String {
    val days = Pair(duration.inWholeDays, "d")
    val hours = Pair(duration.inWholeHours, "h")
    val minutes = Pair(duration.inWholeMinutes, "m")
    val seconds = Pair(duration.inWholeSeconds, "s")
    val milliseconds = Pair(duration.inWholeMilliseconds, "ms")
    val microseconds = Pair(duration.inWholeMicroseconds, "µs")
    val nanoseconds = Pair(duration.inWholeNanoseconds, "ns")

    val time = arrayOf(days, hours, minutes, seconds, milliseconds, microseconds, nanoseconds)
    return time
        .joinToString(" ") {
            if (it.first in 1..<1000) "${it.first}${it.second}" else ""
        }.trim()
}
