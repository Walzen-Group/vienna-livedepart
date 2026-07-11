# Departures index (register) view — design

A second mode on the departures screen: a fast crown spin flips from sliding
between stops into a route-strip index of every stop on the line, where the rider
crowns to a stop and taps to jump there. Slow crowning keeps sliding one stop at a
time as it does today.

## Motivation

Crowning stop-by-stop is fine for neighbours but slow for a long line. The index
view gives a fast overview of the whole line and a direct jump, reusing the
already-resolved stop chain. It reads like a transit line diagram (à la Google
Maps): a coloured line with a dot per stop and names alongside.

## Modes

The departures screen has two modes, held in screen-level state inside the
`key(stops)` block:

- **Departures** (current): the `VerticalPager` of per-stop departure bodies. A
  slow single crown step slides to the neighbour (animated ~420ms); a multi-stop
  fast jump used to snap — it now enters Index mode instead.
- **Index**: the route strip described below. Entered by a fast crown, left by
  selecting a stop (tap) or cancelling (swipe).

State: `var indexMode by remember { mutableStateOf(false) }` and
`var highlight by remember { mutableIntStateOf(<current page>) }`.

## Entering index mode (trigger)

Fast crown = several detents in quick succession. Detection uses crown velocity:
track the time of the last detent (`android.os.SystemClock.uptimeMillis()`); if a
new step lands within a short window (~120ms) of the previous one, count it as
"fast". Two or more fast steps in a row flips `indexMode = true` and seeds
`highlight` with the current stop index. A single slow step still slides.

The exact window/step count is tuned live on the watch; the design fixes the
mechanism (velocity), not the constants.

## Index view

A full-screen route strip over the same persistent background (mode-colour ground
gradient) as departures.

- **Route line**: a vertical stroke in the line's mode colour (`ModeColor.forLine`)
  down the left side, **bowed to follow the round screen** — each row is offset
  horizontally by its distance from vertical centre (a circle equation, e.g.
  `dx = R - sqrt(R^2 - dy^2)` clamped), so rows near the top and bottom inset
  toward centre and the line arcs with the bezel instead of clipping at the
  corners. The line is drawn behind the rows, threading through the dots.
- **Stop dots**: a white dot on the line at each stop. The highlighted stop gets a
  larger, brighter dot; others are smaller/dimmer.
- **Stop names**: to the right of each dot, up to **two lines** (wrap, then
  ellipsize). The highlighted name is brighter/bold; neighbours dim with distance.
- **Line badge**: the small `SquareBadge` at the top, consistent with departures.
- **Bezel indicator**: the existing curved `PositionIndicator` tracks `highlight`
  over the stop count.

Only a window of stops around `highlight` is rendered (the strip scrolls so the
highlight stays vertically centred). No departures requests fire in this mode.

## Interactions

- **Crown**: moves `highlight` up/down one stop per detent, clamped to the chain
  ends. The strip re-centres on the new highlight. In index mode the crown drives
  the highlight directly (the route strip is the rotary surface here).
- **Select — tap centre**: tapping the centred (highlighted) stop exits index mode
  to that stop's departures. The stop is added to the `loaded` set and its
  departures **auto-load** (the rider explicitly chose it). The pager is moved to
  the selected index.
- **Cancel — swipe left or right**: leaves index mode and returns to the stop the
  rider was on before entering, unchanged (no new load, `loaded` untouched).

## Data & dependencies

- **Stops**: the already-resolved `stops` chain (the `RouteRepository` line→ordered
  stops). Index mode is only meaningful when `stops.size > 1`; with a single stop a
  fast crown does nothing.
- **Colour**: `ModeColor.forLine(line, lineType)` for the route line, same as the
  ground gradient.
- **Loaded set / refresh**: unchanged. Selecting auto-loads and marks loaded; live
  refresh stays scoped to the on-screen stop.

## Component structure

- `IndexView(stops, highlight, lineColor, line, onHighlightChange, onSelect, onCancel)`
  — a new private composable in `DeparturesScreen.kt` (or a sibling file if it
  grows). Owns the curved layout, route line + dots (Canvas / `drawBehind`), and the
  two-line names. Stateless beyond what the parent passes; highlight and mode live in
  `DeparturesScreen`.
- `DeparturesScreen` gains the mode branch: when `indexMode`, render `IndexView`
  and route the crown to `onHighlightChange`; otherwise render the existing pager.
  The velocity trigger lives in the departures-mode rotary handler.

## Edge cases

- Single-stop line (`stops.size <= 1`): index mode never engages.
- Highlight clamping at chain ends.
- Long names: two lines then ellipsis; the strip row height is fixed so the curve
  math stays stable regardless of name length.
- Entering index mode mid-slide: cancel any in-flight pager animation first.
- Selecting the already-current stop: valid, exits and (re)loads it.

## Out of scope

- Reordering or editing the line.
- Showing live countdowns in the index (it's a chooser, not a departures list).
- Horizontal (direction) awareness in the index — it lists the physical stop chain.
