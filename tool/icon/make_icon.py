"""Draws the Distraction Killa icon: a clock cut in two by a sword slash."""
import math, sys

BG = "#0D0F12"
ACCENT = "#5BC0F8"
C = 54.0                      # adaptive icon canvas is 108x108; safe zone r=33
R = 23.0                      # clock radius
ANG = math.radians(-36)       # slash angle (up-right)
d = (math.cos(ANG), math.sin(ANG))      # along the slash
n = (-d[1], d[0])                        # normal (points down-right)

def pt(a, b):  # C + a*d + b*n
    return (C + a * d[0] + b * n[0], C + a * d[1] + b * n[1])

def poly(points):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in points)

def svg(mono=False, with_bg=False, legacy=False):
    col = "#FFFFFF" if mono else ACCENT
    L = 120
    upper = poly([pt(L, 0), pt(L, -L), pt(-L, -L), pt(-L, 0)])
    lower = poly([pt(L, 0), pt(L, L), pt(-L, L), pt(-L, 0)])
    slide, push = 3.6, 2.4   # how far the halves are knocked apart
    ux, uy = -d[0] * slide - n[0] * push, -d[1] * slide - n[1] * push
    lx, ly = d[0] * slide + n[0] * push, d[1] * slide + n[1] * push

    ticks = ""
    for k in range(12):
        a = math.radians(k * 30)
        r1, r2 = (R - 7.5, R - 3.6) if k % 3 == 0 else (R - 5.6, R - 3.8)
        w = 2.6 if k % 3 == 0 else 1.4
        x1, y1 = C + r1 * math.sin(a), C - r1 * math.cos(a)
        x2, y2 = C + r2 * math.sin(a), C - r2 * math.cos(a)
        ticks += f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>'

    clock = f'''
      <circle cx="{C}" cy="{C}" r="{R}" fill="none" stroke="{col}" stroke-width="4.6"/>
      {ticks}
      <line x1="{C}" y1="{C}" x2="{C - 11.5}" y2="{C}" stroke="{col}" stroke-width="3.6" stroke-linecap="round"/>
      <line x1="{C}" y1="{C}" x2="{C}" y2="{C - 15.5}" stroke="{col}" stroke-width="2.6" stroke-linecap="round"/>
      <circle cx="{C}" cy="{C}" r="2.6" fill="{col}"/>'''

    # Blade streak: a thin crescent along the cut, fading at both ends.
    half = 37
    p0, p1 = pt(-half, 0), pt(half, 0)
    c1, c2 = pt(0, -4.2), pt(0, -0.8)
    blade = f"M{p0[0]:.2f},{p0[1]:.2f} Q{c1[0]:.2f},{c1[1]:.2f} {p1[0]:.2f},{p1[1]:.2f} Q{c2[0]:.2f},{c2[1]:.2f} {p0[0]:.2f},{p0[1]:.2f}Z"
    g0, g1 = pt(-half, 0), pt(half, 0)
    white = "#FFFFFF"
    edge = white if mono else ACCENT

    bg = ""
    if with_bg:
        bg = f'<rect x="0" y="0" width="108" height="108" fill="{BG}"/>'
    view = "18 18 72 72" if legacy else "0 0 108 108"
    clip_round = ""
    if legacy:
        bg = f'<rect x="18" y="18" width="72" height="72" rx="16" fill="{BG}"/>'

    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="512" height="512">
  <defs>
    <clipPath id="up"><polygon points="{upper}"/></clipPath>
    <clipPath id="lo"><polygon points="{lower}"/></clipPath>
    <linearGradient id="blade" gradientUnits="userSpaceOnUse" x1="{g0[0]:.2f}" y1="{g0[1]:.2f}" x2="{g1[0]:.2f}" y2="{g1[1]:.2f}">
      <stop offset="0" stop-color="{edge}" stop-opacity="0"/>
      <stop offset="0.35" stop-color="{white}" stop-opacity="1"/>
      <stop offset="0.7" stop-color="{white}" stop-opacity="1"/>
      <stop offset="1" stop-color="{edge}" stop-opacity="0"/>
    </linearGradient>
    <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.6"/></filter>
  </defs>
  {bg}
  <g clip-path="url(#up)"><g transform="translate({ux:.2f},{uy:.2f})">{clock}</g></g>
  <g clip-path="url(#lo)"><g transform="translate({lx:.2f},{ly:.2f})">{clock}</g></g>
  {"" if mono else f'<path d="{blade}" fill="{ACCENT}" opacity="0.9" filter="url(#glow)"/>'}
  <path d="{blade}" fill="url(#blade)"/>
</svg>'''

open("fg.svg", "w").write(svg())
open("mono.svg", "w").write(svg(mono=True))
open("preview.svg", "w").write(svg(legacy=True))
