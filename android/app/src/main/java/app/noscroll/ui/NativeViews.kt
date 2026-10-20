package app.noscroll.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object NativeViews {
    val background = Color.rgb(17, 20, 17)
    val text = Color.rgb(241, 243, 236)
    val muted = Color.rgb(170, 180, 168)
    val accent = Color.rgb(186, 228, 149)
    val line = Color.rgb(48, 57, 47)
    val warning = Color.rgb(238, 192, 122)

    fun Context.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    fun label(context: Context, value: String, size: Float, color: Int = text, bold: Boolean = false) =
        TextView(context).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(context.dp(3).toFloat(), 1f)
        }

    fun button(context: Context, value: String, primary: Boolean, action: () -> Unit) =
        Button(context).apply {
            text = value
            isAllCaps = false
            textSize = 15f
            setTextColor(if (primary) NativeViews.background else NativeViews.text)
            val shape = GradientDrawable().apply {
                setColor(if (primary) accent else Color.rgb(36, 43, 35))
                cornerRadius = context.dp(8).toFloat()
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33777777), shape, null)
            stateListAnimator = null
            minHeight = context.dp(52)
            setPadding(context.dp(18), 0, context.dp(18), 0)
            setOnClickListener { action() }
        }

    fun LinearLayout.gap(height: Int) {
        addView(View(context), LinearLayout.LayoutParams(1, context.dp(height)))
    }

    fun LinearLayout.rule() {
        addView(View(context).apply { setBackgroundColor(line) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, context.dp(1)))
    }
}
