import 'package:flutter/services.dart';

import 'models.dart';

/// Bridge to the Kotlin side (android/app/src/main/kotlin/...).
class Native {
  static const _ch = MethodChannel('distraction_killa/native');

  static Future<bool> hasUsageAccess() async =>
      await _ch.invokeMethod<bool>('hasUsageAccess') ?? false;

  static Future<void> openUsageAccessSettings() =>
      _ch.invokeMethod('openUsageAccessSettings');

  /// Per-app screen time summed across [ranges].
  static Future<List<AppUsage>> appUsage(List<TimeRange> ranges) async {
    final raw = await _ch.invokeListMethod<Map>('getAppUsage', {
      'ranges': [
        for (final r in ranges)
          [r.start.millisecondsSinceEpoch, r.end.millisecondsSinceEpoch],
      ],
    });
    return (raw ?? []).map(AppUsage.fromMap).toList()
      ..sort((a, b) => b.time.compareTo(a.time));
  }
}
