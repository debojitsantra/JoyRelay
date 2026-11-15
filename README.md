 <p align="center">
  <img src="pc\assets\icon.png" width="120" style="border-radius: 24px;" alt="app logo" referrerPolicy="no-referrer" />
  <h2 align="center"> Padrelay</h3>
</p>

### Use a wired USB gamepad wirelessly on a PC, with an Android phone as the bridge.

### Install and use

1. Install the Android APK on an Android 12 or newer phone. For the packaged PC app, download the Windows
   `.exe` or extract the Linux tarball from Releases. Then install [ViGEmBus Driver](https://github.com/nefarius/ViGEmBus/releases) on Windows.
   and launch `PadRelay`.
   On Linux, allow the app to access `/dev/uinput` (commonly by configuring udev permissions).
2. Start the PC app. It listens on UDP port `47500` by default and shows the PC's local IP addresses. Allow
   PadRelay through the firewall on a trusted/private network if prompted.
3. Connect the USB controller to the phone with a USB-C OTG adapter. Put the phone and PC on the same local
   network; Ethernet for the PC and 5 GHz Wi-Fi for the phone usually reduce delay.
4. Enter the PC's IP address in the Android app and tap **Start**. Keep PadRelay in the foreground while
   playing because Android delivers controller input to its foreground window. Its foreground service keeps
   network streaming active and exposes a persistent Stop notification.
5. Verify input on the matching controller simulation on both screens. Windows games should see the
   ViGEmBus Xbox 360 controller; Linux games can see the uinput controller.

Closing the PC window hides it in the system tray. Select **Quit** in the tray menu to stop the receiver.
Only one PC app instance per user can run at a time. To stop streaming from Android, tap **Stop** or use the
notification action.

## Alternative PC setup

```
pip install -r pc/requirements.txt     # Windows: vgamepad. Linux: evdev.
python pc/padrelay_gui.py              # window
python pc/receiver.py                  # or headless
```

Needs Python 3.9+ with tkinter (included with the python.org installer; on Debian/Ubuntu `apt install python3-tk`)

The first time on Windows, allow Python/PadRelay through the firewall (private networks) or UDP port 47500 is blocked.

### Build the PC app locally

PyInstaller builds for the OS it runs on. Build separately on Windows and Linux; the Windows app is an
`.exe`, and Linux is a standalone executable. Install Python 3.9+ first. On Linux, install Tk support
(`python3-tk` on Debian/Ubuntu) and make sure `/dev/uinput` is available if you want virtual controller
output.

```sh
python -m pip install pyinstaller -r pc/requirements.txt
cd pc
python build.py
```

The output is in `pc/dist/`. Windows gets `PadRelay.exe` with the ViGEm client library and icon. Linux gets
the `PadRelay` executable.

The `vgamepad` package automatically installs ViGEmBus driver.

### Build Android locally

Install Android Studio with Android SDK platform 34, Android build tools, Java 17, and Gradle 8.7. Open the
`android/` directory in Android Studio and run the app on an Android 12+ device, or build a debug APK from
the repository root:

```sh
cd android
gradle assembleDebug
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

## Known limits

- No pairing or authentication: anyone on your LAN who knows the IP and port can send input. In the PC app use
  "Only this phone IP", and only use it on a network you trust.
- Android input throughput depends on the phone, controller, and Android event delivery. A controller's
  advertised USB polling rate (for example, 1000 Hz) does not guarantee PadRelay will receive or forward
  1000 state updates per second; check the live input and packet rates with the actual hardware.
- Android must keep PadRelay in front while streaming so it receives gamepad events. The foreground service
  keeps the network bridge active but cannot collect events that Android does not deliver to the activity.
- The protocol has no authentication or encryption. Use only on a trusted local network and use the PC
  receiver's phone-IP filter when possible.
