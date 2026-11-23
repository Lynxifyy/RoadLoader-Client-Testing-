# Screenshots

Captured on a Motorola `clo200` cloud phone (Android 11, 1080 x 2400) running
the debug build with Minecraft 1.17.41.01 imported. The launcher is portrait,
the game surface is landscape, and the 32-bit companion is the only thing
drawing inside it - the overlay in the corner is Dear ImGui, not Compose.

## Launcher

| Home | Worlds |
| --- | --- |
| ![Home](screenshots/01-home.jpg) | ![Worlds](screenshots/02-worlds.jpg) |
| Instance card with the engine, heap and HUD summary; quick actions; a short explainer of the bridge. | Imported builds with version, ABI and size. The play button launches the companion. |

| Mods | Settings |
| --- | --- |
| ![Mods](screenshots/03-mods.jpg) | ![Settings](screenshots/04-settings.jpg) |
| The same switches the native console exposes, mirrored into Compose and persisted together. | Heap cap, render distance and audio level feed the companion at launch; the module toggles follow. |

![Account](screenshots/05-account.jpg)

Device-code sign-in. The token is sealed with an AES-GCM key in the Android
keystore; only the gamertag is stored in plain text.

## In game

![In-game HUD](screenshots/06-in-game-hud.jpg)

The HUD is drawn by the 32-bit companion's ImGui context: watermark card,
frame rate sparkline, ping from the heartbeat, heap against the 1536 MB cap,
CPS, session time and engine state. Every value comes back over the shared
memory bridge.

| Modules | HUD |
| --- | --- |
| ![Modules tab](screenshots/07-mod-menu.jpg) | ![HUD tab](screenshots/08-mod-menu-hud.jpg) |
| BetterRenderDragon, Safe Mode, glass backdrop and the audio level. | Per-element visibility for the watermark and the stat cards. |

![Engine tab](screenshots/09-mod-menu-engine.jpg)

Live engine telemetry: render distance, heap, FPS, heartbeat age, frame count,
render cost, forwarded input events and audio voices.

![Leave dialog](screenshots/10-leave-dialog.jpg)

Back asks before it stops the companion, so the 32-bit process is never left
orphaned behind the launcher.
