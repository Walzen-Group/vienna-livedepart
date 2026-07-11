#!/usr/bin/env python3
"""
Derive a canonical line -> ordered-stops table from Wiener Linien GTFS, keyed by
trip_headsign (which matches the live monitor `towards` label). Replaces the noisy
fahrwegverlaeufe pattern heuristic that mis-routes lines (e.g. 9 through Dornbach).

Output: wear-app/app/src/main/assets/line_routes.csv  (line;headsign;seq;diva)
  - one row per stop, ordered by seq, one entry per physical stop (DIVA), platforms
    collapsed. `headsign` has the GTFS "Wien " prefix stripped.

GTFS stop_id (at:49:<station>:0:<platform>) has no DIVA, so stations are matched to
our bundled haltepunkte.csv by coordinate + name (see map_stations).

Usage:
  uv run tools/build_routes.py                      # download GTFS, write assets
  uv run tools/build_routes.py --gtfs-dir <dir>     # reuse already-downloaded txt
Run weekly in CI; commit the resulting line_routes.csv.
"""
from __future__ import annotations
import argparse, csv, datetime, math, os, re, sys, tempfile, urllib.request
from collections import defaultdict, Counter

GTFS_BASE = "https://www.wienerlinien.at/ogd_realtime/doku/ogd/gtfs/"
GTFS_FILES = ["routes.txt", "trips.txt", "stops.txt", "stop_times.txt"]
HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
HALTEPUNKTE = os.path.join(REPO, "wear-app/app/src/main/assets/haltepunkte.csv")
OUT = os.path.join(REPO, "wear-app/app/src/main/assets/line_routes.csv")

MATCH_NAME_M = 120.0   # accept an exact-name match within this radius
MATCH_NEAR_M = 60.0    # else accept nearest within this radius
MIN_TRIPS = 20         # drop headsigns with fewer trips than this (noise/one-offs)


def norm(s: str) -> str:
    s = s.lower().strip()
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss")):
        s = s.replace(a, b)
    return re.sub(r"[^a-z0-9]", "", s)


