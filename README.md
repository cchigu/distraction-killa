# Distraction Killa

Android app (Flutter + Kotlin) that finds your phone time sinks and blocks them from 9 PM to 9 AM.

## Milestones
1. **Usage stats** – per-app screen time: Today / 7 days / Nights (9 PM–9 AM) ✅
2. **Website tracking** – time per site in Firefox, Chrome, Samsung Internet (accessibility service) ✅
3. **Blocker** – pick apps + sites (suggested from your stats), block screen 9 PM–9 AM
4. **Lockdown** – hard to bypass: guards its own settings/uninstall during the window, delayed loosening
5. **Polish** – icon, final UI pass, release build

## Run
```
flutter pub get
flutter run        # phone plugged in with USB debugging
```
Needs Flutter 3.47+.
