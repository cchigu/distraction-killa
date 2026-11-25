package com.cchigu.distraction_killa

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** On-device log of "browser X switched to site Y at time T". Nothing leaves the phone. */
class SiteLog private constructor(context: Context) :
    SQLiteOpenHelper(context, "sites.db", null, 1) {

    data class Visit(val ts: Long, val domain: String)

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
        private const val KEEP_MS = 35 * DAY

        @Volatile private var instance: SiteLog? = null

        fun get(context: Context): SiteLog =
            instance ?: synchronized(this) {
                instance ?: SiteLog(context.applicationContext).also { instance = it }
            }
    }

    private var lastPrune = 0L

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE visits (ts INTEGER NOT NULL, pkg TEXT NOT NULL, domain TEXT NOT NULL)")
        db.execSQL("CREATE INDEX visits_ts ON visits(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun add(ts: Long, pkg: String, domain: String) {
        val db = writableDatabase
        db.insert("visits", null, ContentValues().apply {
            put("ts", ts)
            put("pkg", pkg)
            put("domain", domain)
        })
        if (ts - lastPrune > DAY) {
            db.delete("visits", "ts < ?", arrayOf((ts - KEEP_MS).toString()))
            lastPrune = ts
        }
    }

    /** Visits in [from, to), grouped by browser package, oldest first. */
    fun visits(from: Long, to: Long): Map<String, List<Visit>> {
        val out = HashMap<String, MutableList<Visit>>()
        readableDatabase.rawQuery(
            "SELECT ts, pkg, domain FROM visits WHERE ts >= ? AND ts < ? ORDER BY ts",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out.getOrPut(c.getString(1)) { mutableListOf() }.add(Visit(c.getLong(0), c.getString(2)))
            }
        }
        return out
    }

    /** When site tracking first recorded anything (null = never). */
    fun firstTs(): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM visits", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
}

/** Pure time-splitting logic (unit tested). */
object SiteTime {
    /**
     * Adds to [totals] the time in each on-screen [spans] of one browser, split by
     * whichever site [visits] (oldest first) says was showing at each moment.
     */
    fun credit(spans: List<Pair<Long, Long>>, visits: List<SiteLog.Visit>, totals: MutableMap<String, Long>) {
        fun add(domain: String?, ms: Long) {
            if (domain.isNullOrEmpty() || ms <= 0) return
            totals[domain] = (totals[domain] ?: 0L) + ms
        }
        for ((s, e) in spans) {
            var cur: String? = null
            var t = s
            for (v in visits) {
                if (v.ts <= s) { cur = v.domain; continue }
                if (v.ts >= e) break
                add(cur, v.ts - t)
                cur = v.domain
                t = v.ts
            }
            add(cur, e - t)
        }
    }
}
