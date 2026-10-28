package au.halliday.clipsync

import android.annotation.SuppressLint
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Android permissions and system screens ClipSync needs. */
object SystemSettings {
    fun batteryExempt(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    @SuppressLint("BatteryLife")
    fun requestBatteryExemption(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
            )
        } catch (e: Exception) {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun notificationsAllowed(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    fun openNotificationSettings(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        )
    }

    fun addTile(activity: Activity, done: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(activity, ClipTileService::class.java),
                activity.getString(R.string.tile_label),
                Icon.createWithResource(activity, R.drawable.ic_tile),
                activity.mainExecutor,
            ) { result ->
                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    Prefs.setTileAdded(activity, true)
                }
                done()
            }
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Add the tile")
                .setMessage("Swipe down twice from the top of the screen, tap the pencil (edit) icon, then drag “Send clipboard” into your tiles.")
                .setPositiveButton("OK") { _, _ -> done() }
                .show()
        }
    }
}
