package au.halliday.clipsync

import android.content.Context
import org.json.JSONObject

object Incoming {
    fun handle(context: Context, crypto: Crypto, event: JSONObject) {
        val header = try {
            JSONObject(crypto.decryptString(event.getString("message")))
        } catch (e: Exception) {
            return // not encrypted with our code
        }
        if (header.optString("from") == Prefs.deviceId(context)) return
        if (header.optString("type") != "text") return

        val text = if (header.has("text")) {
            header.getString("text")
        } else {
            val url = event.optJSONObject("attachment")?.optString("url") ?: return
            String(crypto.decrypt(Ntfy.download(url)), Charsets.UTF_8)
        }
        Clip.setText(context, text)
        Notifications.textReceived(context, header.optString("name").ifBlank { "your other phone" }, text)
    }
}
