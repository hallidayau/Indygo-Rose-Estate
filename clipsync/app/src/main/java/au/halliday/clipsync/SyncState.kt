package au.halliday.clipsync

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Connection status shared between the background service and the screens. */
object SyncState {
    enum class Status { OFF, CONNECTING, CONNECTED, OFFLINE, ERROR }

    @Volatile
    var status = Status.OFF
        private set

    /** Last time anything (message or keepalive) arrived from the relay. */
    @Volatile
    var lastContact = 0L

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun set(value: Status) {
        if (status == value) return
        status = value
        changed()
    }

    fun changed() {
        main.post { listeners.forEach { it() } }
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)

    fun removeListener(listener: () -> Unit) = listeners.remove(listener)
}
