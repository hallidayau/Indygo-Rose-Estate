package au.halliday.clipsync

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import java.security.SecureRandom
import java.util.Locale

object PairingCode {
    // No 0/O/1/I/L so the code can't be mistyped.
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** Random 16-character code like K7QM-2XPA-9RTD-VH4N (~79 bits). */
    fun generate(): String {
        val random = SecureRandom()
        return format((0 until 16).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString(""))
    }

    /** Accepts any spacing, dashes or case; returns null unless it's a full 16-character code. */
    fun normalize(input: String): String? {
        val raw = clean(input)
        return if (raw.length == 16) format(raw) else null
    }

    /** Formats partial input as it's typed: "k7qm2x" → "K7QM-2X". */
    fun formatPartial(input: String): String = format(clean(input).take(16))

    private fun clean(input: String) = input.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }

    private fun format(raw: String) = raw.chunked(4).joinToString("-")

    /** Opens Messages with the code filled in; falls back to the share sheet. */
    fun sendByText(activity: Activity, code: String) {
        val message = "My ClipSync pairing code is $code"
        try {
            activity.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", message))
        } catch (e: ActivityNotFoundException) {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
            activity.startActivity(Intent.createChooser(share, "Text this code"))
        }
    }
}