def haversine(la1, lo1, la2, lo2) -> float:
    R, p = 6371000.0, math.pi / 180
    dla, dlo = (la2 - la1) * p, (lo2 - lo1) * p
    a = math.sin(dla / 2) ** 2 + math.cos(la1 * p) * math.cos(la2 * p) * math.sin(dlo / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def ensure_gtfs(gtfs_dir: str | None) -> str:
    if gtfs_dir:
        return gtfs_dir
    d = tempfile.mkdtemp(prefix="wl-gtfs-")
    for f in GTFS_FILES:
        print(f"downloading {f} ...", flush=True)
        urllib.request.urlretrieve(GTFS_BASE + f, os.path.join(d, f))
    return d


def load_haltepunkte():
    """DIVA -> (name, lat, lon) averaged over its platforms."""
    acc = defaultdict(lambda: {"n": [], "la": [], "lo": []})
    with open(HALTEPUNKTE, encoding="utf-8") as fh:
        for r in csv.DictReader(fh, delimiter=";"):
            a = acc[r["DIVA"]]
            a["n"].append(r["StopText"]); a["la"].append(float(r["Latitude"])); a["lo"].append(float(r["Longitude"]))
    return {d: (max(set(v["n"]), key=v["n"].count), sum(v["la"]) / len(v["la"]), sum(v["lo"]) / len(v["lo"]))
            for d, v in acc.items()}


def map_stations(gtfs_dir: str, halte: dict) -> dict:
    """GTFS station number (at:49:N) -> DIVA, by coordinate + name."""
    stat = defaultdict(lambda: {"n": [], "la": [], "lo": []})
    with open(os.path.join(gtfs_dir, "stops.txt"), encoding="utf-8-sig") as fh:
        for r in csv.DictReader(fh):
            sid = r["stop_id"]
            if not sid.startswith("at:49:"):
                continue
            N = sid.split(":")[2]
            s = stat[N]; s["n"].append(r["stop_name"]); s["la"].append(float(r["stop_lat"])); s["lo"].append(float(r["stop_lon"]))
    hlist = [(d, norm(nm), la, lo) for d, (nm, la, lo) in halte.items()]
    out, unresolved = {}, 0
    for N, v in stat.items():
        gname = norm(max(set(v["n"]), key=v["n"].count))
        gla, glo = sum(v["la"]) / len(v["la"]), sum(v["lo"]) / len(v["lo"])
        best_near, bnd = None, 9e9
        best_name, bmd = None, 9e9
        for d, hn, hla, hlo in hlist:
            dd = haversine(gla, glo, hla, hlo)
            if dd < bnd:
                bnd, best_near = dd, d
            if hn == gname and dd < bmd:
                bmd, best_name = dd, d
        if best_name is not None and bmd <= MATCH_NAME_M:
            out[N] = best_name
        elif best_near is not None and bnd <= MATCH_NEAR_M:
            out[N] = best_near
        else:
            unresolved += 1
    print(f"stations: {len(stat)} mapped {len(out)} unresolved {unresolved}", flush=True)
    return out


def load_trips(gtfs_dir: str):
    """trip_id -> (line, headsign) using route_short_name; 'Wien ' prefix stripped."""
    rid2line = {}
    with open(os.path.join(gtfs_dir, "routes.txt"), encoding="utf-8-sig") as fh:
        for r in csv.DictReader(fh):
            rid2line[r["route_id"]] = r["route_short_name"]
    tmeta = {}
    with open(os.path.join(gtfs_dir, "trips.txt"), encoding="utf-8-sig") as fh:
        for r in csv.DictReader(fh):
            line = rid2line.get(r["route_id"])
            if not line:
                continue
            hs = r["trip_headsign"].strip()
            if hs.startswith("Wien "):
                hs = hs[5:]
            tmeta[r["trip_id"]] = (line, hs)
    return tmeta


def dominant_sequences(gtfs_dir: str, tmeta: dict):
    """Stream stop_times (grouped by trip) -> per (line, headsign) most-common stop_id sequence."""
    sigs = defaultdict(Counter)   # (line, headsign) -> Counter[tuple(stop_id)]
    counts = defaultdict(int)     # (line, headsign) -> trip count
    path = os.path.join(gtfs_dir, "stop_times.txt")
    with open(path, encoding="utf-8-sig") as fh:
        rd = csv.reader(fh)
        h = next(rd)
        ti, si, qi = h.index("trip_id"), h.index("stop_id"), h.index("stop_sequence")
        cur, rows = None, []

        def flush(trip, rows):
            meta = tmeta.get(trip)
            if not meta or not rows:
                return
            rows.sort(key=lambda x: x[0])
            sig = tuple(sid for _, sid in rows)
            sigs[meta][sig] += 1
            counts[meta] += 1

        for row in rd:
            t = row[ti]
            if t != cur:
                if cur is not None:
                    flush(cur, rows)
                cur, rows = t, []
            rows.append((int(row[qi]), row[si]))
        flush(cur, rows)
    return sigs, counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gtfs-dir", default=None)
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    gtfs = ensure_gtfs(args.gtfs_dir)
    halte = load_haltepunkte()
    n2diva = map_stations(gtfs, halte)
    tmeta = load_trips(gtfs)
    print(f"trips: {len(tmeta)}", flush=True)
    sigs, counts = dominant_sequences(gtfs, tmeta)

    rows_out = []
    dropped_stops = Counter()
    kept = 0
    for (line, hs), c in sorted(sigs.items()):
        trips = counts[(line, hs)]
        if trips < MIN_TRIPS:
            continue
        sig, _ = sigs[(line, hs)].most_common(1)[0]
        # map stop_ids -> DIVA, collapse consecutive platforms of one physical stop
        seq, last = [], None
        for sid in sig:
            N = sid.split(":")[2] if sid.startswith("at:49:") else None
            diva = n2diva.get(N)
            if diva is None:
                dropped_stops[line] += 1
                continue
            if diva != last:
                seq.append(diva); last = diva
        if len(seq) < 2:
            continue
        kept += 1
        for i, diva in enumerate(seq):
            rows_out.append((line, hs, i, diva))

    gen = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as fh:
        fh.write(f"#generated={gen}\n")
        w = csv.writer(fh, delimiter=";")
        w.writerow(["line", "headsign", "seq", "diva"])
        w.writerows(rows_out)
    print(f"wrote {args.out}: {kept} routes, {len(rows_out)} stop rows", flush=True)
    if dropped_stops:
        top = ", ".join(f"{l}:{n}" for l, n in dropped_stops.most_common(8))
        print(f"unmapped stops per line (top): {top}", flush=True)


if __name__ == "__main__":
    main()
