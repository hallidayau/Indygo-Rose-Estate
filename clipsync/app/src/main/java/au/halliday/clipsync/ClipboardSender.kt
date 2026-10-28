package au.halliday.clipsync

import android.app.Activity
import android.content.ClipboardManager

/** Must be called from a focused activity — Android blocks clipboard reads otherwise. */
object ClipboardSender {
    /** [report] receives the message to show; defaults to a toast. */
    fun sendCurrent(activity: Activity, report: ((String) -> Unit)? = null) {
        val app = activity.applicationContext
        val show = report ?: { message -> SendTask.toast(app, message) }
        val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            show("Nothing to send")
            return
        }
        SendTask.run(app, { Sender.sendText(app, text) }) { error ->
            show(error ?: "Sent to ${Prefs.peerLabel(app)}")
        }
    }
}
