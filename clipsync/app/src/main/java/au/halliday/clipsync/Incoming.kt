package au.halliday.clipsync

import android.content.Context
import android.util.Log
import org.json.JSONObject

object Incoming {
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
                Clip.setText(context, text)
                History.add(context, History.RECEIVED, text, from)
                // The home screen shows new items itself; only notify when it isn't open.
                if (!MainActivity.visible) Notifications.textReceived(context, from, text)
            }
        }
    }
}
