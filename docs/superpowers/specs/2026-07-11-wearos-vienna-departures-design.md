# Vienna live departures on Wear OS — design spec

Status: design approved, ready for implementation planning.
Draft mockup: `docs/design/wearos-ux-draft.html` (published artifact).
Companion: `docs/design/tile-style-options.html` (tile style exploration).

## Goal

A standalone Wear OS app that shows live Wiener Linien departures — the real-time
data Google Maps omits. Open the app, use your location to find the nearest stop,
pick a line, read the countdown. Pin the lines you ride to a glanceable tile.

The user is a beginner. Build in runnable phases; each phase must run before the
next starts. Claude Code writes and edits code; the developer runs builds,
installs to the watch, and anything that must execute on-device. Claude Code can
reach the Wiener Linien API from its own environment (useful for verifying data),
but cannot run a Wear OS build.

## Data source

Wiener Linien OGD real-time API. Public, unauthenticated, CC-BY, no API key.

### Live departures — monitor endpoint

```
GET https://www.wienerlinien.at/ogd_realtime/monitor?rbl=<id>
Header: Accept: application/json
```

- `rbl` identifies one platform/direction, not a whole stop. Repeatable:
  `?rbl=555&rbl=1370`.
- Response path the app depends on:

```
message.value                              "OK" when healthy
data.monitors[]                            one per requested platform (RBL)
  locationStop.properties.title            stop name
  locationStop.properties.attributes.rbl   the RBL
  locationStop.geometry.coordinates        [lon, lat] WGS84
  lines[]
    name                                   line label, e.g. "44"
    towards                                destination, e.g. "Schottentor U"
    direction                              "H" (Hin) or "R" (Rück)
    departures.departure[]
      departureTime.countdown              minutes until departure (int)
      departureTime.timeReal               real timestamp (ISO 8601), when available
      departureTime.timePlanned            scheduled timestamp
    vehicle                                per-departure metadata (see below)
```

### Per-vehicle metadata (`departures.departure[].vehicle`)

Verified live and against the OGD documentation (V1.5, 2026-05-21):

| Field | Meaning | Use |
|---|---|---|
| `cooling` | vehicle has air conditioning | ❄ snowflake |
| `barrierFree` | step-free / accessible | ♿ glyph |
| `foldingRamp` | has a folding ramp | accessibility, optional |
| `trafficjam` | congestion on approach | optional later |
| `type` | mode code: `ptTram`, `ptBusCity`, `ptMetro`, `ptTrainS`, `ptBusNight`, `ptRufBus`, `ptTramWLB` | color mapping |
| `name`, `towards`, `direction` | per-departure line/destination/direction | fall back to line-level values |

`cooling` is new (V1.5); treat a missing key as "unknown," not "no A/C." The
`attributes` object is always empty — ignore it.

### Stop reference data (bundled offline)

```
https://www.wienerlinien.at/ogd_realtime/doku/ogd/wienerlinien-ogd-haltepunkte.csv
Encoding: cp1252 (Windows-1252) — has Vienna umlauts (Döblinger Hauptstraße)
Columns (semicolon): StopID ; DIVA ; StopText ; Municipality ; MunicipalityID ; Longitude ; Latitude
```

- `StopID` is the RBL. `StopText` is the stop name. `DIVA` is the physical-stop id.
- Ship this file inside the app. It's the offline half of every lookup; only the
  live departure fetch needs network.

## Core data model

- **Physical stop** = a DIVA. Its several platforms (RBLs) collect under it. This
  is the real unit: a hub like Schottentor has one DIVA whose platforms carry
  slightly different names ("Schottentor U", "Schottentor", "…(Bedarf)"). Group
  by DIVA, not by name string. Rows with a missing/all-zero DIVA stand alone,
  keyed by name.
- **Canonical stop name** = the most common `StopText` among a DIVA's platforms;
  tie-break toward a name without a "(…)" qualifier, then the shortest.
- **Platform** = an RBL, with coordinates.
- **Line** = a `name` (e.g. "44", "U2"). This is what a favorite pins.
- **Direction** = `H` / `R` (Hin/Rück). Not the same as destination.
- **Destination** = `towards`. A single direction can have several destinations
  (short-turns, branches). All are shown; none are filtered.
- **Compass label** = derived from the platform's coordinate versus the stop's
  centroid, bucketed into 8 points (N, NE, …). Coarse; only used to tell two
  same-direction platforms apart. The API publishes no cardinal directions.

### Decisions that shaped the model

- **Short-turns / depot runs are shown, not hidden.** A `Betriebsbahnhof`
  destination is a passenger-serving short-turn you can ride to; hiding it would
  drop a real departure. Sort by countdown; the far-out ones naturally sink to the
  bottom.
- **Multiple destinations per direction** are handled by the platform header
  (destination + compass + RBL). Two destinations = two headers, same mechanism as
  two poles.
- **GPS resolves the two-pole problem.** When a stop has two platforms for a
  direction, the app can pick the nearest by coordinate; in the departures list
  both are shown so the rider chooses.

