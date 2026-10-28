package au.halliday.clipsync

import android.app.Activity

/** Opened by the quick tile. Android only allows clipboard reads once we have window focus. */
class SendClipboardActivity : Activity() {
    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            ClipboardSender.sendCurrent(this)
            finish()
        }
    }
}
