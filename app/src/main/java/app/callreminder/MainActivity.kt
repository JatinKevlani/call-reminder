package app.callreminder

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : Activity() {
    private lateinit var list: ListView
    private lateinit var empty: View
    private lateinit var banner: TextView
    private val adapter = ReminderAdapter()
    private var items: List<Reminder> = emptyList()
    private var problems: List<Pair<String, () -> Unit>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Notifications.createChannels(this)

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        banner = findViewById(R.id.setupBanner)

        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> startActivity(EditActivity.intent(this, items[pos].id)) }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(items[pos])
            true
        }
        findViewById<View>(R.id.add).setOnClickListener { startActivity(EditActivity.intent(this, 0)) }
        banner.setOnClickListener { problems.firstOrNull()?.second?.invoke() }

        Scheduler.scheduleAll(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        items = ReminderStore.all(this).sortedWith(compareBy({ !it.enabled }, { it.hour * 60 + it.minute }))
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        problems = findProblems()
        val first = problems.firstOrNull()
        if (first == null) {
            banner.visibility = View.GONE
        } else {
            banner.visibility = View.VISIBLE
            banner.text = getString(R.string.setup_banner, first.first)
        }
    }

    /** Things that would stop a reminder call from ringing, each with a way to fix it. */
    private fun findProblems(): List<Pair<String, () -> Unit>> {
        val out = mutableListOf<Pair<String, () -> Unit>>()
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) {
            out += "Notifications are off, so reminder calls can’t ring." to {
                openSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            out += "Allow full-screen notifications so the call can show on your lock screen." to {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            out += "Battery saver may block reminder calls. Choose “Allow” on the next screen." to {
                openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        return out
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun setEnabled(r: Reminder, on: Boolean) {
        r.enabled = on
        if (on && r.repeat == Reminder.REPEAT_ONCE && r.nextTrigger() == null) {
            // Turning an old one-time reminder back on: move it to the next time that hour comes round.
            val today = LocalDate.now()
            val upcoming = LocalTime.of(r.hour, r.minute).isAfter(LocalTime.now())
            r.date = (if (upcoming) today else today.plusDays(1)).toString()
        }
        ReminderStore.save(this, r)
        Scheduler.schedule(this, r)
        if (on) r.nextTrigger()?.let {
            Toast.makeText(this, "I’ll call you ${Fmt.until(it)}", Toast.LENGTH_SHORT).show()
        }
        list.post { refresh() }
    }

    private fun confirmDelete(r: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("Delete this reminder?")
            .setMessage(r.text)
            .setPositiveButton("Delete") { _, _ ->
                Scheduler.cancel(this, r.id)
                ReminderStore.delete(this, r.id)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun meta(r: Reminder): String {
        val next = r.nextTrigger()
        return when {
            !r.enabled -> "Off · ${r.repeatLabel()}"
            next == null -> "Done · ${r.repeatLabel()}"
            else -> "${r.repeatLabel()} · Next: ${Fmt.moment(this, next)}"
        }
    }

    private inner class ReminderAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_reminder, parent, false)
            val r = items[position]
            v.findViewById<TextView>(R.id.time).text = Fmt.time(this@MainActivity, r.hour, r.minute)
            v.findViewById<TextView>(R.id.text).text = r.text
            v.findViewById<TextView>(R.id.meta).text = meta(r)
            v.alpha = if (r.enabled) 1f else 0.55f
            val toggle = v.findViewById<Switch>(R.id.toggle)
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = r.enabled
            toggle.setOnCheckedChangeListener { _, on -> setEnabled(r, on) }
            return v
        }
    }
}
