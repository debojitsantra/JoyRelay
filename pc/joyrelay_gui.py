#!/usr/bin/env python3
import collections
import getpass
import hashlib
import os
import sys
import tempfile
import tkinter as tk
from tkinter import font as tkfont

import portalocker
from joyrelay_core import PORT, Receiver, local_ips, make_backend

APP_VERSION = "0.5"

BG = "#000000"
CARD = "#111111"
CARD_HI = "#1B1B1B"
BORDER = "#555555"
ACCENT = "#FFFFFF"
ACCENT_HI = "#DDDDDD"
TEXT = "#FFFFFF"
MUTED = "#AAAAAA"
GOOD = "#FFFFFF"
WARN = "#CCCCCC"
BAD = "#888888"
OFF = "#222222"


def resource_path(rel):
    base = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, rel)


class Pill(tk.Canvas):
    """Square button. kind: primary | secondary | danger"""

    COLORS = {
        "primary": (ACCENT, "#000000", ACCENT_HI, ACCENT),
        "danger": (BAD, "#000000", "#AAAAAA", BAD),
        "secondary": (CARD_HI, TEXT, "#333333", BORDER),
    }

    def __init__(self, master, text, command, kind="primary", height=42, font=None):
        super().__init__(master, height=height, bg=master["bg"], highlightthickness=0,
                         cursor="hand2")
        self._text, self._cmd, self._kind, self._hover, self._font = text, command, kind, False, font
        self.bind("<Configure>", lambda e: self._draw())
        self.bind("<Enter>", lambda e: self._set_hover(True))
        self.bind("<Leave>", lambda e: self._set_hover(False))
        self.bind("<ButtonRelease-1>", lambda e: self._cmd())

    def _set_hover(self, v):
        self._hover = v
        self._draw()

    def configure_pill(self, text=None, kind=None):
        if text is not None:
            self._text = text
        if kind is not None:
            self._kind = kind
        self._draw()

    def _draw(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        fill, fg, hover, line = self.COLORS[self._kind]
        self.create_rectangle(1, 1, w - 1, h - 1, fill=hover if self._hover else fill, outline=line)
        self.create_text(w / 2, h / 2, text=self._text, fill=fg, font=self._font)


class App:
    def __init__(self, root):
        self.root = root
        root.title(f"JoyRelay {APP_VERSION}")
        root.configure(bg=BG)
        root.geometry("1100x760")
        root.minsize(900, 600)

        fams = set(tkfont.families())
        base = next((f for f in ("Segoe UI", "SF Pro Text", "Helvetica Neue", "Noto Sans",
                                 "DejaVu Sans") if f in fams), "TkDefaultFont")
        mono = next((f for f in ("Consolas", "Menlo", "DejaVu Sans Mono") if f in fams), "TkFixedFont")
        self.f_small = (base, 9)
        self.f_cap = (base, 8, "bold")
        self.f_body = (base, 10)
        self.f_bold = (base, 11, "bold")
        self.f_big = (base, 40, "bold")
        self.f_tile = (base, 16, "bold")
        self.f_mono = (mono, 13, "bold")

        try:
            self.icon_big = tk.PhotoImage(file=resource_path("assets/icon.png"))
            root.iconphoto(True, self.icon_big.subsample(4))
        except Exception:
            self.icon_big = None

        self.backend = make_backend()
        self.tray_icon = None
        self.rx = None
        self.rtts = collections.deque(maxlen=90)
        self._last_rtt_seen = None
        self._snap = {}

        self._build()
        self.start()
        self._tick()
        root.protocol("WM_DELETE_WINDOW", self.close_to_tray)

    def close_to_tray(self):
        if self.tray_icon is not None:
            self.root.withdraw()
            return
        try:
            import pystray
            from PIL import Image

            image = Image.open(resource_path("assets/icon.png")).convert("RGBA")
            open_item = pystray.MenuItem(
                "Open JoyRelay", lambda icon, item: self.root.after(0, self.restore), default=True
            )
            quit_item = pystray.MenuItem(
                "Quit", lambda icon, item: self.root.after(0, self.quit)
            )
            self.tray_icon = pystray.Icon(
                "JoyRelay", image, f"JoyRelay {APP_VERSION} - running in the background",
                pystray.Menu(open_item, quit_item)
            )
            self.tray_icon.run_detached()
            self.root.withdraw()
        except Exception as exc:
            self.msg.configure(text=f"Could not create the tray icon: {exc}")
            self.root.iconify()

    def restore(self):
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def card(self, parent):
        return tk.Frame(parent, bg=CARD, highlightthickness=1, highlightbackground=BORDER)

    def cap(self, parent, text):
        return tk.Label(parent, text=text.upper(), bg=parent["bg"], fg=MUTED, font=self.f_cap)

    def _build(self):
        r = self.root
        r.grid_columnconfigure(0, weight=6, uniform="columns")
        r.grid_columnconfigure(1, weight=5, uniform="columns")
        r.grid_rowconfigure(1, weight=1)

        head = tk.Frame(r, bg=BG)
        head.grid(row=0, column=0, columnspan=2, sticky="ew", padx=20, pady=(10, 8))
        brand = tk.Frame(head, bg=BG)
        brand.pack(anchor="center")
        logo = tk.Canvas(brand, width=46, height=46, bg=BG, highlightthickness=0)
        logo.pack(side="left", padx=(0, 12))
        logo.create_rectangle(14, 28, 32, 33, fill=TEXT, outline=TEXT)
        logo.create_rectangle(18, 17, 21, 30, fill=TEXT, outline=TEXT)
        logo.create_rectangle(25, 17, 28, 30, fill=TEXT, outline=TEXT)
        logo.create_line(19, 18, 21, 22, 23, 25, 25, 22, 27, 18,
                         fill=TEXT, width=2, capstyle="butt", joinstyle="bevel")
        self.pill = tk.Canvas(brand, width=190, height=32, bg=BG, highlightthickness=0)
        self.pill.pack(side="left")

        left = tk.Frame(r, bg=BG)
        left.grid(row=1, column=0, sticky="nsew", padx=(20, 8), pady=(4, 18))
        right = tk.Frame(r, bg=BG)
        right.grid(row=1, column=1, sticky="nsew", padx=(8, 20), pady=(4, 18))

        lat = self.card(left)
        lat.pack(fill="x")
        self.cap(lat, "Round-trip latency").pack(anchor="w", padx=16, pady=(12, 0))
        row = tk.Frame(lat, bg=CARD)
        row.pack(anchor="w", padx=16)
        self.lat_big = tk.Label(row, text="--", bg=CARD, fg=MUTED, font=self.f_big)
        self.lat_big.pack(side="left")
        tk.Label(row, text=" ms", bg=CARD, fg=MUTED, font=self.f_body).pack(side="left", pady=(14, 0))
        self.lat_sub = tk.Label(lat, text="waiting for the phone...", bg=CARD, fg=MUTED,
                                font=self.f_small, anchor="w")
        self.lat_sub.pack(anchor="w", fill="x", padx=16)
        self.graph = tk.Canvas(lat, height=82, bg=CARD, highlightthickness=0)
        self.graph.pack(fill="x", padx=16, pady=(8, 14))

        pc = self.card(left)
        pc.pack(fill="both", expand=True, pady=(12, 0))
        self.cap(pc, "Controller").pack(anchor="w", padx=16, pady=(12, 0))
        self.pad = tk.Canvas(pc, bg=CARD, highlightthickness=0, height=300)
        self.pad.pack(fill="both", expand=True, padx=16, pady=(4, 14))

        tiles = tk.Frame(right, bg=BG)
        tiles.pack(fill="x")
        tiles.grid_columnconfigure(0, weight=1, uniform="tiles")
        tiles.grid_columnconfigure(1, weight=1, uniform="tiles")
        self.tile_vals = []
        for i, name in enumerate(("Packets/s", "Phone")):
            tile = self.card(tiles)
            tile.grid(row=0, column=i, sticky="ew", padx=(0, 5) if i == 0 else (5, 0))
            self.cap(tile, name).pack(anchor="w", padx=14, pady=(10, 0))
            value = tk.Label(tile, text="--", bg=CARD, fg=TEXT,
                             font=self.f_tile if i == 0 else self.f_mono, anchor="w")
            value.pack(anchor="w", padx=14, pady=(2, 10))
            self.tile_vals.append(value)

        cn = self.card(right)
        cn.pack(fill="both", expand=True, pady=(12, 0))
        self.cap(cn, "Connection").pack(anchor="w", padx=16, pady=(12, 0))
        tk.Label(cn, text="Enter one of these in the phone app:", bg=CARD, fg=MUTED,
                 font=self.f_small).pack(anchor="w", padx=16, pady=(6, 4))
        ips = local_ips() or ["(no network found)"]
        for ip in ips[:3]:
            line = tk.Frame(cn, bg=CARD_HI, highlightthickness=1, highlightbackground=BORDER)
            line.pack(fill="x", padx=16, pady=3)
            tk.Label(line, text=ip, bg=CARD_HI, fg=TEXT, font=self.f_mono).pack(
                side="left", padx=12, pady=8)
            copy = tk.Label(line, text="Copy", bg=CARD_HI, fg=ACCENT_HI, font=self.f_cap,
                            cursor="hand2")
            copy.pack(side="right", padx=12)
            copy.bind("<Button-1>", lambda e, v=ip, w=copy: self.copy(v, w))

        form = tk.Frame(cn, bg=CARD)
        form.pack(fill="x", padx=16, pady=(10, 0))
        form.grid_columnconfigure(1, weight=1)
        tk.Label(form, text="Port", bg=CARD, fg=MUTED, font=self.f_small).grid(
            row=0, column=0, sticky="w", pady=3)
        self.port_var = tk.StringVar(value=str(PORT))
        self.entry(form, self.port_var).grid(row=0, column=1, sticky="ew", padx=(12, 0), pady=3)
        tk.Label(form, text="Only this phone IP", bg=CARD, fg=MUTED, font=self.f_small).grid(
            row=1, column=0, sticky="w", pady=3)
        self.allow_var = tk.StringVar()
        self.entry(form, self.allow_var).grid(row=1, column=1, sticky="ew", padx=(12, 0), pady=3)
        tk.Label(form, text="(blank = any phone on your network)", bg=CARD, fg=MUTED,
                 font=self.f_small).grid(row=2, column=1, sticky="w", padx=(12, 0))

        self.toggle = Pill(cn, "Stop", self.on_toggle, "danger", font=self.f_bold)
        self.toggle.pack(fill="x", padx=16, pady=(14, 0))
        tk.Label(cn, text=f"Backend: {self.backend.name}", bg=CARD, fg=TEXT,
                 font=self.f_small, anchor="w").pack(fill="x", padx=16, pady=(12, 0))
        self.note = tk.Label(cn, text=self.backend.note, bg=CARD,
                             fg=WARN if self.backend.note else MUTED, font=self.f_small,
                             wraplength=440, justify="left", anchor="w")
        self.note.pack(fill="x", padx=16, pady=(2, 0))
        self.msg = tk.Label(cn, text="", bg=CARD, fg=BAD, font=self.f_small,
                            wraplength=440, justify="left", anchor="w")
        self.msg.pack(fill="x", padx=16, pady=(4, 12))

    def entry(self, parent, var):
        return tk.Entry(parent, textvariable=var, bg=CARD_HI, fg=TEXT, insertbackground=TEXT,
                        relief="flat", highlightthickness=1, highlightbackground=BORDER,
                        highlightcolor=ACCENT, font=self.f_body)

    def copy(self, value, widget):
        self.root.clipboard_clear()
        self.root.clipboard_append(value)
        widget.configure(text="Copied")
        self.root.after(1200, lambda: widget.configure(text="Copy"))

    def start(self):
        try:
            port = int(self.port_var.get())
        except ValueError:
            self.msg.configure(text="Port must be a number")
            return
        self.rx = Receiver(self.backend, port, self.allow_var.get().strip() or None)
        try:
            self.rx.start()
            self.msg.configure(text="")
        except OSError as exc:
            self.msg.configure(text=f"Could not listen on port {port}: {exc}")
            self.rx = None
        self.rtts.clear()
        self._last_rtt_seen = None

    def stop(self):
        if self.rx:
            self.rx.stop()
            self.rx = None

    def on_toggle(self):
        if self.rx and self.rx.running:
            self.stop()
        else:
            self.start()

    def quit(self):
        if self.tray_icon is not None:
            self.tray_icon.stop()
            self.tray_icon = None
        self.stop()
        self.backend.close()
        self.root.destroy()

    def _tick(self):
        s = self.rx.snapshot() if self.rx else {}
        running = bool(s.get("running"))
        connected = bool(s.get("connected"))
        if not running:
            self.set_pill("STOPPED", MUTED)
        elif not connected:
            self.set_pill("WAITING FOR PHONE", WARN)
        else:
            self.set_pill("CONNECTED", GOOD)
        self.toggle.configure_pill("Stop" if running else "Start", "danger" if running else "primary")

        rtt = s.get("rtt_ms") if connected else None
        if rtt is not None and s.get("rtt_at") != self._last_rtt_seen:
            self._last_rtt_seen = s.get("rtt_at")
            self.rtts.append(rtt)
        if rtt is None:
            self.lat_big.configure(text="--", fg=MUTED)
            self.lat_sub.configure(text="waiting for the phone..." if running else "receiver stopped")
        else:
            col = GOOD if rtt < 10 else WARN if rtt < 25 else BAD
            self.lat_big.configure(text=f"{rtt:.1f}", fg=col)
            h = list(self.rtts)
            jit = sum(abs(h[i] - h[i - 1]) for i in range(1, len(h))) / max(1, len(h) - 1)
            self.lat_sub.configure(
                text=f"avg {sum(h[-40:]) / len(h[-40:]):.1f}  |  min {min(h):.1f}  |  "
                     f"max {max(h):.1f}  |  jitter {jit:.1f} ms")
        self.draw_graph(list(self.rtts) if connected else [])

        self.tile_vals[0].configure(text=str(s.get("pps", 0)) if running else "--")
        self.tile_vals[1].configure(text=s.get("phone_ip") or "--" if connected else "--")

        self.draw_pad(s if connected else {})
        self.root.after(50, self._tick)

    def set_pill(self, label, color):
        c = self.pill
        c.delete("all")
        w, h = int(c["width"]), int(c["height"])
        c.create_rectangle(1, 1, w - 1, h - 1, fill=BG, outline=color)
        c.create_text(w / 2, h / 2, text="●  " + label, fill=color, font=self.f_cap)

    def draw_graph(self, data):
        cv = self.graph
        cv.delete("all")
        w, h = cv.winfo_width(), cv.winfo_height()
        if w < 10:
            return
        base = h - 14
        cv.create_line(0, base, w, base, fill=BORDER)
        cv.create_line(0, base / 2, w, base / 2, fill=BORDER)
        if len(data) < 2:
            cv.create_text(0, h - 2, text="waiting for data...", fill=MUTED, anchor="sw", font=self.f_small)
            return
        top = max(10.0, max(data) * 1.25)
        step = w / 89.0
        x0 = w - step * (len(data) - 1)
        pts = []
        for i, v in enumerate(data):
            pts += [x0 + i * step, base - (min(v, top) / top) * (base - 4) - 2]
        cv.create_line(pts, fill=ACCENT_HI, width=2)
        cv.create_text(0, h - 2, text="0", fill=MUTED, anchor="sw", font=self.f_small)
        cv.create_text(w, h - 2, text=f"{top:.0f} ms", fill=MUTED, anchor="se", font=self.f_small)

    def draw_pad(self, state):
        cv = self.pad
        cv.delete("all")
        width, height = cv.winfo_width(), cv.winfo_height()
        scale = min(width / 440, height / 400)
        if scale <= 0:
            return
        ox, oy = (width - 440 * scale) / 2, (height - 400 * scale) / 2

        def point(x, y):
            return ox + x * scale, oy + y * scale

        def oval(x, y, radius, fill, outline=""):
            px, py = point(x, y)
            radius *= scale
            cv.create_oval(px-radius, py-radius, px+radius, py+radius,
                           fill=fill, outline=outline, width=max(1, 2.5 * scale))

        def square(x1, y1, x2, y2, fill, outline=""):
            a, b = point(x1, y1)
            c, d = point(x2, y2)
            cv.create_rectangle(a, b, c, d, fill=fill, outline=outline,
                                width=max(1, 2 * scale))

        def pressed(bit):
            return bool(state.get("buttons", 0) & bit)

        segments = [
            ((220.5,92),(200.5,92),(154,92),(128,92)),
            ((128,92),(95.5,92),(66.5,109.5),(55,137.5)),
            ((55,137.5),(43.5,165.5),(4,271.1),(4,317.5)),
            ((4,317.5),(4,363.9),(17.5,378.5),(49.5,378.5)),
            ((49.5,378.5),(81.5,378.5),(105,294.5),(150,294.5)),
            ((150,294.5),(195,294.5),(220.5,294.5),(220.5,294.5)),
            ((220.5,294.5),(220.5,294.5),(245.5,294.5),(290.5,294.5)),
            ((290.5,294.5),(335.5,294.5),(359,378.5),(391,378.5)),
            ((391,378.5),(423,378.5),(436.5,363.9),(436.5,317.5)),
            ((436.5,317.5),(436.5,271.1),(397,165.5),(385.5,137.5)),
            ((385.5,137.5),(374,109.5),(345,92),(312.5,92)),
            ((312.5,92),(286.5,92),(240,92),(220.5,92)),
        ]
        outline_source = []
        for p0, p1, p2, p3 in segments:
            for j in range(9):
                t = j / 8
                q = 1 - t
                x = q**3*p0[0] + 3*q*q*t*p1[0] + 3*q*t*t*p2[0] + t**3*p3[0]
                y = q**3*p0[1] + 3*q*q*t*p1[1] + 3*q*t*t*p2[1] + t**3*p3[1]
                outline_source.append((x, y))
        outline_points = [coord for x, y in outline_source for coord in point(x, y)]
        shadow = [coord for x, y in outline_source for coord in point(x, y + 17)]
        cv.create_polygon(shadow, fill=CARD_HI, outline="")
        cv.create_polygon(outline_points, fill=CARD_HI, outline=ACCENT_HI,
                          width=max(1, 2.5 * scale))

        square(111.5, 61.5, 152.5, 74.5, ACCENT_HI if pressed(0x0100) else OFF, BORDER)
        square(289.5, 61.5, 330.5, 74.5, ACCENT_HI if pressed(0x0200) else OFF, BORDER)
        for x, amount in ((138.5, state.get("lt", 0)), (303.5, state.get("rt", 0))):
            square(x-14, 2.5, x+14, 44.5, OFF, BORDER)
            fill_height = 42 * amount / 255
            if amount:
                square(x-14, 44.5-fill_height, x+14, 44.5, ACCENT_HI)
            a, b = point(x-14, 2.5)
            c, d = point(x+14, 44.5)
            cv.create_rectangle(a, b, c, d, outline=BORDER, width=max(1, 2*scale))

        oval(113, 160, 37.5, "", ACCENT_HI)
        oval(278, 238, 37.5, "", ACCENT_HI)
        lx, ly = state.get("lx", 0)/32767, state.get("ly", 0)/32767
        rx, ry = state.get("rx", 0)/32767, state.get("ry", 0)/32767
        oval(113, 160, 28, OFF)
        oval(278, 238, 28, OFF)
        left_moved = abs(lx) > 0.08 or abs(ly) > 0.08
        right_moved = abs(rx) > 0.08 or abs(ry) > 0.08
        oval(113 + lx*17, 160 - ly*17, 20,
             ACCENT_HI if pressed(0x0040) or left_moved else CARD_HI)
        oval(278 + rx*17, 238 - ry*17, 20,
             ACCENT_HI if pressed(0x0080) or right_moved else CARD_HI)

        oval(166, 238, 37.5, "", ACCENT_HI)
        for x, y, vertical, bit in ((166,220,True,0x0001), (184,238,False,0x0008),
                                     (166,256,True,0x0002), (148,238,False,0x0004)):
            hw, hh = (7.5,10) if vertical else (10,7.5)
            square(x-hw, y-hh, x+hw, y+hh,
                   ACCENT_HI if pressed(bit) else OFF, BORDER)

        oval(329, 160, 37.5, "", ACCENT_HI)
        for x, y, bit in ((329,141,0x8000), (348,160,0x2000),
                          (329,179,0x1000), (310,160,0x4000)):
            oval(x, y, 10.5, ACCENT_HI if pressed(bit) else OFF)
        oval(185, 162, 10, ACCENT_HI if pressed(0x0020) else OFF, ACCENT_HI)
        oval(259, 162, 10, ACCENT_HI if pressed(0x0010) else OFF, ACCENT_HI)
        for x, label in ((185, "−"), (259, "+")):
            px, py = point(x, 162)
            cv.create_text(px, py, text=label, fill=BG, font=self.f_cap)

def main():
    user_id = getattr(os, "getuid", lambda: getpass.getuser())()
    instance_id = hashlib.sha256(f"joyrelay:{user_id}".encode()).hexdigest()[:16]
    lock_path = os.path.join(tempfile.gettempdir(), f"joyrelay-{instance_id}.lock")
    instance_lock = open(lock_path, "a+")
    try:
        portalocker.lock(instance_lock, portalocker.LOCK_EX | portalocker.LOCK_NB)
    except portalocker.exceptions.LockException:
        instance_lock.close()
        return

    root = tk.Tk()
    try:
        App(root)
        root.mainloop()
    finally:
        instance_lock.close()


if __name__ == "__main__":
    main()