## App structure and navigation

The **app** is the product: location, search, browsing, live times, and managing
favorites. The **tile** is a glanceable shortcut set, edited from the app.

### Home — a horizontal pager

The app opens on **Nearby** (the middle screen). Swipe left/right between three
home screens; vertical scroll (crown / swipe up-down) is left free to scroll the
lists.

```
‹ Favorites   ·   Nearby (default)   ·   Search ›
```

- **Nearby** — a flat circular button with a red map-pin and "Nearby" below.
  Tapping it uses location to list nearby stops.
- **Favorites** — the saved lines as a scrollable list; pin, unpin, reorder.
- **Search a stop** — the watch's built-in text + voice input (the standard
  message-reply interface); no custom keyboard. Matches against the bundled stop
  list with fuzzy matching (the Phase 0 approach: `difflib`-style scoring with
  umlaut folding, DIVA grouping).

### Drill-down

Nearby / Search / a favorite → **pick a line** → **departures**.

- **Nearby stops** — physical stops sorted by distance, with metres shown.
- **Pick a line** — one row per line at the stop. The row shows the line's two
  termini, one per line, both equal weight (neither direction is privileged), with
  a **square** line badge spanning both rows.
- **Departures** — see below.

### Tapping a favorite (from tile or list)

A favorite is a *line*, not a stop. Tapping it geolocates, finds the nearest stop
on that line, and opens its departures. A brief locating state (pulsing GPS ring)
covers the fix.

## The departures screen

Shows **one line, one direction at a time**, at the current stop.

- **Direction** is chosen by horizontal **swipe** (H ↔ R). The two destinations
  sit up top as tabs (`‹ Westbahnhof · Gersthof ›`), active one highlighted, so
  you always see what the other swipe holds.
- **Crown** steps to the next stop along the line, farther out. A rotary indicator
  on the right edge tracks position. (Swipe = direction, crown = stops — two axes,
  no collision.)
- **Platforms within the shown direction stack.** If the direction runs from more
  than one platform (two poles, or two destinations like a short-turn), each gets
  its own header and its next departures below it. When a direction has a single
  platform, the header is omitted (it would just repeat the tab).
- **Platform header** = `→ destination · compass · RBL · H/R`, shown only when
  there are 2+ platforms to disambiguate.
- **Two departures per platform.**
- **Per-departure glyphs**: countdown (whole minutes), ❄ air conditioning
  (`cooling`), ♿ step-free (`barrierFree`).
- **Background** carries the line's mode color as a deep tint (e.g. tram red for
  line 9), fading to black. Kept year-round.
- **Star** pins the line to the favorites tile.

Example — line 9 at JNBP, swiped to Gersthof (R), which has two poles:

```
        9 · JNBP  ★
   ‹ Westbahnhof · [Gersthof] ›
   → Gersthof    SW · 547    R
   [ 2 min    ♿ ]
   [ 17 min   ❄ ]
   → Gersthof    NE · 548    R
   [ 6 min ]
   [ 21 min   ❄ ]
```

Single-platform direction (swipe left to Westbahnhof) drops the header:

```
        9 · JNBP  ★
   ‹ [Westbahnhof] · Gersthof ›
   [ 4 min    ❄ ]
   [ 18 min ]
```

## Favorites and the tile

- A favorite is a **line**. The user pins up to **six**.
- **Manage** in the app (the Favorites home screen): a list with a star to
  pin/unpin, reorderable.
- **The tile** (Wear OS Tile, a glanceable surface): six line buttons arranged in
  a honeycomb around a **centre app button** (a white train icon). Tapping the
  centre opens the app; tapping a line jumps to its nearest-stop departures.
- **Hard cap of six.** Wear OS Tiles are static and cannot scroll, so whatever
  fits is the limit; the seventh slot is the app button, and pinning a seventh
  line is simply not offered.
- The tile stores only the line list — no live data — so it never goes stale or
  drains battery. The fetch happens on tap, once the app knows where you are.

### Tile button style

Dark neutral circular tiles (`#1c1c22`, thin 1px light border), the **line number
in its mode color** (brightened enough that dark hues like bus blue stay legible
on near-black). Color is the mark, not the fill. Flat, no gloss or bevel.

## Visual system

### Color = transport mode

The Wiener Linien feed publishes **no colors**. Color is an in-app map, keyed off
the line name (U-Bahn) and `vehicle.type` / `MeansOfTransport` (everything else).

| Mode | Hex | Source |
|---|---|---|
| U1 | `#E20613` | Wikidata P465 (authoritative) |
| U2 | `#A762A3` | Wikidata P465 |
| U3 | `#F07D00` | Wikidata P465 |
| U4 | `#009540` | Wikidata P465 |
| U5 | `#008E95` | Wikidata P465 |
| U6 | `#9D6930` | Wikidata P465 |
| Tram (`ptTram`) | `#E20613` | Wiener Linien red |
| S-Bahn (`ptTrainS`) | `#159DD9` | Wikidata P465 |
| Bus (`ptBusCity`/`ptBusNight`/`ptRufBus`) | `#1C3F94` | conventional — confirm against brand guide |
| Badner Bahn (`ptTramWLB`) | `#0069B4` | conventional — confirm against brand guide |

