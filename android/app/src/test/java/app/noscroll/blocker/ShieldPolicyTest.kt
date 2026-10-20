package app.noscroll.blocker

import org.junit.Assert.*
import org.junit.Test

class ShieldPolicyTest {
    private val youtube = Platform.YOUTUBE
    private val instagram = Platform.INSTAGRAM
    private val enabled = Platform.entries.toSet()
    private val shorts = FeedMatch(youtube, "${youtube.packageName}:id/reel_watch_player")
    private val reels = FeedMatch(instagram, "${instagram.packageName}:id/clips_viewer_view_pager")

    @Test fun recognizedFeedRaisesShield() {
        assertEquals(shorts, ShieldPolicy().update(youtube.packageName, shorts, enabled, 1000))
    }
    @Test fun homeScreenNeverRaisesShield() {
        assertNull(ShieldPolicy().update(youtube.packageName, null, enabled, 1000))
    }
    @Test fun briefLayoutGapDoesNotFlashTheFeedThrough() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertEquals(shorts, policy.update(youtube.packageName, null, enabled, 1200))
    }
    @Test fun changedLayoutReleasesAfterGracePeriod() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(youtube.packageName, null, enabled, 1400))
    }
    @Test fun continuingFeedRenewsShieldInsteadOfTimingOut() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        policy.update(youtube.packageName, shorts, enabled, 5000)
        assertEquals(shorts, policy.update(youtube.packageName, null, enabled, 5200))
    }
    @Test fun homeOrAnotherAppRemovesShieldImmediately() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update("com.sec.android.app.launcher", null, enabled, 1001))
    }
    @Test fun systemPermissionDialogCannotBeCovered() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update("com.google.android.permissioncontroller", null, enabled, 1001))
    }
    @Test fun unreadableForegroundCannotKeepOverlayTrapped() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(null, null, enabled, 1001))
    }
    @Test fun ourSettingsScreenRemovesShield() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update("app.noscroll", null, enabled, 1001))
    }
    @Test fun disablingPlatformTakesEffectImmediately() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(youtube.packageName, shorts, setOf(instagram), 1001))
    }
    @Test fun pauseClearsEvenAnActivelyDetectedFeed() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(youtube.packageName, shorts, emptySet(), 1001))
    }
    @Test fun movingBetweenTargetAppsDoesNotCarryOldShield() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(instagram.packageName, null, enabled, 1001))
    }
    @Test fun movingToAnotherBlockedFeedUpdatesPlatform() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertEquals(reels, policy.update(instagram.packageName, reels, enabled, 1001))
    }
    @Test fun staleCrossPackageMatchCannotRaiseShield() {
        assertNull(ShieldPolicy().update(instagram.packageName, shorts, enabled, 1000))
    }
    @Test fun clearingPolicyDiscardsGracePeriod() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        policy.clear()
        assertNull(policy.update(youtube.packageName, null, enabled, 1001))
    }
    @Test fun clockMovingBackwardsCannotExtendGraceIndefinitely() {
        val policy = ShieldPolicy()
        policy.update(youtube.packageName, shorts, enabled, 1000)
        assertNull(policy.update(youtube.packageName, null, enabled, 500))
    }
    @Test fun actualDetectorFeedsProductionPolicy() {
        val signals = listOf(ViewSignal("${youtube.packageName}:id/reel_watch_player", widthFraction = 1f, heightFraction = 0.9f))
        val policy = ShieldPolicy()
        val detection = FeedDetector.detect(youtube.packageName, signals)
        assertEquals(shorts, policy.update(youtube.packageName, detection, enabled, 1000))
        val home = listOf(ViewSignal("${youtube.packageName}:id/watch_player", widthFraction = 1f, heightFraction = 0.9f))
        assertNull(policy.update(youtube.packageName, FeedDetector.detect(youtube.packageName, home), enabled, 1500))
    }
}
