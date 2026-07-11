# Vienna Departures — Wear OS app

A Compose for Wear OS app for live Wiener Linien departures. It uses your
location to find the nearest stops, lets you pick a line, and shows the next
departures — themed by the line's mode color, with air-conditioning / step-free /
congestion glyphs.

**Phase 1** (done) proved the toolchain with one hardcoded stop. **Phase 2**
(current) adds the real flow: **Nearby → pick a line → departures**, driven by
GPS and the bundled stop reference. Still to come in Phase 3: favorites, the
tile, and the swipe-direction / crown-scroll polish on the departures screen. See
`docs/superpowers/specs/2026-07-11-wearos-vienna-departures-design.md`.

## What you'll see

1. **Nearby** — a list of the closest stops with distances (asks for location
   permission the first time).
2. Tap a stop → **pick a line** — each line with its two termini and a mode-color
   badge.
3. Tap a line → **departures** — both directions, on a deep mode-color ground.
   When a direction runs from two platforms, each is grouped with its compass +
   RBL:

```
        U1 · Stephansplatz
   → Leopoldau          H
   [ 15 min ]  [ 30 min ]
   → Oberlaa            R
   [ 11 min  ❄️ ]  [ 25 min  ♿ ]
```

Each departure shows the countdown in minutes and, when the feed reports them,
glyphs for air conditioning (❄️ `vehicle.cooling`), step-free access
(♿ `vehicle.barrierFree`), and congestion on approach (⚠️ `vehicle.trafficjam`).
Swipe from the left edge to go back.

## Prerequisites

- **Android Studio** (any recent version — Ladybug or newer). It bundles the
  Kotlin compiler, Android SDK, `adb`, and the Wear OS emulator.
- JDK 17 (Android Studio bundles one; a system JDK 17 also works).

Nothing else — no API key, the Wiener Linien data is open.

## Open it

1. In Android Studio: **File → Open** and select the `wear-app/` folder (the one
   with `settings.gradle.kts`).
2. Let Gradle sync. First sync downloads Gradle 8.11.1 and the dependencies; give
   it a few minutes. `local.properties` is created automatically pointing at your
   SDK (a copy is already here for this machine).
3. If prompted to install an SDK platform or build-tools, accept.

## Run on the emulator

1. **Tools → Device Manager → Create Device → Wear OS**. Pick a round device
   (e.g. "Wear OS Small Round") and a recent system image (API 34+). Download the
   image if needed.
2. Start the emulator.
3. **Set a location in Vienna** so "Nearby" has something to find: emulator
   window → **`...` (Extended controls) → Location**, enter a Vienna point (e.g.
   lat `48.2088`, lon `16.3726` for Stephansplatz) and click **Send / Set
   location**. Without this the emulator has no GPS fix and Nearby shows an error.
4. Make sure the emulator has internet (the Wear emulator normally has network
   through the host).
5. Pick the `app` run configuration and press **Run** (▶). Grant the location
   permission when asked.

You should land on a list of nearby stops; tap through to live departures.

## Run on a real watch (optional)

Most Wear OS watches install over Wi-Fi (no USB). On the watch: enable Developer
options (tap the build number 7×), turn on ADB + Wireless debugging, then on the
computer `adb pair <ip:port>` with the shown code and `adb connect <ip:port>`.
Android Studio can then install to the watch. Exact menu labels vary by brand.

## Command line (optional)

From `wear-app/`:

```
./gradlew :app:assembleDebug     # build the debug APK
./gradlew :app:installDebug      # install to a running emulator / connected watch
```

The APK lands in `app/build/outputs/apk/debug/`.

## Project layout

```
wear-app/
  app/src/main/
    AndroidManifest.xml                 standalone Wear app, INTERNET + location
    assets/haltepunkte.csv              bundled stop reference (cp1252)
    java/com/walzengroup/viennadepart/
      MainActivity.kt                   SwipeDismissableNavHost: nearby → lines → departures
      AppViewModel.kt                   holds the current stop + line selection
      data/
        MonitorModels.kt                @Serializable mirror of the API JSON
        WienerLinienApi.kt              the monitor GET, no key
        DeparturesRepository.kt         lines at a stop, and departures per line (platform-grouped)
        UiModels.kt                     LineOption / DeparturesUi / PlatformGroup
        stops/
          StopModels.kt                 PhysicalStop / Platform / StopDistance
          StopRepository.kt             loads the CSV, DIVA grouping, nearest / search, compass
      location/LocationProvider.kt      fused current-location + permission check
      ui/
        NearbyScreen.kt                 GPS → nearest stops list
        StopLinesScreen.kt              lines at a stop (two termini, square badge)
        DeparturesScreen.kt             departures, both directions, platform-grouped
        common/Ui.kt                    Loadable / error / badge / glyph helpers
        theme/ModeColor.kt              transport color mapping (U-Bahn / vehicle.type)
        theme/Theme.kt                  Wear MaterialTheme
    res/                                launcher icon, strings, theme
  gradle/, gradlew*, settings.gradle.kts, build files
```

## Notes and known bits

- **Nearest stop** uses the fused location provider plus the bundled
  `haltepunkte.csv` (grouped by DIVA into physical stops). Lines at a stop and
  their departures come from one live monitor call over the stop's RBLs.
- **Not yet**: the home pager (Favorites / Nearby / Search), text/voice search,
  swipe-to-change-direction, and crown-scroll to farther stops — those are the
  rest of Phase 2/3.
- **Material**: built on Wear Compose Material (the stable 1.4 line). The spec
  targets Material 3 (`androidx.wear.compose:compose-material3`); that's a later
  swap once the components we need are confirmed stable.
- **Versions** in `gradle/libs.versions.toml` are pinned to a set that builds
  today (AGP 8.7.3, Kotlin 2.1.0, Wear Compose 1.4.1). Android Studio may suggest
  bumps.
