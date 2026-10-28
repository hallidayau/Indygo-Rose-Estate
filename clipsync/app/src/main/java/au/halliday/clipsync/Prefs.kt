package au.halliday.clipsync

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.UUID

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("clipsync", Context.MODE_PRIVATE)

    fun passphrase(c: Context): String = sp(c).getString("passphrase", "") ?: ""

    fun setPassphrase(c: Context, value: String) {
        sp(c).edit().putString("passphrase", value).apply()
    }

    fun setupDone(c: Context): Boolean = sp(c).getBoolean("setup_done", false)

    fun setSetupDone(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("setup_done", value).apply()
    }

    fun deviceId(c: Context): String {
        val p = sp(c)
        p.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString("device_id", id).apply()
        return id
    }

    /** The name the other phone sees for this one. */
    fun deviceName(c: Context): String =
        sp(c).getString("device_name", null)?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android phone"

    fun setDeviceName(c: Context, value: String) {
        sp(c).edit().putString("device_name", value.trim()).apply()
    }

    /** Other devices on this pairing code (id → name), learned from their messages. */
    private fun peers(c: Context): JSONObject =
        try {
            JSONObject(sp(c).getString("peers", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

    /** Names of the other devices, e.g. "Pixel 8, DESKTOP-PC"; null until one has said hello. */
    fun peerName(c: Context): String? {
        val all = peers(c)
        val names = all.keys().asSequence().map { all.getString(it) }.distinct().toList()
        return if (names.isEmpty()) null else names.joinToString(", ")
    }

    fun setPeer(c: Context, id: String, name: String) {
        val all = peers(c)
        if (all.optString(id) == name) return
        sp(c).edit().putString("peers", all.put(id, name).toString()).apply()
        SyncState.changed()
    }

    fun clearPeers(c: Context) {
        sp(c).edit().remove("peers").apply()
        SyncState.changed()
    }

    fun peerLabel(c: Context): String = peerName(c) ?: "your other devices"

    fun historyLimit(c: Context): Int = sp(c).getInt("history_limit", 10)

    fun setHistoryLimit(c: Context, value: Int) {
        sp(c).edit().putInt("history_limit", value).apply()
    }

    fun tileAdded(c: Context): Boolean = sp(c).getBoolean("tile_added", false)

    fun setTileAdded(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("tile_added", value).apply()
    }

    fun lastMessageId(c: Context, topic: String): String? = sp(c).getString("last_$topic", null)

    fun setLastMessageId(c: Context, topic: String, id: String) {
        sp(c).edit().putString("last_$topic", id).apply()
    }
}