`ptMetro` maps by line name (U1–U6). U-Bahn hexes are locked; tram and S-Bahn are
solid; **bus and WLB blues are conventional and need a human check against the
official Wiener Linien brand guide before shipping.**

### Other

- Watch screens are OLED black grounds; the departures screen adds the mode-color
  deep tint.
- Numbers that line up in columns use tabular figures.
- The tram/train app-button icon and the map pin are vector assets tinted white /
  red at build time.

## This is a draft — build on Material

The mockup defines **intent** (layout, flow, color-by-mode, glyphs, gestures). The
build uses **Compose for Wear OS with Material 3** (`androidx.wear.compose.material3`),
so exact shapes, corner rounding, typography, and touch feedback come from the
component library and will differ slightly. That's expected and fine.

| Draft element | Wear Material 3 component |
|---|---|
| Nearby / pick-line / favorites lists | `ScalingLazyColumn` + `Chip` / `TitleCard` |
| Nearby location button | `Button` with icon |
| Departures rows (pills) | `Card` / `Chip` in a `ScalingLazyColumn` |
| Home left/right pager | `HorizontalPager` |
| Crown = next stop | `rotaryScrollable` / `onRotaryScrollEvent` + `FocusRequester` |
| Color-by-mode, tram-red ground | custom `ColorScheme` in `MaterialTheme` |
| The tile | Tiles / ProtoLayout API — **not Compose** |

The tile is the one real divergence: Wear Tiles are built with the ProtoLayout
library (boxes, text, images, arcs, limited styling), so the honeycomb and its
corner rounding will be looser than the app screens. Plan the tile as its own,
more constrained surface.

Interaction gotcha to remember: **rotary events only reach the focused element.**
The departures screen must request focus, or the crown does nothing.

## Build phases

Each phase runs and shows something before the next begins.

- **Phase 0 — done.** A standalone `uv`/Python laptop script (`phase0/departures.py`)
  that fuzzy-matches any stop and prints live departures with compass labels and
  H/R. Proved the API, the data contract, DIVA grouping, and every field the app
  needs. No API key.
- **Phase 1 — done.** Bare Wear OS app, live departures on the emulator.
- **Phase 2 — done.** Home pager, GPS, bundled CSVs, nearby stops, search, pick a
  line, swipe-direction departures, stacked platforms, and crown-scroll between
  stations. Also: bundled + refreshable route data.
- **Phase 3 — favorites done; tile remaining.** Pin lines (max six, persisted),
  favorites page, favorite → nearest stop → departures, settings. The honeycomb
  **Tile** (ProtoLayout) is the outstanding feature.

**Implementation deviations from this draft (see `docs/HANDOFF.md`):** the
favorites list shows the line's **mode** ("Tram"/"Bus"/…), not its termini (route
data has depot/short-working variants); the **crown scrolls between stations** and
each crowned-to station shows a **reload button** (no request until tapped, to
avoid rate-limiting); Settings is a 4th home slide with an **open-to-favorites**
toggle. Built on Wear Compose **Material 2** (1.4.1), not Material 3.

## Locked interaction summary

- **Home** — Nearby in the middle; swipe left/right to Favorites and Search;
  up/down scrolls the lists.
- **Search** — built-in text + voice input; fuzzy match against the bundled stops.
- **Swipe** (departures) — flip direction, H ↔ R; one direction on screen.
- **Crown** (departures) — step to the next stop along the line, farther out.
- **Platforms** — same-direction platforms stack, each with a header and its next
  two; a single platform skips the header.
- **A/C snowflake** — shown year-round.
- **Times-screen ground** — deep mode-colored background stays.
- **Tile** — six lines around a centre app button; static, no scroll, hard cap six.

## Risks and deferred items

- **Bus / Badner Bahn colors** are conventional; confirm against the official
  brand guide before shipping.
- **Tile rendering** is constrained (ProtoLayout); the honeycomb fidelity and
  corner rounding will be approximate.
- **`cooling` may be absent** on older/cached responses — render "unknown," not
  "no A/C."
- **Compass labels are coarse** (8 buckets); at a tight junction two poles can land
  in the same bucket. The RBL in the header disambiguates.
- **Rotary API has churned** across `androidx.wear.compose` versions; verify the
  exact API and a focus-plus-rotary snippet at build time rather than from memory.
- **Elterleinplatz RBLs** in the mockup are placeholder; JNBP's (556/547/548) are
  real.

## Out of scope (v1)

- Trip planning / routing (this is departures, not directions).
- Disruptions banner (`trafficInfoList`) — possible later.
- Notifications, watch-face complications.
- Platforms other than Wear OS.
