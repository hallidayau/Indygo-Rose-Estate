package au.halliday.clipsync

import android.content.Context
import org.json.JSONObject

object Sender {
    // ntfy.sh keeps messages under 4096 bytes inline; longer text goes as an attachment.
    private const val MAX_INLINE = 3800

    fun sendText(context: Context, text: String) {
        val crypto = crypto(context)
        val inline = crypto.encryptToString(header(context, "text").put("text", text).toString())
        if (inline.length <= MAX_INLINE) {
            Ntfy.publishMessage(crypto.topic, inline)
        } else {
            Ntfy.publishAttachment(
                crypto.topic,
                crypto.encryptToString(header(context, "text").toString()),
                crypto.encrypt(text.toByteArray(Charsets.UTF_8)),
            )
        }
        History.add(context, History.SENT, text, Prefs.deviceName(context))
    }

    /** Tells the other phone our name; it answers with an ack carrying its own. */
    fun sendHello(context: Context, ack: Boolean) {
        val crypto = crypto(context)
        val type = if (ack) "hello_ack" else "hello"
        Ntfy.publishMessage(crypto.topic, crypto.encryptToString(header(context, type).toString()))
    }

    private fun crypto(context: Context): Crypto {
        val passphrase = Prefs.passphrase(context)
        if (passphrase.isBlank()) throw IllegalStateException("Open ClipSync and set a pairing code first")
        return Crypto.get(passphrase)
    }

    private fun header(context: Context, type: String) = JSONObject()
        .put("v", 1)
        .put("from", Prefs.deviceId(context))
        .put("name", Prefs.deviceName(context))
        .put("type", type)
}
