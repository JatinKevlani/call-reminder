package app.callreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

object Notifications {
    private const val CH_CALL = "reminder_calls"
    private const val CH_CALL_BACKUP = "reminder_calls_backup"
    private const val CH_MISSED = "missed_reminders"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun createChannels(c: Context) {
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val call = NotificationChannel(CH_CALL, "Reminder calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "The ringing call screen for your reminders"
            setSound(null, null) // the app plays the ringtone itself
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val backup = NotificationChannel(CH_CALL_BACKUP, "Reminder calls (backup)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Used if the phone blocks the normal ringing screen"
            setSound(Settings.System.DEFAULT_RINGTONE_URI, alarmAudio)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 700, 900, 700, 900)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(CH_MISSED, "Missed reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminder calls you didn’t answer"
        }
        nm(c).createNotificationChannels(listOf(call, backup, missed))
    }

    fun buildCall(c: Context, text: String, backup: Boolean = false): Notification {
        createChannels(c)
        val ring = PendingIntent.getActivity(c, 1, CallActivity.intent(c, text, answer = false), FLAGS)
        val answer = PendingIntent.getActivity(c, 2, CallActivity.intent(c, text, answer = true), FLAGS)
        val decline = PendingIntent.getService(
            c, 3, Intent(c, CallService::class.java).setAction(CallService.ACTION_STOP), FLAGS
        )
        val caller = c.getString(R.string.caller_name)

        val b = Notification.Builder(c, if (backup) CH_CALL_BACKUP else CH_CALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(caller)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(ring)
            .setFullScreenIntent(ring, true)

        if (!backup && Build.VERSION.SDK_INT >= 31) {
            val person = Person.Builder().setName(caller).setImportant(true).build()
            b.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
        } else {
            b.addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_call_end), "Decline", decline).build())
            b.addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_call), "Answer", answer).build())
        }
        if (backup) b.setTimeoutAfter(60_000)
        return b.build()
    }

    fun showCallFallback(c: Context, text: String) {
        val n = buildCall(c, text, backup = true)
        n.flags = n.flags or Notification.FLAG_INSISTENT // keep ringing until dealt with
        nm(c).notify(CallService.NOTIF_ID, n)
    }

    fun showMissed(c: Context, text: String) {
        createChannels(c)
        val open = PendingIntent.getActivity(c, 4, Intent(c, MainActivity::class.java), FLAGS)
        val n = Notification.Builder(c, CH_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed reminder call")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm(c).notify(10 + (System.currentTimeMillis() / 1000 % 100_000).toInt(), n)
    }

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)
}
