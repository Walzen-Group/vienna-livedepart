# Handoff — Vienna Wear OS departures app

Continuation doc for a fresh session. Read this + the design spec
(`docs/superpowers/specs/2026-07-11-wearos-vienna-departures-design.md`, the UX
source of truth) before writing code. The design spec has the full UX detail;
this doc is **state + remaining work + gotchas**.

## Where things stand

- **Phase 0 — done.** `phase0/departures.py` (uv/Python) proves the API. Bundles
  `phase0/data/wienerlinien-ogd-haltepunkte.csv`.
- **Phase 1 — done.** Bare Wear app compiled and ran on the emulator.
- **Phase 2 — increment 1 of 2 done.** The location-driven flow works end to end:
  **Home (Nearby button) → nearby stops (GPS) → pick a line → departures**.
- **Phase 2 increment 2 + Phase 3 — remaining** (this doc).

The app builds and runs. It's been installed and driven on the emulator this
session. Umlauts, location, and caching are sorted.

## Project / build facts

- **App module:** `wear-app/` (open this folder in Android Studio). Package
  `com.walzengroup.viennadepart`.
- **Toolchain (set by Android Studio's upgrade assistant):** AGP **9.2.1**,
  Kotlin **2.2.10**, Gradle **9.4.1**, JDK 17. UI on **Wear Compose Material
  1.4.1** (the spec targets Material 3 later; M2 is what builds today). AGP 9 is
  bleeding-edge — `gradle.properties` has AGP-9 compat flags that emit harmless
  deprecation warnings.
- **Build from CLI (works, verified):** from `wear-app/`,
  `./gradlew.bat :app:assembleDebug`. Use PowerShell, not the Bash tool (a hook
  redirects build commands). Filter output for `error:|BUILD|FAILED`.
- **Install + launch on the running emulator (adb at `C:\adb\adb.exe`):**
  ```
  adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
  adb -s emulator-5554 shell am start -n com.walzengroup.viennadepart/.MainActivity
  ```
- **Set the emulator's location (needed for Nearby):**
  `adb -s emulator-5554 emu geo fix 16.3726 48.2088` (lon lat = Stephansplatz).
  In the GUI it's the emulator's `⋮` More button → Extended controls → Location.
- **Working split:** Claude writes/edits code and can build + install + launch via
  the commands above; Claude can also reach the Wiener Linien API directly (curl /
  ctx_execute) to check RBLs and data. The developer watches the emulator and
  gives feedback.

## What Phase 2 increment 1 built

Under `wear-app/app/src/main/java/com/walzengroup/viennadepart/`:

- `data/stops/StopRepository.kt` — loads `assets/haltepunkte.csv` (**UTF-8**),
  groups platforms by DIVA into `PhysicalStop`, derives compass labels, answers
  `nearest()` / `search()`. Cached after first load.
- `location/LocationProvider.kt` — one-shot fused location with a 10s timeout and
  a framework last-known fallback (works on emulators without GMS). 60s cache.
- `data/DeparturesRepository.kt` — `linesAtStop()` and `departuresForLine()` from
  one monitor call over the stop's RBLs; groups by platform; 20s `MonitorCache`.
- `ui/HomeScreen.kt` — the Nearby map-pin button (start destination).
- `ui/NearbyScreen.kt` — permission + GPS + nearest-stops list.
- `ui/StopLinesScreen.kt` — lines at a stop (two termini, square badge).
- `ui/DeparturesScreen.kt` — departures, **both directions stacked**, platform
  grouping (compass+RBL header when a direction has 2+ platforms), mode-color
  ground, ❄️/♿/⚠️ glyphs.
- `ui/common/Ui.kt` — `Loadable` (spinner/error/retry), badges, glyphs.
- `MainActivity.kt` — `SwipeDismissableNavHost`: home → nearby → lines →
  departures. `AppViewModel.kt` holds the stop+line selection.
- `ui/theme/ModeColor.kt` — the verified transport colors.

## Remaining work

### Phase 2 — increment 2 (finish the departures interaction + home)

1. **Swipe = direction on the departures screen.** Right now it shows both
   directions stacked; the design shows **one direction at a time**, swipe
   left/right to flip H ↔ R, with the two destinations as tabs
   (`‹ Westbahnhof · Gersthof ›`) up top. Within a direction, keep the existing
   platform-stacking (2+ poles → a `→ dest compass·RBL` header each; single
   platform → no header). Use `HorizontalPager` (2 pages: H, R). The grouping
   data already exists in `departuresForLine` — split `PlatformGroup`s by
   direction into two pages.
2. **Crown = next stop along the line (farther out).** *Needs data we don't bundle
   yet.* `haltepunkte.csv` has no line→stop routing. Decide one of:
   - Bundle the OGD **`linien` + `fahrwegverlaeufe`** (route path) CSVs and step
     along the real sequence from the current stop outward, OR
   - Approximate: step through stops that serve the line, sorted by distance from
     the user (no true route order).
   Then wire rotary input: `Modifier.onRotaryScrollEvent` / `rotaryScrollable`
   with a `FocusRequester` (rotary events only reach the focused element — this
   is the #1 gotcha).
3. **Home pager: Favorites / Nearby / Search.** Replace the single Nearby button
   with a `HorizontalPager` — swipe left for Favorites, right for Search; Nearby
   in the middle. Up/down stays free for list scrolling. (Favorites content is
   Phase 3; a placeholder is fine until then.)
4. **Search a stop.** Use the watch's built-in text + voice input
   (`RemoteInput` via `ActivityResultContracts` / Wear input intent), fuzzy-match
   with `StopRepository.search()` (already written), show results like Nearby.
5. **First-load affordance (polish).** The first stop fetch is one network call;
   consider a lightweight "loading…" cue so it doesn't feel dead. (Open question
   I left with the user.)

### Phase 3 — favorites + tile

6. **Favorites model.** A favorite is a **line** (not a stop), max **6**. Persist
   locally (DataStore Preferences). A Favorites management screen (the pager's
   left page): pin/unpin (star), reorder.
7. **Tapping a favorite line → nearest stop on that line → departures.** *Same
   routing-data problem as crown:* "nearest stop that serves line X" needs
   line→stops mapping, or an approximation (check nearby stops' live monitors for
   the line). Decide alongside task 2.
8. **The tile.** A Wear OS **Tile** (ProtoLayout / `androidx.wear.tiles` +
   `androidx.wear.protolayout`, **NOT Compose** — the biggest divergence). Six
   line buttons in a honeycomb around a centre app button (white train icon).
   Dark circular tiles, line number in mode color. Static, no scroll, hard cap 6.
   Tapping a line deep-links into the located departures view; tapping the centre
   opens the app. Tiles render as static snapshots — plan refresh/tap intents.

## Design decisions already locked (don't re-litigate)

From the design spec + this session:

- Favorite = a **line**; tap geolocates to the nearest stop on it.
- Departures: **swipe = direction**, **crown = next stop**. Same-direction
  platforms stack; single-platform directions skip the header.
- Home opens on **Nearby**; **swipe** left/right to Favorites/Search (up/down
  scrolls lists). Search = system text+voice.
- **A/C snowflake year-round.** Deep **mode-color ground** on the times screen.
- **Short-turns / depot runs are shown**, not filtered (they're rideable). They
  sort to the bottom by countdown and get their own `→ destination` header.
- Tile: **6 lines + centre app button**, static, no scroll.
- Colors are an **in-app map** (feed has none) — see `ModeColor.kt`. Bus / Badner
  Bahn blues are conventional; confirm vs the official brand guide before ship.

## Gotchas discovered this session

- **CSV is UTF-8**, not cp1252 (the earlier cp1252 reading caused `BrandstÃ¤tte`).
  Phase 0's `departures.py` still reads cp1252 — tidy it if that file is UTF-8 too
  (low priority, it's the throwaway proof script).
- **Rotary needs focus** — no `FocusRequester` = crown does nothing.
- **`vehicle.cooling` may be absent** on old responses → render "unknown", not
  "no A/C".
- **Compass labels are coarse** (8 buckets); the RBL in the header disambiguates.
- **Route/sequence data is not bundled** — the crown-stops and
  nearest-stop-on-a-line features both hinge on deciding how to get it. Resolve
  this first when starting increment 2, it blocks tasks 2 and 7.
- **Emulator location must be set** or Nearby errors (fast, with Retry).

## Suggested order for next session

1. Decide the route-data question (bundle `linien`/`fahrwegverlaeufe` vs
   approximate) — unblocks crown + favorites.
2. Swipe-direction on the departures screen (task 1) — self-contained, high value.
3. Home pager + search (tasks 3, 4).
4. Crown-stops (task 2).
5. Phase 3: favorites persistence + management (tasks 6, 7).
6. Phase 3: the tile (task 8) — largest, most different (ProtoLayout).

Each should build (`:app:assembleDebug`) and ideally be installed to the emulator
for a look before moving on.
