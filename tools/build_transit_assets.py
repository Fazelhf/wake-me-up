#!/usr/bin/env python3
"""Builds the bundled transit assets (schema v2) from the vendored sources.

Sources (see tools/sources/ATTRIBUTION.md):
  - tools/sources/tehran_metro_stations.json  (mostafa-kheibary/tehran-metro-data)
  - tools/sources/tehran_brt_stations.json    (MohammadAmin-Andy/iran-public-transport)

Output:
  - app/src/main/assets/transit/tehran_metro.json
  - app/src/main/assets/transit/tehran_brt.json

Schema v2 (consumed by TransitParser):
{
  "version": 2,
  "lines": [
    {
      "id": "1", "name": "خط ۱", "en": "Line 1", "color": "#E0001F",
      "stations": [ {"key": "Tajrish", "name": "تجریش", "en": "Tajrish",
                     "lat": 35.80465, "lon": 51.43349}, ... ],
      "edges": [[0, 1], [1, 2], ...]   // indices into "stations"
    }
  ]
}

A station appearing on several lines keeps the same "key" in each of them,
which is how the app links transfer stations when building the routing graph.
"""

from __future__ import annotations

import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "tools" / "sources"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "transit"

# --- Metro ------------------------------------------------------------------

METRO_LINE_NAMES = {
    1: ("خط ۱", "Line 1"),
    2: ("خط ۲", "Line 2"),
    3: ("خط ۳", "Line 3"),
    4: ("خط ۴", "Line 4"),
    5: ("خط ۵", "Line 5"),
    6: ("خط ۶", "Line 6"),
    7: ("خط ۷", "Line 7"),
}

# Source-data gaps: extra line memberships. "Ayatollah Kashani" is the
# line 4/6 interchange on line 4's western extension (…– Allameh Jafari –
# Ayatollah Kashani – Chaharbagh) but is only tagged with line 6 in the
# source, which strands Chaharbagh with no line-4 neighbor.
METRO_EXTRA_LINES = {
    "Ayatollah Kashani": [4],
}

# --- BRT --------------------------------------------------------------------

BRT_LINE_NAMES = {
    1: ("بی‌آرتی ۱ (تهرانپارس – پایانه آزادی)", "BRT 1 (Tehranpars – Azadi Terminal)"),
    4: ("بی‌آرتی ۴ (پایانه افشار – ترمینال جنوب)", "BRT 4 (Afshar – South Terminal)"),
}

BRT_LINE_COLORS = {1: "#E0001F", 4: "#00AEEF"}

# Typos in the source's relations, mapped to the real station keys
# (None = drop the relation; the target does not exist in the dataset).
BRT_RELATION_FIXES = {
    "Fatahnaei": "Fathnaei",
    "OstadMoein": "Ostad moein",
    "sadegiyeh - Jennah": None,
    "Payaneh Jonub": "Terminal-e Jonoob",
}

# In line 4's "Jomhouri-orumie" the neighbor "Azadi" means the line-4 stop
# "Azadi4" (Navvab x Azadi), not the line-1 Azadi Square stop.
BRT_RELATION_FIXES_BY_STATION = {
    ("Jomhouri-orumie", "Azadi"): "Azadi4",
}

# BRT stops that are physically at a metro station (same square/crossing):
# take the exact metro coordinates.
BRT_METRO_ANCHORS = {
    "Emam Hossein": "Imam Hossein",
    "Darvaze dowlat": "Darvazeh Dolat",
    "Ferdowsi": "Ferdowsi",
    "Chaharah-e Vali asr": "Teatr-e Shahr",
    "Meydan-e Enghelab": "Meydan-e Enghelab-e Eslami",
    "Ostad moein": "Ostad Mo'in",
    "Azadi": "Meydan-e Azadi",
    "Tohid": "Towhid",
}

