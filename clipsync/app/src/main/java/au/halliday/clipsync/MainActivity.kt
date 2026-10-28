package au.halliday.clipsync

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import java.util.Date

/** Home: connection status, send button and recent items. */
class MainActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusIcon: ImageView
    private lateinit var statusProgress: CircularProgressIndicator
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var statusMeta: TextView
    private lateinit var statusActions: View
    private lateinit var peer: TextView
    private lateinit var sendButton: MaterialButton
    private lateinit var clear: MaterialButton
    private lateinit var historyList: LinearLayout
    private lateinit var emptyState: View
    private var newestShown = 0L

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderStatus()
            ticker.postDelayed(this, 15_000)
        }
    }
    private val stateListener: () -> Unit = { renderStatus() }
    private val historyListener: () -> Unit = { renderHistory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.setupDone(this)) {
            if (Prefs.passphrase(this).isNotBlank()) {
                Prefs.setSetupDone(this, true) // paired with an earlier version
            } else {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return
            }
        }
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        statusCard = findViewById(R.id.status_card)
        statusIcon = findViewById(R.id.status_icon)
        statusProgress = findViewById(R.id.status_progress)
        statusTitle = findViewById(R.id.status_title)
        statusBody = findViewById(R.id.status_body)
        statusMeta = findViewById(R.id.status_meta)
        statusActions = findViewById(R.id.status_actions)
        peer = findViewById(R.id.peer)
        sendButton = findViewById(R.id.send_button)
        clear = findViewById(R.id.clear)
        historyList = findViewById(R.id.history_list)
        emptyState = findViewById(R.id.empty_state)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.home)
            setOnMenuItemClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                true
            }
        }
        sendButton.setOnClickListener { v ->
            haptic(v)
            ClipboardSender.sendCurrent(this) { snackbar(it) }
        }
        clear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear recent items?")
                .setMessage("This only removes them from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> History.clear(this) }
                .show()
        }
        findViewById<MaterialButton>(R.id.retry).setOnClickListener {
            SyncService.start(this, restart = true)
        }
        findViewById<MaterialButton>(R.id.check_code).setOnClickListener {
            startActivity(Intent(this, PairingActivity::class.java))
        }
        SyncService.start(this)
    }

    override fun onStart() {
        super.onStart()
        SyncState.addListener(stateListener)
        History.addListener(historyListener)
        renderHistory()
        tick.run()
    }

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onStop() {
        SyncState.removeListener(stateListener)
        History.removeListener(historyListener)
        ticker.removeCallbacks(tick)
        super.onStop()
    }

    private fun renderStatus() {
        statusProgress.visibility = View.GONE
        statusIcon.visibility = View.VISIBLE
        statusMeta.visibility = View.GONE
        statusActions.visibility = View.GONE
        sendButton.isEnabled = true
        when (SyncState.status) {
            SyncState.Status.CONNECTED -> {
                statusStyle(R.color.success_container, R.drawable.ic_check_circle, R.color.success)
                statusTitle.text = "Connected"
                statusBody.text = "Ready to receive"
                if (SyncState.lastContact > 0) {
                    statusMeta.visibility = View.VISIBLE
                    statusMeta.text = "Last synced ${relative(SyncState.lastContact)}"
                }
            }
            SyncState.Status.OFFLINE -> {
                statusStyle(R.color.warning_container, R.drawable.ic_cloud_off, R.color.warning)
                statusTitle.text = "No connection"
                statusBody.text = "Retrying in the background…"
                sendButton.isEnabled = false
            }
            SyncState.Status.ERROR -> {
                statusStyle(R.color.error_container, R.drawable.ic_error, R.color.error)
                statusTitle.text = "Connection problem"
                statusBody.text = "Both phones need the same pairing code and an internet connection."
                statusActions.visibility = View.VISIBLE
            }
            SyncState.Status.CONNECTING, SyncState.Status.OFF -> {
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_container))
                statusIcon.visibility = View.GONE
                statusProgress.visibility = View.VISIBLE
                statusTitle.text = "Connecting…"
                statusBody.text = "This usually takes a few seconds."
            }
        }
        peer.text = Prefs.peerName(this) ?: "Waiting for your other phone"
    }

    private fun statusStyle(background: Int, iconRes: Int, tint: Int) {
        statusCard.setCardBackgroundColor(ContextCompat.getColor(this, background))
        statusIcon.setImageResource(iconRes)
        statusIcon.imageTintList = ContextCompat.getColorStateList(this, tint)
    }

    private fun renderHistory() {
        val items = History.items(this)
        val historyOff = Prefs.historyLimit(this) == 0
        historyList.removeAllViews()
        clear.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.empty_title).text = if (historyOff) "History is off" else "No items yet"
        findViewById<TextView>(R.id.empty_body).text =
            if (historyOff) "Turn it on in Settings to see recent items here."
            else "Items you send or receive will appear here."

        val previousNewest = newestShown
        items.forEach { item ->
            val row = layoutInflater.inflate(R.layout.item_history, historyList, false)
            val sent = item.direction == History.SENT
            row.findViewById<ImageView>(R.id.dir_icon).apply {
                setImageResource(if (sent) R.drawable.ic_north_east else R.drawable.ic_south_west)
                backgroundTintList = ContextCompat.getColorStateList(
                    this@MainActivity, if (sent) R.color.primary_container else R.color.success_container
                )
                imageTintList = ContextCompat.getColorStateList(this@MainActivity, if (sent) R.color.primary else R.color.success)
            }
            row.findViewById<TextView>(R.id.label).text = if (sent) "You sent" else "From ${item.from}"
            row.findViewById<TextView>(R.id.preview).text = item.text.trim().take(300)
            row.findViewById<TextView>(R.id.time).text = timeLabel(item.time)
            val url = Links.urlOf(item.text)
            if (url != null) {
                row.findViewById<ImageButton>(R.id.open).apply {
                    visibility = View.VISIBLE
                    setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            }
            row.setOnClickListener { v ->
                haptic(v)
                Clip.setText(this, item.text)
                snackbar("Copied again")
            }
            if (previousNewest != 0L && item.time > previousNewest) {
                row.alpha = 0f
                row.animate().alpha(1f).setDuration(300).start()
            }
            historyList.addView(row)
        }
        newestShown = items.firstOrNull()?.time ?: 0L
    }

    private fun relative(time: Long): String {
        val diff = System.currentTimeMillis() - time
        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            else -> DateUtils.getRelativeTimeSpanString(time).toString()
        }
    }

    private fun timeLabel(time: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = time }
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay(then, now) -> DateFormat.getTimeFormat(this).format(Date(time))
            sameDay(then, yesterday) -> "Yesterday"
            else -> DateFormat.format("d MMM", time).toString()
        }
    }

    private fun snackbar(message: String) = Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show()

    private fun haptic(v: View) {
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    companion object {
        /** True while the home screen is in front, so incoming clips skip the notification. */
        @Volatile
        var visible = false
    }
}
