package au.halliday.clipsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
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
                description = "Keeps ClipSync listening for your other phone"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CLIPS, "Received clips", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun service(c: Context, status: String): Notification =
        NotificationCompat.Builder(c, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ClipSync is on")
            .setContentText(status)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(c))
            .build()

    fun textReceived(c: Context, from: String, text: String) {
        ensureChannels(c)
        val id = nextId.incrementAndGet()
        val trimmed = text.trim()
        val isLink = !trimmed.contains(' ') && Patterns.WEB_URL.matcher(trimmed).matches()
        val tap = if (isLink) {
            val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
            PendingIntent.getActivity(
                c, id, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            openApp(c)
        }
        val builder = NotificationCompat.Builder(c, CHANNEL_CLIPS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (isLink) "Link copied from $from" else "Copied from $from")
            .setContentText(trimmed.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(trimmed.take(1000)))
            .setAutoCancel(true)
            .setContentIntent(tap)
        if (text.length <= 50_000) {
            builder.addAction(0, "Copy again", ActionReceiver.copyText(c, id, text))
        }
        notify(c, id, builder.build())
    }

    private fun notify(c: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(c).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed; the clipboard is still updated.
        }
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
