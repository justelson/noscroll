# NoScroll, native Android fork

Block Instagram Reels and YouTube Shorts inside the official apps. No browser wrapper, launcher, timers or account login.

**Status: first native test build.** The detector signatures are starter rules, not yet verified against the app versions on the target phone. A successful build or shield preview does not prove that a real feed is detected.

Fork of [Blueturboguy07/noscroll](https://github.com/Blueturboguy07/noscroll), rewritten as an Android-only application. Upstream history and the AGPL-3.0-or-later licence are retained.

## Behavior

1. You explicitly enable the NoScroll Accessibility service in Android Settings.
2. The service identifies the foreground application. It traverses UI nodes only inside `com.instagram.android` and `com.google.android.youtube`.
3. Exact view-resource IDs, visibility, selected state and layout bounds identify a short-video viewer. Generic text or "reel" substring matching is not used.
4. A detected feed is covered by an opaque, touchable `TYPE_ACCESSIBILITY_OVERLAY` window. Touches cannot reach the feed through the shield.
5. **Go back** requests one Android Back action. The service does not autonomously dispatch gestures or repeatedly navigate.
6. Leaving the viewer, switching apps, pausing, disabling a target, or losing service access removes the overlay. System windows must remain usable.

The shield does **not** pause or mute the underlying player. Audio may continue until you leave the feed. Unknown layouts fail open rather than blocking unrelated app screens. Detection is reactive, so brief exposure is possible before a match. This is not tamper-proof parental control.

Normal YouTube videos, Instagram DMs, profiles and ordinary navigation are negative cases that must be checked on each supported release. Shared links and notifications need explicit device tests too.

## Privacy and permission boundary

Android accessibility access is broad. This implementation reads view IDs, bounds, visibility and selection state from the two target apps, not node text, content descriptions, messages, passwords or screenshot pixels. It does not persist accessibility trees. The only detection history stored is the name of the most recently blocked service.

There is no `INTERNET`, `SYSTEM_ALERT_WINDOW`, VPN, notification-listener or gesture-injection permission. Accessibility overlays use the accessibility grant itself. Service entry is protected by `BIND_ACCESSIBILITY_SERVICE`.

The service receives foreground/window events outside the two apps solely to dismiss stale overlays. It does not traverse other apps' contents. UI scans are coalesced and bounded; recurring checks run only while the shield or its ten-second preview is visible.

No Play Store policy approval is claimed. Distribution through a store would require a separate policy review and accurate accessibility declarations.

## Build

Requirements: JDK 17 or 21, Android SDK platform/build-tools 34, and the checked-in Gradle wrapper. No Node, pnpm, JavaScript engine or signing server is needed.

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon
```

Windows Command Prompt/PowerShell can use `gradlew.bat` instead.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. It is debug-signed for local testing, not a production release. Package `app.noscroll`, version code 2, version `0.2.0-native`, Android 8 or later. Using the same local debug key allows an upgrade from the earlier wrapper build.

## First phone test

1. Connect the phone, enable USB debugging, and explicitly authorize this computer.
2. Install with `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`.
3. Open NoScroll and read the permission disclosure before enabling its Accessibility service.
4. If Android blocks enabling a sideloaded service, review App info → menu → Allow restricted settings. Only allow this if you trust the build. NoScroll never enables its own service or changes secure settings.
5. Use **Preview blocking screen** to test overlay presentation and dismissal. A preview is not a detector test.
6. Open Shorts and Reels through tabs, swipes, shared links and notifications. Verify the shield, Back, Home, notification shade and normal app screens.
7. Test pause, each platform switch and service revocation. Record misses and false positives, then adjust exact signatures against the installed app versions.

Do not collect or commit full accessibility dumps containing personal messages. If a signature needs inspection, inspect only the target viewer and retain the minimum resource IDs/geometry needed for a test fixture.

## Source map

- `MainActivity.kt`: native setup, explicit disclosure, platform switches and pause.
- `blocker/FeedDetector.kt`: pure Kotlin resource-ID rules and overlay state policy.
- `blocker/FeedBlockerService.kt`: window selection, bounded UI-node collection and lifecycle.
- `blocker/FeedShield.kt`: opaque overlay and user-requested Back.
- `blocker/BlockerSettings.kt`: local switches and last-blocked service name.
- `src/test`: synthetic detector and state-policy regression tests. Real app fixtures remain pending device verification.

See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Not affiliated with Meta, Instagram, Google or YouTube.
