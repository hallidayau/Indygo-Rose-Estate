package au.halliday.clipsync

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var code: EditText
    private lateinit var battery: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        code = findViewById(R.id.code)
        battery = findViewById(R.id.battery)

        code.setText(Prefs.passphrase(this))
        findViewById<Button>(R.id.generate).setOnClickListener { code.setText(Prefs.generateCode()) }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        findViewById<Button>(R.id.send).setOnClickListener { ClipboardSender.sendCurrent(this) }
        findViewById<Button>(R.id.tile).setOnClickListener { addTile() }
        battery.setOnClickListener { requestBatteryExemption() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Prefs.passphrase(this).isNotBlank()) SyncService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun save() {
        // Uppercase so typing the code in lower case on the other phone still matches.
        val value = code.text.toString().trim().uppercase(Locale.ROOT)
        if (value.length < 8) {
            SendTask.toast(this, "Use at least 8 characters, or tap Generate code")
            return
        }
        Prefs.setPassphrase(this, value)
        code.setText(value)
        SyncService.start(this, restart = true)
        SendTask.toast(this, "Saved. Enter the same code on your other phone.")
        refresh()
    }

    private fun refresh() {
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        battery.text = if (exempt) "Background running: allowed ✓" else "Allow running in background (recommended)"
        status.text = if (Prefs.passphrase(this).isBlank()) {
            "Not set up yet. Tap Generate code, then Save & connect, and enter the same code on your other phone."
        } else {
            "Status: ${SyncService.status}"
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, ClipTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_stat),
                mainExecutor,
            ) { }
        } else {
            SendTask.toast(this, "Swipe down twice, tap the pencil icon, and drag 'Send clipboard' into your tiles")
        }
    }
}
