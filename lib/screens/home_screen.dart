import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../models.dart';
import '../native.dart';
import '../theme.dart';

enum _Tab { apps, sites }

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  bool? _usageOk;
  bool _a11yOk = false;
  _Tab _tab = _Tab.apps;
  Span _span = Span.today;

  List<AppUsage> _apps = [];
  List<SiteUsage> _sites = [];
  List<TimeRange> _ranges = [];
  DateTime? _trackingSince;
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // Re-check when coming back from Settings (e.g. after granting access).
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final usage = await Native.hasUsageAccess();
    final a11y = await Native.hasAccessibility();
    if (!mounted) return;
    setState(() {
      _usageOk = usage;
      _a11yOk = a11y;
    });
    if (usage) await _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final ranges = _span.ranges(DateTime.now());
    try {
      final apps = await Native.appUsage(ranges);
      final sites = _a11yOk ? await Native.siteUsage(ranges) : <SiteUsage>[];
      final since = _a11yOk ? await Native.trackingSince() : null;
      if (!mounted) return;
      setState(() {
        _apps = apps;
        _sites = sites;
        _ranges = ranges;
        _trackingSince = since;
      });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: switch (_usageOk) {
          null => const SizedBox.shrink(),
          false => const _PermissionView(
            icon: Icons.hourglass_bottom_rounded,
            title: 'See where your time goes',
            body: 'Turn on usage access for Distraction Killa.',
            button: 'Allow access',
            onTap: Native.openUsageAccessSettings,
          ),
          true => _buildStats(),
        },
      ),
      bottomNavigationBar: _usageOk == true
          ? NavigationBar(
              selectedIndex: _tab.index,
              onDestinationSelected: (i) => setState(() => _tab = _Tab.values[i]),
              destinations: const [
                NavigationDestination(icon: Icon(Icons.apps_rounded), label: 'Apps'),
                NavigationDestination(icon: Icon(Icons.language_rounded), label: 'Sites'),
              ],
            )
          : null,
    );
  }

  Widget _buildStats() {
    final header = [
      const Text(
        'Distraction Killa',
        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: .3),
      ),
      const SizedBox(height: 20),
      SegmentedButton<Span>(
        showSelectedIcon: false,
        segments: [for (final s in Span.values) ButtonSegment(value: s, label: Text(s.label))],
        selected: {_span},
        onSelectionChanged: (sel) {
          setState(() => _span = sel.first);
          _load();
        },
      ),
    ];

    if (_tab == _Tab.sites && !_a11yOk) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          ...header,
          const SizedBox(height: 40),
          const _PermissionView(
            icon: Icons.language_rounded,
            title: 'Track your websites',
            body: 'Turn on Distraction Killa in Accessibility.\nSites are only read on this phone.',
            button: 'Turn on',
            onTap: Native.openAccessibilitySettings,
            hint: 'Greyed out? App info → ⋮ → Allow restricted settings',
            padded: false,
          ),
        ],
      );
    }

    final rows = _tab == _Tab.apps
        ? [
            for (final a in _apps)
              (leading: _AppIcon(a.icon), title: a.name, time: a.time),
          ]
        : [
            for (final s in _sites)
              (leading: _SiteIcon(s.domain), title: s.domain, time: s.time),
          ];

    final total = rows.fold(Duration.zero, (sum, r) => sum + r.time);
    final top = rows.isEmpty ? Duration.zero : rows.first.time;
    final periods = _ranges.isEmpty ? 1 : _ranges.length;
    final what = _tab == _Tab.apps ? 'screen time' : 'on websites';

    final subtitle = switch (_span) {
      Span.today => '$what today',
      Span.week => '${fmtDuration(total ~/ 7)} a day on average',
      Span.nights => '${fmtDuration(total ~/ periods)} a night · 9 PM – 9 AM',
    };

    // Site tracking only knows what happened after it was switched on.
    final since = _trackingSince;
    final showSince = _tab == _Tab.sites &&
        (since == null || (_ranges.isNotEmpty && since.isAfter(_ranges.last.start)));

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          ...header,
          const SizedBox(height: 32),
          AnimatedOpacity(
            opacity: _loading ? .4 : 1,
            duration: const Duration(milliseconds: 200),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  fmtDuration(total),
                  style: const TextStyle(fontSize: 56, fontWeight: FontWeight.w900, height: 1),
                ),
                const SizedBox(height: 6),
                Text(subtitle, style: const TextStyle(color: kMuted)),
                const SizedBox(height: 28),
                if (rows.isEmpty && !_loading)
                  Padding(
                    padding: const EdgeInsets.only(top: 40),
                    child: Center(
                      child: Text(
                        _tab == _Tab.sites ? 'Browse a bit, then check back' : 'Nothing yet',
                        style: const TextStyle(color: kMuted),
                      ),
                    ),
                  ),
                for (final r in rows.take(25))
                  _UsageRow(leading: r.leading, title: r.title, time: r.time, max: top),
                if (showSince)
                  Padding(
                    padding: const EdgeInsets.only(top: 20),
                    child: Center(
                      child: Text(
                        since == null ? 'Tracking just started' : 'Tracking since ${fmtDateTime(since)}',
                        style: const TextStyle(color: kMuted, fontSize: 12),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UsageRow extends StatelessWidget {
  final Widget leading;
  final String title;
  final Duration time;
  final Duration max;
  const _UsageRow({required this.leading, required this.title, required this.time, required this.max});

  @override
  Widget build(BuildContext context) {
    final frac = max.inMilliseconds == 0 ? 0.0 : time.inMilliseconds / max.inMilliseconds;
    final barColor = Color.lerp(kMuted.withValues(alpha: .5), kAccent, frac)!;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9),
      child: Row(
        children: [
          leading,
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600)),
                const SizedBox(height: 6),
                ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: Stack(
                    children: [
                      Container(height: 6, color: kSurface),
                      FractionallySizedBox(
                        widthFactor: frac.clamp(0.02, 1.0),
                        child: Container(height: 6, color: barColor),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          SizedBox(
            width: 64,
            child: Text(fmtDuration(time), textAlign: TextAlign.right,
                style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}

class _AppIcon extends StatelessWidget {
  final Uint8List? bytes;
  const _AppIcon(this.bytes);

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(10),
      child: bytes != null
          ? Image.memory(bytes!, width: 40, height: 40, gaplessPlayback: true)
          : Container(width: 40, height: 40, color: kSurface),
    );
  }
}

/// The site's own favicon, or its first letter if it has none.
class _SiteIcon extends StatelessWidget {
  final String domain;
  const _SiteIcon(this.domain);

  @override
  Widget build(BuildContext context) {
    final letter = Text(
      domain.characters.first.toUpperCase(),
      style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: kMuted),
    );
    return Container(
      width: 40,
      height: 40,
      alignment: Alignment.center,
      decoration: BoxDecoration(color: kSurface, borderRadius: BorderRadius.circular(10)),
      child: Image.network(
        'https://$domain/favicon.ico',
        width: 22,
        height: 22,
        filterQuality: FilterQuality.medium,
        errorBuilder: (_, _, _) => letter,
      ),
    );
  }
}

class _PermissionView extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  final String button;
  final VoidCallback onTap;
  final String? hint;
  final bool padded;

  const _PermissionView({
    required this.icon,
    required this.title,
    required this.body,
    required this.button,
    required this.onTap,
    this.hint,
    this.padded = true,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.all(padded ? 32 : 0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 72, color: kAccent),
          const SizedBox(height: 24),
          Text(title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 10),
          Text(body, textAlign: TextAlign.center, style: const TextStyle(color: kMuted)),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton(
              onPressed: onTap,
              child: Text(button, style: const TextStyle(fontSize: 16)),
            ),
          ),
          if (hint != null) ...[
            const SizedBox(height: 14),
            Text(hint!, textAlign: TextAlign.center,
                style: const TextStyle(color: kMuted, fontSize: 12)),
          ],
        ],
      ),
    );
  }
}
