package au.halliday.clipsync

import android.content.Context
import android.os.Build
import java.security.SecureRandom
import java.util.UUID

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("clipsync", Context.MODE_PRIVATE)

    fun passphrase(c: Context): String = sp(c).getString("passphrase", "") ?: ""

    fun setPassphrase(c: Context, value: String) {
        sp(c).edit().putString("passphrase", value).apply()
    }

    fun deviceId(c: Context): String {
        val p = sp(c)
        p.getString("device_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString("device_id", id).apply()
        return id
    }

    fun deviceName(): String = Build.MODEL ?: "your other phone"

    fun lastMessageId(c: Context, topic: String): String? = sp(c).getString("last_$topic", null)

    fun setLastMessageId(c: Context, topic: String, id: String) {
        sp(c).edit().putString("last_$topic", id).apply()
    }

    /** Random 16-character code like K7QM-2XPA-9RTD-VH4N (~79 bits). No 0/O/1/I/L to avoid typos. */
    fun generateCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (0 until 16).map { alphabet[random.nextInt(alphabet.length)] }
            .chunked(4) { it.joinToString("") }
            .joinToString("-")
    }
}
