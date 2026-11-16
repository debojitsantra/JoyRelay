# JoyRelay design

This document describes the implementation in this repository.

## System overview

JoyRelay forwards a wired game controller connected to an Android phone to a PC over the local network.
Android converts platform input events into a compact controller state packet. The PC receives that state
and updates an operating-system virtual gamepad backend.

```text
USB controller
   │ Android InputDevice key and motion events
   ▼
MainActivity ── Bridge ── UdpSender
                              │ UDP, port 47500
                              ▼
PC Receiver ── VigemBackend (Windows) / UinputBackend (Linux)
      │
      ├── tkinter UI and controller simulation
      └── PG ping replies to Android
```

The Android app intentionally uses Android's input framework. It does not claim the USB interface or parse
raw USB reports: controller report formats and vendor protocols differ, and claiming the device can prevent
Android or the controller from delivering usable gamepad events.

## Android lifecycle and input path

### Start streaming

1. The user enters the PC's IP and UDP port (default `47500`) and taps **Start** in `MainActivity`.
2. `MainActivity` saves those values and starts `BridgeService` as a foreground service. Android 13+ may
   ask for notification permission.
3. `BridgeService` creates its notification channel, enters foreground mode, then calls `Bridge.start()`.
4. `Bridge.start()` acquires a Wi-Fi low-latency lock, creates and starts `UdpSender`, marks the bridge as
   running, and submits the current neutral state. The notification provides a Stop action.

`MainActivity` holds `FLAG_KEEP_SCREEN_ON` while open. Android gamepad input is collected by the foreground
activity, so the user must keep JoyRelay in front while streaming. The service keeps networking alive, but
does not replace the foreground activity as the source of input events.

### Read and normalize controls

`MainActivity.dispatchKeyEvent()` handles digital gamepad keys and maps Android key codes to XUSB button
bits. `dispatchGenericMotionEvent()` accepts joystick move events and processes all historical samples in
order before the newest sample, so batched high-rate controller events are not collapsed to one state.

- Left stick: `AXIS_X`, `AXIS_Y`.
- Right stick: prefer `AXIS_Z`, `AXIS_RZ`; if both are zero, use `AXIS_RX`, `AXIS_RY`.
- Triggers: left uses the greater of `AXIS_LTRIGGER` and `AXIS_BRAKE`; right uses the greater of
  `AXIS_RTRIGGER` and `AXIS_GAS`. Values are clamped to 0–1 then scaled to 0–255. If a device exposes
  an analog trigger axis, a simultaneous L2/R2 key event does not override its analog pressure. If no
  analog axis is exposed, the key is used as a 0/255 fallback.
- D-pad: `AXIS_HAT_X` and `AXIS_HAT_Y` are thresholded at ±0.5. D-pad key events are also mapped through
  the digital key map.
- Stick axes: deadzone is 0.08; remaining values are clamped to −1..1 and scaled to signed 16-bit range.
  Android's positive-down Y coordinates are inverted so up is positive in the wire protocol.

Every input state is submitted to `Bridge` and mirrored in its shared fields. The UI reads those fields to
draw the controller simulation, so it represents the state captured on the phone. When the activity pauses,
it clears controls and submits a neutral state to release held inputs.

`Bridge.refreshRates()` calculates input samples per second and sent packets per second for the UI. These
rates are observed rates, not a promise of the controller's configured USB polling frequency: Android event
delivery, scheduling, and Wi-Fi may lower them. The sender preserves historical motion samples by queueing
states individually; packet throughput depends on the device and network.

### UDP sender and latency measurement

`UdpSender` owns a background thread and a UDP socket. Each update is appended to a FIFO queue. When no
state is queued, the thread waits up to 100 ms and sends the latest state as a heartbeat. Every 250 ms it
adds a ping payload. The socket's receive thread listens for the PC echo and reports round-trip time back to
`Bridge`; the UI shows recent latency and a graph. The app's displayed latency is round-trip time, not
one-way input latency.

On Stop, `BridgeService` calls `Bridge.stop()`. The sender stops, the Wi-Fi lock is released, and shared
state is reset.

## Wire protocol

All integers are little-endian. A normal Android-to-PC state packet is 18 bytes. A ping packet is 24 bytes.

| Offset | Bytes | Field         | Meaning                                         |
| -----: | ----: | ------------- | ----------------------------------------------- |
|      0 |     2 | Magic         | ASCII `PR`                                      |
|      2 |     1 | Version       | `1`                                             |
|      3 |     2 | Sequence      | Unsigned 16-bit counter, wraps at 65535         |
|      5 |     2 | Buttons       | XUSB button bitmask                             |
|      7 |     2 | Left X        | Signed 16-bit; negative is left                 |
|      9 |     2 | Left Y        | Signed 16-bit; positive is up                   |
|     11 |     2 | Right X       | Signed 16-bit; negative is left                 |
|     13 |     2 | Right Y       | Signed 16-bit; positive is up                   |
|     15 |     1 | Left trigger  | Unsigned 0–255                                  |
|     16 |     1 | Right trigger | Unsigned 0–255                                  |
|     17 |     1 | Flags         | `0x02` means ping fields follow                 |
|     18 |     4 | Ping ID       | Present only when `0x02` is set                 |
|     22 |     2 | Previous RTT  | Tenths of a millisecond; `0xFFFF` means unknown |

