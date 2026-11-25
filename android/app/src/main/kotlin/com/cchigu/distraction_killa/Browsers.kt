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

    fun isFirefox(pkg: String) = pkg.startsWith("org.mozilla.")

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
    private const val MAX_NODES = 1500

    /** Firefox's address-bar placeholder text (its new toolbar puts this in the label). */
    private val FIREFOX_HINTS = listOf("Search or enter address", "Search the web", "Search")

    /** The Firefox address box we found last time, re-checked first (much faster than searching). */
    private val cachedBox = HashMap<String, AccessibilityNodeInfo>()

    /**
     * The site currently shown in [pkg]'s window.
     * Returns a domain, "" for a blank/new-tab/non-web page, or null if there's no
     * address bar on screen (tab list, menus, settings, user typing an address).
     */
    fun read(root: AccessibilityNodeInfo, pkg: String, screenHeight: Int): String? {
        for (id in Browsers.URL_BAR_IDS[pkg].orEmpty()) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(id) } catch (e: Exception) { emptyList() }
            for (n in nodes) {
                val text = n.text?.toString()?.trim().orEmpty()
                domainOf(text)?.let { return it }
                domainOf(n.contentDescription?.toString().orEmpty())?.let { return it }
                if (n.isFocused) return null // typing a search / address
                if (n.isEditable && (text.isEmpty() || text == n.hintText?.toString())) return ""
            }
        }
        return if (Browsers.isFirefox(pkg)) readFirefox(root, pkg) else scanToolbar(root, screenHeight)
    }

    /**
     * Firefox's Compose toolbar exposes the address only as one label:
     * "<page title> <url>. Search or enter address" (or just the hint on a blank tab).
     */
    private fun readFirefox(root: AccessibilityNodeInfo, pkg: String): String? {
        cachedBox[pkg]?.let { box ->
            if (try { box.refresh() } catch (e: Exception) { false }) {
                firefoxLabel(box.contentDescription?.toString())?.let { return it }
            }
            cachedBox.remove(pkg)
        }
        val box = findNode(root) { firefoxLabel(it.contentDescription?.toString()) != null } ?: return null
        cachedBox[pkg] = box
        return firefoxLabel(box.contentDescription?.toString())
    }

    /** Domain from a Firefox address-box label, "" for blank/non-web, null if it isn't that label. */
    fun firefoxLabel(label: String?): String? {
        val t = label?.trim() ?: return null
        if (t in FIREFOX_HINTS) return ""
        for (hint in FIREFOX_HINTS) {
            if (t.endsWith(". $hint")) {
                val url = t.removeSuffix(". $hint").substringAfterLast(' ')
                return domainOf(url) ?: "" // search terms, about: pages, etc.
            }
        }
        return null
    }

    /** Fallback: a domain-looking label near the top or bottom of the screen, outside page content. */
    private fun scanToolbar(root: AccessibilityNodeInfo, screenHeight: Int): String? {
        val r = Rect()
        var found: String? = null
        findNode(root) { n ->
            listOf(n.text, n.contentDescription).any { label ->
                val d = domainOf(label?.toString() ?: return@any false) ?: return@any false
                n.getBoundsInScreen(r)
                val nearEdge = r.centerY() < screenHeight * 0.25 || r.centerY() > screenHeight * 0.75
                if (nearEdge) found = d
                nearEdge
            }
        }
        return found
    }

    /**
     * Breadth-first search, so the shallow toolbar is reached before deep page content.
     * Page content itself (WebView / GeckoView) is skipped entirely.
     */
    private fun findNode(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen < MAX_NODES) {
            val n = queue.removeFirst()
            seen++
            val cls = n.className?.toString().orEmpty()
            if (cls.contains("WebView") || cls.contains("GeckoView")) continue
            if (match(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
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
