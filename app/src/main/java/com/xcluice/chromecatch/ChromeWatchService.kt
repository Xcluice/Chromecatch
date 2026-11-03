package com.xcluice.chromecatch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Fires whenever Chrome (com.android.chrome) comes to the foreground.
 * Configured in res/xml/accessibility_service_config.xml to only listen
 * for Chrome's window-state changes — it never reads screen content.
 */
class ChromeWatchService : AccessibilityService() {

    private var lastTriggerAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.android.chrome") return

        // Debounce: tab switches / multiple window events fire rapidly.
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < 15_000) return
        lastTriggerAt = now

        val intent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_CHROME_OPENED
        }
        startForegroundService(intent)
    }

    override fun onInterrupt() {}
}
