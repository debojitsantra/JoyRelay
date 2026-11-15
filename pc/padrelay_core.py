#!/usr/bin/env python3

import socket
import struct
import sys
import threading
import time

PORT = 47500
VERSION = 1
STATE_FMT = "<2sBHHhhhhBBB"
STATE_SIZE = struct.calcsize(STATE_FMT)
PING_SIZE = 24
FLAG_PING = 0x02
RELEASE_AFTER = 0.3   # s without packets -> release every button
CONNECTED_WITHIN = 1.0
RTT_STALE_AFTER = 2.5

class NullBackend:
    #No virtual controller: the app still receives and shows everything.

    def __init__(self, reason=""):
        self.name = "Monitor only"
        self.note = reason or "No virtual controller backend on this system."

    def update(self, *a):
        pass

    def reset(self):
        pass

    def close(self):
        pass


class VigemBackend:
    #Windows: virtual Xbox 360 controller through ViGEmBus (package: vgamepad).
    def __init__(self):
        import vgamepad as vg  # raises if missing or the ViGEmBus driver is absent
        self.name = "Virtual Xbox 360 controller (ViGEmBus)"
        self.note = ""
        self._pad = vg.VX360Gamepad()

    def update(self, buttons, lx, ly, rx, ry, lt, rt):
        r = self._pad.report
        r.wButtons = buttons
        r.sThumbLX, r.sThumbLY, r.sThumbRX, r.sThumbRY = lx, ly, rx, ry
        r.bLeftTrigger, r.bRightTrigger = lt, rt
        self._pad.update()

    def reset(self):
        self._pad.reset()
        self._pad.update()

    def close(self):
        self._pad = None


class UinputBackend:
    #Linux: virtual gamepad through /dev/uinput (package: evdev).

    def __init__(self):
        from evdev import AbsInfo, UInput, ecodes as e  # raises if missing
        self.e = e
        self.name = "Virtual gamepad (uinput)"
        self.note = ""
        stick = AbsInfo(0, -32768, 32767, 16, 128, 0)
        trig = AbsInfo(0, 0, 255, 0, 0, 0)
        hat = AbsInfo(0, -1, 1, 0, 0, 0)
        caps = {
            e.EV_KEY: [e.BTN_SOUTH, e.BTN_EAST, e.BTN_NORTH, e.BTN_WEST, e.BTN_TL, e.BTN_TR,
                       e.BTN_MODE,
                       e.BTN_SELECT, e.BTN_START, e.BTN_THUMBL, e.BTN_THUMBR],
            e.EV_ABS: [(e.ABS_X, stick), (e.ABS_Y, stick), (e.ABS_RX, stick), (e.ABS_RY, stick),
                       (e.ABS_Z, trig), (e.ABS_RZ, trig), (e.ABS_HAT0X, hat), (e.ABS_HAT0Y, hat)],
        }
        # Match Xbox 360 IDs so games and Steam recognize this controller.
        self._ui = UInput(caps, name="PadRelay Virtual Gamepad",
                          vendor=0x045E, product=0x028E, version=0x110)
        self._map = [
            (0x1000, e.BTN_SOUTH), (0x2000, e.BTN_EAST), (0x4000, e.BTN_NORTH),
            (0x8000, e.BTN_WEST), (0x0100, e.BTN_TL), (0x0200, e.BTN_TR), (0x0400, e.BTN_MODE),
            (0x0020, e.BTN_SELECT), (0x0010, e.BTN_START),
            (0x0040, e.BTN_THUMBL), (0x0080, e.BTN_THUMBR),
        ]
        self._last = {}

    def _set(self, etype, code, value):
        if self._last.get((etype, code)) != value:
            self._last[(etype, code)] = value
            self._ui.write(etype, code, value)

    def update(self, buttons, lx, ly, rx, ry, lt, rt):
        e = self.e
        for mask, code in self._map:
            self._set(e.EV_KEY, code, 1 if buttons & mask else 0)
        self._set(e.EV_ABS, e.ABS_X, lx)
        self._set(e.EV_ABS, e.ABS_Y, -ly)      # Linux: down is positive
        self._set(e.EV_ABS, e.ABS_RX, rx)
        self._set(e.EV_ABS, e.ABS_RY, -ry)
        self._set(e.EV_ABS, e.ABS_Z, lt)
        self._set(e.EV_ABS, e.ABS_RZ, rt)
        self._set(e.EV_ABS, e.ABS_HAT0X, (1 if buttons & 0x0008 else 0) - (1 if buttons & 0x0004 else 0))
        self._set(e.EV_ABS, e.ABS_HAT0Y, (1 if buttons & 0x0002 else 0) - (1 if buttons & 0x0001 else 0))
        self._ui.syn()

    def reset(self):
        self.update(0, 0, 0, 0, 0, 0, 0)

    def close(self):
        try:
            self._ui.close()
        except Exception:
            pass


