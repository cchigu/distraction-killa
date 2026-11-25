import 'package:flutter/material.dart';

const kBg = Color(0xFF0D0F12);
const kSurface = Color(0xFF181C22);
const kAccent = Color(0xFF5BC0F8); // light blue
const kText = Color(0xFFF2F5F8);
const kMuted = Color(0xFF8A939E);

ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: kAccent,
    brightness: Brightness.dark,
  ).copyWith(
    primary: kAccent,
    onPrimary: Colors.black,
    surface: kBg,
    onSurface: kText,
    secondaryContainer: kAccent.withValues(alpha: .22),
    onSecondaryContainer: kAccent,
  );

  return ThemeData(
    colorScheme: scheme,
    scaffoldBackgroundColor: kBg,
    splashFactory: InkSparkle.splashFactory,
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: kBg,
      indicatorColor: kAccent.withValues(alpha: .22),
      height: 68,
    ),
  );
}
