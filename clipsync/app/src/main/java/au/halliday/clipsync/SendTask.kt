package au.halliday.clipsync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.concurrent.thread

/** Runs a send off the main thread, then reports an error message (or null on success) on the main thread. */
object SendTask {
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, work: () -> Unit, done: (error: String?) -> Unit) {
        thread(name = "clipsync-send") {
            val error = try {
                work()
                null
            } catch (e: Exception) {
                Log.w("ClipSync", "Send failed", e)
                describe(e)
            }
            main.post { done(error) }
        }
    }

    /** Sends text and reports the result as a toast — for use outside the app's screens. */
    fun sendWithToast(context: Context, text: String) {
        val app = context.applicationContext
        run(app, { Sender.sendText(app, text) }) { error ->
            toast(app, error ?: "Sent to ${Prefs.peerLabel(app)}")
        }
    }

    fun toast(context: Context, message: String) {
        val app = context.applicationContext
        main.post { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Couldn't send while offline"
        else -> e.message ?: "Couldn't send"
    }
}
