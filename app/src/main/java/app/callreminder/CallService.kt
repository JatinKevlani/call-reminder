package app.callreminder

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** Rings and vibrates like an incoming call until it's answered, declined, or 60 seconds pass. */
class CallService : Service() {
    companion object {
        const val ACTION_RING = "app.callreminder.RING"
        const val ACTION_STOP = "app.callreminder.STOP"
        const val NOTIF_ID = 4242
        private const val RING_TIMEOUT_MS = 60_000L

        /** Called on the main thread whenever a ringing call ends (answered, declined or missed). */
        var onCallEnded: (() -> Unit)? = null

        fun start(c: Context, text: String) {
            val i = Intent(c, CallService::class.java)
                .setAction(ACTION_RING)
                .putExtra(Scheduler.EXTRA_TEXT, text)
            try {
                c.startForegroundService(i)
            } catch (e: Exception) {
                // Android refused to start the ringing service; use a loud notification instead.
                Notifications.showCallFallback(c, text)
            }
        }

        fun stop(c: Context) {
            c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
            try {
                c.startService(Intent(c, CallService::class.java).setAction(ACTION_STOP))
            } catch (ignored: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var text: String? = null

    private val missed = Runnable {
        text?.let { Notifications.showMissed(this, it) }
        endCall()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RING) {
            val t = intent.getStringExtra(Scheduler.EXTRA_TEXT) ?: getString(R.string.caller_name)
            text = t
            val n = Notifications.buildCall(this, t)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
            startRinging()
            handler.removeCallbacks(missed)
            handler.postDelayed(missed, RING_TIMEOUT_MS)
        } else {
            endCall()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        stopRinging()
        // Alarm audio so it still rings when the phone is on silent.
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        for (uri in listOf(Settings.System.DEFAULT_RINGTONE_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                break
            } catch (e: Exception) {
                mp.release()
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }
        @Suppress("DEPRECATION")
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0), attrs)

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallReminder:ring")
            .apply { acquire(RING_TIMEOUT_MS + 10_000) }
    }

    private fun stopRinging() {
        player?.let {
            try {
                it.stop()
            } catch (ignored: Exception) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun endCall() {
        handler.removeCallbacks(missed)
        stopRinging()
        text = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
        onCallEnded?.invoke()
    }

    override fun onDestroy() {
        handler.removeCallbacks(missed)
        stopRinging()
        super.onDestroy()
    }
}
