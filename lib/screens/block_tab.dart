import 'package:flutter/material.dart';

import '../models.dart';
import '../native.dart';
import '../theme.dart';
import '../widgets.dart';

/// Pick what gets blocked from 9 PM to 9 AM.
class BlockTab extends StatefulWidget {
  final bool a11yOk;
  const BlockTab({super.key, required this.a11yOk});

  @override
  State<BlockTab> createState() => _BlockTabState();
}

class _BlockTabState extends State<BlockTab> {
  static const _appsShown = 10;

  BlockRules? _rules;
  List<AppUsage> _apps = [];
  Map<String, Duration> _appTime = {};
  List<String> _sites = [];
  Map<String, Duration> _siteTime = {};
  List<String> _otherBrowsers = [];
  bool _showAllApps = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(BlockTab old) {
    super.didUpdateWidget(old);
    if (old.a11yOk != widget.a11yOk) _load();
  }

  Future<void> _load() async {
    final week = Span.week.ranges(DateTime.now());
    final rules = await Native.blockRules();
    final installed = await Native.installedApps();
    final usage = await Native.appUsage(week);
    final sites = widget.a11yOk ? await Native.siteUsage(week) : <SiteUsage>[];
    final others = await Native.otherBrowsers();

    final appTime = {for (final a in usage) a.package: a.time};
    final siteTime = {for (final s in sites) s.domain: s.time};

    // Already-blocked first, then most used. Sorted once so rows don't jump while tapping.
    int rank(bool blocked, Duration? t) => (blocked ? 1 << 40 : 0) + (t?.inSeconds ?? 0);
    installed.sort((a, b) => rank(rules.apps.contains(b.package), appTime[b.package])
        .compareTo(rank(rules.apps.contains(a.package), appTime[a.package])));
    final siteList = {...rules.sites, ...sites.take(15).map((s) => s.domain)}.toList()
      ..sort((a, b) => rank(rules.sites.contains(b), siteTime[b])
          .compareTo(rank(rules.sites.contains(a), siteTime[a])));

    if (!mounted) return;
    setState(() {
      _rules = rules;
      _apps = installed;
      _appTime = appTime;
      _sites = siteList;
      _siteTime = siteTime;
      _otherBrowsers = others;
    });
  }

  /// 9 PM – 9 AM with blocking on: only stricter changes allowed (also enforced natively).
  bool get _locked => (_rules?.enabled ?? false) && inBlockWindow(DateTime.now());

  void _showLocked() {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(const SnackBar(content: Text('Locked until 9 AM')));
  }

