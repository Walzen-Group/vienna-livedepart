# Handoff — Vienna Wear OS departures app

Continuation doc for a fresh session. Read this + the design spec
(`docs/superpowers/specs/2026-07-11-wearos-vienna-departures-design.md`, the UX
source of truth) before writing code. The spec has the UX intent; this doc is
**current state + remaining work + gotchas**, and records where the build
deviated from the spec.

## Where things stand

- **Phase 0 — done.** `phase0/departures.py` (uv/Python) proves the API.
- **Phase 1 — done.** Bare Wear app on the emulator.
- **Phase 2 — done.** Location flow, swipe-direction departures, home pager,
  search, crown-scroll between stations, bundled+refreshable route data.
- **Phase 3 — favorites done; tile NOT started.** Star/pin lines, favorites page,
  favorite → nearest stop → departures, settings. The **honeycomb Tile** is the
  main remaining feature.

The app builds and runs; it's been driven on the emulator continuously this
session.

## Project / build facts

- **App module:** `wear-app/` (open in Android Studio). Package
  `com.walzengroup.viennadepart`.
- **Toolchain:** AGP **9.2.1**, Kotlin **2.2.10**, Gradle **9.4.1**, JDK 17. Wear
  Compose Material **1.4.1** (M2 — the spec's Material 3 is a future migration).
  `androidx.wear:wear-input:1.2.0` added for search input. AGP 9 emits harmless
  deprecation warnings.
- **Build (verified):** from `wear-app/`, PowerShell (a hook redirects build
  commands away from the Bash tool):
  `./gradlew.bat :app:assembleDebug` — filter for `error:|BUILD|FAILED|e: `.
- **Install + launch (adb at `C:\adb\adb.exe`):** always **force-stop** before
  launch so the new APK's process actually restarts:
  ```
  adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
  adb -s emulator-5554 shell am force-stop com.walzengroup.viennadepart
  adb -s emulator-5554 shell am start -n com.walzengroup.viennadepart/.MainActivity
  ```
- **Emulator location (needed for Nearby / favorites open):**
  `adb -s emulator-5554 emu geo fix 16.3726 48.2088` (Stephansplatz). A reboot
  clears it — re-set after `adb reboot`.
- **Working split:** Claude edits code and can build + install + launch + reach
  the Wiener Linien API (curl / ctx_execute) to verify data. The developer
  watches the emulator and gives feedback.

## What's built (files under `.../viennadepart/`)

- **Data:** `stops/StopRepository.kt` (bundled `haltepunkte.csv`, UTF-8; nearest /
  typo-tolerant `search()` off the main thread; `stopForRbl`),
  `DeparturesRepository.kt` (`linesAtStop`, `departuresForLine`; 20s
  `MonitorCache`), `RouteRepository.kt` (bundled `linien.csv` +
  `fahrwegverlaeufe.csv` → line→ordered-stop chain **matched to the live termini**,
  and `nearestStopOnLine`), `TransitData.kt` (filesDir-preferred loading +
  validated download/refresh + cache invalidation), `LastConnection.kt`,
  `SearchHistory.kt`, `Favorites.kt`, `AppSettings.kt`, `UiModels.kt`.
- **UI:** `HomeScreen.kt` (4-page `HorizontalPager`: Favorites · Nearby · Search ·
  Settings; Nearby = split Locate/recent pill; Search = text+voice + fuzzy +
  history; Favorites list + gold star/gradient), `DeparturesScreen.kt`
  (swipe=direction tabs+`HorizontalPager`, **crown = vertical station
  `VerticalPager`**, bottom star, boarding ✳, mode-color gradient),
  `SettingsScreen.kt` (SettingsPage: update transit data + open-to toggle),
  `NearbyScreen.kt`, `StopLinesScreen.kt`, `FavoriteOpenScreen.kt`,
  `ui/common/Ui.kt` (`Loadable` with `loadingLabel`/`refreshMs`/`key`),
  `ui/theme/ModeColor.kt` (`forLine` + `modeName`).
- **AppViewModel.kt** — holds selection + hoists `searchHistory` and `favorites`
  (so they survive home page swipes) via `ensureLoaded`.
- **assets:** `haltepunkte.csv`, `linien.csv`, `fahrwegverlaeufe.csv`.
  **res/drawable:** `ic_pin`, `ic_search`, `ic_star`, `ic_refresh`.

## Remaining work

1. **The Tile (Phase 3, biggest).** Wear OS Tile via ProtoLayout
   (`androidx.wear.tiles` + `androidx.wear.protolayout`, **NOT Compose**). Six
   line buttons in a honeycomb around a centre app button; dark circular tiles,
   line number in mode color; static, no scroll, hard cap 6. Tapping a line
   deep-links into that favorite's located departures (same as `onOpenFavorite`);
   centre opens the app. Reads `FavoritesStore`.
2. **Favorites management on the list** — currently tap = open, and unpin is only
   via the star on the departures screen. Add unpin + reorder on the Favorites
   page (spec wants it).
3. **Crown scrolling — needs rework (known problems).** The current
   `VerticalPager`-over-stations approach works but feels wrong:
   - **Catches on multi-platform stations** — a stop with 2+ platforms (inner
     scrollable list) traps the crown and won't advance past it.
   - **Doesn't feel like one sheet** — it reads as paging through separate windows
     with heavy snapping, not scrolling a single continuous surface. Likely wants
     a proportional/continuous rotary scroll (a single scrollable surface, or
     `rotaryScrollable` with a gentler snap) rather than page-per-stop.
   - **Load button shows the global spinner** — tapping reload swaps in
     `Loadable`'s full-screen `CenteredProgress`. It should keep the line number +
     station name visible and only show a spinner in the body (don't blank the
     header).
   - **Background gradient scrolls with the pages** — it should be a single
     persistent background behind everything, not re-drawn per sliding page.
4. **Polish / deferred:** confirm bus / Badner-Bahn brand colors; Material 3
   migration (currently M2).

## Design decisions locked (incl. deviations from the spec)

- **Favorite = a line.** The favorites list shows the line badge + **mode label**
  ("Tram"/"Bus"/"Subway"/…), *not* termini — deriving termini from the route data
  was unreliable (depot/short-working variants). Tap → nearest stop on the line →
  departures.
- **Crown = scroll between stations** (`VerticalPager`, crown-only, inner list
  keeps touch-scroll). Crowning fires **no API requests**: each crowned-to station
  shows its name + a **reload button** to tap; only the opened stop auto-loads.
  This replaced the earlier discrete-step + auto-fetch (which got rate-limited).
- **Crown route order** comes from the `fahrwegverlaeufe` pattern whose **terminus
  matches a live destination** (`DeparturesUi.directions` labels), not the longest
  pattern — avoids depot runs like "Bhf Hernals Kurzführung".
- **Home** = Favorites · Nearby · Search · Settings (Settings is a 4th slide, not
  a nav screen). Settings has an **"Open to favorites / Open to home"** toggle.
- Departures: swipe = direction; same-direction platforms stack (single platform
  skips the header and is centered + non-scrollable). **No H/R in the group
  header** (the tabs already say the direction).
- Boarding = **✳** blink at 0 min. A/C snowflake year-round. Deep mode-color
  ground. Short-turns/depot runs shown, not filtered.
- Transit CSVs are bundled **and** refreshable from Settings (filesDir wins over
  the bundled asset).

## Gotchas discovered

- **Rotary needs `rememberActiveFocusRequester()`** (Wear foundation,
  `@OptIn(ExperimentalWearFoundationApi::class)`). A plain `FocusRequester` +
  manual `requestFocus()` did NOT hold focus → crown fired nothing.
- **Search input:** the watch keyboard leaves the last word uncommitted unless the
  IME action commits it → set `setInputActionType(IME_ACTION_SEARCH)` via
  `WearableRemoteInputExtender` (wear-input 1.2.0). The **PC hardware keyboard**
  still won't commit on the emulator — emulator-only, fine on-device.
- **`fahrwegverlaeufe.StopID` = the RBL** (haltepunkte StopID), Direction 1/2 =
  H/R. Lines have **many patterns** (depot runs, short-workings); pick by matching
  the live terminus, not by length.
- **`am start` alone can resume a stale process** — always `am force-stop` first.
- **Header at the narrow top of a round screen clips** full-width content —
  constrain to ~0.66 width, badge fixed, name marquees.
- CSV is UTF-8. `vehicle.cooling` may be absent → "unknown". Compass labels coarse
  (RBL disambiguates). Emulator location must be set or Nearby errors.

## Suggested order for next session

1. The **Tile** (ProtoLayout) — the last major feature.
2. Favorites unpin/reorder on the list.
3. Brand-color check; consider the Material 3 migration.

Each change should build (`:app:assembleDebug`) and be installed (with
`am force-stop`) for a look before moving on.
