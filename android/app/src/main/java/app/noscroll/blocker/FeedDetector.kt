package app.noscroll.blocker

/** No Android dependency: the production service and JVM tests use this same detector. */
enum class Platform(val packageName: String, val feedName: String) {
    INSTAGRAM("com.instagram.android", "Instagram Reels"),
    YOUTUBE("com.google.android.youtube", "YouTube Shorts");

    companion object {
        fun fromPackage(packageName: String?) = entries.firstOrNull { it.packageName == packageName }
    }
}

data class ViewSignal(
    val resourceId: String,
    val visible: Boolean = true,
    val selected: Boolean = false,
    val widthFraction: Float = 0f,
    val heightFraction: Float = 0f,
)

data class FeedMatch(val platform: Platform, val resourceId: String)

/**
 * Starter signatures, NOT certified against a particular app release yet.
 * Deliberately no text matching, screenshots, generic "reel" substrings or ML.
 * A thumbnail/shelf is not a full-screen viewer. Unrecognised layouts fail open.
 */
object FeedDetector {
    private val viewerIds = mapOf(
        Platform.YOUTUBE to setOf(
            "reel_watch_player", "reel_recycler", "reel_player_page_container", "shorts_player_container",
        ),
        Platform.INSTAGRAM to setOf(
            "clips_viewer_view_pager", "clips_viewer_viewpager", "clips_viewer_container",
            "clips_viewer_root", "clips_viewer_recycler_view",
        ),
    )
    private val selectedTabIds = mapOf(
        Platform.YOUTUBE to setOf("reel_pivot", "shorts_tab"),
        Platform.INSTAGRAM to setOf("clips_tab"),
    )

    fun detect(packageName: String, signals: List<ViewSignal>): FeedMatch? {
        val platform = Platform.fromPackage(packageName) ?: return null
        for (signal in signals) {
            if (!signal.visible || !signal.resourceId.startsWith("$packageName:id/")) continue
            val id = signal.resourceId.substringAfter(":id/")
            val hasBounds = signal.widthFraction.isFinite() && signal.heightFraction.isFinite() &&
                signal.widthFraction > 0f && signal.heightFraction > 0f &&
                signal.widthFraction <= 1.05f && signal.heightFraction <= 1.05f
            val viewer = id in viewerIds.getValue(platform) && hasBounds &&
                signal.widthFraction >= 0.70f && signal.heightFraction >= 0.40f
            val selectedTab = id in selectedTabIds.getValue(platform) && signal.selected && hasBounds
            if (viewer || selectedTab) return FeedMatch(platform, signal.resourceId)
        }
        return null
    }
}

/**
 * Keep a shield briefly through layout churn, but never carry it into another app.
 * Uses monotonic elapsed time supplied by the service. No auto-navigation or timers
 * that silently unlock a known feed: positive detections continually renew the shield.
 */
class ShieldPolicy(private val graceMs: Long = 400L) {
    private var current: FeedMatch? = null
    private var lastMatchAt = 0L

    fun update(
        foregroundPackage: String?,
        match: FeedMatch?,
        enabled: Set<Platform>,
        now: Long,
    ): FeedMatch? {
        val platform = Platform.fromPackage(foregroundPackage)
        if (platform == null || platform !in enabled) return clear()
        if (match != null && match.platform == platform) {
            current = match
            lastMatchAt = now
            return current
        }
        if (current?.platform == platform && now >= lastMatchAt && now - lastMatchAt < graceMs) {
            return current
        }
        return clear()
    }

    fun clear(): FeedMatch? {
        current = null
        lastMatchAt = 0L
        return null
    }
}