  Future<void> _setEnabled(bool v) async {
    if (!v && _locked) return _showLocked();
    if (v && inBlockWindow(DateTime.now())) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Lock until 9 AM?'),
          content: const Text("You won't be able to turn this off or unblock anything until 9 AM."),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Lock')),
          ],
        ),
      );
      if (ok != true) return;
    }
    _update((r) => r.enabled = v);
  }

  void _update(void Function(BlockRules r) change) {
    final r = _rules;
    if (r == null) return;
    setState(() => change(r));
    Native.saveBlockRules(r);
  }

  void _toggle(Set<String> set, String item) {
    if (set.contains(item) && _locked) return _showLocked();
    _update((_) => set.contains(item) ? set.remove(item) : set.add(item));
  }

  Future<void> _addSite() async {
    final domain = await showDialog<String>(context: context, builder: (_) => const _AddSiteDialog());
    if (domain == null) return;
    _update((r) => r.sites.add(domain));
    if (!_sites.contains(domain)) setState(() => _sites.insert(0, domain));
  }

  @override
  Widget build(BuildContext context) {
    final rules = _rules;
    if (rules == null) return const SizedBox.shrink();

    final apps = _showAllApps ? _apps : _apps.take(_appsShown).toList();

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
          _StatusCard(enabled: rules.enabled, locked: _locked, onChanged: _setEnabled),
          if (!widget.a11yOk) ...[
            const SizedBox(height: 12),
            _Notice(
              text: 'Blocking needs Distraction Killa turned on in Accessibility.',
              button: 'Turn on',
              onTap: Native.openAccessibilitySettings,
            ),
          ],
          const SizedBox(height: 28),
          _SectionHeader('Apps', rules.apps.length),
          for (final a in apps)
            _PickRow(
              leading: AppIcon(a.icon),
              title: a.name,
              time: _appTime[a.package],
              selected: rules.apps.contains(a.package),
              locked: _locked,
              onTap: () => _toggle(rules.apps, a.package),
            ),
          if (_apps.length > _appsShown)
            Center(
              child: TextButton(
                onPressed: () => setState(() => _showAllApps = !_showAllApps),
                child: Text(_showAllApps ? 'Show less' : 'Show all ${_apps.length} apps'),
              ),
            ),
          const SizedBox(height: 24),
          _SectionHeader('Sites', rules.sites.length),
          for (final s in _sites)
            _PickRow(
              leading: SiteIcon(s),
              title: s,
              time: _siteTime[s],
              selected: rules.sites.contains(s),
              locked: _locked,
              onTap: () => _toggle(rules.sites, s),
            ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: _addSite,
            icon: const Icon(Icons.add_rounded),
            label: const Text('Add site'),
            style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
          ),
          if (_otherBrowsers.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 20),
              child: Text(
                'Also blocked at night: ${_otherBrowsers.join(', ')}',
                textAlign: TextAlign.center,
                style: const TextStyle(color: kMuted, fontSize: 12),
              ),
            ),
        ],
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  final bool enabled;
  final bool locked;
  final ValueChanged<bool> onChanged;
  const _StatusCard({required this.enabled, required this.locked, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final status = !enabled
        ? 'Off'
        : locked
            ? 'Locked · until 9 AM'
            : 'Starts at 9 PM';

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 14, 18),
      decoration: BoxDecoration(
        color: enabled ? kAccent.withValues(alpha: .12) : kSurface,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('9 PM – 9 AM', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
                const SizedBox(height: 4),
                Text(status, style: TextStyle(color: enabled ? kAccent : kMuted)),
              ],
            ),
          ),
          if (locked)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 14),
              child: Icon(Icons.lock_rounded, color: kAccent, size: 30),
            )
          else
            Switch(value: enabled, onChanged: onChanged),
        ],
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  final String text;
  final String button;
  final VoidCallback onTap;
  const _Notice({required this.text, required this.button, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: kSurface, borderRadius: BorderRadius.circular(16)),
      child: Row(
        children: [
          Expanded(child: Text(text, style: const TextStyle(color: kMuted))),
          const SizedBox(width: 12),
          FilledButton(onPressed: onTap, child: Text(button)),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final int count;
  const _SectionHeader(this.title, this.count);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        children: [
          Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const Spacer(),
          if (count > 0) Text('$count blocked', style: const TextStyle(color: kAccent)),
        ],
      ),
    );
  }
}

class _PickRow extends StatelessWidget {
  final Widget leading;
  final String title;
  final Duration? time;
  final bool selected;
  final bool locked;
  final VoidCallback onTap;

  const _PickRow({
    required this.locked,
    required this.leading,
    required this.title,
    required this.time,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = time;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
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
                  if (t != null && t > Duration.zero)
                    Text('${fmtDuration(t)} this week',
                        style: const TextStyle(color: kMuted, fontSize: 12)),
                ],
              ),
            ),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 150),
              child: Icon(
                selected
                    ? (locked ? Icons.lock_rounded : Icons.check_circle_rounded)
                    : Icons.circle_outlined,
                key: ValueKey((selected, locked)),
                color: selected ? kAccent : kMuted,
                size: 28,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AddSiteDialog extends StatefulWidget {
  const _AddSiteDialog();

  @override
  State<_AddSiteDialog> createState() => _AddSiteDialogState();
}

class _AddSiteDialogState extends State<_AddSiteDialog> {
  final _ctrl = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _submit() {
    final d = normalizeDomain(_ctrl.text);
    if (d == null) {
      setState(() => _error = 'Enter a site like youtube.com');
    } else {
      Navigator.pop(context, d);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Block a site'),
      content: TextField(
        controller: _ctrl,
        autofocus: true,
        keyboardType: TextInputType.url,
        autocorrect: false,
        decoration: InputDecoration(hintText: 'youtube.com', errorText: _error),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(onPressed: _submit, child: const Text('Block')),
      ],
    );
  }
}