def make_backend():
    #Best backend for this OS, falling back to monitor-only with an explanation.
    try:
        if sys.platform == "win32":
            return VigemBackend()
        if sys.platform.startswith("linux"):
            return UinputBackend()
        return NullBackend("macOS has no built-in virtual gamepad. The window still shows "
                           "the phone's input, but games will not see a controller.")
    except Exception as exc:  # missing package, driver or permission
        hint = {
            "win32": "Install ViGEmBus (pip install vgamepad does this) and restart.",
        }.get(sys.platform, "Install python-evdev and make sure /dev/uinput is writable "
                            "(e.g. run with sudo or add a udev rule).")
        return NullBackend(f"{type(exc).__name__}: {exc}. {hint}")


def local_ips():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))  # no packet is sent; just picks the main interface
        ips.append(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass
    return ips


class Receiver:
    def __init__(self, backend, port=PORT, allow=None):
        self.backend = backend
        self.port = port
        self.allow = allow or None
        self._sock = None
        self._thread = None
        self._stop = threading.Event()
        self._lock = threading.Lock()
        self._addr = None
        self._reset_state()

    def _reset_state(self):
        self._s = dict(buttons=0, lx=0, ly=0, rx=0, ry=0, lt=0, rt=0)
        self._last_rx = 0.0
        self._last_seq = None
        self._active = False
        self._rtt = None
        self._rtt_at = 0.0
        self._count = 0
        self._pps = 0
        self._pps_t0 = time.time()
        self._total = 0
        self._phone_ip = None

    @property
    def running(self):
        return self._thread is not None and self._thread.is_alive()

    def start(self):
        if self.running:
            return
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(("0.0.0.0", self.port))
        except OSError:
            sock.close()
            raise
        sock.settimeout(0.05)
        self._sock = sock
        self._stop.clear()
        self._reset_state()
        self._thread = threading.Thread(target=self._loop, name="padrelay-rx", daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=1.0)
        self._thread = None
        if self._sock:
            self._sock.close()
            self._sock = None
        try:
            self.backend.reset()
        except Exception:
            pass

    def close(self):
        self.stop()
        self.backend.close()

    def _loop(self):
        sock = self._sock
        while not self._stop.is_set():
            now = time.time()
            if now - self._pps_t0 >= 1.0:
                self._pps = int(self._count / (now - self._pps_t0))
                self._count = 0
                self._pps_t0 = now
            try:
                data, addr = sock.recvfrom(64)
            except socket.timeout:
                if self._active and time.time() - self._last_rx > RELEASE_AFTER:
                    self._active = False
                    self._last_seq = None
                    with self._lock:
                        self._s = dict(buttons=0, lx=0, ly=0, rx=0, ry=0, lt=0, rt=0)
                    self.backend.reset()
                continue
            except OSError:
                break

            if self.allow and addr[0] != self.allow:
                continue
            if len(data) < STATE_SIZE:
                continue
            magic, ver, seq, buttons, lx, ly, rx, ry, lt, rt, flags = struct.unpack(
                STATE_FMT, data[:STATE_SIZE])
            if magic != b"PR" or ver != VERSION:
                continue

            if flags & FLAG_PING and len(data) >= PING_SIZE:
                ping_id, rtt = struct.unpack("<IH", data[18:24])
                try:
                    sock.sendto(b"PG" + struct.pack("<I", ping_id), addr)
                except OSError:
                    pass
                self._rtt = None if rtt == 0xFFFF else rtt / 10.0
                self._rtt_at = time.time()

            # drop duplicate / out-of-order packets (16-bit wraparound aware)
            if self._last_seq is not None:
                diff = (seq - self._last_seq) & 0xFFFF
                if diff == 0 or diff > 0x8000:
                    continue
            self._last_seq = seq
            self._last_rx = time.time()
            self._addr = addr
            self._count += 1
            self._total += 1
            self._phone_ip = addr[0]
            self._active = True

            with self._lock:
                self._s = dict(buttons=buttons, lx=lx, ly=ly, rx=rx, ry=ry, lt=lt, rt=rt)
            self.backend.update(buttons, lx, ly, rx, ry, lt, rt)

    def snapshot(self):
        now = time.time()
        with self._lock:
            s = dict(self._s)
        rtt = self._rtt if (self._rtt is not None and now - self._rtt_at < RTT_STALE_AFTER) else None
        s.update(
            running=self.running,
            connected=self._active and now - self._last_rx < CONNECTED_WITHIN,
            phone_ip=self._phone_ip,
            pps=self._pps,
            rtt_ms=rtt,
            rtt_at=self._rtt_at,
            total=self._total,
        )
        return s
