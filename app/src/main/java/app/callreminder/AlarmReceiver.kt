package app.callreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/** Fires when a reminder's time arrives: starts the ringing call and books the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)
        val text = if (id > 0) {
            val r = ReminderStore.get(c, id) ?: return
            if (!r.enabled) return
            if (r.repeat == Reminder.REPEAT_ONCE) {
                r.enabled = false
                ReminderStore.save(c, r)
            } else {
                Scheduler.schedule(c, r, ZonedDateTime.now().plusMinutes(1))
            }
            r.text
        } else {
            intent.getStringExtra(Scheduler.EXTRA_TEXT) ?: return
        }
        CallService.start(c, text)
    }
}
