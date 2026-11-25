import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'theme.dart';

void main() => runApp(const DistractionKilla());

class DistractionKilla extends StatelessWidget {
  const DistractionKilla({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Distraction Killa',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      home: const HomeScreen(),
    );
  }
}
