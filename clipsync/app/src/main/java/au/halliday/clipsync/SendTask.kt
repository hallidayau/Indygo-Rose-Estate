package au.halliday.clipsync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.concurrent.thread

/** Runs a send off the main thread and reports the result with a toast. */
object SendTask {
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, work: () -> Unit) {
        val app = context.applicationContext
        thread(name = "clipsync-send") {
            try {
                work()
                toast(app, "Sent to your other phone ✓")
            } catch (e: Exception) {
                Log.w("ClipSync", "Send failed", e)
                toast(app, "ClipSync: ${e.message ?: "send failed"}")
            }
        }
    }

    fun toast(context: Context, message: String) {
        val app = context.applicationContext
        main.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }
}
