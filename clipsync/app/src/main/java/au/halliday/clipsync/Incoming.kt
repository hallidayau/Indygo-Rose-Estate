package au.halliday.clipsync

import android.content.Context
import android.util.Log
import org.json.JSONObject

object Incoming {
    // ntfy.sh replays missed messages for up to 12 hours after reconnecting.
    // Older ones go into history but don't overwrite the clipboard or notify.
    private const val FRESH_MS = 10 * 60 * 1000L

    fun handle(context: Context, crypto: Crypto, event: JSONObject) {
        val header = try {
            JSONObject(crypto.decryptString(event.getString("message")))
        } catch (e: Exception) {
            return // not encrypted with our code
        }
        if (header.optString("from") == Prefs.deviceId(context)) return

        val name = header.optString("name").ifBlank { null }
        if (name != null) Prefs.setPeerName(context, name)

        when (header.optString("type")) {
            "hello" -> try {
                Sender.sendHello(context, ack = true)
            } catch (e: Exception) {
                Log.w("ClipSync", "Couldn't answer hello", e)
            }
            "text" -> {
                val text = if (header.has("text")) {
                    header.getString("text")
                } else {
                    val url = event.optJSONObject("attachment")?.optString("url") ?: return
                    String(crypto.decrypt(Ntfy.download(url)), Charsets.UTF_8)
                }
                val from = name ?: "your other phone"
                val sentAt = event.optLong("time", 0L) * 1000
                val fresh = sentAt == 0L || System.currentTimeMillis() - sentAt < FRESH_MS
                History.add(context, History.RECEIVED, text, from, if (sentAt > 0) sentAt else System.currentTimeMillis())
                if (!fresh) return
                Clip.setText(context, text)
                // The home screen shows new items itself; only notify when it isn't open.
                if (!MainActivity.visible) Notifications.textReceived(context, from, text)
            }
        }
    }
}
