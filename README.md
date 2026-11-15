<p align="center">
  <img src="pc/assets/icon.png" width="120" alt="PadRelay logo">
</p>

<h1 align="center">PadRelay</h1>

<p align="center">Use a wired USB gamepad wirelessly on your PC, with an Android phone as the bridge.</p>

PadRelay turns a phone into a wireless controller adapter. Plug a USB gamepad into an Android phone, and the phone streams its input over your local network to a PC app. The PC app presents it to games as a virtual controller: an Xbox 360 controller on Windows (via ViGEmBus) or a `uinput` gamepad on Linux.

> [!WARNING]
> PadRelay has no pairing, authentication, or encryption. Use it only on a network you trust. See [Security](#security).

## What you need

- A wired USB gamepad and a USB-C OTG adapter
- An Android 12+ phone
- A Windows or Linux PC on the same local network as the phone

macOS is not supported for gameplay: it has no built-in virtual gamepad, so the PC app can show the phone's input but games won't see a controller.

## Quick start

1. **Install the apps.** Download the Android APK, plus the Windows `.exe` or the Linux tarball, from [Releases](../../releases).
   - **Windows:** install the [ViGEmBus driver](https://github.com/nefarius/ViGEmBus/releases) first, then restart PadRelay if it was already open.
   - **Linux:** allow your user to write to `/dev/uinput` (commonly with a udev rule).
2. **Start the PC app.** It listens on UDP port `47500` by default and shows the PC's local IP addresses. If prompted, allow PadRelay through the firewall on a private network.
3. **Connect the controller to the phone** with the OTG adapter. Put the phone and PC on the same network. Ethernet for the PC and 5 GHz Wi-Fi for the phone usually give the lowest delay.
4. **Start streaming.** Enter the PC's IP address (and the port, if you changed it) in the Android app and tap **Start**. On Android 13+, allow notifications when asked. Keep PadRelay in the foreground while you play.
5. **Check it works.** Both apps show the controller's live input. Windows games see an Xbox 360 controller; Linux games see the `uinput` controller.

To stop, tap **Stop** in the Android app or use the Stop action in its notification.

Each release file has a matching `.sha256.txt` checksum if you want to verify your download.

## Using the PC app

- Closing the window hides it in the system tray. Choose **Quit** in the tray menu to stop the receiver. If the tray icon can't be created, the window is minimized instead.
- Only one PadRelay instance can run per user at a time.
- Set **Only this phone IP** to accept input from one phone only. Turn it on whenever possible.
- Change **Port** if `47500` is taken. Use the same port in the Android app.

## Run from source

Requires Python 3.9+ with tkinter (included with the python.org installer; on Debian/Ubuntu run `sudo apt install python3-tk`).

```sh
pip install -r pc/requirements.txt                 # Windows: vgamepad. Linux: evdev.
python pc/padrelay_gui.py                          # with a window
python pc/receiver.py --allow 192.168.1.50         # headless; --port and --allow are optional
```

On Windows, allow Python through the firewall on private networks the first time, or UDP port `47500` stays blocked. Installing `vgamepad` with pip also installs the ViGEmBus driver; the packaged `.exe` does not, so install the driver yourself (see Quick start).

## Build

### PC app

PyInstaller builds for the OS it runs on, so build on Windows and Linux separately. On Linux, install Tk (`python3-tk` on Debian/Ubuntu) and make sure `/dev/uinput` is available if you want virtual controller output.

```sh
python -m pip install -r pc/requirements-build.txt
cd pc
python build.py
```

Output goes to `pc/dist/`: `PadRelay.exe` on Windows or a standalone `PadRelay` executable on Linux.

### Android app

Requires Android Studio with Android SDK platform 34 and build tools, Java 17, and Gradle 8.7. Open `android/` in Android Studio and run it on an Android 12+ device, or build a debug APK from the command line:

```sh
cd android
gradle assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

For how the pieces fit together, see [docs/DESIGN.md](docs/DESIGN.md).

## Security

Anyone on your LAN who knows the PC's IP address and port can send input to it. Traffic is neither authenticated nor encrypted. Use PadRelay only on a trusted network, and enable **Only this phone IP** in the PC app (or `--allow` when running headless).

## Known limitations

- **Polling rate is not guaranteed.** A controller's advertised USB rate (for example, 1000 Hz) doesn't mean PadRelay receives or forwards that many updates per second. Throughput depends on the phone, the controller, and how Android delivers events. Check the live packet rates on your own hardware.
- **The app must stay in the foreground.** Android only delivers gamepad events to the foreground window. The foreground service keeps the network connection alive but can't capture events Android doesn't deliver.

## License

PadRelay is licensed under the [GNU General Public License v3.0](LICENSE).
