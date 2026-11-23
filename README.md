# RoadClient

A 64-bit Minecraft Bedrock client for the builds that only ship a 32-bit
`libminecraftpe.so` (1.16 - 1.17). RoadClient is the UI and the host; the game
itself runs in a separate `armeabi-v7a` process started by RoadLoader and
streamed into a `SurfaceView` over shared memory.

```
64-bit UI (arm64-v8a)                    32-bit companion (:road32)
+---------------------------+            +---------------------------+
| Compose launcher          |            | RoadLoader                |
|   library / worlds / mods |            |   signature scan          |
|   account / settings      |  commands  |   Dobby hooks             |
|                           | ---------> |   libminecraftpe.so       |
| GameSurfaceView           |            |                           |
|   RGBA frames  <----------|-- shm ---- |   swapBuffers capture     |
| Dear ImGui overlay        |            |                           |
+---------------------------+            +---------------------------+
```

## What is in the box

- **Launcher** - imports 1.16/1.17 APKs, extracts the 32-bit `libminecraftpe.so`,
  keeps a library of instances and tracks play time.
- **Session** - `EngineHost` starts the companion, `GameRenderer` owns the frame
  ring, `GameSurfaceView` presents it, `NativeOverlay` feeds touches to the
  native menu.
- **Native overlay** - Dear ImGui 1.91.5: HUD module list, five-tab console
  (Modules, HUD, Look, Engine, About), drag and touch handling, config persisted
  to `hud.json`.
- **Account** - Microsoft device-code sign-in with keystore-backed token storage.

## Requirements

- Android 8.0+ (API 26) on `arm64-v8a`
- JDK 21, Android SDK 36, NDK 27
- The `RoadLoader` submodule (`git submodule update --init --recursive`)

## Build

```bash
git submodule update --init --recursive
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

The APK carries both ABIs: `libcaramel_universe.so` in `arm64-v8a` and the
companion core in `armeabi-v7a`.

## Install and run

1. Install the APK on a 64-bit device.
2. `Import APK` and pick a Minecraft 1.16 / 1.17 build.
3. `Launch world` - the companion process starts, the first frame replaces the
   loading spinner.
4. Tap the menu button in the top-right corner (or use `Open menu on launch` in
   Settings) to open the console.

## Notes

- The engine runs as `android:process=":road32"` and is invisible in the task
  switcher.
- Leaving a world goes through a confirmation dialog; the companion is stopped
  and the library comes back.
- Crash guard: three consecutive failures block the launch and ask for Safe
  Mode.
