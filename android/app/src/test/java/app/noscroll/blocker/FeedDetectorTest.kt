package app.noscroll.blocker

import org.junit.Assert.*
import org.junit.Test

/** Synthetic UI signals, not recorded phone fixtures. Real-app verification is a separate gate. */
class FeedDetectorTest {
    private val youtube = Platform.YOUTUBE.packageName
    private val instagram = Platform.INSTAGRAM.packageName
    private fun node(pkg: String, id: String, width: Float = 1f, height: Float = 0.9f,
        selected: Boolean = false, visible: Boolean = true) =
        ViewSignal("$pkg:id/$id", visible, selected, width, height)
    private fun detect(pkg: String, vararg nodes: ViewSignal) = FeedDetector.detect(pkg, nodes.toList())

    @Test fun shortsViewerIsRecognized() {
        assertEquals(Platform.YOUTUBE, detect(youtube, node(youtube, "reel_watch_player"))?.platform)
    }
    @Test fun reelsViewerIsRecognized() {
        assertEquals(Platform.INSTAGRAM, detect(instagram, node(instagram, "clips_viewer_view_pager"))?.platform)
    }
    @Test fun allYouTubeViewerSignaturesAreRecognized() {
        for (id in listOf("reel_watch_player", "reel_recycler", "reel_player_page_container", "shorts_player_container")) {
            assertNotNull(id, detect(youtube, node(youtube, id)))
        }
    }
    @Test fun allInstagramViewerSignaturesAreRecognized() {
        for (id in listOf("clips_viewer_view_pager", "clips_viewer_viewpager", "clips_viewer_container", "clips_viewer_root", "clips_viewer_recycler_view")) {
            assertNotNull(id, detect(instagram, node(instagram, id)))
        }
    }
    @Test fun normalYouTubePlayerStaysUsable() {
        assertNull(detect(youtube, node(youtube, "watch_player"), node(youtube, "player_view")))
    }
    @Test fun instagramMessagesStayUsable() {
        assertNull(detect(instagram, node(instagram, "direct_thread_view"), node(instagram, "row_message_textview")))
    }
    @Test fun reelsThumbnailInMessagesIsNotAViewer() {
        assertNull(detect(instagram, node(instagram, "clips_viewer_container", 0.5f, 0.2f)))
    }
    @Test fun shortsShelfIsNotAViewer() {
        assertNull(detect(youtube, node(youtube, "reel_recycler", 1f, 0.25f)))
    }
    @Test fun narrowViewerDoesNotQualify() {
        assertNull(detect(youtube, node(youtube, "reel_watch_player", 0.5f, 0.9f)))
    }
    @Test fun invisibleStaleViewerDoesNotQualify() {
        assertNull(detect(youtube, node(youtube, "reel_watch_player", visible = false)))
    }
    @Test fun selectedShortsNavigationQualifies() {
        assertNotNull(detect(youtube, node(youtube, "reel_pivot", 0.15f, 0.05f, selected = true)))
    }
    @Test fun selectedReelsNavigationQualifies() {
        assertNotNull(detect(instagram, node(instagram, "clips_tab", 0.15f, 0.05f, selected = true)))
    }
    @Test fun unselectedShortsButtonDoesNotBlockHome() {
        assertNull(detect(youtube, node(youtube, "reel_pivot", 0.15f, 0.05f)))
    }
    @Test fun invisibleSelectedTabDoesNotBlockOtherScreens() {
        assertNull(detect(instagram, node(instagram, "clips_tab", selected = true, visible = false)))
    }
    @Test fun selectedZeroSizeTabDoesNotQualify() {
        assertNull(detect(youtube, node(youtube, "reel_pivot", 0f, 0f, selected = true)))
    }
    @Test fun unknownPackageNeverMatches() {
        assertNull(detect("com.example.reader", node(youtube, "reel_watch_player")))
    }
    @Test fun appCannotMatchOtherPlatformsIds() {
        assertNull(detect(youtube, node(instagram, "clips_viewer_view_pager")))
    }
    @Test fun spoofedIdPrefixIsRejected() {
        assertNull(detect(youtube, ViewSignal("evil:id/reel_watch_player", widthFraction = 1f, heightFraction = 1f)))
    }
    @Test fun substringMatchesAreRejected() {
        assertNull(detect(youtube, node(youtube, "reel_watch_player_thumbnail")))
    }
    @Test fun missingIdsAndEmptyTreesFailOpen() {
        assertNull(detect(youtube))
        assertNull(detect(youtube, ViewSignal("")))
    }
    @Test fun nonFiniteBoundsFailOpen() {
        assertNull(detect(youtube, node(youtube, "reel_watch_player", Float.NaN)))
        assertNull(detect(youtube, node(youtube, "reel_watch_player", height = Float.POSITIVE_INFINITY)))
    }
    @Test fun negativeAndOversizedBoundsFailOpen() {
        assertNull(detect(youtube, node(youtube, "reel_watch_player", -1f)))
        assertNull(detect(youtube, node(youtube, "reel_watch_player", 4f)))
    }
    @Test fun matchedResourceIdIsPreservedWithoutPageText() {
        assertEquals("$youtube:id/reel_watch_player", detect(youtube, node(youtube, "reel_watch_player"))?.resourceId)
    }
    @Test fun unrelatedNodesDoNotPreventFindingViewer() {
        assertNotNull(detect(youtube, node(youtube, "toolbar"), node(youtube, "reel_watch_player")))
    }
    @Test fun exactMinimumViewerBoundsQualify() {
        assertNotNull(detect(youtube, node(youtube, "reel_watch_player", 0.70f, 0.40f)))
    }
}
