package au.halliday.clipsync

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Invisible target for "Share → ClipSync" and the text-selection menu. */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.toString()

        if (text.isNullOrBlank()) {
            SendTask.toast(this, "Nothing to send — ClipSync only sends text and links")
        } else {
            SendTask.sendWithToast(this, text)
        }
        finish()
    }
}
