# Vienna tram departures on Wear OS - build plan

A handoff brief for Claude Code. Goal: a Wear OS app that shows live Wiener Linien
departures (the real-time data Google Maps ignores), plus a launcher tile of pinned
station-lines that deep-link into the app.

The developer is a beginner. Explain each file, build in runnable phases, and never
skip straight to a big-bang implementation.

## What we are building

- Standalone Wear OS app, Kotlin + Compose for Wear OS.
- Core feature: pick a stop, see the next 2 (or N) departures per line and direction, live.
- Nearest stop selected by GPS from a bundled stop list.
- Favorites: user pins station-lines.
- A launcher Tile listing pinned station-lines as tappable rows; each row deep-links
  into the app screen for that stop and fetches fresh data on open.
- Target platform is Wear OS only. Not Apple Watch, not Garmin.

## The data source (confirmed working, no API key)

Wiener Linien OGD real-time API. License CC-BY, commercial use allowed.

### Departures endpoint

```
GET https://www.wienerlinien.at/ogd_realtime/monitor?rbl=1450
Header: Accept: application/json
```

- `rbl` (also accepted as `stopId`) identifies one platform/direction, not a whole stop.
- Multiple IDs in one call: `?rbl=1450&rbl=1451&rbl=252`.
- Response shape:

```
data.monitors[]                        one per requested platform
  locationStop.properties.title        stop name
  locationStop.properties.attributes.rbl
  locationStop.geometry.coordinates    [lon, lat] WGS84
  lines[]
    name                               e.g. "46"
    towards                            e.g. "Joachimsthalerplatz"
    direction                          "H" (hin) or "R" (retour)
    departures.departure[]
      departureTime.countdown          minutes until departure (integer)
      departureTime.timeReal           real timestamp (ISO 8601)
      departureTime.timePlanned        scheduled timestamp
```

Next 2 departures = first two entries of `departures.departure`.

### Disruptions endpoint (optional, phase 3+)

```
GET https://www.wienerlinien.at/ogd_realtime/trafficInfoList
    ?relatedLine=46
```

Returns current disruptions and elevator/service notices. This is the equivalent of the
"Track construction work" banner in the app.

### Stop reference data (bundled offline)

```
https://www.wienerlinien.at/ogd_realtime/doku/ogd/wienerlinien-ogd-haltepunkte.csv
Format: StopID;DIVA;StopText;Municipality;MunicipalityID;Longitude;Latitude
```

Download once, ship it inside the app. Gives GPS coordinates for nearest-stop math and the
RBL needed for the monitor call. One physical stop maps to several RBLs (one per
platform/direction), so a "nearest stop" resolves to a small set of RBLs.

### Reliability notes to design around

- Endpoint can time out or rate-limit. Cache the last good response and show a staleness
  timestamp instead of a blank screen or spinner.
- Fetch on screen open, not on a tight background loop. Battery matters on a watch.

## Toolchain

- Android Studio (free, official). Bundles the Kotlin compiler, Android SDK, `adb`, and the
  Wear OS emulator. No paid account needed for sideloading to your own watch.
- Language: Kotlin. UI: Compose for Wear OS.
- Do not use plain IntelliJ; the Wear templates and emulator live in Android Studio.

## Build phases (each one runs and shows something)

### Phase 0 - prove the API on a laptop, no Android

A tiny standalone script (Python or Node) that calls the monitor endpoint for one real RBL
and prints the next departures with countdowns. Purpose: confirm the API and lock the data
contract before any Android complexity. Runs in any editor, no Android Studio.

Deliverable: working script + a sample stop's RBL to test with.

### Phase 1 - bare Wear OS app, one hardcoded stop

A minimal Compose for Wear OS app that fetches one hardcoded RBL and lists live departures.
This phase is really about learning the toolchain: project structure, the emulator, then
installing to the physical watch. Keep the UI trivial.

Deliverable: app that shows live times for one stop, running first on the emulator, then on
the real watch.

### Phase 2 - stop list, location, nearest stop

Bundle the `haltepunkte` CSV. Read device location via the fused location provider. Compute
the nearest physical stop, resolve its RBLs, fetch and display. Test using the emulator's
fake-GPS feature set to Vienna coordinates.

Deliverable: open the app anywhere, see the nearest stop's next departures.

Design decision to settle here: when a nearest stop has multiple directions, show all of
them or let the user pin the one they use. This shapes the favorites model.

### Phase 3 - favorites and the launcher tile

- Favorites: user pins station-lines; persist them locally.
- Launcher Tile: renders pinned station-lines as tappable rows. Each row deep-links into the
  app screen for that stop, which fetches fresh on open. Because the tile is just shortcuts,
  tile refresh staleness does not matter.
- Optional: location-aware ordering (nearest favorites first).
- Optional: disruption banner from `trafficInfoList`.

Deliverable: glanceable tile next to Google Maps; tap a line, see live times.

## Getting the app onto the real watch (wireless debugging)

Most Wear OS watches have no data USB port, so install over Wi-Fi. Both devices on the same
network:

1. On the watch: Settings, find the build number, tap it 7 times to unlock Developer options.
2. In Developer options: enable ADB debugging and Wireless debugging. The watch shows an IP,
   a port, and a pairing code.
3. On the computer: run `adb pair <ip:port>` with the code, then `adb connect <ip:port>`.
4. In Android Studio, "Run" now installs directly to the watch.

Exact menu labels vary by brand (Pixel vs Samsung) and OS version. Sort the precise taps live.
The pairing step is the one fiddly moment; everything after is smooth.

## Working style for Claude Code

- One phase at a time. Do not jump ahead. Each phase must be runnable before moving on.
- When something breaks, the developer pastes the error; fix iteratively.
- Claude Code writes and edits the code; the developer runs builds and installs (Claude Code
  cannot run a Wear OS build or reach the Vienna API).

## Open questions to confirm early

- Phase 0 script language: Python or JavaScript?
- Home stop and lines to use as test data (e.g. Line 46 toward Joachimsthalerplatz).
- Favorites: manual pinning first, location-aware ordering later? (Recommended.)
