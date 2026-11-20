package com.stoa.app.mindful

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.stoa.app.Prefs

/**
 * Watches which app comes to the front. When it's one you marked as mindful, a breathing pause is shown first,
 * however the app was opened (launcher, notification, recents, link).
 */
class MindfulService : AccessibilityService() {
    private lateinit var prefs: Prefs
    private var lastPkg = ""
    private var lastShown = 0L

    override fun onServiceConnected() { prefs = Prefs(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg == lastPkg) return
        lastPkg = pkg
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!prefs.mindful.contains(pkg)) return
        val now = System.currentTimeMillis()
        if (prefs.passUntil(pkg) > now || now - lastShown < 1500) return
        lastShown = now
        startActivity(Intent(this, PauseActivity::class.java).putExtra("pkg", pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
    }

    override fun onInterrupt() {}
}
