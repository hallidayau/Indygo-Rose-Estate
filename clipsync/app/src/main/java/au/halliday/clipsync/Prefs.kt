package au.halliday.clipsync

import android.content.Context
import android.os.Build
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

    /** Learned from the other phone's messages; null until it has said hello. */
    fun peerName(c: Context): String? = sp(c).getString("peer_name", null)

    fun setPeerName(c: Context, value: String?) {
        if (value == peerName(c)) return
        sp(c).edit().putString("peer_name", value).apply()
        SyncState.changed()
    }

    fun peerLabel(c: Context): String = peerName(c) ?: "your other phone"

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
