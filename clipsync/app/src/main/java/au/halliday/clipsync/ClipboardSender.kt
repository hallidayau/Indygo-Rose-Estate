package au.halliday.clipsync

import android.app.Activity
import android.content.ClipboardManager

/** Must be called from a focused activity — Android blocks clipboard reads otherwise. */
object ClipboardSender {
    fun sendCurrent(activity: Activity) {
        val app = activity.applicationContext
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            SendTask.toast(app, "No text on the clipboard to send")
        } else {
            SendTask.run(app) { Sender.sendText(app, text) }
        }
    }
}
