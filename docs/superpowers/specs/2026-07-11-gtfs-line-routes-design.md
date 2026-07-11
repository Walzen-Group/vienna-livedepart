# GTFS-derived line routes — design

Replace the noisy `fahrwegverlaeufe.csv` route heuristic with a clean, canonical
line→ordered-stops table derived offline from Wiener Linien **GTFS**, keyed by the
**headsign** that the live monitor already gives us. Fixes wrong crown/index chains
(e.g. line 9 wandering through Dornbach) caused by headsign↔terminus-name mismatch
and ~98 depot/short-turn patterns per line.

## Problem (confirmed)

`RouteRepository.chainFor(line, termini)` picks a `fahrwegverlaeufe` pattern by
matching the live destination text to a pattern's terminus **stop name**. But the
live label is a **headsign** ("Gersthof", "Westbahnhof"), not the terminus stop
("Wallrißstraße", "Westbahnhof S U"). When an end fails to match, the fallback
("longest pattern ending at a matching terminus") selects a depot/variant pattern —
for line 9 a 26-stop run through Dornbach. There are ~98 patterns per line to
disambiguate and no headsign→stop link in that CSV.

## Why GTFS fixes it

GTFS carries the missing field:
- `trips.txt.trip_headsign` == the live monitor `towards` label ("Gersthof").
- `stop_times.txt` gives the exact ordered stop sequence per trip.
- The **most frequent trip per route + direction + headsign** is the canonical
  everyday route (what the official Linienführung shows), dropping depot/short-turn
  noise automatically.

So the chain is chosen by exact headsign match, not fuzzy stop-name matching.

## Offline derivation (Phase-0-style script)

A reproducible script (`tools/build_routes.py`, uv/Python, like `phase0/`):

1. Download the Wiener Linien GTFS zip (data.gv.at dataset
   `wiener-linien-fahrplandaten-gtfs-wien`).
2. Parse `routes.txt` (route_short_name = our line name), `trips.txt`
   (route_id, direction_id, trip_headsign), `stop_times.txt` (ordered stops per
   trip), `stops.txt` (stop_id, name, coords, parent_station).
3. For each `(route_short_name, trip_headsign)`, take the **dominant stop
   sequence** (the most common ordered stop list across its trips; tie-break by
   longest/most-complete).
4. **Map GTFS stops → our stop identity.** Our app resolves `PhysicalStop` by
   **DIVA** (via bundled `haltepunkte.csv`, StopID=RBL, DIVA, coords). The exact
   GTFS↔DIVA link is determined during build by inspecting the GTFS `stops.txt`
   ids/`parent_station` against `haltepunkte.csv` / `wienerlinien-ogd-steige.csv`
   (which links RBL↔DIVA). If no direct id link exists, fall back to a
   name+coordinate nearest-match join. Collapse consecutive platforms of the same
   physical stop into one DIVA.
5. Emit a small bundled CSV `line_routes.csv`:
   `line;headsign;seq;diva` (ordered), plus whatever the resolver needs. Only the
   derived table is bundled — never the full GTFS.

The script prints a coverage report (lines resolved, unmapped stops) so gaps are
visible, not silent.

## App changes

- **`RouteRepository`** reads `line_routes.csv` instead of `fahrwegverlaeufe.csv`.
  - `chainFor(context, line, termini)`: match each live terminus label to a
    `headsign` for that line (exact, then normalized/contains as a fallback), take
    that headsign's ordered DIVAs, resolve to `PhysicalStop`s via `StopRepository`.
    Prefer the headsign whose sequence contains the opened stop; if two directions
    resolve, stitch/choose per the current both-ends intent but now on headsigns.
  - `nearestStopOnLine(context, line, lat, lon)`: nearest among the union of the
    line's route stops (all headsigns).
  - Keep the 20s in-memory cache shape; `invalidate()` clears on data refresh.
- **`TransitData`** refresh: `line_routes.csv` joins the refreshable bundle set
  (filesDir-preferred), replacing `fahrwegverlaeufe.csv` (which can be dropped from
  assets once the new table covers its uses).
- Departures/index UI unchanged — they consume `PhysicalStop` chains as today.

## Validation

- Line 9 resolves to Gersthof(Wallrißstraße) ↔ Westbahnhof incl. the Winckelmannstraße
  loop, **no Dornbach**. Spot-check 44 (Schottentor ↔ Maroltingergasse), a U-Bahn,
  and a bus against the official Linienführung.
- Build + install; drive the index view for those lines on the emulator.

## Out of scope

- Live GTFS-RT. We keep using the OGD realtime monitor for departures.
- Shapes/geometry (map polylines) — only the stop sequence is needed.
- Auto-updating GTFS on device; the derived table refreshes via the existing
  Settings data-refresh path when a new bundle is published.

## Open question resolved during build

The GTFS `stop_id` → DIVA mapping (direct id vs steige.csv join vs coord/name
match) is settled by inspecting the actual GTFS + OGD files first; the script's
coverage report drives which method is reliable enough.
