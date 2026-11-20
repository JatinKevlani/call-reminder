package app.callreminder

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class EditActivity : Activity() {
    companion object {
        fun intent(c: Context, id: Int): Intent =
            Intent(c, EditActivity::class.java).putExtra(Scheduler.EXTRA_ID, id)
    }

    private var existing: Reminder? = null
    private var hour = 9
    private var minute = 0
    private var date: LocalDate = LocalDate.now()
    private var repeat = Reminder.REPEAT_ONCE
    private var days = Reminder.WEEKDAYS

    private lateinit var input: EditText
    private lateinit var timeBtn: Button
    private lateinit var dateBtn: Button
    private lateinit var dateRow: View
    private lateinit var daysRow: LinearLayout
    private lateinit var preview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        input = findViewById(R.id.input)
        timeBtn = findViewById(R.id.time)
        dateBtn = findViewById(R.id.date)
        dateRow = findViewById(R.id.dateRow)
        daysRow = findViewById(R.id.days)
        preview = findViewById(R.id.preview)

        existing = intent.getIntExtra(Scheduler.EXTRA_ID, 0).takeIf { it > 0 }?.let { ReminderStore.get(this, it) }
        val r = existing
        if (r != null) {
            hour = r.hour
            minute = r.minute
            repeat = r.repeat
            if (r.days != 0) days = r.days
            date = r.date?.let { LocalDate.parse(it) }?.takeIf { !it.isBefore(LocalDate.now()) }
                ?: nextDateFor(hour, minute)
            input.setText(r.text)
            findViewById<TextView>(R.id.title).setText(R.string.edit_title)
            findViewById<View>(R.id.delete).visibility = View.VISIBLE
        } else {
            val t = LocalDateTime.now().plusHours(1).withMinute(0)
            hour = t.hour
            minute = 0
            date = t.toLocalDate()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            input.requestFocus()
        }

        val group = findViewById<RadioGroup>(R.id.repeat)
        group.check(
            when (repeat) {
                Reminder.REPEAT_ONCE -> R.id.repeatOnce
                Reminder.REPEAT_DAILY -> R.id.repeatDaily
                else -> R.id.repeatDays
            }
        )
        group.setOnCheckedChangeListener { _, checked ->
            repeat = when (checked) {
                R.id.repeatOnce -> Reminder.REPEAT_ONCE
                R.id.repeatDaily -> Reminder.REPEAT_DAILY
                else -> Reminder.REPEAT_DAYS
            }
            updateUi()
        }

        buildDayToggles()

        timeBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                if (repeat == Reminder.REPEAT_ONCE && date == LocalDate.now() &&
                    !LocalTime.of(h, m).isAfter(LocalTime.now())
                ) {
                    date = date.plusDays(1)
                }
                updateUi()
            }, hour, minute, DateFormat.is24HourFormat(this)).show()
        }

        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, mo, d ->
                date = LocalDate.of(y, mo + 1, d)
                updateUi()
            }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
            }.show()
        }

        findViewById<View>(R.id.save).setOnClickListener { save() }
        findViewById<View>(R.id.test).setOnClickListener { test() }
        findViewById<View>(R.id.close).setOnClickListener { finish() }
        findViewById<View>(R.id.delete).setOnClickListener { confirmDelete() }

        updateUi()
    }

    private fun nextDateFor(h: Int, m: Int): LocalDate {
        val today = LocalDate.now()
        return if (LocalTime.of(h, m).isAfter(LocalTime.now())) today else today.plusDays(1)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildDayToggles() {
        val all = DayOfWeek.values()
        all.forEachIndexed { i, d ->
            val bit = Reminder.dayBit(d)
            val tv = TextView(this).apply {
                text = d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(resources.getColorStateList(R.color.day_text, theme))
                background = getDrawable(R.drawable.day_toggle)
                isSelected = days and bit != 0
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (i < all.size - 1) marginEnd = dp(6)
                }
                setOnClickListener {
                    days = days xor bit
                    isSelected = days and bit != 0
                    updateUi()
                }
            }
            daysRow.addView(tv)
        }
    }

    private fun draft(id: Int) = Reminder(
        id = id,
        text = input.text.toString().trim(),
        hour = hour,
        minute = minute,
        repeat = repeat,
        days = days,
        date = if (repeat == Reminder.REPEAT_ONCE) date.toString() else null,
        enabled = true,
    )

    private fun updateUi() {
        timeBtn.text = Fmt.time(this, hour, minute)
        dateBtn.text = Fmt.day(date)
        dateRow.visibility = if (repeat == Reminder.REPEAT_ONCE) View.VISIBLE else View.GONE
        daysRow.visibility = if (repeat == Reminder.REPEAT_DAYS) View.VISIBLE else View.GONE

        val next = draft(0).nextTrigger()
        preview.text = when {
            repeat == Reminder.REPEAT_DAYS && days == 0 -> "Pick at least one day."
            next == null -> "That time has already passed. Pick a later time."
            else -> "First call: ${Fmt.moment(this, next)} (${Fmt.until(next)})"
        }
    }

    private fun save() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            input.error = "What should I remind you about?"
            input.requestFocus()
            return
        }
        if (repeat == Reminder.REPEAT_DAYS && days == 0) {
            toast("Pick at least one day")
            return
        }
        val next = draft(0).nextTrigger()
        if (next == null) {
            toast("That time has already passed. Pick a later time.")
            return
        }
        val r = draft(existing?.id ?: ReminderStore.newId(this))
        ReminderStore.save(this, r)
        Scheduler.schedule(this, r)
        toast("Saved! I’ll call you ${Fmt.until(next)}.")
        finish()
    }

    private fun test() {
        val text = input.text.toString().trim().ifEmpty { "This is a test reminder" }
        Scheduler.callIn(this, text, 10_000)
        toast("Test call in 10 seconds. Lock your phone to see it ring.")
    }

    private fun confirmDelete() {
        val r = existing ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete this reminder?")
            .setMessage(r.text)
            .setPositiveButton("Delete") { _, _ ->
                Scheduler.cancel(this, r.id)
                ReminderStore.delete(this, r.id)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
