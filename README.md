# Distraction Killa

Android app (Flutter + Kotlin) that finds your phone time sinks and blocks them from 9 PM to 9 AM.

## Milestones
1. **Usage stats** – per-app screen time: Today / 7 days / Nights (9 PM–9 AM) ✅
2. **Website tracking** – time per site in Firefox, Chrome, Samsung Internet (accessibility service; stays on the phone) ✅
3. **Blocker** – pick apps + sites (sorted by your usage), "Killed." screen 9 PM–9 AM, unreadable browsers blocked too ✅
4. **Lockdown** – 9 PM–9 AM: switch + lists locked (can only block more), own settings + date & time guarded; uninstall from home screen still works ✅
5. **Polish** – slashed-clock icon (adaptive + themed), dark splash screen, v1.0.0 ✅

## Run
```
flutter pub get
flutter run --release   # phone plugged in with USB debugging
```
Needs Flutter 3.47+. First launch: allow **Usage access**, then turn on **Distraction Killa** in Accessibility (Sites or Block tab).

## Icon
`tool/icon/make_icon.py` draws the icon as SVG; PNGs are rendered from it with `rsvg-convert`.
