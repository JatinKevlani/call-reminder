package app.callreminder

import android.content.Context
import org.json.JSONArray

/** Keeps reminders on the phone as a small JSON list. */
object ReminderStore {
    private fun prefs(c: Context) = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Reminder> {
        val arr = try {
            JSONArray(prefs(c).getString("list", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until arr.length()).map { Reminder.fromJson(arr.getJSONObject(it)) }.toMutableList()
    }

    fun get(c: Context, id: Int): Reminder? = all(c).firstOrNull { it.id == id }

    fun save(c: Context, r: Reminder) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == r.id }
        if (i >= 0) list[i] = r else list.add(r)
        write(c, list)
    }

    fun delete(c: Context, id: Int) = write(c, all(c).filterNot { it.id == id })

    fun newId(c: Context): Int {
        val p = prefs(c)
        val id = p.getInt("next_id", 1)
        p.edit().putInt("next_id", id + 1).commit()
        return id
    }

    private fun write(c: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(c).edit().putString("list", arr.toString()).commit()
    }
}
