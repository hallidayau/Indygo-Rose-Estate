package au.halliday.clipsync

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Keeps one long-lived connection to the relay so clips from the other phone
 * arrive within a second or two, on Wi-Fi or mobile data.
 */
class SyncService : Service() {
    @Volatile private var running = false
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var defaultNetwork: Network? = null
    private val retryLock = Object()
    private var retryNow = false
    private var worker: Thread? = null
    private var shownStatus: SyncState.Status? = null
    private var shownPeer: String? = null

    private val stateListener: () -> Unit = { updateNotification() }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Switched between Wi-Fi and mobile, or came back online: reconnect now.
            val previous = defaultNetwork
            defaultNetwork = network
            if (previous != null && previous != network) connection?.disconnect()
            wake()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        SyncState.set(SyncState.Status.CONNECTING)
        goForeground()
        SyncState.addListener(stateListener)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (Prefs.passphrase(this).isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESTART) {
            connection?.disconnect()
            wake()
        }
        if (!running) {
            running = true
            worker = thread(name = "clipsync-listen") { listenLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        connection?.disconnect()
        wake()
        SyncState.removeListener(stateListener)
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // already unregistered
        }
        SyncState.set(SyncState.Status.OFF)
        super.onDestroy()
    }

    private fun wake() {
        synchronized(retryLock) {
            retryNow = true
            retryLock.notifyAll()
        }
    }

    private fun listenLoop() {
        var backoff = 2_000L
        var failures = 0
        var greetedTopic: String? = null
        while (running) {
            val passphrase = Prefs.passphrase(this)
            if (passphrase.isBlank()) break
            try {
                val crypto = Crypto.get(passphrase)
                val since = Prefs.lastMessageId(this, crypto.topic)
                val url = "${Ntfy.BASE_URL}/${crypto.topic}/json" + (since?.let { "?since=$it" } ?: "")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 90_000 // ntfy sends a keepalive every 45s
                }
                connection = conn
                conn.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        SyncState.lastContact = System.currentTimeMillis()
                        val event = JSONObject(line)
                        when (event.optString("event")) {
                            "open" -> {
                                backoff = 2_000L
                                failures = 0
                                SyncState.set(SyncState.Status.CONNECTED)
                                SyncState.changed()
                                // Introduce ourselves until the other phone has answered.
                                if (Prefs.peerName(this) == null && greetedTopic != crypto.topic) {
                                    greetedTopic = crypto.topic
                                    try {
                                        Sender.sendHello(this, ack = false)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Couldn't send hello", e)
                                    }
                                }
                            }
                            "message" -> {
                                try {
                                    Incoming.handle(this, crypto, event)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Couldn't handle clip", e)
                                }
                                Prefs.setLastMessageId(this, crypto.topic, event.getString("id"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection dropped", e)
            } finally {
                connection?.disconnect()
                connection = null
            }
            if (!running) break

            failures++
            SyncState.set(
                when {
                    !hasNetwork() -> SyncState.Status.OFFLINE
                    failures >= 3 -> SyncState.Status.ERROR
                    else -> SyncState.Status.CONNECTING
                }
            )
            val woken = synchronized(retryLock) {
                if (!retryNow) {
                    try {
                        retryLock.wait(backoff)
                    } catch (e: InterruptedException) {
                        // fall through and retry
                    }
                }
                val w = retryNow
                retryNow = false
                w
            }
            backoff = if (woken) 2_000L else (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateNotification() {
        val status = SyncState.status
        val peer = Prefs.peerName(this)
        if (status == SyncState.Status.OFF || (status == shownStatus && peer == shownPeer)) return
        shownStatus = status
        shownPeer = peer
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.SERVICE_ID, Notifications.service(this))
    }

    private fun goForeground() {
        val notification = Notifications.service(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(Notifications.SERVICE_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ClipSync"
        private const val ACTION_RESTART = "au.halliday.clipsync.RESTART"

        fun start(c: Context, restart: Boolean = false) {
            val intent = Intent(c, SyncService::class.java)
            if (restart) intent.action = ACTION_RESTART
            try {
                ContextCompat.startForegroundService(c, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start service", e)
            }
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, SyncService::class.java))
        }
    }
}
