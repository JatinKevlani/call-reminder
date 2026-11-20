package app.callreminder

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

data class Reminder(
    val id: Int,
    var text: String,
    var hour: Int,
    var minute: Int,
    var repeat: Int,
    /** Weekdays for REPEAT_DAYS as a bitmask: bit 0 = Monday ... bit 6 = Sunday. */
    var days: Int,
    /** ISO date (yyyy-MM-dd) for REPEAT_ONCE, null otherwise. */
    var date: String?,
    var enabled: Boolean = true,
) {
    companion object {
        const val REPEAT_ONCE = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_DAYS = 2

        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        const val ALL_DAYS = 0b1111111

        fun dayBit(d: DayOfWeek) = 1 shl (d.value - 1)

        fun fromJson(o: JSONObject) = Reminder(
            id = o.getInt("id"),
            text = o.getString("text"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            repeat = o.optInt("repeat", REPEAT_ONCE),
            days = o.optInt("days", 0),
            date = if (o.isNull("date")) null else o.getString("date"),
            enabled = o.optBoolean("enabled", true),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("hour", hour)
        .put("minute", minute)
        .put("repeat", repeat)
        .put("days", days)
        .put("date", date ?: JSONObject.NULL)
        .put("enabled", enabled)

    /** The next moment this reminder should ring, strictly after [after], or null if never again. */
    fun nextTrigger(after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val time = LocalTime.of(hour, minute)
        val zone = after.zone
        val today = after.toLocalDate()
        return when (repeat) {
            REPEAT_ONCE -> {
                val d = date?.let { LocalDate.parse(it) } ?: return null
                ZonedDateTime.of(d, time, zone).takeIf { it.isAfter(after) }
            }
            REPEAT_DAILY -> {
                val t = ZonedDateTime.of(today, time, zone)
                if (t.isAfter(after)) t else ZonedDateTime.of(today.plusDays(1), time, zone)
            }
            else -> {
                if (days == 0) return null
                (0..7).asSequence()
                    .map { today.plusDays(it.toLong()) }
                    .filter { days and dayBit(it.dayOfWeek) != 0 }
                    .map { ZonedDateTime.of(it, time, zone) }
                    .firstOrNull { it.isAfter(after) }
            }
        }
    }

    fun repeatLabel(): String = when (repeat) {
        REPEAT_ONCE -> "Once"
        REPEAT_DAILY -> "Every day"
        else -> when (days) {
            ALL_DAYS -> "Every day"
            WEEKDAYS -> "Weekdays"
            WEEKEND -> "Weekends"
            else -> DayOfWeek.values()
                .filter { days and dayBit(it) != 0 }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
    }
}