# Street-corridor anchors for stops with no source coordinates and no
# co-located metro station. Approximate but on the correct street; stops
# between anchors are interpolated along the line's station chain.
BRT_MANUAL_ANCHORS = {
    # Line 1 (Damavand St -> Enghelab St -> Azadi St, east to west).
    "Sabalan": (35.7085, 51.4585),
    "Navvab": (35.7009, 51.3789),      # Enghelab/Azadi St x Navvab (not the metro stop further south)
    "Payaneh Azadi": (35.7042, 51.3352),
    # Line 4 (Chamran highway -> Navvab -> south belt, north to south).
    "Payaneh afshar": (35.7920, 51.3960),
    "Pomp-e benzin": (35.7880, 51.3945),
    "Namayeshgah": (35.7838, 51.3922),
    "Ati Saz": (35.7770, 51.3880),
    "Pol-e Modiriat": (35.7688, 51.3800),
    "Mollasadra": (35.7565, 51.3748),
    "Kooy-e nasr": (35.7442, 51.3762),
    "Bagherkhan": (35.7095, 51.3778),
    "Azadi4": (35.6995, 51.3790),
    "Jomhouri-orumie": (35.6932, 51.3796),
    "Azarbayjan": (35.6893, 51.3800),
    "Emam Khomeini": (35.6842, 51.3806),
    "Komeyl": (35.6795, 51.3812),
    "Pole Ghazvin": (35.6672, 51.3830),
    "Helal-e ahmar": (35.6630, 51.3843),
    "Pol-e Javadiyeh": (35.6560, 51.3925),
    "Meydan-e Bahman": (35.6552, 51.4040),
    "Terminal-e Jonoob": (35.6520, 51.4215),
}


