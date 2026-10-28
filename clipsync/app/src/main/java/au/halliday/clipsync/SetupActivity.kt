package au.halliday.clipsync

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** First-run flow: Welcome → Pair → Notifications → Background → Tile → Done. */
class SetupActivity : AppCompatActivity() {
    private enum class Step { WELCOME, PAIR, NOTIFY, BACKGROUND, TILE, DONE }

    private var step = Step.WELCOME
    private var enterMode = false
    private var generatedCode = ""
    private var waitingForBattery = false

    private lateinit var toolbar: MaterialToolbar
    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var extra: FrameLayout
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private lateinit var tertiary: MaterialButton
    private lateinit var progress: TextView

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { advance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        toolbar = findViewById(R.id.toolbar)
        icon = findViewById(R.id.step_icon)
        title = findViewById(R.id.step_title)
        body = findViewById(R.id.step_body)
        extra = findViewById(R.id.step_extra)
        primary = findViewById(R.id.primary)
        secondary = findViewById(R.id.secondary)
        tertiary = findViewById(R.id.tertiary)
        progress = findViewById(R.id.step_progress)

        step = savedInstanceState?.getString("step")?.let { Step.valueOf(it) } ?: Step.WELCOME
        enterMode = savedInstanceState?.getBoolean("enter") ?: false
        generatedCode = savedInstanceState?.getString("code") ?: PairingCode.generate()

        toolbar.setNavigationOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step == Step.WELCOME || step == Step.DONE) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    goBack()
                }
            }
        })
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("step", step.name)
        outState.putBoolean("enter", enterMode)
        outState.putString("code", generatedCode)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForBattery && step == Step.BACKGROUND) {
            waitingForBattery = false
            if (SystemSettings.batteryExempt(this)) {
                render()
                primary.postDelayed({ if (step == Step.BACKGROUND) advance() }, 800)
            }
        }
    }

    private fun advance() {
        if (step != Step.DONE) step = Step.values()[step.ordinal + 1]
        render()
    }

    private fun goBack() {
        if (step != Step.WELCOME) step = Step.values()[step.ordinal - 1]
        render()
    }

    private fun render() {
        extra.removeAllViews()
        extra.visibility = View.GONE
        toolbar.navigationIcon =
            if (step == Step.WELCOME || step == Step.DONE) null else ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
        title.setTextAppearance(R.style.TextAppearance_ClipSync_Headline)
        setIcon(0)
        tertiary.visibility = View.GONE
        secondary.visibility = View.VISIBLE
        primary.isEnabled = true
        progress.text = ""

        when (step) {
            Step.WELCOME -> {
                icon.background = null
                icon.imageTintList = null
                icon.setPadding(0, 0, 0, 0)
                icon.setImageResource(R.mipmap.ic_launcher)
                title.setTextAppearance(R.style.TextAppearance_ClipSync_Display)
                title.text = getString(R.string.app_name)
                body.text = "Text and links between your phones."
                val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                feature(list, R.drawable.ic_lock, "End-to-end encrypted", "Only your devices can read it.")
                feature(list, R.drawable.ic_bolt, "Fast", "Usually arrives in 1–2 seconds.")
                feature(list, R.drawable.ic_devices, "Works anywhere", "Wi-Fi or mobile data.")
                extra.visibility = View.VISIBLE
                extra.addView(list)
                primary.text = "Get started"
                primary.setOnClickListener { advance() }
                secondary.visibility = View.GONE
            }

            Step.PAIR -> {
                setIcon(R.drawable.ic_devices)
                title.text = "Set up your devices"
                body.text = "Use the same pairing code on both of your phones."
                progress.text = "Step 1 of 4"
                if (enterMode) renderEnterCode() else renderGenerateCode()
            }

            Step.NOTIFY -> {
                setIcon(R.drawable.ic_notifications)
                title.text = "Allow notifications"
                body.text = "We'll notify you when text or links arrive from your other phone."
                progress.text = "Step 2 of 4"
                val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                primary.text = if (needsPermission) "Allow notifications" else "Continue"
                primary.setOnClickListener {
                    if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else advance()
                }
                secondary.text = "Not now"
                secondary.setOnClickListener { advance() }
                secondary.visibility = if (needsPermission) View.VISIBLE else View.GONE
            }

            Step.BACKGROUND -> {
                setIcon(R.drawable.ic_battery)
                title.text = "Allow background running"
                progress.text = "Step 3 of 4"
                if (SystemSettings.batteryExempt(this)) {
                    body.text = "Background running allowed ✓"
                    primary.text = "Continue"
                    primary.setOnClickListener { advance() }
                    secondary.visibility = View.GONE
                } else {
                    body.text = "Keeps ClipSync working so you can receive text even when the app isn't open."
                    primary.text = "Allow background running"
                    primary.setOnClickListener {
                        waitingForBattery = true
                        SystemSettings.requestBatteryExemption(this)
                    }
                    secondary.text = "Not now"
                    secondary.setOnClickListener { advance() }
                }
            }

            Step.TILE -> {
                setIcon(R.drawable.ic_swap_vert)
                title.text = "Add “Send clipboard”"
                body.text = "Send what's on your clipboard without opening ClipSync."
                progress.text = "Step 4 of 4"
                showExtra(R.layout.view_tile_preview)
                primary.text = "Add tile"
                primary.setOnClickListener { SystemSettings.addTile(this) { advance() } }
                secondary.text = "Not now"
                secondary.setOnClickListener { advance() }
            }

            Step.DONE -> {
                setIcon(R.drawable.ic_check_circle, success = true)
                title.text = "All set"
                body.text = "ClipSync is ready to sync between your phones."
                primary.text = "Go to ClipSync"
                primary.setOnClickListener {
                    Prefs.setSetupDone(this, true)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                secondary.visibility = View.GONE
            }
        }
    }

    private fun renderGenerateCode() {
        val view = showExtra(R.layout.view_pair_generate)
        val codeView = view.findViewById<TextView>(R.id.code)
        codeView.text = generatedCode
        view.findViewById<MaterialButton>(R.id.regenerate).setOnClickListener {
            generatedCode = PairingCode.generate()
            codeView.text = generatedCode
        }
        view.findViewById<MaterialButton>(R.id.copy).setOnClickListener {
            Clip.setText(this, generatedCode)
            SendTask.toast(this, "Code copied")
        }
        primary.text = "Save & connect"
        primary.setOnClickListener { savePairing(generatedCode) }
        tertiary.visibility = View.VISIBLE
        tertiary.setOnClickListener { PairingCode.sendByText(this, generatedCode) }
        secondary.text = "Enter code instead"
        secondary.setOnClickListener {
            enterMode = true
            render()
        }
    }

    private fun renderEnterCode() {
        val layout = showExtra(R.layout.view_pair_enter) as TextInputLayout
        val input = layout.findViewById<TextInputEditText>(R.id.code_input)
        input.addTextChangedListener(object : TextWatcher {
            private var editing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (editing) return
                layout.error = null
                val formatted = PairingCode.formatPartial(s.toString())
                if (formatted != s.toString()) {
                    editing = true
                    s.replace(0, s.length, formatted)
                    editing = false
                }
            }
        })
        layout.setEndIconOnClickListener {
            val pasted = getSystemService(ClipboardManager::class.java).primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (pasted.isNullOrBlank()) {
                SendTask.toast(this, "Nothing to paste")
            } else {
                input.setText(pasted)
                input.setSelection(input.length())
            }
        }
        primary.text = "Save & connect"
        primary.setOnClickListener {
            val code = PairingCode.normalize(input.text.toString())
            if (code == null) layout.error = "Check the code and try again." else savePairing(code)
        }
        secondary.text = "Generate a code instead"
        secondary.setOnClickListener {
            enterMode = false
            render()
        }
    }

    private fun savePairing(code: String) {
        if (code != Prefs.passphrase(this)) {
            Prefs.setPassphrase(this, code)
            Prefs.setPeerName(this, null)
        }
        SyncService.start(this, restart = true)
        advance()
    }

    private fun setIcon(res: Int, success: Boolean = false) {
        icon.setBackgroundResource(R.drawable.bg_circle)
        val pad = (18 * resources.displayMetrics.density).toInt()
        icon.setPadding(pad, pad, pad, pad)
        icon.backgroundTintList = ContextCompat.getColorStateList(
            this, if (success) R.color.success_container else R.color.primary_container
        )
        icon.imageTintList = ContextCompat.getColorStateList(this, if (success) R.color.success else R.color.primary)
        if (res != 0) icon.setImageResource(res)
    }

    private fun showExtra(layout: Int): View {
        extra.visibility = View.VISIBLE
        return layoutInflater.inflate(layout, extra, false).also { extra.addView(it) }
    }

    private fun feature(parent: LinearLayout, iconRes: Int, title: String, subtitle: String) {
        val row = layoutInflater.inflate(R.layout.item_feature, parent, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title).text = title
        row.findViewById<TextView>(R.id.subtitle).text = subtitle
        parent.addView(row)
    }
}
