#!/usr/bin/env python3
"""
    pip install pyinstaller            (and: pip install vgamepad   on Windows,
                                              pip install evdev      on Linux)
    python build.py

Output goes to pc/dist/.
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SEP = ";" if os.name == "nt" else ":"


def main():
    work = os.path.join(HERE, "build")
    windows_icon = None
    cmd = [
        sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--windowed",
        "--name", "PadRelay",
        "--distpath", os.path.join(HERE, "dist"),
        "--workpath", work, "--specpath", work,
        "--add-data", f"{os.path.join(HERE, 'assets', 'icon.png')}{SEP}assets",
        "--collect-all", "pystray", "--collect-all", "PIL", "--collect-all", "portalocker",
    ]
    if sys.platform == "win32":
        from PIL import Image

        with tempfile.NamedTemporaryFile(suffix=".ico", delete=False) as icon_file:
            windows_icon = icon_file.name
        Image.open(os.path.join(HERE, "assets", "icon.png")).save(
            windows_icon, format="ICO",
            sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)],
        )
        cmd += ["--onefile", "--icon", windows_icon,
                "--version-file", os.path.join(HERE, "version_info.txt"),
                "--collect-all", "vgamepad"]
    elif sys.platform == "darwin":
        cmd += ["--icon", os.path.join(HERE, "assets", "icon.icns")]  # onedir .app bundle
    else:
        cmd += ["--onefile", "--hidden-import", "evdev"]
    cmd.append(os.path.join(HERE, "padrelay_gui.py"))
    print(" ".join(cmd))
    try:
        result = subprocess.call(cmd, cwd=HERE)
    finally:
        if windows_icon and os.path.exists(windows_icon):
            os.remove(windows_icon)
    sys.exit(result)


if __name__ == "__main__":
    main()
