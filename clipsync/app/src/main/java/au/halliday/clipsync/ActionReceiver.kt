package au.halliday.clipsync

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Copy again" notification button. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY_TEXT) return
        Clip.setText(context, intent.getStringExtra(EXTRA_TEXT) ?: return)
        SendTask.toast(context, "Copied")
    }

    companion object {
        private const val ACTION_COPY_TEXT = "au.halliday.clipsync.COPY_TEXT"
        private const val EXTRA_TEXT = "text"

        fun copyText(c: Context, id: Int, text: String): PendingIntent = PendingIntent.getBroadcast(
            c,
            id,
            Intent(c, ActionReceiver::class.java).setAction(ACTION_COPY_TEXT).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
