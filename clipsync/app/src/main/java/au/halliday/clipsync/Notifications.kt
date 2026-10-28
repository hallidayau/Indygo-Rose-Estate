package au.halliday.clipsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

object Notifications {
    const val SERVICE_ID = 1
    private const val CHANNEL_SERVICE = "service"
    private const val CHANNEL_CLIPS = "clips"
    private val nextId = AtomicInteger(100)

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Connection", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps ClipSync listening for your other devices"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CLIPS, "Received clips", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** The silent ongoing notification, worded for the current connection state. */
    fun service(c: Context): Notification {
        val (title, text) = when (SyncState.status) {
            SyncState.Status.CONNECTED ->
                "ClipSync is on" to (Prefs.peerName(c)?.let { "Connected to $it" } ?: "Connected")
            SyncState.Status.OFFLINE, SyncState.Status.ERROR ->
                "ClipSync is offline" to "We'll reconnect automatically."
            else -> "ClipSync is on" to "Reconnecting…"
        }
        return NotificationCompat.Builder(c, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(c))
            .addAction(0, "Send clipboard", sendClipboard(c))
            .build()
    }

    fun textReceived(c: Context, from: String, text: String) {
        ensureChannels(c)
        val id = nextId.incrementAndGet()
        val trimmed = text.trim()
        val url = Links.urlOf(trimmed)
        val builder = NotificationCompat.Builder(c, CHANNEL_CLIPS)
            .setSmallIcon(R.drawable.ic_stat)
            .setAutoCancel(true)
        if (url != null) {
            val open = PendingIntent.getActivity(
                c, id, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentTitle("Link copied from $from")
                .setContentText(Links.host(url))
                .setContentIntent(open)
                .addAction(0, "Open link", open)
        } else {
            builder.setContentTitle("Copied from $from")
                .setContentText(trimmed.take(200))
                .setStyle(NotificationCompat.BigTextStyle().bigText(trimmed.take(1000)))
                .setContentIntent(openApp(c))
        }
        if (text.length <= 50_000) {
            builder.addAction(0, "Copy again", ActionReceiver.copyText(c, id, text))
        }
        try {
            NotificationManagerCompat.from(c).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Notifications not allowed; the clipboard is still updated.
        }
    }

    private fun sendClipboard(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 1, Intent(c, SendClipboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
