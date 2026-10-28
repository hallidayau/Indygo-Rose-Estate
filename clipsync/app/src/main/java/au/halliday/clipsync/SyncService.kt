package au.halliday.clipsync

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
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
    private var worker: Thread? = null
    private var lastStatus = ""

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Switched between Wi-Fi and mobile: reconnect now instead of waiting for a timeout.
            val previous = defaultNetwork
            defaultNetwork = network
            if (previous != null && previous != network) connection?.disconnect()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        goForeground("Connecting…")
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(status)
        if (Prefs.passphrase(this).isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESTART) connection?.disconnect()
        if (!running) {
            running = true
            worker = thread(name = "clipsync-listen") { listenLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        connection?.disconnect()
        worker?.interrupt()
        try {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // already unregistered
        }
        status = "Off"
        super.onDestroy()
    }

    private fun listenLoop() {
        var backoff = 2_000L
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
                        val event = JSONObject(line)
                        when (event.optString("event")) {
                            "open" -> {
                                backoff = 2_000L
                                updateStatus("Connected — ready to receive")
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
            updateStatus("No connection — retrying…")
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                break
            }
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun updateStatus(text: String) {
        status = text
        if (text == lastStatus) return
        lastStatus = text
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.SERVICE_ID, Notifications.service(this, text))
    }

    private fun goForeground(text: String) {
        val notification = Notifications.service(this, text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(Notifications.SERVICE_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ClipSync"
        private const val ACTION_RESTART = "au.halliday.clipsync.RESTART"

        @Volatile
        var status = "Connecting…"
            private set

        fun start(c: Context, restart: Boolean = false) {
            val intent = Intent(c, SyncService::class.java)
            if (restart) intent.action = ACTION_RESTART
            try {
                ContextCompat.startForegroundService(c, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't start service", e)
            }
        }
    }
}
