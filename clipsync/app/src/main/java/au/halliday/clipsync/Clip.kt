package au.halliday.clipsync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

object Clip {
    private val main = Handler(Looper.getMainLooper())

    fun setText(context: Context, text: String) {
        val app = context.applicationContext
        main.post {
            app.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("ClipSync", text))
        }
    }
}
