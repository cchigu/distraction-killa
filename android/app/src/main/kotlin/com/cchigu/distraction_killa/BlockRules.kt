package com.cchigu.distraction_killa

import android.content.Context
import java.util.Calendar

/** What to block and when. Saved on the phone; read by [KillaService] on every check. */
object BlockRules {

    const val START_HOUR = 21 // 9 PM
    const val END_HOUR = 9    // 9 AM

    private const val PREFS = "block_rules"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun apps(ctx: Context): Set<String> = prefs(ctx).getStringSet("apps", null) ?: emptySet()
    fun sites(ctx: Context): Set<String> = prefs(ctx).getStringSet("sites", null) ?: emptySet()

    fun save(ctx: Context, enabled: Boolean, apps: Collection<String>, sites: Collection<String>) {
        prefs(ctx).edit()
            .putBoolean("enabled", enabled)
            .putStringSet("apps", HashSet(apps))
            .putStringSet("sites", HashSet(sites))
            .apply()
    }

    fun toMap(ctx: Context): Map<String, Any> = mapOf(
        "enabled" to enabled(ctx),
        "apps" to apps(ctx).toList(),
        "sites" to sites(ctx).toList(),
    )

    /** True if blocking is switched on and it's currently between 9 PM and 9 AM. */
    fun active(ctx: Context): Boolean =
        enabled(ctx) && inWindow(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    fun inWindow(hour: Int): Boolean =
        if (START_HOUR > END_HOUR) hour >= START_HOUR || hour < END_HOUR
        else hour in START_HOUR until END_HOUR

    /** "m.youtube.com" is covered by a "youtube.com" rule. */
    fun siteBlocked(domain: String, sites: Set<String>): Boolean =
        domain.isNotEmpty() && sites.any { domain == it || domain.endsWith(".$it") }
}
