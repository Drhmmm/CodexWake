package com.desmond.gptwake

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** The configuration screen only; recording and launching remain owned by the original service. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var enable: Button
    private lateinit var stop: Button
    private var enabling = false
    private var waitingFor = 0
    private var message: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AudioStateMonitor.install(this)
        fixWakePhrase()
        KwsEngine.keywordsThreshold = WakeWordStore.threshold(this)
        enabling = savedInstanceState?.getBoolean("enabling") ?: false
        waitingFor = savedInstanceState?.getInt("waitingFor") ?: 0
        message = savedInstanceState?.getString("message")

        val spacing = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        content.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 28f
        })
        status = TextView(this).apply {
            textSize = 20f
            setPadding(0, spacing, 0, spacing)
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            setText(R.string.codex_instructions)
            textSize = 16f
            setPadding(0, 0, 0, spacing)
        })
        enable = Button(this).apply {
            setText(R.string.codex_enable)
            setOnClickListener {
                message = null
                enabling = true
                continueEnable()
            }
        }
        stop = Button(this).apply {
            setText(R.string.codex_stop)
            setOnClickListener {
                enabling = false
                message = null
                Prefs.setListeningEnabled(this@MainActivity, false)
                stopService(Intent(this@MainActivity, WakeService::class.java))
                renderStatus()
            }
        }
        content.addView(enable)
        content.addView(stop)
        setContentView(scroll)
        scroll.requestApplyInsets()
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("enabling", enabling)
        outState.putInt("waitingFor", waitingFor)
        outState.putString("message", message)
        super.onSaveInstanceState(outState)
    }

    private fun fixWakePhrase() {
        val stored = WakeWordStore.read(this)
        if (stored.language != WakeLanguage.ZH_EN ||
            stored.phrase != WakeWordStore.DEFAULT_PHRASE ||
            stored.keywordLine != WakeWordStore.DEFAULT_LINE) {
            WakeWordStore.save(this, WakeWordStore.DEFAULT_PHRASE, WakeWordStore.DEFAULT_LINE)
            WakeService.controller()?.restartStream()
            WakeService.refresh(this)
        }
        KwsEngine.customKeywordLine = WakeWordStore.DEFAULT_LINE
    }

    private fun granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun chatGptIsAssistant() =
        Settings.Secure.getString(contentResolver, "voice_interaction_service")
            ?.let(ComponentName::unflattenFromString)?.packageName == "com.openai.chatgpt"

    private fun continueEnable() {
        if (!enabling || waitingFor != 0) return
        when {
            !granted(Manifest.permission.RECORD_AUDIO) -> {
                val permission = Manifest.permission.RECORD_AUDIO
                if (getPreferences(MODE_PRIVATE).getBoolean("asked_mic", false) &&
                    !shouldShowRequestPermissionRationale(permission)) {
                    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")), MIC_SETTINGS)
                } else {
                    waitingFor = MIC
                    getPreferences(MODE_PRIVATE).edit().putBoolean("asked_mic", true).apply()
                    requestPermissions(arrayOf(permission), MIC)
                }
            }
            Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) &&
                !getPreferences(MODE_PRIVATE).getBoolean("asked_notifications", false) -> {
                waitingFor = NOTIFICATIONS
                getPreferences(MODE_PRIVATE).edit().putBoolean("asked_notifications", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS)
            }
            !Settings.canDrawOverlays(this) -> openSettings(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), OVERLAY)
            !chatGptIsAssistant() -> openSettings(Intent("android.settings.VOICE_INPUT_SETTINGS"), ASSISTANT)
            else -> {
                enabling = false
                fixWakePhrase()
                if (WakeService.isForegroundNow()) {
                    WakeService.controller()?.restartStream()
                } else {
                    startActivity(Intent(this, ShimActivity::class.java)
                        .putExtra(ShimActivity.EXTRA_ACTION, "fgs"))
                }
            }
        }
        renderStatus()
    }

    @Suppress("DEPRECATION")
    private fun openSettings(intent: Intent, request: Int) {
        waitingFor = request
        try {
            startActivityForResult(intent, request)
        } catch (error: android.content.ActivityNotFoundException) {
            L.e("LAUNCH_SETTINGS_FAIL", error)
            waitingFor = 0
            enabling = false
            message = getString(R.string.msg_no_settings_page)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != waitingFor || requestCode !in setOf(MIC, NOTIFICATIONS)) return
        waitingFor = 0
        if (requestCode == MIC && (results.isEmpty() || results[0] != PackageManager.PERMISSION_GRANTED)) {
            enabling = false
            message = getString(R.string.codex_mic_needed)
        }
        // Notification denial does not prohibit a microphone foreground service.
        continueEnable()
        renderStatus()
    }

    @Deprecated("Framework callback used by this native Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != waitingFor) return
        waitingFor = 0
        val completed = when (requestCode) {
            MIC_SETTINGS -> granted(Manifest.permission.RECORD_AUDIO)
            OVERLAY -> Settings.canDrawOverlays(this)
            ASSISTANT -> chatGptIsAssistant()
            else -> return
        }
        if (!completed) {
            enabling = false
            message = getString(when (requestCode) {
                MIC_SETTINGS -> R.string.codex_mic_needed
                OVERLAY -> R.string.codex_overlay_needed
                else -> R.string.codex_assistant_needed
            })
        }
        continueEnable()
        renderStatus()
    }

    private fun renderStatus() {
        val state = WakeService.controller()?.state()
        status.text = message ?: getString(when {
            enabling -> R.string.codex_setup
            !WakeService.isForegroundNow() -> R.string.codex_disabled
            else -> when (state) {
                WakeController.State.KWS_LISTENING -> R.string.codex_listening
                WakeController.State.MIC_HANDOFF, WakeController.State.CHATGPT_LAUNCHING -> R.string.codex_launching
                WakeController.State.VOICE_ACTIVE -> R.string.codex_voice
                WakeController.State.EXTERNAL_COMMUNICATION -> R.string.codex_paused
                WakeController.State.ERROR -> R.string.codex_error
                WakeController.State.STOPPED -> R.string.codex_disabled
                else -> R.string.codex_starting
            }
        })
        enable.isEnabled = !enabling && (!WakeService.isForegroundNow() || state == WakeController.State.ERROR)
        stop.isEnabled = enabling || WakeService.isForegroundNow() || Prefs.listeningEnabled(this)
    }

    private companion object {
        const val MIC = 10
        const val NOTIFICATIONS = 11
        const val MIC_SETTINGS = 12
        const val OVERLAY = 13
        const val ASSISTANT = 14
    }
}
