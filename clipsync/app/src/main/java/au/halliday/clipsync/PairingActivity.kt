package au.halliday.clipsync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PairingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
        val root = findViewById<View>(R.id.root)
        val code = Prefs.passphrase(this)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.this_device).text = Prefs.deviceName(this)
        findViewById<TextView>(R.id.peer_device).text = Prefs.peerName(this) ?: "Not seen yet"
        findViewById<TextView>(R.id.code).text = code

        findViewById<MaterialButton>(R.id.copy_code).setOnClickListener {
            Clip.setText(this, code)
            Snackbar.make(root, "Code copied", Snackbar.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.text_code).setOnClickListener { PairingCode.sendByText(this, code) }
        findViewById<MaterialButton>(R.id.disconnect).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Disconnect devices?")
                .setMessage("This phone will stop syncing until you pair it again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect") { _, _ -> disconnect() }
                .show()
        }
    }

    private fun disconnect() {
        SyncService.stop(this)
        Prefs.setPassphrase(this, "")
        Prefs.setPeerName(this, null)
        Prefs.setSetupDone(this, false)
        startActivity(
            Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
