# /// script
# requires-python = ">=3.11"
# dependencies = ["requests"]
# ///
"""
Phase 0 - prove the Wiener Linien real-time API from a laptop, no Android.

What this does:
  Type any Vienna stop name. The script fuzzy-matches it against the bundled
  stop list, then prints the next live departures at that stop as a flat list,
  one row per line and destination, soonest first.

Why it exists:
  Before touching any Android/Wear OS complexity, we confirm the API works and
  lock down the exact shape of the data we'll rely on.

How the pieces fit:
  - A bundled CSV (data/wienerlinien-ogd-haltepunkte.csv) maps every stop name
    to its platform IDs (RBLs). This is offline reference data - the same file
    the app will ship in Phase 2.
  - The live "monitor" endpoint turns a set of RBLs into real-time departures.
  So: your text -> matched stop name -> its RBLs -> one live API call -> times.

Run it (uv reads the dependency block above and sets everything up for you):
    uv run phase0/departures.py frauengasse
    uv run phase0/departures.py schottentor --line 44
    uv run phase0/departures.py "reumann" -n 3
    uv run phase0/departures.py --rbl 555 1370      # raw platform IDs, skip lookup

No API key is needed. The data is CC-BY licensed by Wiener Linien.
"""

import argparse
import csv
import difflib
import math
import sys
from collections import Counter
from pathlib import Path

import requests

MONITOR_URL = "https://www.wienerlinien.at/ogd_realtime/monitor"

# The bundled stop reference, found relative to THIS file so the script works
# from any working directory. Columns (semicolon-separated):
#   StopID ; DIVA ; StopText ; Municipality ; MunicipalityID ; Longitude ; Latitude
# StopID is the RBL. StopText is the human stop name. The file is cp1252-encoded
# (Vienna umlauts: Döblinger Hauptstraße).
STOPS_CSV = Path(__file__).parent / "data" / "wienerlinien-ogd-haltepunkte.csv"

# Only names scoring at least this well count as matches. Below it, we treat the
# query as "no match" rather than guessing wildly.
MATCH_CUTOFF = 0.6


def normalize(text: str) -> str:
    """Fold a name to a comparable form: lowercase, umlauts spelled out.

    The CSV mixes 'Straße' and 'Nussdorf', and users type either 'pötzleinsdorf'
    or 'poetzleinsdorf'. Folding both sides makes those equivalent.
    """
    text = text.strip().lower()
    for src, dst in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss")):
        text = text.replace(src, dst)
    return text


# The 8 compass points, in bearing order starting at north.
CARDINALS = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]


def parse_coord(lon_s: str | None, lat_s: str | None) -> tuple[float, float] | None:
    """Parse a CSV lon/lat pair, or None for blank / placeholder rows.

    A few legacy rows carry all-zero coordinates; the Vienna bounding box below
    rejects those.
    """
    try:
        lon = float((lon_s or "").strip())
        lat = float((lat_s or "").strip())
    except ValueError:
        return None
    if 16.0 <= lon <= 16.7 and 48.0 <= lat <= 48.4:
        return (lon, lat)
    return None


