import 'package:flutter/services.dart';

import 'models.dart';

/// Bridge to the Kotlin side (android/app/src/main/kotlin/...).
class Native {
  static const _ch = MethodChannel('distraction_killa/native');

  static Future<bool> hasUsageAccess() async =>
      await _ch.invokeMethod<bool>('hasUsageAccess') ?? false;

  static Future<void> openUsageAccessSettings() =>
      _ch.invokeMethod('openUsageAccessSettings');

  static Future<bool> hasAccessibility() async =>
      await _ch.invokeMethod<bool>('hasAccessibility') ?? false;

  static Future<void> openAccessibilitySettings() =>
      _ch.invokeMethod('openAccessibilitySettings');

  static List<List<int>> _ranges(List<TimeRange> ranges) => [
    for (final r in ranges) [r.start.millisecondsSinceEpoch, r.end.millisecondsSinceEpoch],
  ];

  /// Per-app screen time summed across [ranges].
  static Future<List<AppUsage>> appUsage(List<TimeRange> ranges) async {
    final raw = await _ch.invokeListMethod<Map>('getAppUsage', {'ranges': _ranges(ranges)});
    return (raw ?? []).map(AppUsage.fromMap).toList()
      ..sort((a, b) => b.time.compareTo(a.time));
  }

  /// Per-website time (Firefox, Chrome, Samsung Internet) summed across [ranges].
  static Future<List<SiteUsage>> siteUsage(List<TimeRange> ranges) async {
    final raw = await _ch.invokeListMethod<Map>('getSiteUsage', {'ranges': _ranges(ranges)});
    return (raw ?? []).map(SiteUsage.fromMap).toList()
      ..sort((a, b) => b.time.compareTo(a.time));
  }

  /// Every app with a launcher icon, A-Z (time is zero).
  static Future<List<AppUsage>> installedApps() async {
    final raw = await _ch.invokeListMethod<Map>('installedApps');
    return (raw ?? []).map((m) => AppUsage.fromMap({...m, 'ms': 0})).toList();
  }

  /// Names of installed browsers we can't read (blocked outright at night).
  static Future<List<String>> otherBrowsers() async =>
      (await _ch.invokeListMethod<String>('otherBrowsers')) ?? [];

  static Future<BlockRules> blockRules() async =>
      BlockRules.fromMap(await _ch.invokeMapMethod<String, dynamic>('getBlockRules') ?? {});

  static Future<void> saveBlockRules(BlockRules r) => _ch.invokeMethod('setBlockRules', {
    'enabled': r.enabled,
    'apps': r.apps.toList(),
    'sites': r.sites.toList(),
  });

  /// When website tracking started, or null if it hasn't recorded anything yet.
  static Future<DateTime?> trackingSince() async {
    final ms = await _ch.invokeMethod<int>('trackingSince');
    return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms);
  }
}
