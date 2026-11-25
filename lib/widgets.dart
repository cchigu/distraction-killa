import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'theme.dart';

class AppIcon extends StatelessWidget {
  final Uint8List? bytes;
  const AppIcon(this.bytes, {super.key});

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
class SiteIcon extends StatelessWidget {
  final String domain;
  const SiteIcon(this.domain, {super.key});

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

class PermissionView extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  final String button;
  final VoidCallback onTap;
  final String? hint;
  final bool padded;

  const PermissionView({
    super.key,
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
