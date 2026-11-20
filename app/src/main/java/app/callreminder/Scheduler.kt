package app.callreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

/** Sets exact "alarm clock" alarms so reminders fire on time even when the phone is asleep. */
object Scheduler {
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TEXT = "reminder_text"

    private const val ONE_SHOT_BASE = 1_000_000
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun schedule(c: Context, r: Reminder, after: ZonedDateTime = ZonedDateTime.now()) {
        val pi = reminderIntent(c, r.id)
        alarms(c).cancel(pi)
        if (!r.enabled) return
        val next = r.nextTrigger(after) ?: return
        setAlarm(c, next.toInstant().toEpochMilli(), pi)
    }

    fun scheduleAll(c: Context) = ReminderStore.all(c).forEach { schedule(c, it) }

    fun cancel(c: Context, id: Int) = alarms(c).cancel(reminderIntent(c, id))

    /** A one-off call (snooze or test) that isn't tied to a saved reminder. */
    fun callIn(c: Context, text: String, delayMs: Long) {
        val at = System.currentTimeMillis() + delayMs
        val intent = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_TEXT, text)
        val code = ONE_SHOT_BASE + ((at / 1000) % 1_000_000).toInt()
        setAlarm(c, at, PendingIntent.getBroadcast(c, code, intent, FLAGS))
    }

    private fun alarms(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun reminderIntent(c: Context, id: Int): PendingIntent {
        val intent = Intent(c, AlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(c, id, intent, FLAGS)
    }

    private fun setAlarm(c: Context, at: Long, pi: PendingIntent) {
        val am = alarms(c)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            return
        }
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), FLAGS)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
    }
}
