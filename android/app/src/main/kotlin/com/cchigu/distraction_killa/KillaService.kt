package com.cchigu.distraction_killa

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service. Milestone 2: notes which site each browser is showing.
 * Milestone 3 adds blocking here too.
 */
class KillaService : AccessibilityService() {

    companion object {
        private const val SCAN_DELAY_MS = 400L

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, KillaService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPkg: String? = null
    private val lastDomainByPkg = HashMap<String, String>()
    private val scanRunnable = Runnable { scan() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in Browsers.TRACKED) return
        // Browsers fire lots of events; read the address bar at most every SCAN_DELAY_MS.
        if (pendingPkg == null) {
            pendingPkg = pkg
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
        }
    }

    private fun scan() {
        val pkg = pendingPkg ?: return
        pendingPkg = null
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val domain = try {
            UrlReader.read(root, pkg, resources.displayMetrics.heightPixels)
        } catch (e: Exception) {
            null
        } ?: return

        if (lastDomainByPkg[pkg] == domain) return
        lastDomainByPkg[pkg] = domain
        SiteLog.get(this).add(System.currentTimeMillis(), pkg, domain)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        super.onDestroy()
    }
}