def compute_dirs(coords: dict[str, tuple[float, float]]) -> dict[str, str]:
    """Label each platform (RBL) by its compass position within the stop.

    We take the stop's centroid (mean of its platform coordinates), measure the
    bearing from there to each platform, and bucket it into one of 8 compass
    points. Platforms sitting essentially on the centroid - or a stop with only
    one platform - get no label, because there's no meaningful direction to give.

    Distances here are tens of metres, so a flat local approximation (metres
    north / east of centroid) is accurate enough; no need for great-circle math.
    """
    if len(coords) < 2:
        return {rbl: "" for rbl in coords}

    center_lon = sum(lon for lon, _ in coords.values()) / len(coords)
    center_lat = sum(lat for _, lat in coords.values()) / len(coords)

    dirs: dict[str, str] = {}
    for rbl, (lon, lat) in coords.items():
        north = (lat - center_lat) * 111_000
        east = (lon - center_lon) * 111_000 * math.cos(math.radians(center_lat))
        if math.hypot(north, east) < 15:  # metres; too close to the centre to call
            dirs[rbl] = ""
            continue
        bearing = (math.degrees(math.atan2(east, north)) + 360) % 360
        dirs[rbl] = CARDINALS[int((bearing + 22.5) // 45) % 8]
    return dirs


def canonical_name(names: Counter) -> str:
    """Pick one display name for a physical stop from its name variants.

    A stop's platforms sometimes carry slightly different names ('Schottentor U',
    'Schottentor', 'Schottentor (Bedarf)'). Prefer the one used by the most
    platforms, then a name without a '(...)' qualifier, then the shortest.
    """
    def rank(item: tuple[str, int]) -> tuple:
        name, count = item
        return (-count, "(" in name, len(name))
    return sorted(names.items(), key=rank)[0][0]


def load_stops(path: Path) -> list[dict]:
    """Read the CSV into a list of physical stops.

    Each physical stop is one real place, identified by its DIVA. Its several
    platforms (RBLs) collect together, so one query reaches every line at the
    stop - the tram platform and the subway platform of a hub like Schottentor
    share a DIVA even when their platform names differ. Rows with no usable DIVA
    stand alone, keyed by name.

    Returns dicts: {"name": canonical, "aliases": [name, ...], "rbls": [rbl, ...]}.
    """
    groups: dict[str, dict] = {}
    with open(path, encoding="cp1252", newline="") as handle:
        reader = csv.DictReader(handle, delimiter=";")
        for row in reader:
            rbl = (row.get("StopID") or "").strip()
            name = (row.get("StopText") or "").strip()
            diva = (row.get("DIVA") or "").strip()
            if not rbl.isdigit() or not name:
                continue  # skip blank or malformed rows
            # A real DIVA is digits with at least one non-zero; anything else
            # (empty, all-zero placeholder) can't group, so key by name.
            key = diva if diva.isdigit() and set(diva) != {"0"} else f"name:{name}"
            group = groups.setdefault(key, {"rbls": [], "names": Counter(), "coords": {}})
            group["rbls"].append(rbl)
            group["names"][name] += 1
            coord = parse_coord(row.get("Longitude"), row.get("Latitude"))
            if coord is not None:
                group["coords"][rbl] = coord

    return [
        {
            "name": canonical_name(group["names"]),
            "aliases": list(group["names"]),
            "rbls": group["rbls"],
            "dirs": compute_dirs(group["coords"]),
        }
        for group in groups.values()
    ]


def name_score(query: str, name: str) -> float:
    """How well `query` matches a single name, from 0.0 to 1.0.

    A substring match ('schotten' inside 'Schottentor U') scores high, with a
    small bonus for shorter names so the tightest match wins. Otherwise we fall
    back to difflib's character-similarity ratio, which tolerates typos.
    """
    q, n = normalize(query), normalize(name)
    if q == n:
        return 1.0
    if q in n:
        return 0.9 + 0.1 * (len(q) / len(n))
    return difflib.SequenceMatcher(None, q, n).ratio()


def stop_score(query: str, stop: dict) -> float:
    """A stop's score is its best-matching name variant."""
    return max(name_score(query, alias) for alias in stop["aliases"])


def find_matches(query: str, stops: list[dict], limit: int = 8) -> list[dict]:
    """Return physical stops scoring above the cutoff, best first."""
    ranked = sorted(stops, key=lambda stop: stop_score(query, stop), reverse=True)
    matches = [stop for stop in ranked if stop_score(query, stop) >= MATCH_CUTOFF]
    return matches[:limit]


def resolve_stop(query: str, stops: list[dict]) -> dict | None:
    """Pick one physical stop for the query, or print candidates and return None.

    We take the single best match when it's clearly ahead of the runner-up (or is
    the only candidate). When several are close, we don't guess - we list them so
    the user can retype more specifically.
    """
    matches = find_matches(query, stops)
    if not matches:
        print(f"No stop matches {query!r}. Try fewer letters or check spelling.",
              file=sys.stderr)
        return None

    best_score = stop_score(query, best := matches[0])
    second_score = stop_score(query, matches[1]) if len(matches) > 1 else 0.0
    confident = (
        len(matches) == 1
        # an exact name match beats any substring/typo runner-up
        or (best_score >= 1.0 and second_score < 1.0)
        or best_score - second_score >= 0.1
    )
    if confident:
        return best

    print(f"{query!r} is ambiguous. Did you mean:", file=sys.stderr)
    for stop in matches:
        print(f"  {stop['name']}", file=sys.stderr)
    return None


def fetch_monitors(rbls: list[str], timeout: float = 15.0) -> dict:
    """Ask the endpoint about the given RBLs and return the parsed JSON."""
    response = requests.get(
        MONITOR_URL,
        params={"rbl": rbls},
        headers={"Accept": "application/json"},
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()


def departure_rows(data: dict, line_filter: str | None, count: int,
                   dirs: dict[str, str]) -> list[tuple]:
    """Flatten the response into (soonest, line, dir, towards, "6, 22 min") rows.

    `dirs` maps an RBL to its compass label (from compute_dirs), so each row is
    tagged with the platform it departs from. That disambiguates a stop where one
    line+destination is served by more than one platform (e.g. line 9 toward
    Gersthof at Johann-Nepomuk-Berger-Platz).

    Response shape we depend on (the data contract):
        data.monitors[].lines[]
          name                              line, e.g. "44"
          towards                           destination, e.g. "Schottentor U"
          departures.departure[].departureTime.countdown   minutes (int)
    Each lines[] entry is already one line+destination, so it becomes one row.
    """
    rows: list[tuple] = []
    for monitor in data.get("data", {}).get("monitors", []):
        rbl = str(monitor["locationStop"]["properties"]["attributes"]["rbl"])
        label = dirs.get(rbl, "")
        for line in monitor["lines"]:
            if not line["name"].strip():
                continue  # the feed occasionally emits a nameless placeholder line
            if line_filter is not None and line["name"] != line_filter:
                continue
            departures = line["departures"]["departure"][:count]
            countdowns = [d["departureTime"]["countdown"] for d in departures]
            if not countdowns:
                continue
            times = ", ".join(f"{c} min" for c in countdowns)
            direction = (line.get("direction") or "").strip() or "-"
            rows.append((countdowns[0], line["name"], direction, label,
                         line["towards"].strip(), times))
    rows.sort(key=lambda r: r[0])  # soonest departure first
    return rows


def print_rows(title: str, rows: list[tuple]) -> None:
    print(f"\n{title}")
    if not rows:
        print("  no upcoming departures")
        return
    for _soonest, name, direction, label, towards, times in rows:
        tag = label or "-"  # "-" = central / single-platform, no compass label
        # direction: H (Hin) / R (Rück), the line's route direction
        print(f"  {name:>4} {direction} {tag:>2} -> {towards:<28} {times}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Live Wiener Linien departures.")
    parser.add_argument("query", nargs="?", help="Stop name to search for.")
    parser.add_argument("--line", default=None, help="Only show this line, e.g. 44.")
    parser.add_argument(
        "-n", "--count", type=int, default=2,
        help="Upcoming departures per line/destination (default 2).",
    )
    parser.add_argument(
        "--rbl", nargs="+", default=None,
        help="Escape hatch: query raw RBL numbers, skipping the stop lookup.",
    )
    args = parser.parse_args()

    # Path 1: raw RBLs, no lookup. Handy for probing platforms directly.
    if args.rbl is not None:
        try:
            data = fetch_monitors(args.rbl)
        except requests.RequestException as exc:
            print(f"Request failed: {exc}", file=sys.stderr)
            return 1
        # No CSV stop here, so derive compass labels from the response coords.
        coords = {
            str(m["locationStop"]["properties"]["attributes"]["rbl"]):
                tuple(m["locationStop"]["geometry"]["coordinates"])
            for m in data.get("data", {}).get("monitors", [])
        }
        print_rows(f"RBLs {', '.join(args.rbl)}",
                   departure_rows(data, args.line, args.count, compute_dirs(coords)))
        return 0

    # Path 2: fuzzy stop lookup.
    if not args.query:
        parser.error("give a stop name to search, or use --rbl")

    stops = load_stops(STOPS_CSV)
    stop = resolve_stop(args.query, stops)
    if stop is None:
        return 2  # message already printed (no match or ambiguous)

    try:
        data = fetch_monitors(stop["rbls"])
    except requests.Timeout:
        print("Request timed out. The endpoint is slow or unreachable.", file=sys.stderr)
        return 1
    except requests.RequestException as exc:
        print(f"Request failed: {exc}", file=sys.stderr)
        return 1

    print_rows(stop["name"], departure_rows(data, args.line, args.count, stop["dirs"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
