# Phase 0 - prove the Wiener Linien API

A standalone Python script that fetches live tram/bus/subway departures from the
Wiener Linien real-time API. Type any Vienna stop name, get the next departures.
No Android, no watch, no API key. If this prints live countdowns, the data
contract for the whole app is proven.

## No API key needed

The data comes from Wiener Linien's open government data (OGD) real-time API.
It's public, unauthenticated, and CC-BY licensed (commercial use allowed). The
only request header is `Accept: application/json`. There's nothing to sign up
for. The single constraint is politeness: the endpoint can rate-limit or time
out, so don't hammer it in a tight loop.

## Prerequisites

Just [uv](https://docs.astral.sh/uv/), Astral's Python runner. Install it:

```
# Windows (PowerShell)
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"

# macOS / Linux
curl -LsSf https://astral.sh/uv/install.sh | sh
```

You do **not** need to install Python, create a virtual environment, or run
`pip install` anything. The script declares its own dependency (`requests`) in a
`# /// script` block at the top ([PEP 723](https://peps.python.org/pep-0723/)),
and `uv` sets all of that up in a throwaway environment the first time you run
it.

## Run it

From the repo root, type a stop name:

```
uv run phase0/departures.py frauengasse
```

Output:

```
Frauengasse
    44 R  W -> Schottentor U                6 min, 21 min
    44 H  E -> Ottakring, Maroltingergasse  11 min, 26 min
```

Columns: `line`, `direction`, `compass`, `-> destination`, `times`. One row per
line and destination, soonest first. All of it is live from a single request;
only the stop lookup is offline.

- **direction** - the operator's route direction, `H` (Hin, outbound) or `R`
  (Rück, return). Straight from the API. `-` if the feed omits it.
- **compass** - which physical platform, derived from coordinates (see below).

The two answer different questions and together disambiguate a junction. At
Johann-Nepomuk-Berger-Platz line 9 toward Gersthof leaves from two poles ~130m
apart: same route direction (`R`), different platforms (`SW` vs `NE`):

```
Johann-Nepomuk-Berger-Platz
     9 R SW -> Gersthof S                   4 min, 19 min
     9 R NE -> Gersthof S                   6 min, 21 min
```

## Commands

```
uv run phase0/departures.py frauengasse             # next 2 per line/destination
uv run phase0/departures.py schottentor --line 44    # only line 44
uv run phase0/departures.py reumannplatz -n 3        # next 3 per line/destination
uv run phase0/departures.py "hauptbahnhof"           # quote names with spaces
uv run phase0/departures.py --rbl 555 1370            # raw platform IDs, skip lookup
```

| Argument      | Meaning                                                        |
|---------------|----------------------------------------------------------------|
| `query`       | The stop name to search for (fuzzy-matched).                   |
| `--line`      | Show only this line, e.g. `44`.                                |
| `-n, --count` | Departures per line/destination (default `2`).                |
| `--rbl`       | Escape hatch: query raw RBL numbers directly.                 |

## How it finds a stop

Two data sources combine:

- **Offline:** `data/wienerlinien-ogd-haltepunkte.csv` maps every platform to its
  physical stop. The script groups platforms by **DIVA** (the physical-stop ID),
  so a hub like Schottentor - where the tram and subway platforms carry slightly
  different names but share a DIVA - resolves to one stop with all its lines.
  This is the same reference file the app bundles in Phase 2. Read locally, no
  network.
- **Live:** the monitor endpoint turns a set of RBLs (platform IDs) into
  real-time departures.

So a run is: `your text -> matched physical stop -> all its RBLs -> one live call`.

Matching is fuzzy, using Python's built-in `difflib` (no extra dependency). It
tolerates partial names and small typos:

- **Clear winner** -> used directly. `frauengasse` finds Frauengasse.
- **Several close matches** -> the script lists them and asks you to be more
  specific, rather than guessing:
  ```
  $ uv run phase0/departures.py schotten
  'schotten' is ambiguous. Did you mean:
    Schottentor
    Schottenring
    Schottentor U
    ...
  ```
- **Nothing close** -> `No stop matches 'xyzzy'.`

Names with umlauts work typed either way (`pötzleinsdorf` or `poetzleinsdorf`),
because both sides are folded (ä->ae, ö->oe, ü->ue, ß->ss) before comparing.

## The compass label

The API gives no cardinal directions - only a coordinate per platform. The
script derives the `N`/`NE`/`E`/… label itself: it takes the stop's centroid
(the average of its platform coordinates), measures the bearing from there to
each platform, and buckets it into one of 8 compass points. A platform sitting
on the centroid, or a stop with a single platform, shows `-` (no meaningful
direction).

It's a coarse gloss on the real data (lat/lon), useful for telling two poles
apart at a junction. In Phase 2 the watch replaces it with something better:
your GPS position picks the nearest pole directly, so you'd see only the one
you're standing at.

## The RBL, explained

The API identifies each platform by a number called an **RBL** (555, 1370, ...).
One physical stop has several - one per platform/direction. You never type them;
the CSV lookup resolves a stop name to all its RBLs and queries them together.
The `--rbl` flag exists only as an escape hatch for probing platforms directly.

## Known limits (fine for a proof script)

- `difflib` is decent but not a real fuzzy-search engine; badly garbled input
  falls back to the "did you mean" list.
- A few legacy entries in the CSV lack a DIVA (e.g. an all-caps
  "SCHOTTENTOR ( U2 )" duplicate) and can't be grouped, so they show up as their
  own stray candidates. Harmless - the properly-grouped stop carries the same
  lines.

## Troubleshooting

- **`Request timed out`** - the endpoint is slow or briefly unreachable. Run it
  again; usually transient.
- **`is ambiguous`** - your query matched several stops. Retype more of the name.
- **`no upcoming departures`** - nothing running at that stop right now (late
  night, or a platform out of service). Check with `--rbl` on a known number.

## The data contract

The response path the app relies on:

```
data.monitors[]                       one per requested platform (RBL)
  locationStop.properties.title        stop name
  locationStop.properties.attributes.rbl
  lines[]
    name                               line, e.g. "44"
    towards                            destination, e.g. "Schottentor U"
    direction                          "H" (hin) or "R" (retour)
    departures.departure[]
      departureTime.countdown          minutes until departure (integer)
      departureTime.timeReal           real timestamp (ISO 8601)
      departureTime.timePlanned        scheduled timestamp
```
