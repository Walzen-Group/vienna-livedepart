# CLAUDE.md — Vienna LiveDepart

Standalone Wear OS app for live Wiener Linien departures. See **`docs/HANDOFF.md`**
for current state, remaining work, and gotchas (read it first when resuming), and
the design spec at `docs/superpowers/specs/2026-07-11-wearos-vienna-departures-design.md`.

## Progress tracking — use the LOCAL Plane connector

Track progress (modules, work items, statuses) in Plane using the **local
connector** — the tools named **`mcp__plane__*`** (self-hosted `plane.walzen.org`,
authenticates as Sam, who owns the project). Keep it up to date as work lands:
create/close work items, move states, add them to the right module.

- Do **NOT** use the `mcp__claude_ai_Plane_MCP__*` connector — it authenticates as
  a different user who is not a member of this project, so it 403s here.
- Project: **Vienna LiveDepart (VLIVE)**, id `c9901d92-b7f4-4d6b-9044-37d7c3b6ad45`.
- `list_projects` (the `projects-lite` endpoint) 404s on the local connector —
  that's fine; use the known `project_id` for project-scoped calls (states,
  modules, work items all work).
- States: **Done** `8f2ecb91-bdfb-4a90-8592-90871e4661c3`, **Todo**
  `ef8a5272-4021-4031-a3b1-e620a5a8c10e` (also Backlog / In Progress / Cancelled).
- Modules mirror the feature areas (Data & Toolchain, Location & Nearby,
  Departures screen, Home pager & Search, Route data & Crown, Favorites,
  Settings & Data refresh, Tile & release polish).

## Build / run (Wear emulator)

The project builds on **both** Sam's machines. `emulator-5554` is the Wear
emulator; a real watch also connects over wireless adb (e.g.
`10.188.237.195:33151`), so pass `-s emulator-5554` to target the emulator. Always
`am force-stop` before launch so the new APK's process restarts. A hook redirects
`gradlew`/build commands away from the Bash tool — run them via
`mcp__plugin_context-mode_context-mode__ctx_execute(language: "shell", ...)` and
pipe through `grep`/`tail` so only the result lines come back (`adb` runs fine
either way). Filter build output for `error:|BUILD|FAILED|e: `.

**macOS + nix** (this shell): `adb`, `java` (Zulu JDK 17), and the Gradle wrapper
are on `PATH` from the nix env; `local.properties` points at
`/Users/sam/Library/Android/sdk`.

```
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop com.walzengroup.viennadepart
adb -s emulator-5554 shell am start -n com.walzengroup.viennadepart/.MainActivity
```

**Windows + PowerShell** (adb at `C:\adb\adb.exe`):

```
./gradlew.bat :app:assembleDebug
C:\adb\adb.exe -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
C:\adb\adb.exe -s emulator-5554 shell am force-stop com.walzengroup.viennadepart
C:\adb\adb.exe -s emulator-5554 shell am start -n com.walzengroup.viennadepart/.MainActivity
```

Set the emulator location (needed for Nearby / favorites) — clears on reboot:
`adb -s emulator-5554 emu geo fix 16.3726 48.2088` (Stephansplatz; `C:\adb\adb.exe`
on Windows). Screenshot: `adb -s emulator-5554 exec-out screencap -p > shot.png`.

If `./gradlew <task>` fails with `Could not find or load main class <task>`, the
wrapper script is corrupted — regenerate it with
`java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain wrapper`.
