package app.noscroll

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import app.noscroll.blocker.BlockerSettings
import app.noscroll.blocker.FeedBlockerService
import app.noscroll.blocker.Platform
import app.noscroll.ui.NativeViews
import app.noscroll.ui.NativeViews.dp
import app.noscroll.ui.NativeViews.gap
import app.noscroll.ui.NativeViews.rule

class MainActivity : AppCompatActivity() {
    private lateinit var settings: BlockerSettings
    private lateinit var state: TextView
    private lateinit var detail: TextView
    private lateinit var lastBlocked: TextView
    private lateinit var primary: Button
    private lateinit var preview: Button
    private lateinit var accessibility: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            renderState()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = BlockerSettings(this)
        window.statusBarColor = NativeViews.background
        window.navigationBarColor = NativeViews.background
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
        }
        body.addView(NativeViews.label(this, "NoScroll", 32f, bold = true))
        body.gap(8)
        body.addView(NativeViews.label(this, "Block Reels and Shorts in the real apps.", 17f, NativeViews.muted))
        body.gap(36)
        state = NativeViews.label(this, "Checking accessibility…", 21f, bold = true)
        body.addView(state)
        body.gap(8)
        detail = NativeViews.label(this, "", 15f, NativeViews.muted)
        body.addView(detail)
        body.gap(20)
        primary = NativeViews.button(this, "Enable blocking", true) {
            if (BlockerSettings.accessibilityEnabled(this) && FeedBlockerService.connected != null) {
                settings.paused = !settings.paused
                renderState()
            } else discloseAndOpenSettings()
        }
        body.addView(primary, buttonLayout())
        body.gap(32)
        body.rule()
        body.addView(platformRow(Platform.YOUTUBE, "YouTube", "Shorts viewer"))
        body.rule()
        body.addView(platformRow(Platform.INSTAGRAM, "Instagram", "Reels viewer"))
        body.rule()
        body.gap(24)
        body.addView(NativeViews.label(this,
            "Normal videos, messages and the rest of each app are not targets.", 14f, NativeViews.muted))
        body.gap(24)
        preview = NativeViews.button(this, "Preview blocking screen", false) {
            if (FeedBlockerService.connected?.preview() != true) {
                Toast.makeText(this, "Enable accessibility and select a feed first.", Toast.LENGTH_SHORT).show()
            }
        }
        body.addView(preview, buttonLayout())
        body.gap(10)
        accessibility = NativeViews.button(this, "NoScroll accessibility settings", false, ::openAccessibility)
        body.addView(accessibility, buttonLayout())
        body.gap(24)
        lastBlocked = NativeViews.label(this, "", 13f, NativeViews.muted)
        body.addView(lastBlocked)
        body.gap(28)
        body.addView(NativeViews.label(this,
            "First build: detection needs a real-app test on your phone. Unrecognised layouts are not blocked.",
            13f, NativeViews.warning))
        body.gap(12)
        body.addView(NativeViews.label(this,
            "On-device only. No internet permission, screen recording or message-text collection. The shield does not mute playback; use Go back to leave a feed.",
            13f, NativeViews.muted))
        setContentView(ScrollView(this).apply {
            setBackgroundColor(NativeViews.background)
            isFillViewport = true
            addView(body)
        })
    }

    private fun buttonLayout() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54))

    private fun platformRow(platform: Platform, name: String, description: String): LinearLayout {
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(NativeViews.label(this@MainActivity, name, 18f, bold = true))
            gap(3)
            addView(NativeViews.label(this@MainActivity, description, 14f, NativeViews.muted))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(88)
            addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(SwitchCompat(this@MainActivity).apply {
                contentDescription = "Block ${platform.feedName}"
                isChecked = settings.enabled(platform)
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(NativeViews.accent, NativeViews.muted),
                )
                setOnCheckedChangeListener { _, checked ->
                    settings.setEnabled(platform, checked)
                    renderState()
                }
            })
        }
    }

    private fun renderState() {
        val authorized = BlockerSettings.accessibilityEnabled(this)
        val service = FeedBlockerService.connected
        val enabled = settings.enabledPlatforms()
        val error = service?.runtimeError
        when {
            !authorized -> {
                state.text = "Accessibility is off"
                detail.text = "Open NoScroll's accessibility page and turn on the service."
            }
            service == null -> {
                state.text = "Waiting for accessibility"
                detail.text = "If this persists, switch NoScroll off and on in Accessibility settings."
            }
            error != null -> {
                state.text = "Blocking needs attention"
                detail.text = error
            }
            settings.paused -> {
                state.text = "Blocking paused"
                detail.text = "No feeds will be covered until you resume."
            }
            enabled.isEmpty() -> {
                state.text = "No feeds selected"
                detail.text = "Turn on Shorts or Reels below."
            }
            else -> {
                state.text = "Ready to detect"
                detail.text = "A detected Reels or Shorts viewer will be covered. Other screens stay usable."
            }
        }
        state.setTextColor(if (authorized && service != null && error == null && enabled.isNotEmpty()) NativeViews.accent else NativeViews.text)
        primary.text = if (!authorized) "Enable blocking"
            else if (service == null) "Reconnect accessibility"
            else if (settings.paused) "Resume blocking" else "Pause blocking"
        preview.isEnabled = authorized && service != null && enabled.isNotEmpty()
        preview.alpha = if (preview.isEnabled) 1f else 0.45f
        accessibility.visibility = if (authorized) View.VISIBLE else View.GONE
        lastBlocked.text = settings.lastBlocked?.let { "Last blocked: $it" } ?: "No feed blocked yet."
    }

    private fun discloseAndOpenSettings() {
        AlertDialog.Builder(this)
            .setTitle("Allow NoScroll accessibility?")
            .setMessage("Android grants this service broad access to your interface. NoScroll uses it to inspect element IDs, layout bounds and selection state inside Instagram and YouTube, then cover detected Reels and Shorts.\n\nIt does not read message text, record the screen or send data anywhere. The Go back button requests one Android Back action. You can pause blocking here or turn the service off in Settings at any time.\n\nOn Samsung, Android may require App info → ⋮ → Allow restricted settings before this sideloaded service can be enabled. Only do this if you trust this build.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open NoScroll settings") { _, _ -> openAccessibility() }
            .show()
    }

    private fun openAccessibility() {
        val component = ComponentName(this, FeedBlockerService::class.java).flattenToString()
        val highlightKey = ":settings:fragment_args_key"
        val highlightArgs = Bundle().apply { putString(highlightKey, component) }
        val destinations = listOf(
            // System/OEM intent, not a public SDK constant. AOSP consumes a flattened
            // String under Intent.EXTRA_COMPONENT_NAME, not a ComponentName Parcelable.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component),
            // Settings implementations may ignore these optional highlight hints.
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(highlightKey, component)
                .putExtra(":settings:show_fragment_args", highlightArgs),
        )
        for (destination in destinations) {
            try {
                startActivity(destination)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the general list if this OEM does not expose the details page.
            } catch (_: SecurityException) {
                // Some OEMs expose the details action but restrict third-party callers.
            }
        }
        Toast.makeText(this, "Open Settings → Accessibility → Installed apps → NoScroll.", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        refresh.run()
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }
}
