#!/usr/bin/env python3
"""
Generates the PadRelay icon from one shape definition:
  - Android adaptive icon (vector foreground with a stacked-offset shadow,
    monochrome layer for themed icons, notification icon)
  - PC icons (assets/icon.png, icon.ico, icon.icns) with a real blurred shadow

Run:  python tools/make_icons.py        (needs Pillow)
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "pc", "assets")

BG = "#000000"
SHADOW = "#000000"
FG = "#FFFFFF"
FG_SOFT = "#FFFFFF"

DY = 8.0                # vertical shift inside the 108 x 108 adaptive-icon canvas

# ---- geometry (108 x 108 viewport, safe zone is the central 72 x 72) --------
DECK = (20.0, 60.0 + DY, 68.0, 5.0, 2.5)      # x, y, w, h, radius
TOWERS = [(33.0, 30.0 + DY, 5.0, 35.0, 1.6), (70.0, 30.0 + DY, 5.0, 35.0, 1.6)]
LIGHTS = []  # (kept for experimentation; round tops looked like figures)
TOP_L, TOP_R = (35.5, 34.0 + DY), (72.5, 34.0 + DY)
SAG = 62.0 + DY      # quadratic control point y -> cable dips ~14 units
END_L, END_R = (20.5, 60.5 + DY), (87.5, 60.5 + DY)
DECK_TOP = 60.0 + DY


def cable_pt(t):
    x = (1 - t) ** 2 * TOP_L[0] + 2 * t * (1 - t) * 54.0 + t ** 2 * TOP_R[0]
    y = (1 - t) ** 2 * TOP_L[1] + 2 * t * (1 - t) * SAG + t ** 2 * TOP_R[1]
    return x, y


HANGERS = [(cable_pt(i / 6.0), (cable_pt(i / 6.0)[0], DECK_TOP)) for i in range(1, 6)]
CABLE_W, HANGER_W = 2.2, 1.2


def rrect_path(x, y, w, h, r):
    return (f"M{x + r:.2f},{y:.2f}h{w - 2 * r:.2f}a{r},{r} 0 0 1 {r},{r}v{h - 2 * r:.2f}"
            f"a{r},{r} 0 0 1 -{r},{r}h-{w - 2 * r:.2f}a{r},{r} 0 0 1 -{r},-{r}"
            f"v-{h - 2 * r:.2f}a{r},{r} 0 0 1 {r},-{r}z")


def circle_path(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r},{r} 0 1 0 {2 * r},0a{r},{r} 0 1 0 -{2 * r},0z"


def shapes():
    """(kind, pathData, width) list. kind: fill | stroke | hanger"""
    out = [("fill", rrect_path(*DECK), 0)]
    for t in TOWERS:
        out.append(("fill", rrect_path(*t), 0))
    for c in LIGHTS:
        out.append(("fill", circle_path(*c), 0))
    out.append(("stroke", f"M{TOP_L[0]},{TOP_L[1]}Q54,{SAG} {TOP_R[0]},{TOP_R[1]}", CABLE_W))
    out.append(("stroke", f"M{END_L[0]},{END_L[1]}L{TOP_L[0]},{TOP_L[1]}", CABLE_W))
    out.append(("stroke", f"M{TOP_R[0]},{TOP_R[1]}L{END_R[0]},{END_R[1]}", CABLE_W))
    for a, b in HANGERS:
        out.append(("hanger", f"M{a[0]:.2f},{a[1]:.2f}L{b[0]:.2f},{b[1]:.2f}", HANGER_W))
    return out


def vec_path(kind, d, w, color, alpha=1.0):
    a = "" if alpha >= 1 else f"\n        android:{'fillAlpha' if kind == 'fill' else 'strokeAlpha'}=\"{alpha}\""
    if kind == "fill":
        return f'    <path\n        android:pathData="{d}"\n        android:fillColor="{color}"{a}/>\n'
    return (f'    <path\n        android:pathData="{d}"\n        android:strokeColor="{color}"\n'
            f'        android:strokeWidth="{w}"\n        android:strokeLineCap="round"{a}/>\n')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def android_vectors():
    head = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
    # foreground: stacked, offset, fading copies fake a soft drop shadow
    fg = head
    layers = 6
    for i in range(layers, 0, -1):
        off = i * 0.7
        fg += f'    <group android:translateX="{off * 0.8:.2f}" android:translateY="{off * 1.25:.2f}">\n'
        for kind, d, w in shapes():
            fg += vec_path(kind, d, w + (i * 0.25 if kind != "fill" else 0), SHADOW, 0.11)
        fg += "    </group>\n"
    for kind, d, w in shapes():
        color = FG_SOFT if kind == "hanger" else FG
        fg += vec_path(kind, d, w, color)
    fg += "</vector>\n"
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), fg)

    mono = head
    for kind, d, w in shapes():
        mono += vec_path(kind, d, w, "#FF000000")
    mono += "</vector>\n"
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), mono)

    stat = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="72"\n    android:viewportHeight="72">\n'
            '    <group android:translateX="-18" android:translateY="-22">\n')
    for kind, d, w in shapes():
        stat += vec_path(kind, d, w + (0.8 if kind != "fill" else 0), "#FFFFFFFF")
    stat += "    </group>\n</vector>\n"
    write(os.path.join(RES, "drawable", "ic_stat_bridge.xml"), stat)

    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"),
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
          '    <background android:drawable="@color/ic_launcher_background"/>\n'
          '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
          '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
          '</adaptive-icon>\n')
    write(os.path.join(RES, "values", "colors.xml"),
          '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
          f'    <color name="ic_launcher_background">{BG}</color>\n</resources>\n')


# ---- PC raster icon ---------------------------------------------------------
def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def draw_shapes(draw, k, ox, oy, color_fill, color_soft=None):
    def P(x, y):
        return ((x - 12) * k + ox, (y - 12) * k + oy)

    def rr(x, y, w, h, r, fill):
        a, b = P(x, y), P(x + w, y + h)
        draw.rounded_rectangle([a, b], radius=r * k, fill=fill)

    rr(*DECK, color_fill)
    for t in TOWERS:
        rr(*t, color_fill)
    for cx, cy, r in LIGHTS:
        c = P(cx, cy)
        draw.ellipse([c[0] - r * k, c[1] - r * k, c[0] + r * k, c[1] + r * k], fill=color_fill)

    def stroke(points, width, fill):
        pts = [P(*p) for p in points]
        draw.line(pts, fill=fill, width=int(width * k), joint="curve")
        for p in (pts[0], pts[-1]) if len(pts) == 2 else pts:
            r = width * k / 2
            draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=fill)

    stroke([cable_pt(i / 80.0) for i in range(81)], CABLE_W, color_fill)
    stroke([END_L, TOP_L], CABLE_W, color_fill)
    stroke([TOP_R, END_R], CABLE_W, color_fill)
    for a, b in HANGERS:
        stroke([a, b], HANGER_W, color_soft or color_fill)


def render_png(size=1024):
    S = size * 2
    k = S / 84.0
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(base).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=hex_rgb(BG) + (255,))

    mask = Image.new("L", (S, S), 0)
    draw_shapes(ImageDraw.Draw(mask), k, 2.2 * k, 3.4 * k, 255)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=2.0 * k))
    shadow = Image.new("RGBA", (S, S), hex_rgb(SHADOW) + (0,))
    shadow.putalpha(mask.point(lambda v: int(v * 0.55)))
    base = Image.alpha_composite(base, shadow)

    fg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw_shapes(ImageDraw.Draw(fg), k, 0, 0, hex_rgb(FG) + (255,), hex_rgb(FG_SOFT) + (255,))
    base = Image.alpha_composite(base, fg)

    clip = Image.new("L", (S, S), 0)
    ImageDraw.Draw(clip).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    base.putalpha(Image.composite(base.getchannel("A"), Image.new("L", (S, S), 0), clip))
    return base.resize((size, size), Image.LANCZOS)


def pc_icons():
    os.makedirs(ASSETS, exist_ok=True)
    big = render_png(1024)
    big.resize((512, 512), Image.LANCZOS).save(os.path.join(ASSETS, "icon.png"))
    big.save(os.path.join(ASSETS, "icon.ico"),
             sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
    big.save(os.path.join(ASSETS, "icon.icns"))


if __name__ == "__main__":
    android_vectors()
    pc_icons()
    print("icons written")
