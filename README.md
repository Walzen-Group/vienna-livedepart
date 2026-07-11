# Vienna LiveDepart

Standalone Wear OS app for live Wiener Linien departures, the real-time data Google
Maps leaves out. Locate yourself, pick the nearest stop and line, read the countdown.
Swipe flips direction, a fast crown opens a route index of the whole line, and pinned
lines sit on a honeycomb tile one swipe from the watch face.

Kotlin and Compose for Wear OS (Material 2). Departures come from the Wiener Linien
OGD realtime monitor (no API key); route order comes from a GTFS-derived table.

## Build and run

From wear-app in PowerShell (a hook keeps build commands off the Bash tool):

```
./gradlew.bat :app:assembleDebug
C:\adb\adb.exe -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
C:\adb\adb.exe -s emulator-5554 shell am force-stop com.walzengroup.viennadepart
C:\adb\adb.exe -s emulator-5554 shell am start -n com.walzengroup.viennadepart/.MainActivity
```

Force-stop before launch so the new APK restarts its process. Nearby and favorites
need a location, which clears on reboot:

```
C:\adb\adb.exe -s emulator-5554 emu geo fix 16.3726 48.2088   # Stephansplatz
```

Toolchain: AGP 9.2.1, Kotlin 2.2.10, Gradle 9.4.1, JDK 17, compileSdk 35, minSdk 30.

## Layout

| Path | Role |
| --- | --- |
| wear-app/ | app module, package com.walzengroup.viennadepart |
| .../data/ | StopRepository, DeparturesRepository, RouteRepository, TransitData, favorites, settings |
| .../ui/ | home pager, Nearby, Search, departures, route index, Settings |
| .../tile/ | FavoritesTileService, the honeycomb tile |
| tools/build_routes.py | derives line_routes.csv from GTFS |
| phase0/ | Python script that proved the OGD API |
| docs/ | HANDOFF.md and design specs |

## Data

Two CSVs under wear-app/app/src/main/assets, both refreshable from Settings and once
a week in the background:

| File | Source | Holds |
| --- | --- | --- |
| haltepunkte.csv | Wiener Linien OGD | stops and platforms: RBL, DIVA, coordinates, names |
| line_routes.csv | GTFS, via tools/build_routes.py | line, headsign, ordered stops by DIVA |

Departures are queried from the realtime monitor by RBL. Route order comes from
line_routes.csv, matched to the live destination by headsign. Matching on headsign
instead of a terminus stop name keeps short-workings correct: line 9 stays Gersthof
to Westbahnhof instead of detouring through Dornbach.

build_routes.py picks the dominant stop sequence per route and headsign from the GTFS
schedule, and maps GTFS stops to haltepunkte by coordinate and name. Run it with uv:

```
uv run tools/build_routes.py
```

The workflow in .github/workflows/build-routes.yml runs it every Monday and commits
the result. The app downloads line_routes.csv from the repo raw URL, and haltepunkte
from the OGD server; Settings shows each file's date and when it last downloaded.

## Progress

Modules and work items live in a self-hosted Plane project. Read docs/HANDOFF.md for
current state and gotchas, and docs/superpowers/specs for the design docs.
