package app.noscroll.blocker

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

class BlockerSettings(context: Context) {
    val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("native_feed_blocker", Context.MODE_PRIVATE)

    var paused: Boolean
        get() = preferences.getBoolean("paused", false)
        set(value) { preferences.edit().putBoolean("paused", value).apply() }

    fun enabled(platform: Platform) = preferences.getBoolean(platform.name, true)
    fun setEnabled(platform: Platform, enabled: Boolean) {
        preferences.edit().putBoolean(platform.name, enabled).apply()
    }
    fun enabledPlatforms(): Set<Platform> =
        if (paused) emptySet() else Platform.entries.filter(::enabled).toSet()

    val lastBlocked: String? get() = preferences.getString("last_blocked", null)
    fun recordBlocked(match: FeedMatch) {
        if (lastBlocked != match.platform.feedName) {
            preferences.edit().putString("last_blocked", match.platform.feedName).apply()
        }
    }

    companion object {
        fun accessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, FeedBlockerService::class.java)
            return Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty().split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
