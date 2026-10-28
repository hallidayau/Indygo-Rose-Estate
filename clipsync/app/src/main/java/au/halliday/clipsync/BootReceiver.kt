package au.halliday.clipsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reconnects after a restart or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.passphrase(context).isNotBlank()) SyncService.start(context)
    }
}
