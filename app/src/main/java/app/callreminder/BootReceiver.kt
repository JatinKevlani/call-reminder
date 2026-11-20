package app.callreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are wiped on reboot, app update or clock change, so book them all again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Scheduler.scheduleAll(c)
    }
}
