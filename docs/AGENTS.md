# Repository guidance

## Project structure

- `android/` is the Android app. It uses Java and Android Views.
- `pc/` is the Python desktop app, receiver, and PyInstaller build script.
- `.github/workflows/release.yml` builds and publishes release artifacts.
- `docs/DESIGN.md` documents the input path and UDP protocol. Read it before changing packet handling or controller mappings.

## Language and build files

- Keep Android application code in Java. Do not add Kotlin source files, Kotlin DSL build files, or Kotlin dependencies.
- Keep the existing Groovy Gradle configuration and Java 17 settings unless the task requires a build-system change.
- Keep the PC app in Python and preserve its Windows and Linux backend behavior.
- Windows PyInstaller builds must collect all `vgamepad` resources so the executable includes its client DLL and driver installer files. Keep the release job on `windows-latest` and upload the built executable from `dist/`.

## UI and assets

- Use a black-and-white palette for both app UIs.
- Use square-cornered controls and panels; avoid rounded cards, buttons, and decorative shapes.
- Keep the PC UI designed for a wide desktop window. Use horizontal space instead of copying the Android portrait layout.
- Keep the bridge mark centered and sized to fit within its app-icon mask. When changing the app icon, update the source assets and the platform/build paths that consume them.
- Keep the controller simulation visible in its neutral state, including triggers and the center `-` and `+` buttons. Preserve their button mappings and pressed-state feedback when changing its drawing code.

## Behavior and protocol

- Do not change packet sizes, field offsets, endianness, button bits, axis direction, or trigger ranges without updating both Android and PC implementations and `docs/DESIGN.md`.
- Only start Android streaming when a controller is connected. Keep the foreground-service and notification Stop behavior intact.
- Keep the on-screen controller simulation driven by the same normalized state that is sent over the network.
- Preserve controller release behavior on disconnect, receiver timeout, and stop.

## Comments

- Keep comments sparse. Remove section dividers and comments that merely narrate nearby code.
- Use short comments only to explain non-obvious intent, platform quirks, protocol details, or necessary workarounds.
- Do not add comments that restate a clear function or variable name.
