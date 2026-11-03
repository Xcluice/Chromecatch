# ChromeCatch

Self-accountability app. When Chrome comes to the foreground on your phone, it takes a front-camera selfie, then 3 minutes later a screenshot of whatever's on screen, and stacks both (full-size, uncropped) into one timestamped PNG saved to `Pictures/ChromeCatch`.

## Setup (one-time, on-device)
1. Install the APK, open the app.
2. Tap through the three buttons in order: Camera permission → Accessibility service (Android makes you flip this on yourself in Settings — no app can do it silently) → Screen capture consent (also a one-time system dialog; the app reuses that consent after this).
3. In your phone's battery settings, exempt ChromeCatch from battery optimization — otherwise Android may kill the background service and you'll need to re-grant step 3.
4. Open Chrome. A collage should land in your gallery ~3 minutes later.

## Build it
Push this repo to GitHub — the included Action (`.github/workflows/build.yml`) builds a release APK on every push to `main` and uploads it as a workflow artifact, matching your usual build-and-poll flow. No local Android Studio needed.

Or locally: `gradle assembleRelease` (requires the Android SDK + the Android Gradle Plugin's Google Maven repo to be reachable).

## Notes / limits
- The accessibility service is scoped (in `accessibility_service_config.xml`) to only listen for Chrome's window-state changes — it can't read screen content, just "Chrome came to front."
- There's a 15s debounce so rapid tab switches inside Chrome don't spam captures.
- If the selfie capture fails (e.g. phone is face-down, camera busy), the collage still saves with just the screenshot half.
- No icon pack, no network calls — everything stays local, nothing leaves the device.
