package au.halliday.clipsync

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

/** The last few clips sent or received, stored only on this phone. */
object History {
    const val SENT = 0
    const val RECEIVED = 1
    private const val MAX_TEXT = 100_000

    class Item(val direction: Int, val text: String, val from: String, val time: Long)

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    private fun sp(c: Context) = c.getSharedPreferences("history", Context.MODE_PRIVATE)

    @Synchronized
    fun items(c: Context): List<Item> {
        val json = sp(c).getString("items", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Item(o.getInt("d"), o.getString("t"), o.optString("f"), o.getLong("at"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(c: Context, direction: Int, text: String, from: String) {
        val limit = Prefs.historyLimit(c)
        if (limit <= 0) return
        val item = Item(direction, text.take(MAX_TEXT), from, System.currentTimeMillis())
        save(c, (listOf(item) + items(c)).take(limit))
    }

    @Synchronized
    fun trim(c: Context) {
        save(c, items(c).take(Prefs.historyLimit(c).coerceAtLeast(0)))
    }

    @Synchronized
    fun clear(c: Context) = save(c, emptyList())

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun save(c: Context, list: List<Item>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("d", it.direction).put("t", it.text).put("f", it.from).put("at", it.time))
        }
        sp(c).edit().putString("items", array.toString()).apply()
        main.post { listeners.forEach { it() } }
    }
}
