package app.noscroll.blocker

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import app.noscroll.ui.NativeViews
import app.noscroll.ui.NativeViews.dp
import app.noscroll.ui.NativeViews.gap

/** One opaque, touchable accessibility window. Never SYSTEM_ALERT_WINDOW. */
class FeedShield(private val context: Context, private val leave: () -> Unit) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var detail: TextView? = null
    private var shownKey: String? = null

    val isVisible: Boolean get() = root != null

    fun show(feed: String?, preview: Boolean = false): Boolean {
        val key = if (preview) "preview" else feed
        if (root != null && shownKey == key) return true
        hide()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(32), context.dp(48), context.dp(32), context.dp(48))
            setBackgroundColor(NativeViews.background)
            isClickable = true // Empty space consumes touches; no swipes reach the app underneath.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        content.addView(NativeViews.label(context, "NoScroll", 16f, NativeViews.accent))
        content.gap(24)
        content.addView(NativeViews.label(context,
            if (preview) "This is the shield." else "${feed ?: "Short videos"} blocked.", 32f, bold = true))
        content.gap(16)
        detail = NativeViews.label(context,
            if (preview) "Preview only. Feed detection still needs a test in the real apps."
            else "The feed underneath is covered and cannot be touched. Go back to leave it. Audio may continue until you leave.",
            16f, NativeViews.muted)
        content.addView(detail)
        content.gap(32)
        content.addView(NativeViews.button(context, if (preview) "Dismiss preview" else "Go back", true, leave),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(56)))
        content.gap(16)
        content.addView(NativeViews.label(context,
            "Home and system controls remain available.", 13f, NativeViews.muted))

        val parameters = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "NoScroll feed shield"
        }
        return try {
            windows.addView(content, parameters)
            root = content
            shownKey = key
            true
        } catch (_: RuntimeException) {
            // A revoked service must not crash or claim protection succeeded.
            root = null
            detail = null
            shownKey = null
            false
        }
    }

    fun backFailed() {
        detail?.text = "Android could not go back. Use Home to leave this app."
    }

    fun hide() {
        root?.let { view -> runCatching { windows.removeViewImmediate(view) } }
        root = null
        detail = null
        shownKey = null
    }
}
