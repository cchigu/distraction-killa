package com.cchigu.distraction_killa

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service:
 *  - notes which site each tracked browser is showing (Milestone 2)
 *  - blocks chosen apps/sites, and browsers it can't read, from 9 PM to 9 AM (Milestone 3)
 */
class KillaService : AccessibilityService() {

    companion object {
        private const val SCAN_DELAY_MS = 400L
        private const val BLOCK_COOLDOWN_MS = 1500L
        private const val BROWSER_REFRESH_MS = 10 * 60 * 1000L

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

    private var lastBlockAt = 0L
    private var untrackedBrowsers: Set<String> = emptySet()
    private var browsersCheckedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (BlockRules.active(this) && appBlocked(pkg)) {
            // Only act if that app really is the one on screen (not a background update).
            if (rootInActiveWindow?.packageName?.toString() == pkg) block(appName(pkg))
            return
        }

        if (pkg !in Browsers.TRACKED) return
        // Browsers fire lots of events; read the address bar at most every SCAN_DELAY_MS.
        if (pendingPkg == null) {
            pendingPkg = pkg
            handler.postDelayed(scanRunnable, SCAN_DELAY_MS)
        }
    }

    private fun appBlocked(pkg: String): Boolean =
        pkg in BlockRules.apps(this) || pkg in untrackedBrowsers()

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

        if (BlockRules.active(this) && BlockRules.siteBlocked(domain, BlockRules.sites(this))) {
            block(domain)
        }

        if (lastDomainByPkg[pkg] == domain) return
        lastDomainByPkg[pkg] = domain
        SiteLog.get(this).add(System.currentTimeMillis(), pkg, domain)
    }

    private fun block(what: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_WHAT, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /** Browsers whose address bar we can't read: blocked outright during the window. */
    private fun untrackedBrowsers(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (browsersCheckedAt == 0L || now - browsersCheckedAt > BROWSER_REFRESH_MS) {
            browsersCheckedAt = now
            untrackedBrowsers = Browsers.untracked(this)
        }
        return untrackedBrowsers
    }

    private fun appName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "This app"
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        super.onDestroy()
    }
}
