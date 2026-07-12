# Vienna Departures — Wear OS app

A standalone Compose for Wear OS app for live Wiener Linien departures. It uses
your location to find nearby stops, lets you pick a line, and shows the next
departures — themed by the line's mode color, with air-conditioning, step-free,
and congestion glyphs. Pin favorite lines, search by name or voice, and open
departures straight from a honeycomb tile.

No API key: the Wiener Linien data is open. Package
`com.walzengroup.viennadepart`. Current version **0.5** (versionCode 5).

## What you'll see

The app opens on a four-page home pager (swipe left/right):

1. **Favorites** — your pinned lines, each a mode-color badge + mode label
   ("Tram" / "Bus" / "Subway"). Tap one → nearest stop on that line → departures.
2. **Nearby** — locate the closest stops, plus your two most-recent stations as a
   pill; older recents scroll below under a "Recent" header. Tap a recent to
   reopen departures on the line last ridden there.
3. **Search** — text or voice search over all stops (fuzzy / typo-tolerant), with
   history and a "Clear all".
4. **Settings** — "open to favorites / open to home" toggle, and a transit-data
   card showing each bundled CSV's date + last download, with a refresh button.

Pick a stop → **lines at the stop** (badge + termini) → **departures**: both
directions on a deep mode-color ground. Swipe changes direction; when a direction
runs from two platforms they stack, each with its compass + RBL:

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
A ✳ blinks at 0 min (boarding). Tap the star to pin/unpin the line.

**Crown scrolling opens an index (register) view** — a mode-color route line bowed
to the round screen with a dot per stop and station names alongside, the current
stop centered. Crown moves the highlight, a tap jumps to that stop's departures, a
horizontal swipe cancels. Swipe from the left edge to go back.

## Tile

A honeycomb **Tile** shows up to six pinned lines around a center app button
(ProtoLayout, not Compose). Tapping a line deep-links into that favorite's
located departures; the center opens the app. Reads the favorites store.

## Prerequisites

- **Android Studio** (Ladybug or newer). It bundles the Kotlin compiler, Android
  SDK, `adb`, and the Wear OS emulator.
- JDK 17 (Android Studio bundles one; a system JDK 17 also works).

Nothing else — no API key.

## Open it

1. In Android Studio: **File → Open** and select the `wear-app/` folder (the one
   with `settings.gradle.kts`).
2. Let Gradle sync. First sync downloads Gradle and the dependencies; give it a
   few minutes. `local.properties` points at your SDK.
3. If prompted to install an SDK platform or build-tools, accept.

## Run on the emulator

1. **Tools → Device Manager → Create Device → Wear OS**. Pick a round device and
   a recent system image (API 34+).
2. Start the emulator.
3. **Set a location in Vienna** so Nearby / Favorites have something to find:
   emulator window → **`...` (Extended controls) → Location**, enter a Vienna
   point (e.g. lat `48.2088`, lon `16.3726` for Stephansplatz) and **Send**.
   Without a fix, Nearby errors. A reboot clears the location — re-set it.
4. Make sure the emulator has internet (the Wear emulator normally has it through
   the host).
5. Pick the `app` run configuration and press **Run** (▶). Grant the location
   permission when asked.

## Run on a real watch

Needs Wear OS 3+ (minSdk 30) and a network (Wi-Fi / LTE / phone tether). Most
watches install over Wi-Fi (no USB): on the watch enable Developer options (tap
the build number 7×), turn on ADB + Wireless debugging, then on the computer
`adb pair <ip:port>` with the shown code and `adb connect <ip:port>`. Android
Studio (or `adb install`) can then install to the watch. Grant location on first
launch.

## Release build (signed, for a watch)

The release APK is signed with the project's own key so it installs as an update
over an existing install without wiping favorites / recents. Signing reads
`keystore.properties` (gitignored) in `wear-app/`, which points at the keystore
(default `vienna-release.jks`, also gitignored) and holds its passwords:

```
storeFile=vienna-release.jks
storePassword=…
keyAlias=vienna
keyPassword=…
```

If `keystore.properties` is absent, the release build is left **unsigned**. If the
keystore file it points at is missing, the build **fails** — restore it from your
backup to `wear-app/vienna-release.jks` first. **Back up the keystore + passwords**:
losing them means any future build gets a different signature, which forces an
uninstall (wiping favorites / recents) before it can install.

