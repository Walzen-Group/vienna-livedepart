# Phase 1 — bare Wear OS app

A minimal Compose for Wear OS app that fetches **one hardcoded stop + line**
(Stephansplatz, line U1) from the Wiener Linien real-time API and shows the next
departures, live, themed by the line's mode color. A central U-Bahn line is used
so there are departures at most hours; a daytime tram is often empty at night.

This phase is about learning the toolchain — project structure, the emulator,
then a real watch — so the scope is deliberately small: no location, no search,
no favorites, no swipe/crown navigation. Those come in Phase 2 and 3 (see
`docs/superpowers/specs/2026-07-11-wearos-vienna-departures-design.md`).

The project compiles to a debug APK with no manual setup beyond the Android SDK.

## What you'll see

A round-screen list on a deep tram-red ground:

```
        U1 · Stephansplatz
   → Leopoldau          H
   [ 15 min ]
   [ 30 min ]
   → Oberlaa            R
   [ 11 min ]
   [ 25 min ]
```

Each departure shows the countdown in minutes and, when the feed reports them,
glyphs for air conditioning (❄️ `vehicle.cooling`), step-free access
(♿ `vehicle.barrierFree`), and congestion on approach (⚠️ `vehicle.trafficjam`).

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
3. Make sure the emulator has internet (the app needs it to reach the API — the
   Wear emulator normally has network through the host).
4. Pick the `app` run configuration and press **Run** (▶). The app installs and
   launches.

You should see live line-44 departures for Frauengasse. Times change if you
reload (the app fetches on open).

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
    AndroidManifest.xml                 standalone Wear app, INTERNET permission
    java/com/walzengroup/viennadepart/
      MainActivity.kt                   sets the Compose content
      DeparturesViewModel.kt            Loading / Success / Error state
      data/
        MonitorModels.kt                @Serializable mirror of the API JSON
        WienerLinienApi.kt              the monitor GET, no key
        DeparturesRepository.kt         maps the response to the UI model (Phase-1 hardcoded RBLs)
        UiModels.kt                     what the screen renders
      ui/
        DeparturesScreen.kt             the round-screen departures list
        theme/ModeColor.kt              transport color mapping (U-Bahn / vehicle.type)
        theme/Theme.kt                  Wear MaterialTheme
    res/                                launcher icon, strings, theme
  gradle/, gradlew*, settings.gradle.kts, build files
```

## Notes and known bits

- **Hardcoded stop**: `DeparturesRepository` fixes Stephansplatz line U1 (RBLs
  4111 and 4118). Phase 2 replaces this with GPS + the bundled stop CSV.
- **Material**: built on Wear Compose Material (the stable 1.4 line). The spec
  targets Material 3 (`androidx.wear.compose:compose-material3`); that's a later
  swap once the components we need are confirmed stable.
- **Versions** in `gradle/libs.versions.toml` are pinned to a set that builds
  today (AGP 8.7.3, Kotlin 2.1.0, Wear Compose 1.4.1). Android Studio may suggest
  bumps.
