package app.noscroll.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.lang.ref.WeakReference
import java.util.ArrayDeque

/**
 * Local deterministic detection. Reads resource IDs, visibility, selection and bounds
 * only inside the two target apps. Never reads node text/contentDescription, takes a
 * screenshot, performs gestures, or makes a network request.
 */
class FeedBlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val policy = ShieldPolicy()
    private lateinit var settings: BlockerSettings
    private lateinit var shield: FeedShield
    private var scanQueued = false
    private var previewUntil = 0L
    var runtimeError: String? = null
        private set
    private val scan = Runnable {
        scanQueued = false
        inspectForeground()
    }

    override fun onServiceConnected() {
        cleanup()
        settings = BlockerSettings(this)
        shield = FeedShield(this, ::leaveFeed)
        settings.preferences.registerOnSharedPreferenceChangeListener(this)
        runtimeError = null
        reference = WeakReference(this)
        schedule(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::shield.isInitialized) return
        // Track app/window exits globally, but ignore other apps' routine content
        // events. Reinspect actual windows, not event.source: our overlay also emits events.
        val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        val targetChanged = Platform.fromPackage(event.packageName?.toString()) != null
        if (windowChanged || targetChanged || shield.isVisible) schedule(70)
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if (key == "last_blocked") return
        previewUntil = 0L
        policy.clear()
        shield.hide()
        handler.removeCallbacks(scan)
        scanQueued = false
        schedule(0)
    }

    private fun schedule(delay: Long) {
        if (!scanQueued) {
            scanQueued = true
            handler.postDelayed(scan, delay)
        }
    }

    private fun inspectForeground() {
        if (!::shield.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        val enabled = settings.enabledPlatforms()
        if (enabled.isEmpty()) {
            previewUntil = 0L
            policy.clear()
            shield.hide()
            return
        }
        try {
            val root = foregroundRoot()
            if (root == null) {
                // Never strand an overlay over an unreadable system/permission window.
                previewUntil = 0L
                policy.clear()
                shield.hide()
                return
            }
            try {
                val foregroundPackage = root.packageName?.toString()
                if (previewUntil > now && foregroundPackage == packageName) {
                    if (!shield.show(null, preview = true)) runtimeError = "Cannot display the shield. Re-enable the accessibility service."
                    schedule(250)
                    return
                }
                previewUntil = 0L
                val platform = Platform.fromPackage(foregroundPackage)
                val match = if (platform in enabled && foregroundPackage != null) {
                    FeedDetector.detect(foregroundPackage, collectSignals(root))
                } else null
                val decision = policy.update(foregroundPackage, match, enabled, now)
                if (decision == null) {
                    shield.hide()
                } else if (shield.show(decision.platform.feedName)) {
                    runtimeError = null
                    settings.recordBlocked(decision)
                    // Poll only while shielded so Back/Home/permission changes cannot
                    // leave a stale shield when a target app stops sending events.
                    schedule(250)
                } else {
                    runtimeError = "Cannot display the shield. Re-enable the accessibility service."
                }
            } finally {
                recycle(root)
            }
        } catch (_: RuntimeException) {
            policy.clear()
            shield.hide()
            runtimeError = "Screen inspection stopped. Reopen the app or re-enable accessibility."
        }
    }

    /** Ignore our accessibility overlay and the keyboard when finding the underlying app. */
    @Suppress("DEPRECATION")
    private fun foregroundRoot(): AccessibilityNodeInfo? {
        val visibleWindows = windows.orEmpty()
        try {
            val candidates = visibleWindows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.type == AccessibilityWindowInfo.TYPE_SYSTEM
            }.sortedByDescending { it.layer }
            val foreground = candidates.firstOrNull { it.isFocused }
                ?: candidates.firstOrNull { it.isActive }
                ?: candidates.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            // A focused system window takes precedence, even if its root is unavailable.
            return if (foreground != null) foreground.root else rootInActiveWindow
        } finally {
            visibleWindows.forEach { it.recycle() }
        }
    }

    private fun collectSignals(root: AccessibilityNodeInfo): List<ViewSignal> {
        val frame = Rect().also(root::getBoundsInScreen)
        val width = frame.width().coerceAtLeast(1).toFloat()
        val height = frame.height().coerceAtLeast(1).toFloat()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val signals = ArrayList<ViewSignal>()
        var visited = 0
        val startedAt = SystemClock.elapsedRealtime()
        fun enqueueChildren(node: AccessibilityNodeInfo) {
            for (index in 0 until node.childCount.coerceAtMost(80)) {
                if (queue.size >= 800) break
                node.getChild(index)?.let(queue::addLast)
            }
        }
        fun record(node: AccessibilityNodeInfo) {
            val id = node.viewIdResourceName ?: return
            val bounds = Rect().also(node::getBoundsInScreen)
            signals += ViewSignal(id, node.isVisibleToUser, node.isSelected,
                bounds.width() / width, bounds.height() / height)
        }
        // Root belongs to caller. Child nodes are owned and released here.
        try {
            record(root)
            enqueueChildren(root)
            while (queue.isNotEmpty() && visited < 800 && SystemClock.elapsedRealtime() - startedAt < 40) {
                val node = queue.removeFirst()
                try {
                    record(node)
                    if (node.isVisibleToUser) enqueueChildren(node)
                } finally {
                    recycle(node)
                }
                visited++
            }
        } finally {
            while (queue.isNotEmpty()) recycle(queue.removeFirst())
        }
        return signals
    }

    fun preview(): Boolean {
        if (!::shield.isInitialized || settings.enabledPlatforms().isEmpty()) return false
        previewUntil = SystemClock.elapsedRealtime() + 10_000L
        schedule(0)
        return true
    }

    private fun leaveFeed() {
        if (previewUntil > 0) {
            previewUntil = 0
            shield.hide()
            return
        }
        // One user-requested Back action. Never an automatic loop or gesture injection.
        if (!performGlobalAction(GLOBAL_ACTION_BACK)) shield.backFailed()
        schedule(120)
    }

    override fun onInterrupt() {
        previewUntil = 0
        policy.clear()
        if (::shield.isInitialized) shield.hide()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        scanQueued = false
        previewUntil = 0L
        policy.clear()
        if (::shield.isInitialized) shield.hide()
        if (::settings.isInitialized) settings.preferences.unregisterOnSharedPreferenceChangeListener(this)
        if (reference?.get() === this) reference = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) = node.recycle()

    companion object {
        private var reference: WeakReference<FeedBlockerService>? = null
        val connected: FeedBlockerService? get() = reference?.get()
    }
}
