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
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service:
 *  - notes which site each tracked browser is showing (Milestone 2)
 *  - blocks chosen apps/sites, and browsers it can't read, from 9 PM to 9 AM (Milestone 3)
 */
class KillaService : AccessibilityService() {

    companion object {
        private const val SCAN_DELAY_MS = 400L
        private const val OPEN_SCAN_DELAY_MS = 120L
        private const val WATCH_INTERVAL_MS = 1000L
        private const val BLOCK_COOLDOWN_MS = 700L
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
    private val watchRunnable = Runnable { watch() }
    private var watching = false

    private var lastBlockAt = 0L
    private var untrackedBrowsers: Set<String> = emptySet()
    private var browsersCheckedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        val active = BlockRules.active(this)

        if (active && appBlocked(pkg)) {
            startWatching()
            // Only act if that app really is the one on screen (not a background update).
            if (rootInActiveWindow?.packageName?.toString() == pkg) blockApp(pkg)
            return
        }

        if (pkg !in Browsers.TRACKED) return
        if (active) startWatching()
        // Browsers fire lots of events; read the address bar at most every SCAN_DELAY_MS,
        // but check quickly when a browser window first comes up.
        if (pendingPkg == null) {
            pendingPkg = pkg
            val opened = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            handler.postDelayed(scanRunnable, if (opened) OPEN_SCAN_DELAY_MS else SCAN_DELAY_MS)
        }
    }

    private fun appBlocked(pkg: String): Boolean =
        pkg in BlockRules.apps(this) || pkg in untrackedBrowsers()

    private fun scan() {
        val pkg = pendingPkg ?: return
        pendingPkg = null
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        checkBrowser(pkg, root)
    }

    /** Read the address bar, log site changes, and block if the site is on the list. */
    private fun checkBrowser(pkg: String, root: AccessibilityNodeInfo) {
        val read = try {
            UrlReader.read(root, pkg, resources.displayMetrics.heightPixels)
        } catch (e: Exception) {
            null
        }
        if (read != null && lastDomainByPkg[pkg] != read) {
            lastDomainByPkg[pkg] = read
            SiteLog.get(this).add(System.currentTimeMillis(), pkg, read)
        }
        // No address bar on screen (tab list, menus, typing): nothing to block.
        val domain = read ?: return
        if (BlockRules.active(this) && BlockRules.siteBlocked(domain, BlockRules.sites(this))) {
            blockSite(domain)
        }
    }

    /**
     * While it's blocking time and a browser or blocked app is on screen, re-check every
     * second. Catches pages that sit still (no events) and apps reopened right after a block.
     */
    private fun startWatching() {
        if (watching) return
        watching = true
        handler.postDelayed(watchRunnable, WATCH_INTERVAL_MS)
    }

    private fun watch() {
        watching = false
        if (!BlockRules.active(this)) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        when {
            pkg == packageName -> return // our "Killed." screen is up
            appBlocked(pkg) -> blockApp(pkg)
            pkg in Browsers.TRACKED -> checkBrowser(pkg, root)
            else -> return // nothing to watch; the next event restarts this
        }
        startWatching()
    }

    private fun blockApp(pkg: String) {
        if (!cooledDown()) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockScreen(appName(pkg))
    }

    /** Go back a page (so reopening the browser doesn't land on the same site), then cover it. */
    private fun blockSite(domain: String) {
        if (!cooledDown()) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ showBlockScreen(domain) }, 120)
    }

    private fun cooledDown(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_COOLDOWN_MS) return false
        lastBlockAt = now
        return true
    }

    private fun showBlockScreen(what: String) {
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
        handler.removeCallbacks(watchRunnable)
        super.onDestroy()
    }
}
