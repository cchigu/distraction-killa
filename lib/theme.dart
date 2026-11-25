import 'package:flutter/material.dart';

const kBg = Color(0xFF0D0D0F);
const kSurface = Color(0xFF1A1A1E);
const kAccent = Color(0xFFFF3B30);
const kMuted = Color(0xFF8E8E93);

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: kAccent,
    brightness: Brightness.dark,
  ).copyWith(primary: kAccent, onPrimary: Colors.white, surface: kBg);

  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: kBg,
    splashFactory: InkSparkle.splashFactory,
  );
}
