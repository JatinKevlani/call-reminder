package app.callreminder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** The full-screen "incoming call". Answering reads the task aloud. */
class CallActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        private const val EXTRA_ANSWER = "answer"
        private const val SNOOZE_MS = 10 * 60_000L

        fun intent(c: Context, text: String, answer: Boolean): Intent =
            Intent(c, CallActivity::class.java)
                .putExtra(Scheduler.EXTRA_TEXT, text)
                .putExtra(EXTRA_ANSWER, answer)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }

    private lateinit var status: TextView
    private lateinit var task: TextView
    private lateinit var ringingControls: View
    private lateinit var callControls: View

    private var text = ""
    private var answered = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var answeredAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val endListener: () -> Unit = { if (!answered) finish() }

    private val ticker = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - answeredAt) / 1000
            status.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_ALARM
        setContentView(R.layout.activity_call)

        status = findViewById(R.id.status)
        task = findViewById(R.id.task)
        ringingControls = findViewById(R.id.ringingControls)
        callControls = findViewById(R.id.callControls)

        findViewById<View>(R.id.answer).setOnClickListener { answer() }
        findViewById<View>(R.id.decline).setOnClickListener {
            CallService.stop(this)
            finish()
        }
        findViewById<View>(R.id.sayAgain).setOnClickListener { speak() }
        findViewById<View>(R.id.snooze).setOnClickListener {
            Scheduler.callIn(this, text, SNOOZE_MS)
            Toast.makeText(this, "OK, I’ll call you again in 10 minutes", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<View>(R.id.done).setOnClickListener { finish() }

        CallService.onCallEnded = endListener
        tts = TextToSpeech(this, this)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        text = i.getStringExtra(Scheduler.EXTRA_TEXT) ?: text
        task.text = text
        if (i.getBooleanExtra(EXTRA_ANSWER, false)) {
            answer()
        } else if (!answered) {
            status.setText(R.string.incoming)
            ringingControls.visibility = View.VISIBLE
            callControls.visibility = View.GONE
        }
    }

    private fun answer() {
        if (answered) return
        answered = true
        CallService.stop(this)
        ringingControls.visibility = View.GONE
        callControls.visibility = View.VISIBLE
        answeredAt = SystemClock.elapsedRealtime()
        handler.post(ticker)
        if (ttsReady) speak()
    }

    override fun onInit(result: Int) {
        val engine = tts ?: return
        if (result != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "Voice isn’t available. Your reminder is on screen.", Toast.LENGTH_LONG).show()
            return
        }
        if (engine.setLanguage(Locale.getDefault()) < 0) engine.setLanguage(Locale.US)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setSpeechRate(0.95f)
        ttsReady = true
        if (answered) speak()
    }

    private fun speak() {
        if (!ttsReady) return
        val message = "Hello! This is your reminder. $text. ... Once again: $text."
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reminder")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // While ringing, Back just hides the screen; the call keeps ringing like a real one.
        if (answered) super.onBackPressed() else moveTaskToBack(true)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        if (CallService.onCallEnded === endListener) CallService.onCallEnded = null
        super.onDestroy()
    }
}
