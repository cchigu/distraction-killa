package com.cchigu.distraction_killa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo

/** Browsers we can read the address bar of, and how to find it. */
object Browsers {

    private fun firefox(pkg: String) = pkg to listOf(
        "$pkg:id/mozac_browser_toolbar_url_view",
        "$pkg:id/url_bar_title",
    )

    private fun chrome(pkg: String) = pkg to listOf("$pkg:id/url_bar")

    private fun samsung(pkg: String) = pkg to listOf(
        "$pkg:id/location_bar_edit_text",
        "$pkg:id/custom_tab_toolbar_url_bar_text",
    )

    val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        firefox("org.mozilla.firefox"),
        firefox("org.mozilla.firefox_beta"),
        firefox("org.mozilla.fenix"),
        chrome("com.android.chrome"),
        chrome("com.chrome.beta"),
        samsung("com.sec.android.app.sbrowser"),
        samsung("com.sec.android.app.sbrowser.beta"),
    )

    val TRACKED: Set<String> = URL_BAR_IDS.keys

    /** Installed browsers whose address bar we can't read (blocked outright at night). */
    fun untracked(ctx: Context): Set<String> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return ctx.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it !in TRACKED && it != ctx.packageName }
            .toSet()
    }
}

object UrlReader {

    private val DOMAIN = Regex("^([a-z0-9-]+\\.)+[a-z]{2,}$")
    private const val MAX_NODES = 300

    /**
     * The site currently shown in [pkg]'s window.
     * Returns a domain, "" for a blank/new-tab page, or null if it can't tell
     * (e.g. the user is typing in the address bar, or the toolbar is hidden).
     */
    fun read(root: AccessibilityNodeInfo, pkg: String, screenHeight: Int): String? {
        for (id in Browsers.URL_BAR_IDS[pkg].orEmpty()) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { emptyList() }
            for (n in nodes) {
                val text = n.text?.toString()?.trim().orEmpty()
                domainOf(text)?.let { return it }
                domainOf(n.contentDescription?.toString().orEmpty())?.let { return it }
                if (n.isFocused) return null // typing a search / address
                if (text.isEmpty() || text == n.hintText?.toString()) return ""
            }
        }
        return scanToolbar(root, screenHeight)
    }

    /** Fallback: a domain-looking label near the top or bottom of the screen, outside page content. */
    private fun scanToolbar(root: AccessibilityNodeInfo, screenHeight: Int): String? {
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val r = Rect()
        var seen = 0
        while (stack.isNotEmpty() && seen < MAX_NODES) {
            val n = stack.removeLast()
            seen++
            val cls = n.className?.toString().orEmpty()
            if (cls.contains("WebView") || cls.contains("GeckoView")) continue // page content
            for (label in listOf(n.text, n.contentDescription)) {
                val d = domainOf(label?.toString() ?: continue) ?: continue
                n.getBoundsInScreen(r)
                if (r.centerY() < screenHeight * 0.25 || r.centerY() > screenHeight * 0.75) return d
            }
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    /** "https://www.youtube.com/watch?v=1" -> "youtube.com". Null if it isn't a web address. */
    fun domainOf(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isEmpty() || s.length > 2000 || s.contains(' ')) return null
        if (s.contains("://")) {
            if (!s.startsWith("http")) return null // about:, file:, etc.
            s = s.substringAfter("://")
        }
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfter('@').substringBefore(':')
        if (!DOMAIN.matches(s)) return null
        for (prefix in listOf("www.", "m.", "mobile.")) {
            if (s.startsWith(prefix) && s.count { it == '.' } > 1) s = s.removePrefix(prefix)
        }
        return s
    }
}
