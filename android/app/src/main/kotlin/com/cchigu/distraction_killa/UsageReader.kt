package com.cchigu.distraction_killa

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream

/**
 * Works out how long each app was actually on screen, using the raw
 * foreground/background events Android records (more accurate than the
 * daily buckets Digital Wellbeing-style APIs give you).
 */
class UsageReader(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    // Raw event type ids (named constants need newer APIs on some of them).
    private companion object {
        const val RESUMED = 1          // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
        const val PAUSED = 2           // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
        const val SCREEN_OFF = 16      // SCREEN_NON_INTERACTIVE
        const val LOCKED = 17          // KEYGUARD_SHOWN
        const val SHUTDOWN = 26        // DEVICE_SHUTDOWN
        const val LOOKBACK_MS = 3 * 60 * 60 * 1000L
        const val SITE_LOOKBACK_MS = 7 * 24 * 60 * 60 * 1000L // find which site was open when a browser resumes
        const val ICON_PX = 96
    }

    /** Milliseconds on screen per package, summed over all [ranges] (each = [start, end]). */
    fun foregroundTimes(ranges: List<Pair<Long, Long>>): Map<String, Long> {
        val totals = HashMap<String, Long>()
        for ((start, end) in ranges) {
            for ((pkg, spans) in foregroundIntervals(start, end)) {
                totals[pkg] = (totals[pkg] ?: 0L) + spans.sumOf { it.second - it.first }
            }
        }
        return totals
    }

    /** Each on-screen stretch per package within [start, end], as (from, to) pairs. */
    fun foregroundIntervals(start: Long, end: Long): Map<String, List<Pair<Long, Long>>> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stop = minOf(end, now)
        if (stop <= start) return emptyMap()

        // Look back a bit so an app already open at `start` is counted from `start`.
        val events = usm.queryEvents(start - LOOKBACK_MS, stop)
        val out = HashMap<String, MutableList<Pair<Long, Long>>>()

        var curPkg: String? = null
        var curStart = 0L
        val openActivities = HashSet<String>()

        fun close(t: Long) {
            val p = curPkg ?: return
            val s = maxOf(curStart, start)
            val e = minOf(t, stop)
            if (e > s) out.getOrPut(p) { mutableListOf() }.add(s to e)
            curPkg = null
            openActivities.clear()
        }

        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val t = ev.timeStamp
            when (ev.eventType) {
                RESUMED -> {
                    if (curPkg != ev.packageName) {
                        close(t)
                        curPkg = ev.packageName
                        curStart = t
                    }
                    openActivities.add(ev.className ?: "")
                }
                PAUSED -> {
                    if (curPkg == ev.packageName) {
                        openActivities.remove(ev.className ?: "")
                        if (openActivities.isEmpty()) close(t)
                    }
                }
                SCREEN_OFF, LOCKED, SHUTDOWN -> close(t)
            }
        }
        close(stop)
        return out
    }

    /**
     * Time per website: every moment a browser was on screen is credited to the
     * site its address bar last showed (from [SiteLog]).
     */
    fun siteUsage(ranges: List<Pair<Long, Long>>): List<Map<String, Any>> {
        val log = SiteLog.get(context)
        val totals = HashMap<String, Long>()
        for ((start, end) in ranges) {
            val intervals = foregroundIntervals(start, end).filterKeys { it in Browsers.TRACKED }
            if (intervals.isEmpty()) continue
            val visits = log.visits(start - SITE_LOOKBACK_MS, end)
            for ((pkg, spans) in intervals) {
                SiteTime.credit(spans, visits[pkg] ?: continue, totals)
            }
        }

        return totals.entries
            .filter { it.value >= 1000 }
            .sortedByDescending { it.value }
            .map { mapOf("domain" to it.key, "ms" to it.value) }
    }

    /** Usage for real, user-facing apps only (no launcher, system UI, or this app). */
    fun appUsage(ranges: List<Pair<Long, Long>>): List<Map<String, Any?>> {
        val ignore = launcherPackages() + setOf(context.packageName, "com.android.systemui")
        return foregroundTimes(ranges)
            .filter { (pkg, ms) -> ms >= 1000 && pkg !in ignore && isUserApp(pkg) }
            .entries
            .sortedByDescending { it.value }
            .map { (pkg, ms) ->
                val info = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { null }
                mapOf(
                    "package" to pkg,
                    "name" to (info?.let { pm.getApplicationLabel(it).toString() } ?: pkg),
                    "ms" to ms,
                    "category" to (info?.category ?: -1),
                    "icon" to iconBytes(pkg),
                )
            }
    }

    private fun isUserApp(pkg: String): Boolean = pm.getLaunchIntentForPackage(pkg) != null

    private fun launcherPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet()
    }

    private fun iconBytes(pkg: String): ByteArray? = try {
        val d = pm.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, ICON_PX, ICON_PX)
        d.draw(Canvas(bmp))
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }
}
