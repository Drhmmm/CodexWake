package com.desmond.gptwake

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 34, 36], qualifiers = "en-rUS-w360dp-h740dp-mdpi")
class MainActivityTest {
    @Test fun diagnosticStatusRefreshesTheMicrophoneReading() {
        // A frozen/default reading would hide the exact failure being investigated on the phone.
        val rms = AudioProbe::class.java.getDeclaredField("lastRms").apply { isAccessible = true }
        val recording = AudioProbe::class.java.getDeclaredField("RECORDING").apply { isAccessible = true }
            .get(null) as java.util.concurrent.atomic.AtomicBoolean
        val running = AudioProbe::class.java.getDeclaredField("RUNNING").apply { isAccessible = true }
            .get(null) as java.util.concurrent.atomic.AtomicBoolean
        try {
            recording.set(true)
            running.set(true)
            Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
                val activity = lifecycle.get()
                val details = activity.window.decorView.findViewWithTag<TextView>("diagnostics")
                assertNotNull("The phone needs a visible microphone reading", details)
                rms.setDouble(null, 1234.0)
                shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(750))
                assertTrue(details.text.toString().contains("1234"))
                rms.setDouble(null, 0.0)
                shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(750))
                assertFalse(details.text.toString().contains("1234"))
            }
        } finally {
            rms.setDouble(null, 0.0)
            recording.set(false)
            running.set(false)
        }
    }

    @Test fun tappingDiagnosticsCopiesTheActualFailureForPhoneOnlyTroubleshooting() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            L.e("DIRECT_LAUNCH_FAIL", IllegalStateException("test launch failure"))
            val details = activity.window.decorView.findViewWithTag<TextView>("diagnostics")
            assertNotNull("Diagnostics must be accessible without USB", details)
            details.performClick()
            val copied = activity.getSystemService(ClipboardManager::class.java)
                .primaryClip!!.getItemAt(0).text.toString()
            assertTrue(copied.contains("DIRECT_LAUNCH_FAIL"))
            assertTrue(copied.contains("test launch failure"))
            assertTrue(copied.contains("Hey Codex"))
        }
    }

    @Test fun openingAppWaitsForEnableBeforeRequestingPermissions() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            assertNotNull(button(activity.window.decorView, "Enable Codex Wake"))
            assertNotNull(button(activity.window.decorView, "Stop listening"))
            assertNull(shadowOf(activity).lastRequestedPermission)
            assertFalse(Prefs.listeningEnabled(activity))
        }
    }

    @Test fun enableRequestsMicrophoneBeforeStartingService() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            assertArrayEquals(arrayOf(Manifest.permission.RECORD_AUDIO),
                shadowOf(activity).lastRequestedPermission.requestedPermissions)
            assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",
                shadowOf(activity).nextStartedActivity.action)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test fun enableUsesExistingShimWithFixedPhraseOnceSetupIsComplete() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS)
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(app.contentResolver, "voice_interaction_service",
            "com.openai.chatgpt/.Assistant")
        WakeWordStore.save(app, "old phrase", "old tokens")
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(ShimActivity::class.java.name, intent.component!!.className)
            assertEquals("fgs", intent.getStringExtra(ShimActivity.EXTRA_ACTION))
            val selection = WakeWordStore.read(app.createDeviceProtectedStorageContext())
            assertEquals("Hey Codex", selection.phrase)
            assertEquals(WakeLanguage.ZH_EN, selection.language)
            assertEquals("HH EY1 K OW1 D EH0 K S @Hey_Codex", selection.keywordLine)
        }
    }

    @Test fun stopDisablesListeningAfterReboot() {
        val app = RuntimeEnvironment.getApplication()
        Prefs.setListeningEnabled(app, true)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Stop listening")!!.performClick()
            assertFalse(Prefs.listeningEnabled(app.createDeviceProtectedStorageContext()))
            assertEquals(WakeService::class.java.name,
                shadowOf(activity).nextStoppedService.component!!.className)
        }
    }

    @Test fun microphoneDenialDoesNotStartListeningOrRepeatThePrompt() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            val request = shadowOf(activity).lastRequestedPermission
            assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",
                shadowOf(activity).nextStartedActivity.action)
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED))
            assertNull(shadowOf(activity).nextStartedActivity)
            assertFalse(Prefs.listeningEnabled(activity))
            assertTrue(button(activity.window.decorView, "Enable Codex Wake")!!.isEnabled)
        }
    }

    @Test fun overlayIsRequestedBeforeTheServiceStarts() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                shadowOf(activity).nextStartedActivity.action)
            assertFalse(Prefs.listeningEnabled(activity))
        }
    }

    @Test fun anotherPackageCannotPassChatGptAssistantSetup() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS)
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(app.contentResolver, "voice_interaction_service",
            "com.openai.chatgpt.other/.Assistant")
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            assertEquals("android.settings.VOICE_INPUT_SETTINGS",
                shadowOf(activity).nextStartedActivity.action)
            assertFalse(Prefs.listeningEnabled(activity))
        }
    }

    @Test @Config(sdk = [34, 36])
    fun notificationDenialStillAllowsTheForegroundListener() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Secure.putString(app.contentResolver, "voice_interaction_service",
            "com.openai.chatgpt/.Assistant")
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            val request = shadowOf(activity).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions)
            assertEquals("android.content.pm.action.REQUEST_PERMISSIONS",
                shadowOf(activity).nextStartedActivity.action)
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED))
            assertEquals(ShimActivity::class.java.name,
                shadowOf(activity).nextStartedActivity.component!!.className)
        }
    }

    @Test fun returningWithoutOverlayAccessLeavesEnableAvailable() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS)
        Robolectric.buildActivity(MainActivity::class.java).setup().use { lifecycle ->
            val activity = lifecycle.get()
            button(activity.window.decorView, "Enable Codex Wake")!!.performClick()
            val request = shadowOf(activity).nextStartedActivityForResult
            assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, request.intent.action)
            shadowOf(activity).receiveResult(request.intent, android.app.Activity.RESULT_CANCELED, null)
            assertTrue(button(activity.window.decorView, "Enable Codex Wake")!!.isEnabled)
            assertFalse(Prefs.listeningEnabled(activity))
        }
    }

    private fun button(view: View, text: String): Button? {
        if (view is Button && view.text.toString().equals(text, ignoreCase = true)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            button(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }
}
