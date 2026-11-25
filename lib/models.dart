import 'dart:typed_data';

/// The lockdown window. Hard-coded for now; becomes a setting in a later milestone.
const kBlockStartHour = 21; // 9 PM
const kBlockEndHour = 9; // 9 AM

class TimeRange {
  final DateTime start;
  final DateTime end;
  const TimeRange(this.start, this.end);
}

class AppUsage {
  final String package;
  final String name;
  final Duration time;
  final int category; // Android ApplicationInfo.category (-1 = unknown)
  final Uint8List? icon;

  const AppUsage({
    required this.package,
    required this.name,
    required this.time,
    required this.category,
    this.icon,
  });

  factory AppUsage.fromMap(Map m) => AppUsage(
    package: m['package'] as String,
    name: m['name'] as String,
    time: Duration(milliseconds: (m['ms'] as num).toInt()),
    category: (m['category'] as num?)?.toInt() ?? -1,
    icon: m['icon'] as Uint8List?,
  );
}

class SiteUsage {
  final String domain;
  final Duration time;
  const SiteUsage(this.domain, this.time);

  factory SiteUsage.fromMap(Map m) =>
      SiteUsage(m['domain'] as String, Duration(milliseconds: (m['ms'] as num).toInt()));
}

class BlockRules {
  bool enabled;
  final Set<String> apps; // package names
  final Set<String> sites; // domains, e.g. "youtube.com"
  BlockRules({required this.enabled, required this.apps, required this.sites});

  factory BlockRules.fromMap(Map m) => BlockRules(
    enabled: m['enabled'] as bool? ?? false,
    apps: {...(m['apps'] as List? ?? []).cast<String>()},
    sites: {...(m['sites'] as List? ?? []).cast<String>()},
  );
}

bool inBlockWindow(DateTime t) => t.hour >= kBlockStartHour || t.hour < kBlockEndHour;

/// "https://www.YouTube.com/watch" -> "youtube.com". Null if it isn't a web address.
String? normalizeDomain(String raw) {
  var s = raw.trim().toLowerCase();
  if (s.contains('://')) s = s.split('://')[1];
  s = s.split(RegExp(r'[/?#:]')).first;
  if (!RegExp(r'^([a-z0-9-]+\.)+[a-z]{2,}$').hasMatch(s)) return null;
  for (final p in ['www.', 'm.', 'mobile.']) {
    if (s.startsWith(p) && '.'.allMatches(s).length > 1) s = s.substring(p.length);
  }
  return s;
}

enum Span { today, week, nights }

extension SpanX on Span {
  String get label => switch (this) {
    Span.today => 'Today',
    Span.week => '7 days',
    Span.nights => 'Nights',
  };

  /// The time windows this span covers, ending at [now].
  List<TimeRange> ranges(DateTime now) {
    final today = DateTime(now.year, now.month, now.day);
    switch (this) {
      case Span.today:
        return [TimeRange(today, now)];
      case Span.week:
        return [
          TimeRange(DateTime(now.year, now.month, now.day - 6), now),
        ];
      case Span.nights:
        // The last 7 lockdown windows (9 PM -> 9 AM), including tonight's if it has started.
        final out = <TimeRange>[];
        for (var i = 0; out.length < 7 && i <= 8; i++) {
          final start = DateTime(now.year, now.month, now.day - i, kBlockStartHour);
          final end = DateTime(now.year, now.month, now.day - i + 1, kBlockEndHour);
          if (start.isBefore(now)) {
            out.add(TimeRange(start, end.isAfter(now) ? now : end));
          }
        }
        return out;
    }
  }
}

/// "Nov 24, 9:58 PM"
String fmtDateTime(DateTime t) {
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  final h = t.hour % 12 == 0 ? 12 : t.hour % 12;
  final m = t.minute.toString().padLeft(2, '0');
  return '${months[t.month - 1]} ${t.day}, $h:$m ${t.hour < 12 ? 'AM' : 'PM'}';
}

/// "2h 14m", "45m", "<1m"
String fmtDuration(Duration d) {
  final h = d.inHours;
  final m = d.inMinutes % 60;
  if (h == 0 && m == 0) return '<1m';
  if (h == 0) return '${m}m';
  if (m == 0) return '${h}h';
  return '${h}h ${m}m';
}