Build, then (optionally) verify the signer is the release key and not the debug key:

```
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# verify signature: apksigner lives in the SDK build-tools. ANDROID_HOME isn't
# always exported, so fall back to the default macOS SDK path, and pick the
# newest build-tools so the glob resolves to a single binary.
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
"$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)" \
  verify --print-certs app/build/outputs/apk/release/app-release.apk
# expect: CN=Vienna LiveDepart, O=Walzen Group, C=AT  (not CN=Android Debug)
```

Install to a connected watch (see "Run on a real watch" for pairing). Same
signature, so `-r` updates in place:

```
adb devices                          # find the watch serial (ip:port or adb-…tls-connect)
adb -s <watch-serial> install -r app/build/outputs/apk/release/app-release.apk
```

Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts` when you
want the watch to treat the build as a proper update; `-r` also reinstalls the same
versionCode in place for quick iteration.

## Command line

From `wear-app/`:

```
./gradlew :app:assembleDebug     # build the debug APK
./gradlew :app:installDebug      # install to a running emulator / connected watch
```

The debug APK lands in `app/build/outputs/apk/debug/`.

## Project layout

```
wear-app/
  app/src/main/
    AndroidManifest.xml                 standalone Wear app; INTERNET + location; tile service
    assets/
      haltepunkte.csv                   bundled stop reference (UTF-8)
      line_routes.csv                   GTFS-derived line → ordered-stop chains
    java/com/walzengroup/viennadepart/
      MainActivity.kt                   nav host: home pager → lines → departures
      AppViewModel.kt                   selection + hoisted favorites / recents / search history
      data/
        MonitorModels.kt                @Serializable mirror of the API JSON
        WienerLinienApi.kt              the monitor GET, no key
        DeparturesRepository.kt         lines at a stop, departures per line (20s cache)
        RouteRepository.kt              line → stop chain matched to live termini
        stops/StopRepository.kt         CSV load, DIVA grouping, nearest / fuzzy search
        stops/StopModels.kt             PhysicalStop / Platform / StopDistance
        UiModels.kt                     LineOption / DeparturesUi / PlatformGroup
        Favorites.kt                    pinned lines
        RecentStops.kt                  recent stations + last line ridden (capped 10)
        SearchHistory.kt                recent searches
        AppSettings.kt                  open-to preference
        TransitData.kt                  filesDir-preferred CSV load + validated refresh
        TransitRefreshWorker.kt         background data refresh (WorkManager)
      location/LocationProvider.kt      fused current-location + permission check
      tile/FavoritesTileService.kt      honeycomb ProtoLayout tile of pinned lines
      ui/
        HomeScreen.kt                   Favorites · Nearby · Search · Settings pager
        NearbyScreen.kt                 GPS → nearest stops + recents
        StopLinesScreen.kt              lines at a stop
        DeparturesScreen.kt             departures + crown index view + star
        SettingsScreen.kt               open-to toggle + transit-data card
        FavoriteOpenScreen.kt           favorite → nearest stop → departures
        common/Ui.kt                    Loadable / error / badge / glyph helpers
        theme/ModeColor.kt              transport color mapping
        theme/Theme.kt                  Wear MaterialTheme
    res/                                launcher icon, drawables, strings, theme
  gradle/, gradlew*, settings.gradle.kts, build files
```

## Notes and known bits

- **Toolchain** (`gradle/libs.versions.toml`): AGP 9.2.1, Kotlin 2.2.10, JDK 17,
  Wear Compose Material 1.4.1, wear-input 1.2.0, wear-tiles 1.4.0,
  wear-protolayout 1.2.0. minSdk 30, targetSdk 34.
- **Material**: built on Wear Compose Material (the stable 1.4 M2 line). The spec
  targets Material 3; that's a later migration.
- **Data**: bundled `haltepunkte.csv` + `line_routes.csv`, both refreshable from
  Settings (a downloaded copy in filesDir wins over the bundled asset). Crown
  route order comes from the `line_routes` pattern whose ends match the live
  termini, avoiding depot / short-working overshoot.
- **Remaining**: favorites unpin/reorder on the Favorites list (unpin is currently
  only via the departures star); brand-color confirmation for bus / Badner-Bahn;
  the Material 3 migration. See `docs/HANDOFF.md` and the design specs under
  `docs/superpowers/specs/` for the full picture.
```