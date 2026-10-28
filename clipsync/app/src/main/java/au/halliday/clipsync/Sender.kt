package au.halliday.clipsync

import android.content.Context
import org.json.JSONObject

object Sender {
    // ntfy.sh keeps messages under 4096 bytes inline; longer text goes as an attachment.
    private const val MAX_INLINE = 3800

    fun sendText(context: Context, text: String) {
        val crypto = crypto(context)
        val inline = crypto.encryptToString(header(context).put("text", text).toString())
        if (inline.length <= MAX_INLINE) {
            Ntfy.publishMessage(crypto.topic, inline)
        } else {
            Ntfy.publishAttachment(
                crypto.topic,
                crypto.encryptToString(header(context).toString()),
                crypto.encrypt(text.toByteArray(Charsets.UTF_8)),
            )
        }
    }

    private fun crypto(context: Context): Crypto {
        val passphrase = Prefs.passphrase(context)
        if (passphrase.isBlank()) throw IllegalStateException("Open ClipSync and set a pairing code first")
        return Crypto.get(passphrase)
    }

    private fun header(context: Context) = JSONObject()
        .put("v", 1)
        .put("from", Prefs.deviceId(context))
        .put("name", Prefs.deviceName())
        .put("type", "text")
}
