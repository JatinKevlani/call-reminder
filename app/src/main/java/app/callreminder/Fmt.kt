package app.callreminder

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    fun time(c: Context, hour: Int, minute: Int): String {
        val pattern = if (DateFormat.is24HourFormat(c)) "HH:mm" else "h:mm a"
        return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun day(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
        }
    }

    fun moment(c: Context, t: ZonedDateTime) = "${day(t.toLocalDate())}, ${time(c, t.hour, t.minute)}"

    fun until(t: ZonedDateTime): String {
        val mins = Duration.between(ZonedDateTime.now(), t).toMinutes()
        return when {
            mins < 1 -> "in less than a minute"
            mins < 60 -> "in $mins min"
            mins < 24 * 60 -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0L) "in $h h" else "in $h h $m min"
            }
            else -> {
                val d = (mins + 12 * 60) / (24 * 60)
                if (d == 1L) "in 1 day" else "in $d days"
            }
        }
    }
}
