import 'package:material_ui/material_ui.dart';

import 'screens/usage_screen.dart';
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
      home: const UsageScreen(),
    );
  }
}