Button bits: D-pad up/down/left/right are `0x0001/0x0002/0x0004/0x0008`; Start/Back are
`0x0010/0x0020`; stick clicks are `0x0040/0x0080`; LB/RB are `0x0100/0x0200`; Guide is `0x0400`;
A/B/X/Y are `0x1000/0x2000/0x4000/0x8000`.

When a packet has the ping flag and all 24 bytes are present, the PC replies to its source IP and UDP port
with `PG` followed by the same 32-bit ping ID. Android measures the time until that reply. The previous RTT
field lets the PC display the phone's most recent measurement. The format has no authentication or
encryption; only use the receiver on a trusted network. The PC UI optionally filters packets to one phone IP.

## PC receiver and virtual gamepad

`Receiver` in `pc/joyrelay_core.py` binds a UDP socket to `0.0.0.0:<port>` and receives packets on a daemon
thread. It optionally checks the sender IP, minimum packet length, magic, and protocol version. Ping replies
are sent to the source address. A wraparound-aware sequence check drops duplicate and old state packets.
Valid packets update a thread-safe snapshot and are immediately passed to the selected backend.

The PC marks a phone connected while state has arrived within the last second. If no accepted packet arrives
for 300 ms, it resets the virtual controller to neutral and clears the active connection state. The receiver
does not implement authentication, session negotiation, packet retransmission, or a pairing protocol.

Backends in `joyrelay_core.py`:

- **Windows:** `VigemBackend` uses Python `vgamepad` and ViGEmBus to expose an Xbox 360 controller.
- **Linux:** `UinputBackend` uses `evdev` and `/dev/uinput` to expose a virtual gamepad.
- **Other / unavailable backend:** `NullBackend` keeps receiving and displaying state in monitor-only mode.

`pc/joyrelay_gui.py` is the tkinter desktop UI. It shows status, phone IP, packet rate, RTT history, and a
simulation of the received controller state. Closing the window hides it to the tray; the tray Quit action
stops the receiver and backend. A nonblocking per-user file lock prevents launching a second GUI instance.
`pc/receiver.py` is a headless receiver that uses the same core.

The Android and PC simulations draw the supplied Xbox-style controller outline locally. They are renderings
of the state; neither is a controller image streamed over the network.

## Source map

| Path                                                                   | Responsibility                                                              |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `android/app/src/main/java/io/github/debojitsantra/joyrelay/MainActivity.java`  | Android UI, controller event capture, normalization, phone simulation       |
| `android/app/src/main/java/io/github/debojitsantra/joyrelay/Bridge.java`        | Shared bridge state, lifecycle, Wi-Fi lock, rates and RTT history           |
| `android/app/src/main/java/io/github/debojitsantra/joyrelay/BridgeService.java` | Foreground service and persistent notification                              |
| `android/app/src/main/java/io/github/debojitsantra/joyrelay/UdpSender.java`     | Queued UDP state packets and ping echo handling                             |
| `android/app/src/main/java/io/github/debojitsantra/joyrelay/Views.java`         | Android controller simulation and latency graph                             |
| `pc/joyrelay_core.py`                                                  | Packet parser, receiver, timeout handling, and platform backends            |
| `pc/joyrelay_gui.py`                                                   | Desktop GUI, tray behavior, single-instance lock, PC simulation             |
| `pc/receiver.py`                                                       | Headless PC receiver entry point                                            |
| `.github/workflows/release.yml`                                        | Builds Android, Windows, Linux release assets and publishes on version tags |

## Reproducing the implementation

To recreate the core behavior in another codebase:

1. Capture platform-standard controller events on Android. Map buttons to the XUSB bit positions above,
   preserve analog trigger values, normalize sticks, and process historical motion samples.
2. Keep a current controller state and send the packet format above over UDP to the PC's listening port.
   Queue each sample if event history must be retained; send periodic state heartbeats while idle.
3. On PC, validate framing and version before unpacking, use sequence numbers to reject stale packets, and
   echo ping IDs to the sender for RTT display.
4. Drive a virtual gamepad backend from each valid state. Reset it to neutral after the sender times out or
   the receiver stops.
5. Add the UI as a view of the same state that is transmitted/received. Keep platform-specific backend
   initialization isolated so monitor-only operation remains possible.

The current Android package uses Java and Android Views; the PC receiver uses Python, tkinter, PyInstaller,
`vgamepad` on Windows, and `evdev` on Linux.