def load(path: Path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def line_key(value):
    """The source uses "lines" (metro) or "line" (BRT)."""
    return value.get("lines") or value.get("line") or []


def build_line_graph(data: dict, line: int) -> tuple[set, set]:
    """Stations of one line and the undirected same-line adjacency edges."""
    nodes = {k for k, v in data.items() if line in line_key(v)}
    edges = set()
    for k in nodes:
        for r in data[k].get("relations") or []:
            if r in nodes and r != k:
                edges.add(tuple(sorted((k, r))))
    return nodes, edges


def order_stations(nodes: set, edges: set) -> list:
    """Orders a line's stations: longest path first, then branch walks.

    Real lines are simple paths with the occasional short branch (airport
    spurs, terminal loops); isolated stations are appended at the end.
    """
    adj = defaultdict(list)
    for a, b in edges:
        adj[a].append(b)
        adj[b].append(a)
    for k in adj:
        adj[k].sort()

    def bfs_farthest(start):
        seen = {start: None}
        queue = [start]
        last = start
        while queue:
            nxt = []
            for cur in queue:
                for n in adj[cur]:
                    if n not in seen:
                        seen[n] = cur
                        nxt.append(n)
                        last = n
            queue = nxt
        path = [last]
        while seen[path[-1]] is not None:
            path.append(seen[path[-1]])
        return last, path[::-1]

    ordered: list = []
    placed = set()
    connected = sorted(k for k in nodes if adj[k])
    if connected:
        a, _ = bfs_farthest(connected[0])
        _, main_path = bfs_farthest(a)
        ordered.extend(main_path)
        placed.update(main_path)
        # Branches: walk unplaced neighbors from any placed station.
        for start in list(ordered):
            for n in adj[start]:
                cur, prev = n, start
                while cur is not None and cur not in placed:
                    ordered.append(cur)
                    placed.add(cur)
                    nxt = [x for x in adj[cur] if x != prev and x not in placed]
                    prev, cur = cur, (nxt[0] if nxt else None)
    ordered.extend(sorted(nodes - placed))  # isolated stations, if any
    return ordered


def make_line(data: dict, line: int, names: tuple, color: str, coords) -> dict:
    nodes, edges = build_line_graph(data, line)
    ordered = order_stations(nodes, edges)
    index = {k: i for i, k in enumerate(ordered)}
    fa, en = names
    return {
        "id": str(line),
        "name": fa,
        "en": en,
        "color": color,
        "stations": [
            {
                "key": k,
                "name": data[k]["translations"]["fa"].strip(),
                "en": k,
                "lat": round(coords[k][0], 6),
                "lon": round(coords[k][1], 6),
            }
            for k in ordered
        ],
        "edges": sorted([sorted((index[a], index[b])) for a, b in edges]),
    }


def build_metro() -> dict:
    data = load(SOURCES / "tehran_metro_stations.json")
    for key, extra in METRO_EXTRA_LINES.items():
        for line in extra:
            if line not in data[key]["lines"]:
                data[key]["lines"].append(line)
    coords = {k: (v["latitude"], v["longitude"]) for k, v in data.items()}
    line_colors = {}
    for line in METRO_LINE_NAMES:
        counter = Counter()
        for v in data.values():
            if line in line_key(v):
                for c in v.get("colors") or []:
                    counter[c] += 1
        line_colors[line] = counter.most_common(1)[0][0]
    return {
        "version": 2,
        "source": "mostafa-kheibary/tehran-metro-data (ISC)",
        "lines": [
            make_line(data, line, METRO_LINE_NAMES[line], line_colors[line], coords)
            for line in sorted(METRO_LINE_NAMES)
        ],
    }


def fix_brt_relations(data: dict) -> dict:
    for k, v in data.items():
        fixed = []
        for r in v.get("relations") or []:
            r = BRT_RELATION_FIXES_BY_STATION.get((k, r), r)
            r = BRT_RELATION_FIXES.get(r, r) if r not in data else r
            if r is not None and r in data and r != k and r not in fixed:
                fixed.append(r)
        v["relations"] = fixed
    return data


def resolve_brt_coords(data: dict, metro: dict) -> dict:
    coords = {}
    for k, v in data.items():
        if v.get("latitude") and v.get("longitude"):
            coords[k] = (v["latitude"], v["longitude"])
    for brt_key, metro_key in BRT_METRO_ANCHORS.items():
        coords[brt_key] = (metro[metro_key]["latitude"], metro[metro_key]["longitude"])
    coords.update(BRT_MANUAL_ANCHORS)

    # Interpolate remaining stops linearly between the nearest anchored
    # neighbors along each line's ordered chain.
    for line in BRT_LINE_NAMES:
        nodes, edges = build_line_graph(data, line)
        ordered = order_stations(nodes, edges)
        known = [i for i, k in enumerate(ordered) if k in coords]
        if len(known) < 2:
            raise SystemExit(f"BRT line {line}: not enough anchors to interpolate")
        for i, k in enumerate(ordered):
            if k in coords:
                continue
            before = max((j for j in known if j < i), default=None)
            after = min((j for j in known if j > i), default=None)
            if before is None or after is None:
                raise SystemExit(f"BRT line {line}: endpoint '{k}' has no anchor")
            t = (i - before) / (after - before)
            (la1, lo1), (la2, lo2) = coords[ordered[before]], coords[ordered[after]]
            coords[k] = (la1 + (la2 - la1) * t, lo1 + (lo2 - lo1) * t)
    return coords


def build_brt() -> dict:
    data = fix_brt_relations(load(SOURCES / "tehran_brt_stations.json"))
    metro = load(SOURCES / "tehran_metro_stations.json")
    coords = resolve_brt_coords(data, metro)
    return {
        "version": 2,
        "source": "MohammadAmin-Andy/iran-public-transport tehran/bus/brt (ODbL)",
        "lines": [
            make_line(data, line, BRT_LINE_NAMES[line], BRT_LINE_COLORS[line], coords)
            for line in sorted(BRT_LINE_NAMES)
        ],
    }


def sanity(network: dict, label: str) -> None:
    for line in network["lines"]:
        n = len(line["stations"])
        for a, b in line["edges"]:
            if not (0 <= a < n and 0 <= b < n):
                raise SystemExit(f"{label} line {line['id']}: edge {a},{b} out of range")
        keys = [s["key"] for s in line["stations"]]
        if len(keys) != len(set(keys)):
            raise SystemExit(f"{label} line {line['id']}: duplicate keys")
        for s in line["stations"]:
            if not (35.0 < s["lat"] < 36.2 and 50.5 < s["lon"] < 52.0):
                raise SystemExit(f"{label}: {s['key']} coordinates look wrong: {s}")
    print(f"{label}: {len(network['lines'])} lines, "
          f"{sum(len(l['stations']) for l in network['lines'])} station entries, "
          f"{sum(len(l['edges']) for l in network['lines'])} edges")


def main() -> None:
    metro = build_metro()
    brt = build_brt()
    sanity(metro, "metro")
    sanity(brt, "brt")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, payload in (("tehran_metro.json", metro), ("tehran_brt.json", brt)):
        with open(OUT_DIR / name, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("wrote", OUT_DIR / name)


if __name__ == "__main__":
    sys.exit(main())
