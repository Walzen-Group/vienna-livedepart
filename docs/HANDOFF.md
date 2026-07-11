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
- **Phase 3 — favorites + crown rework + index view done; tile NOT started.**
  Star/pin lines, favorites page, favorite → nearest stop → departures, settings.
  The crown UX was reworked (see below) and a **fast-crown index (register) view**
  was added. The **honeycomb Tile** is the main remaining feature.

The app builds and runs; it's been driven on the emulator continuously this
session.

## Departures crown + index view (current behaviour)

- **Crown opens the index.** Slow step-between-stops is gone. Any crown in the
  departures view opens a full-screen **index (register) view**: a mode-colour
  route line bowed to the round screen (a parabola in y, sampled — not a polyline),
  a white dot per stop, two-line station names to the right, the current stop
  highlighted and centred. Crown moves the highlight; a **tap** selects (auto-loads
  that stop, adds it to `loaded`); a **horizontal swipe** cancels back to the prior
  stop. `TimeText` is hidden while the index is open. `IndexView` uses a plain
  `Box` + `onSizeChanged` (NOT `BoxWithConstraints` — the IDE's Compose lint flags
  an unused scope even when the CLI lint doesn't).
- **Chrome is persistent, header rides in the page.** Background gradient +
  bezel `PositionIndicator` are drawn once above the pager; the header (badge +
  stop name, from bundled data so it never blanks) slides inside each page. The
  multi-platform list is a plain touch-scroll `Column`, NOT `ScalingLazyColumn`
  (the SLC has built-in rotary and swallowed the crown once it took focus on touch).
- **Loaded stops persist.** `loaded` is a `mutableStateListOf` hoisted above the
  pager, so a stop stays loaded when its page scrolls out and back. Live refresh is
  scoped to the on-screen stop (`Loadable` keys on `refreshMs`).
- **Favorite star** is the last item in the scrolling departures content (no
  background); on single-platform stops it sits at the bottom of the page. **Do not
  make it a floating overlay or a chrome-docked bar** (repeatedly-corrected point).
- **Departure cards** show the wall-clock time overlaid at the card's true centre.
- **`chainFor` matches BOTH pattern ends to live termini** before taking the
  longest, so short-worked lines (e.g. 44) don't extend to a rare long variant's
  far terminus (Winckelmannstraße). Terminus-only + longest-overall are fallbacks.

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
  validated download/refresh + cache invalidation), `RecentStops.kt` (recently
  opened stations + the last line ridden there; deduped by DIVA, most-recent-first,
  capped at 10 — replaces the old single `LastConnection`),
  `SearchHistory.kt`, `Favorites.kt`, `AppSettings.kt`, `UiModels.kt`.
- **UI:** `HomeScreen.kt` (4-page `HorizontalPager`: Favorites · Nearby · Search ·
  Settings; Nearby = split Locate + two most-recent stations pill centered on the
  first screenful, older recents scroll below under a "Recent" header with a docked
  "Clear all" pill at the end; Search = text+voice (U2-tinted magnifier) + fuzzy +
  history + docked "Clear all"; Favorites list + gold star/gradient), `DeparturesScreen.kt`
  (swipe=direction tabs+`HorizontalPager`, **crown = vertical station
  `VerticalPager`**, bottom star, boarding ✳, mode-color gradient),
  `SettingsScreen.kt` (SettingsPage: open-to toggle + a transit-data card showing
  each CSV's date and last-download time + refresh button),
  `NearbyScreen.kt`, `StopLinesScreen.kt`, `FavoriteOpenScreen.kt`,
  `ui/common/Ui.kt` (`Loadable` with `loadingLabel`/`refreshMs`/`key`),
  `ui/theme/ModeColor.kt` (`forLine` + `modeName`).
- **AppViewModel.kt** — holds selection + hoists `searchHistory`, `favorites`, and
  `recentStops` (so they survive home page swipes) via `ensureLoaded`. Opening a
  station's departures calls `addRecent` (once per stop view, not per 30s refresh).
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
3. **Crown scrolling — DONE.** Reworked into persistent chrome + in-page header +
   plain touch-scroll list, then replaced stop-stepping with the fast-crown **index
   view**. See "Departures crown + index view" above. Spec:
   `docs/superpowers/specs/2026-07-11-departures-index-view-design.md`.
4. **Polish / deferred:** confirm bus / Badner-Bahn brand colors; Material 3
   migration (currently M2). Index view tuning knobs (trigger sensitivity, curve
   bow, spacing, dot sizes, name width) are constants in `IndexView`.

## Design decisions locked (incl. deviations from the spec)

- **Favorite = a line.** The favorites list shows the line badge + **mode label**
  ("Tram"/"Bus"/"Subway"/…), *not* termini — deriving termini from the route data
  was unreliable (depot/short-working variants). Tap → nearest stop on the line →
  departures.
- **Crown opens the index view** (see the crown section above). Crowning fires
  **no API requests**; selecting a stop in the index auto-loads it. Reached-but-not-
  selected stops never fetch.
- **Crown route order / chain** comes from the `fahrwegverlaeufe` pattern whose
  ends match the live termini (`DeparturesUi.directions` labels). `chainFor`
  requires **both** ends to be live termini (then longest), falling back to
  terminus-only then longest-overall — avoids depot runs ("Bhf Hernals
  Kurzführung") and short-work overshoot (44 → Winckelmannstraße).
- **Home** = Favorites · Nearby · Search · Settings (Settings is a 4th slide, not
  a nav screen). Settings has an **"Open to favorites / Open to home"** toggle.
- **Recent stations** replace the old single last-connection. Nearby shows the two
  newest as the split pill's right half, the rest as chips below; tapping any recent
  reopens departures on the line last ridden there. **"Clear all" is docked in the
  scrolling list, never a floating overlay** (matches the favorite-star rule). It is
  the shared `ClearAllButton` (`CompactChip`) on both Nearby and Search. The Search
  list sets `scalingParams(edgeScale = 1f)` so the bottom-edge item isn't shrunk by
  the SLC fisheye, keeping it the same physical size as on Nearby.
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
