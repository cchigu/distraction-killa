package com.cchigu.distraction_killa

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {

    private val worker = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "distraction_killa/native")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasUsageAccess" -> result.success(hasUsageAccess())

                    "openUsageAccessSettings" -> {
                        openUsageAccessSettings()
                        result.success(null)
                    }

                    "hasAccessibility" -> result.success(KillaService.isEnabled(this))

                    "openAccessibilitySettings" -> {
                        openAccessibilitySettings()
                        result.success(null)
                    }

                    // ranges: [[startMs, endMs], ...]
                    "getAppUsage" -> runInBackground(result) {
                        UsageReader(applicationContext).appUsage(ranges(call.argument("ranges")))
                    }

                    "getSiteUsage" -> runInBackground(result) {
                        UsageReader(applicationContext).siteUsage(ranges(call.argument("ranges")))
                    }

                    "installedApps" -> runInBackground(result) {
                        UsageReader(applicationContext).installedApps()
                    }

                    "otherBrowsers" -> runInBackground(result) {
                        val reader = UsageReader(applicationContext)
                        Browsers.untracked(applicationContext).map { reader.appInfo(it)["name"] }.sortedBy { it.toString() }
                    }

                    "getBlockRules" -> result.success(BlockRules.toMap(this))

                    "setBlockRules" -> {
                        BlockRules.save(
                            this,
                            call.argument<Boolean>("enabled") ?: false,
                            call.argument<List<String>>("apps").orEmpty(),
                            call.argument<List<String>>("sites").orEmpty(),
                        )
                        result.success(null)
                    }

                    "trackingSince" -> runInBackground(result) {
                        SiteLog.get(applicationContext).firstTs()
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun ranges(raw: List<List<Number>>?): List<Pair<Long, Long>> =
        raw.orEmpty().map { it[0].toLong() to it[1].toLong() }

    private fun runInBackground(result: MethodChannel.Result, work: () -> Any?) {
        worker.execute {
            try {
                val data = work()
                mainThread.post { result.success(data) }
            } catch (e: Exception) {
                mainThread.post { result.error("native_failed", e.message, null) }
            }
        }
    }

    private fun openAccessibilitySettings() {
        // Try this app's own accessibility page; fall back to the full list.
        try {
            startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(this, KillaService::class.java).flattenToString(),
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun openUsageAccessSettings() {
        // Try to jump straight to this app's toggle; fall back to the full list.
        try {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
}
