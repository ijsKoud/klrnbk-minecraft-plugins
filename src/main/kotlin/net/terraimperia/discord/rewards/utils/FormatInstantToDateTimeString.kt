package net.terraimperia.discord.rewards.utils

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlin.time.Instant

val formatter: DateTimeFormat<DateTimeComponents> =
    DateTimeComponents.Format {
        day()
        char('-')
        monthNumber()
        char('-')
        year()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

fun formatInstantToDateTimeString(instant: Instant): String = instant.format(formatter)
