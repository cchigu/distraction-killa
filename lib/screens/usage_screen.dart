import 'package:flutter/material.dart';

import '../models.dart';
import '../native.dart';
import '../theme.dart';

class UsageScreen extends StatefulWidget {
  const UsageScreen({super.key});

  @override
  State<UsageScreen> createState() => _UsageScreenState();
}

class _UsageScreenState extends State<UsageScreen> with WidgetsBindingObserver {
  bool? _hasAccess;
  Span _span = Span.today;
  List<AppUsage> _apps = [];
  int _periods = 1;
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
    final ok = await Native.hasUsageAccess();
    if (!mounted) return;
    setState(() => _hasAccess = ok);
    if (ok) await _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final ranges = _span.ranges(DateTime.now());
    try {
      final apps = await Native.appUsage(ranges);
      if (!mounted) return;
      setState(() {
        _apps = apps;
        _periods = ranges.length;
      });
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: switch (_hasAccess) {
          null => const SizedBox.shrink(),
          false => const _PermissionView(),
          true => _buildStats(),
        },
      ),
    );
  }

  Widget _buildStats() {
    final total = _apps.fold(Duration.zero, (sum, a) => sum + a.time);
    final top = _apps.isEmpty ? Duration.zero : _apps.first.time;

    final subtitle = switch (_span) {
      Span.today => 'screen time today',
      Span.week => '${fmtDuration(total ~/ 7)} a day on average',
      Span.nights => '${fmtDuration(total ~/ _periods)} a night · 9 PM – 9 AM',
    };

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [
          const Text(
            'Distraction Killa',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, letterSpacing: .3),
          ),
          const SizedBox(height: 20),
          SegmentedButton<Span>(
            showSelectedIcon: false,
            segments: [
              for (final s in Span.values) ButtonSegment(value: s, label: Text(s.label)),
            ],
            selected: {_span},
            onSelectionChanged: (sel) {
              setState(() => _span = sel.first);
              _load();
            },
          ),
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
                if (_apps.isEmpty && !_loading)
                  const Padding(
                    padding: EdgeInsets.only(top: 40),
                    child: Center(child: Text('Nothing yet', style: TextStyle(color: kMuted))),
                  ),
                for (final a in _apps.take(25)) _AppRow(app: a, max: top),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AppRow extends StatelessWidget {
  final AppUsage app;
  final Duration max;
  const _AppRow({required this.app, required this.max});

  @override
  Widget build(BuildContext context) {
    final frac = max.inMilliseconds == 0 ? 0.0 : app.time.inMilliseconds / max.inMilliseconds;
    final barColor = Color.lerp(kMuted.withValues(alpha: .5), kAccent, frac)!;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9),
      child: Row(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: app.icon != null
                ? Image.memory(app.icon!, width: 40, height: 40, gaplessPlayback: true)
                : Container(width: 40, height: 40, color: kSurface),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(app.name, maxLines: 1, overflow: TextOverflow.ellipsis,
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
            child: Text(fmtDuration(app.time), textAlign: TextAlign.right,
                style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
  }
}

class _PermissionView extends StatelessWidget {
  const _PermissionView();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.hourglass_bottom_rounded, size: 72, color: kAccent),
          const SizedBox(height: 24),
          const Text('See where your time goes',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 10),
          const Text('Turn on usage access for Distraction Killa.',
              textAlign: TextAlign.center, style: TextStyle(color: kMuted)),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            height: 52,
            child: FilledButton(
              onPressed: Native.openUsageAccessSettings,
              child: const Text('Allow access', style: TextStyle(fontSize: 16)),
            ),
          ),
        ],
      ),
    );
  }
}
