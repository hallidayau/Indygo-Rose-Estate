package au.halliday.clipsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    private lateinit var rows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        rows = findViewById(R.id.rows)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        buildRows()
    }

    private fun buildRows() {
        rows.removeAllViews()
        row(R.drawable.ic_smartphone, "Device name", Prefs.deviceName(this)) { editName() }
        row(R.drawable.ic_link, "Paired device", Prefs.peerName(this) ?: "Not seen yet") {
            startActivity(Intent(this, PairingActivity::class.java))
        }
        val tile = Prefs.tileAdded(this)
        row(R.drawable.ic_swap_vert, "“Send clipboard” tile", if (tile) "Added" else "Not added", tile) {
            SystemSettings.addTile(this) { buildRows() }
        }
        val battery = SystemSettings.batteryExempt(this)
        row(R.drawable.ic_battery, "Background running", if (battery) "Allowed ✓" else "Action needed", battery) {
            SystemSettings.requestBatteryExemption(this)
        }
        val notifications = SystemSettings.notificationsAllowed(this)
        row(R.drawable.ic_notifications, "Notifications", if (notifications) "Allowed ✓" else "Not allowed", notifications) {
            SystemSettings.openNotificationSettings(this)
        }
        row(R.drawable.ic_history, "Keep history", historyLabel(Prefs.historyLimit(this))) { chooseHistory() }
        row(R.drawable.ic_delete, "Clear history", "Remove recent items from this phone") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear recent items?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> History.clear(this) }
                .show()
        }
        row(R.drawable.ic_shield, "Privacy & security", "End-to-end encrypted") { showPrivacy() }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        row(R.drawable.ic_info, "About ClipSync", "Version $version", chevron = false) {}
    }

    /** [ok] colours the subtitle: true = success, false = needs attention, null = neutral. */
    private fun row(
        icon: Int,
        title: String,
        subtitle: String,
        ok: Boolean? = null,
        chevron: Boolean = true,
        onClick: () -> Unit,
    ) {
        val view = layoutInflater.inflate(R.layout.item_setting, rows, false)
        view.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        view.findViewById<TextView>(R.id.title).text = title
        view.findViewById<TextView>(R.id.subtitle).apply {
            text = subtitle
            when (ok) {
                true -> setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.success))
                false -> setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.warning))
                null -> Unit
            }
        }
        view.findViewById<View>(R.id.chevron).visibility = if (chevron) View.VISIBLE else View.GONE
        view.setOnClickListener { onClick() }
        rows.addView(view)
    }

    private fun editName() {
        val content = layoutInflater.inflate(R.layout.view_name_input, null)
        val input = content.findViewById<TextInputEditText>(R.id.name_input)
        input.setText(Prefs.deviceName(this))
        input.setSelection(input.length())
        MaterialAlertDialogBuilder(this)
            .setTitle("Device name")
            .setMessage("This is the name your other phone sees.")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    Prefs.setDeviceName(this, name)
                    // Let the other phone know the new name.
                    SendTask.run(this, { Sender.sendHello(applicationContext, ack = false) }) { }
                    buildRows()
                }
            }
            .show()
    }

    private fun chooseHistory() {
        val values = intArrayOf(10, 5, 0)
        val labels = values.map { historyLabel(it) }.toTypedArray()
        val current = values.indexOf(Prefs.historyLimit(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Keep history")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Prefs.setHistoryLimit(this, values[which])
                History.trim(this)
                dialog.dismiss()
                buildRows()
            }
            .show()
    }

    private fun historyLabel(limit: Int) = if (limit == 0) "Don't keep history" else "$limit items"

    private fun showPrivacy() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Privacy & security")
            .setMessage(
                "End-to-end encryption\n" +
                    "Clipboard text is encrypted on this phone before it's sent. The relay server only ever sees scrambled data.\n\n" +
                    "History\n" +
                    "Recent items are stored only on this phone. You can turn history off or clear it here in Settings."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
