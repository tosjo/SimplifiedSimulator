"""Builds the lane-level network of the Harlingerstraatweg crossing in Leeuwarden.

Input:  an OpenStreetMap extract of the crossing (osm-harlingerstraatweg.osm).
Output: src/main/resources/crossing/network.json

The OpenStreetMap ways give the carriageway centre lines, lane counts and turn lanes.
Lane positions, stop lines and the details OpenStreetMap does not describe were
checked against the PDOK aerial photo (8 cm resolution); see README.md in this folder.

Coordinates are local metres with x pointing east and y pointing north. The origin
is RD (EPSG:28992) 180440, 579965, in the middle of the crossing.
"""
from __future__ import annotations

import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from pyproj import Transformer

from geometry import (Point, add, bezier, cut, direction_at, dedupe, extend, join, length, mul, norm, offset,
                      point_at, polyline_length, project, resample, right_normal, segment_intersection, sub)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OSM_FILE = HERE / "osm-harlingerstraatweg.osm"
OUT_FILE = ROOT / "src" / "main" / "resources" / "crossing" / "network.json"

ORIGIN_RD = (180440.0, 579965.0)
_TO_RD = Transformer.from_crs(4326, 28992, always_xy=True)
LANE = 3.2  # lane width in metres, measured on the aerial photo
URBAN_SPEED = 50 / 3.6

# ---------------------------------------------------------------------------------------------
# OpenStreetMap input
# ---------------------------------------------------------------------------------------------


def rd_from_wgs84(lon: float, lat: float) -> tuple[float, float]:
    """WGS84 -> RD New (EPSG:28992), including the RDNAPTRANS grid shift via PROJ."""
    return _TO_RD.transform(lon, lat)


def load_osm(path: Path) -> dict[str, list[Point]]:
    root = ET.parse(path).getroot()
    nodes = {}
    for n in root.iter("node"):
        x, y = rd_from_wgs84(float(n.get("lon")), float(n.get("lat")))
        nodes[n.get("id")] = (round(x - ORIGIN_RD[0], 2), round(y - ORIGIN_RD[1], 2))
    return {w.get("id"): [nodes[nd.get("ref")] for nd in w.iter("nd")] for w in root.iter("way")}


WAYS = load_osm(OSM_FILE)


def way(*ids: str) -> list[Point]:
    """One or more connected OSM ways joined into a single polyline."""
    return join(*(WAYS[i] for i in ids))


def reverse(points):
    return list(reversed(points))


def until_x(points, x, increasing=True):
    """Cuts a polyline where it first reaches the given x coordinate."""
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        if (a[0] - x) * (b[0] - x) <= 0 and a[0] != b[0]:
            t = (x - a[0]) / (b[0] - a[0])
            return points[: i + 1] + [add(a, mul(sub(b, a), t))]
    return list(points)


def from_x(points, x):
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        if (a[0] - x) * (b[0] - x) <= 0 and a[0] != b[0]:
            t = (x - a[0]) / (b[0] - a[0])
            return [add(a, mul(sub(b, a), t))] + points[i + 1:]
    return list(points)


def smooth(points, iterations=2):
    """Chaikin corner cutting that keeps both end points."""
    pts = list(points)
    for _ in range(iterations):
        out = [pts[0]]
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            out.append(add(mul(a, 0.75), mul(b, 0.25)))
            out.append(add(mul(a, 0.25), mul(b, 0.75)))
        out.append(pts[-1])
        pts = out
    return dedupe(pts, 0.3)


# ---------------------------------------------------------------------------------------------
# Network model
# ---------------------------------------------------------------------------------------------


class Network:
    def __init__(self):
        self.lanes: dict[str, dict] = {}
        self.routes: list[dict] = []
        self.groups: dict[str, dict] = {}
        self.bus_stops: list[dict] = []

    def lane(self, lane_id, points, kind="car", width=LANE, speed=URBAN_SPEED, **extra):
        pts = resample(dedupe(points), 2.0)
        self.lanes[lane_id] = {"id": lane_id, "kind": kind, "width": width, "speed": round(speed, 2),
                               "points": [[round(x, 2), round(y, 2)] for x, y in pts], **extra}
        return lane_id

    def group(self, group_id, kind, name, approach):
        self.groups[group_id] = {"id": group_id, "kind": kind, "name": name, "approach": approach,
                                 "heads": []}

    def route(self, route_id, kinds, lanes, group, stop_lane, stop_s, movement, weight=1.0, **extra):
        self.routes.append({"id": route_id, "kinds": kinds, "lanes": lanes, "signalGroup": group,
                            "stopLane": stop_lane, "stopS": round(stop_s, 2), "movement": movement,
                            "weight": weight, **extra})

    def lane_points(self, lane_id):
        return [tuple(p) for p in self.lanes[lane_id]["points"]]


net = Network()


def stop_s_at(points, q):
    return project(points, q)


def approach_lane(net, lane_id, centre, off, stop_point, kind="car", before=0.0):
    """An approach lane that ends exactly at its stop line."""
    pts = offset(centre, off)
    pts = extend(pts, before=before)
    s = stop_s_at(pts, stop_point)
    pts = cut(pts, 0.0, s)
    return net.lane(lane_id, pts, kind)


def connector(net, lane_id, start_lane, end_points, via=None, kind="car", end_s=0.0, speed=URBAN_SPEED):
    """A path from the end of an approach lane to a point on an exit lane."""
    a = net.lane_points(start_lane)
    p0, d0 = a[-1], norm(sub(a[-1], a[-2]))
    p1 = point_at(end_points, end_s)
    d1 = direction_at(end_points, end_s)
    if via:
        pts = join(via, bezier(via[-1], norm(sub(via[-1], via[-2])), p1, d1))
        pts = join([p0], pts)
    else:
        pts = bezier(p0, d0, p1, d1)
    return net.lane(lane_id, pts, kind, speed=speed)


def exit_lane(net, lane_id, centre, off, start_s=0.0, after=0.0, kind="car", max_len=None):
    pts = extend(offset(centre, off), after=after)
    total = polyline_length(pts)
    end = total if max_len is None else min(total, start_s + max_len)
    return net.lane(lane_id, cut(pts, start_s, end), kind)


# ---------------------------------------------------------------------------------------------
# Carriageway centre lines (OSM)
# ---------------------------------------------------------------------------------------------

# West arm, eastbound (towards the crossing): left | through | through | right
west_in = way("266434531")
west_in_inner = way("1206957219")                  # stop line to the crossing area
west_busway = from_x(way("627572101"), -200.0)    # separate bus lane, joins at the crossing
west_bus_inner = way("1206897073")
west_slip = way("1206957221")                     # right turn slip road to Slauerhoffweg

# East arm, westbound: left | through | through | bus | right slip
east_in = extend(way("1206957213"), before=60.0)
east_in_inner = way("969241758")
east_slip = way("1206957212")

# North arm (Douwe Kalmaleane), southbound: left | through | right slip
north_in = extend(way("969241770", "969241718"), before=40.0)
north_in_inner = way("1206957217")
north_slip = way("1206957220")

# South arm (Slauerhoffweg), northbound: left | through | right
south_in = from_x(way("822298312"), -20.0)
south_in = way("822298312")
south_in = cut(south_in, project(south_in, (-16.4, -126.2)), polyline_length(south_in))

# Exits
east_out = extend(way("969241724", "969241722", "1206957218"), after=90.0)
west_out = way("6491083")
west_out = cut(west_out, 0, 200)
north_out = extend(way("969241719", "462396311"), after=40.0)
south_out = way("627572097", "822298318")

# ---------------------------------------------------------------------------------------------
# Car and bus lanes
# ---------------------------------------------------------------------------------------------

# --- West approach (eastbound), lanes from left (north) to right (south) ---
W_STOP = (-29.88, -1.9)
w_offsets = {1: -2 * LANE, 2: -LANE, 3: 0.0, 4: LANE}
for k, off in w_offsets.items():
    n = right_normal(direction_at(west_in, polyline_length(west_in)))
    stop = add(W_STOP, mul(n, off))
    approach_lane(net, f"W_in_{k}", west_in, off, stop, before=40.0)

# Bus lane: stop line in line with the general stop line
bus_stop_s = project(west_busway, (-29.9, -8.9))
net.lane("W_bus_in", cut(west_busway, 0, bus_stop_s), "bus")

# --- East approach (westbound), lanes from left (south) to right (north) ---
E_STOP = (29.27, 5.2)
E_STOP_OUTER_X = 41.0     # bus lane and right-turn lane have their stop line further back
e_offsets = {1: -LANE, 2: 0.0, 3: LANE, 4: 2 * LANE, 5: 3 * LANE}
for k, off in e_offsets.items():
    if k <= 3:
        n = right_normal(direction_at(east_in, polyline_length(east_in)))
        stop = add(E_STOP, mul(n, off))
    else:
        lane_pts = offset(east_in, off)
        stop = point_at(lane_pts, project(lane_pts, (E_STOP_OUTER_X, 0)))
        stop = until_x(lane_pts, E_STOP_OUTER_X)[-1]
    approach_lane(net, f"E_in_{k}", east_in, off, stop, kind="bus" if k == 4 else "car")

# --- North approach (southbound), lanes from left (east) to right (west) ---
N_STOP = (-3.53, 30.21)
n_offsets = {1: -LANE, 2: 0.0, 3: LANE}
for k, off in n_offsets.items():
    lane_pts = offset(north_in, off)
    s = project(lane_pts, N_STOP)
    stop = point_at(lane_pts, s)
    approach_lane(net, f"N_in_{k}", north_in, off, stop)

# --- South approach (northbound), lanes from left (west) to right (east) ---
S_STOP = (-1.33, -28.17)
s_offsets = {1: -LANE, 2: 0.0, 3: LANE}
for k, off in s_offsets.items():
    lane_pts = offset(south_in, off)
    stop = point_at(lane_pts, project(lane_pts, S_STOP))
    approach_lane(net, f"S_in_{k}", south_in, off, stop)

# --- Exit lanes ---
# Eastbound towards the city: two general lanes plus a bus lane on the right
exit_lane(net, "E_out_1", east_out, -LANE)
exit_lane(net, "E_out_2", east_out, 0.0)
exit_lane(net, "E_out_bus", east_out, LANE, kind="bus")
# Westbound towards Harlingen: two lanes
exit_lane(net, "W_out_1", west_out, -LANE / 2)
exit_lane(net, "W_out_2", west_out, LANE / 2)
# Northbound (Douwe Kalmaleane) and southbound (Slauerhoffweg): one lane each
exit_lane(net, "N_out", north_out, 0.0)
exit_lane(net, "S_out", south_out, 0.0)

E_OUT_1 = net.lane_points("E_out_1")
E_OUT_2 = net.lane_points("E_out_2")
E_OUT_BUS = net.lane_points("E_out_bus")
W_OUT_1 = net.lane_points("W_out_1")
W_OUT_2 = net.lane_points("W_out_2")
N_OUT = net.lane_points("N_out")
S_OUT = net.lane_points("S_out")

# ---------------------------------------------------------------------------------------------
# Connectors (paths across the crossing) and routes
# ---------------------------------------------------------------------------------------------

SLOW_TURN = 30 / 3.6
TIGHT_TURN = 22 / 3.6


def inner(points, off, upto=None):
    pts = offset(points, off)
    return pts if upto is None else cut(pts, 0, upto)


# West approach -------------------------------------------------------------------------------
w_inner_len = polyline_length(west_in_inner)
connector(net, "W_L", "W_in_1", N_OUT, via=inner(west_in_inner, -2 * LANE, w_inner_len - 2.0), speed=TIGHT_TURN)
connector(net, "W_T_1", "W_in_2", E_OUT_1, via=inner(west_in_inner, -LANE, w_inner_len - 1.0), end_s=3.0)
connector(net, "W_T_2", "W_in_3", E_OUT_2, via=inner(west_in_inner, 0.0, w_inner_len - 1.0), end_s=3.0)
# Right turn slip: leaves the fourth lane at the stop line and merges into Slauerhoffweg
slip_pts = net.lane_points("W_in_4")[-1:] + west_slip[2:]
s_merge = project(S_OUT, west_slip[-1])
net.lane("W_R", smooth(join(slip_pts, [point_at(S_OUT, s_merge + 4.0)])), "car", speed=SLOW_TURN)
# Bus: straight through on its own lane
net.lane("W_bus_T", join(net.lane_points("W_bus_in")[-1:], west_bus_inner[:-1],
                         [point_at(E_OUT_BUS, 3.0)]), "bus")

# East approach -------------------------------------------------------------------------------
e_inner_len = polyline_length(east_in_inner)
connector(net, "E_L", "E_in_1", S_OUT, via=inner(east_in_inner, -LANE, e_inner_len - 4.0), speed=TIGHT_TURN)
connector(net, "E_T_1", "E_in_2", W_OUT_1, via=inner(east_in_inner, 0.0, e_inner_len - 1.0), end_s=4.0)
connector(net, "E_T_2", "E_in_3", W_OUT_2, via=inner(east_in_inner, LANE, e_inner_len - 1.0), end_s=4.0)
# Bus lane continues straight and merges into the right westbound lane
bus_via = offset(east_in_inner, 2 * LANE)
bus_via = cut(bus_via, 0, polyline_length(bus_via) - 8.0)
connector(net, "E_bus_T", "E_in_4", W_OUT_2, via=net.lane_points("E_in_4")[-1:] + bus_via, kind="bus", end_s=14.0)
# Right turn slip to Douwe Kalmaleane
e5 = net.lane_points("E_in_5")
slip_start = e5[-1]
slip_mid = until_x(offset(east_in_inner, 3 * LANE), 17.0)
n_merge_s = project(N_OUT, (7.6, 29.4))
slip = join([slip_start], from_x(slip_mid, slip_start[0] - 1.0)[1:],
            bezier(slip_mid[-1], norm(sub(slip_mid[-1], slip_mid[-2])), (8.3, 21.5), (-0.2, 1.0)),
            [point_at(N_OUT, n_merge_s + 3.0)])
net.lane("E_R", smooth(slip), "car", speed=SLOW_TURN)

# North approach ------------------------------------------------------------------------------
n_inner_len = polyline_length(north_in_inner)
connector(net, "N_L", "N_in_1", E_OUT_1, via=inner(north_in_inner, -LANE, n_inner_len - 3.0), speed=TIGHT_TURN,
          end_s=6.0)
connector(net, "N_T", "N_in_2", S_OUT, via=inner(north_in_inner, 0.0, n_inner_len - 1.0), end_s=2.0)
n3 = net.lane_points("N_in_3")
w_merge_s = project(W_OUT_2, north_slip[-1])
slip = join(n3[-1:], bezier(n3[-1], norm(sub(n3[-1], n3[-2])), north_slip[3], norm(sub(north_slip[4], north_slip[2]))),
            north_slip[4:-1], [point_at(W_OUT_2, w_merge_s + 4.0)])
net.lane("N_R", smooth(slip), "car", speed=SLOW_TURN)

# South approach ------------------------------------------------------------------------------
s1, s2, s3 = (net.lane_points(f"S_in_{k}") for k in (1, 2, 3))
south_inner = way("822298312")
south_inner = cut(south_inner, project(south_inner, S_STOP), polyline_length(south_inner))
south_inner = join(south_inner, way("6491065")[1:])
si_left = offset(south_inner, -LANE)
connector(net, "S_L", "S_in_1", W_OUT_1, via=cut(si_left, 0, project(si_left, (-2.0, -4.0))), speed=TIGHT_TURN,
          end_s=4.0)
si_mid = offset(south_inner, 0.0)
connector(net, "S_T", "S_in_2", N_OUT, via=cut(si_mid, 0, project(si_mid, (3.0, 4.0))), end_s=3.0)
si_right = offset(south_inner, LANE)
connector(net, "S_R", "S_in_3", E_OUT_2, via=cut(si_right, 0, project(si_right, (3.6, -14.0))), speed=SLOW_TURN,
          end_s=12.0)

# ---------------------------------------------------------------------------------------------
# Signal groups (Dutch numbering: 01-12 motor traffic, 2x cyclists, 3x pedestrians, 4x buses)
# ---------------------------------------------------------------------------------------------
for gid, kind, name, approach in [
    ("01", "car", "Harlingerstraatweg west, right turn", "W"),
    ("02", "car", "Harlingerstraatweg west, straight on", "W"),
    ("03", "car", "Harlingerstraatweg west, left turn", "W"),
    ("04", "car", "Douwe Kalmaleane, right turn", "N"),
    ("05", "car", "Douwe Kalmaleane, straight on", "N"),
    ("06", "car", "Douwe Kalmaleane, left turn", "N"),
    ("07", "car", "Harlingerstraatweg east, right turn", "E"),
    ("08", "car", "Harlingerstraatweg east, straight on", "E"),
    ("09", "car", "Harlingerstraatweg east, left turn", "E"),
    ("10", "car", "Slauerhoffweg, right turn", "S"),
    ("11", "car", "Slauerhoffweg, straight on", "S"),
    ("12", "car", "Slauerhoffweg, left turn", "S"),
    ("22", "bike", "Cyclists crossing Douwe Kalmaleane", "N"),
    ("24", "bike", "Cyclists crossing Harlingerstraatweg east", "E"),
    ("26", "bike", "Cyclists crossing Slauerhoffweg", "S"),
    ("32", "foot", "Pedestrians crossing Douwe Kalmaleane", "N"),
    ("34", "foot", "Pedestrians crossing Harlingerstraatweg east", "E"),
    ("36", "foot", "Pedestrians crossing Slauerhoffweg", "S"),
    ("41", "bus", "Bus lane from Harlingen, straight on", "W"),
    ("42", "bus", "Bus lane from the city, straight on", "E"),
]:
    net.group(gid, kind, name, approach)


def car_route(rid, approach_lane_id, conn, exit_id, group, movement, weight=1.0, kinds=("car",)):
    lanes = [approach_lane_id, conn, exit_id]
    net.route(rid, list(kinds), lanes, group, approach_lane_id,
              polyline_length(net.lane_points(approach_lane_id)), movement, weight)


car_route("W1_L", "W_in_1", "W_L", "N_out", "03", "left", 1.0)
car_route("W2_T", "W_in_2", "W_T_1", "E_out_1", "02", "through", 1.0)
car_route("W3_T", "W_in_3", "W_T_2", "E_out_2", "02", "through", 1.0)
car_route("W4_R", "W_in_4", "W_R", "S_out", "01", "right", 1.0)
car_route("Wb_T", "W_bus_in", "W_bus_T", "E_out_bus", "41", "through", 1.0, kinds=("bus",))
car_route("E1_L", "E_in_1", "E_L", "S_out", "09", "left", 1.0)
car_route("E2_T", "E_in_2", "E_T_1", "W_out_1", "08", "through", 1.0)
car_route("E3_T", "E_in_3", "E_T_2", "W_out_2", "08", "through", 1.0)
car_route("Eb_T", "E_in_4", "E_bus_T", "W_out_2", "42", "through", 1.0, kinds=("bus",))
car_route("E5_R", "E_in_5", "E_R", "N_out", "07", "right", 1.0)
car_route("N1_L", "N_in_1", "N_L", "E_out_1", "06", "left", 1.0)
car_route("N2_T", "N_in_2", "N_T", "S_out", "05", "through", 1.0)
car_route("N3_R", "N_in_3", "N_R", "W_out_2", "04", "right", 1.0)
car_route("S1_L", "S_in_1", "S_L", "W_out_1", "12", "left", 1.0)
car_route("S2_T", "S_in_2", "S_T", "N_out", "11", "through", 1.0)
car_route("S3_R", "S_in_3", "S_R", "E_out_2", "10", "right", 1.0)

# Merges that are not resolved by the signal plan: slip roads give way to through traffic.
for r in net.routes:
    if r["id"] in ("W4_R", "E5_R", "N3_R", "Eb_T"):
        r["mergeYield"] = True

# ---------------------------------------------------------------------------------------------
# Cyclists and pedestrians (two-way paths, crossings controlled by 2x / 3x groups)
# ---------------------------------------------------------------------------------------------
BIKE_SPEED = 18 / 3.6
WALK_SPEED = 1.35

north_cycle = way("1206897056", "1206897075", "1206897063")       # west -> east along the north side
south_cycle = way("6491054", "1025307918", "4720315")              # west -> east along the south side
south_cycle = from_x(way("1206897069"), -90.0)[:-1] + south_cycle
east_cycle = way("31672979", "1206897064")                         # south -> north over the east arm
east_cycle = way("1066219985")[-6:-1] + east_cycle                  # from Slauerhoffweg's cycle path


def two_way(name, centre, crossing_from, crossing_to, group, kind, speed, width):
    """Creates both directions of a path, split at a crossing stop line."""
    for direction, pts in (("fwd", centre), ("rev", reverse(centre))):
        side = 0.6 if kind == "bike" else 0.5
        lane_pts = offset(pts, side)
        a = project(lane_pts, crossing_from if direction == "fwd" else crossing_to)
        b = project(lane_pts, crossing_to if direction == "fwd" else crossing_from)
        net.lane(f"{name}_{direction}_in", cut(lane_pts, 0, a), kind, width=width, speed=speed)
        net.lane(f"{name}_{direction}_x", cut(lane_pts, a, b), kind, width=width, speed=speed)
        net.lane(f"{name}_{direction}_out", cut(lane_pts, b, polyline_length(lane_pts)), kind, width=width,
                 speed=speed)
        net.route(f"{name}_{direction}", [kind], [f"{name}_{direction}_in", f"{name}_{direction}_x",
                                                  f"{name}_{direction}_out"], group, f"{name}_{direction}_in",
                  polyline_length(net.lane_points(f"{name}_{direction}_in")), "crossing")


# Stop positions: the kerb edges either side of the carriageways that are crossed
two_way("BN", north_cycle, (-15.5, 24.6), (14.0, 23.4), "22", "bike", BIKE_SPEED, 2.0)
two_way("BS", south_cycle, (-17.5, -18.1), (8.5, -22.1), "26", "bike", BIKE_SPEED, 2.0)
two_way("BE", east_cycle, (19.0, -17.2), (25.3, 17.2), "24", "bike", BIKE_SPEED, 2.0)

north_walk = way("1206897062", "1206897061", "1206897060", "1206897059", "1348559162")
north_walk = join(way("969241783")[:2], north_walk, way("1348559161", "1348559160"), [(35.0, 62.1)])
south_walk = join(way("969241786")[1:], way("969241778", "969241776", "969241779", "969241780", "969241729",
                                               "969241727"))
s_side = way("969241775")
south_walk = join(south_walk, cut(s_side, project(s_side, (11.1, -31.1)), project(s_side, (3.4, -79.6))))
east_walk = join(way("1348559146", "969241757", "969241743", "969241744"), way("969241742")[1:5])
two_way("PN", north_walk, (-11.0, 27.6), (11.5, 26.1), "32", "foot", WALK_SPEED, 1.5)
two_way("PS", south_walk, (-15.5, -21.2), (5.0, -24.3), "36", "foot", WALK_SPEED, 1.5)
two_way("PE", east_walk, (23.4, -16.8), (28.3, 15.8), "34", "foot", WALK_SPEED, 1.5)

# ---------------------------------------------------------------------------------------------
# Signal heads: one per lane at the stop line, mounted on the right-hand kerb or island
# ---------------------------------------------------------------------------------------------


def add_head(group, pos, facing, lane=None):
    net.groups[group]["heads"].append({"position": [round(pos[0], 2), round(pos[1], 2)],
                                       "facing": round(facing, 1), "lane": lane})


def heading_deg(d):
    """Compass-style heading in degrees, 0 = north, 90 = east (direction the head faces)."""
    return math.degrees(math.atan2(d[0], d[1])) % 360


for r in net.routes:
    lane_id = r["stopLane"]
    pts = net.lane_points(lane_id)
    end, d = pts[-1], norm(sub(pts[-1], pts[-2]))
    w = net.lanes[lane_id]["width"]
    side = right_normal(d)
    # head stands just past the stop line on the right side of the lane, facing the traffic
    pos = add(add(end, mul(side, w / 2 + 0.4)), mul(d, 0.8 if r["kinds"][0] in ("car", "bus") else 0.3))
    heads = net.groups[r["signalGroup"]]["heads"]
    if any(h["lane"] == lane_id for h in heads):
        continue
    add_head(r["signalGroup"], pos, heading_deg((-d[0], -d[1])), lane_id)

# ---------------------------------------------------------------------------------------------
# Detectors: a long loop at the stop line and a short one further upstream for each car lane
# ---------------------------------------------------------------------------------------------
detectors = []
for r in net.routes:
    lane_id = r["stopLane"]
    if any(d["lane"] == lane_id for d in detectors):
        continue
    length_ = polyline_length(net.lane_points(lane_id))
    kind = r["kinds"][0]
    if kind in ("car", "bus"):
        detectors.append({"id": f"{lane_id}_near", "lane": lane_id, "group": r["signalGroup"], "role": "near",
                          "from": round(length_ - 14.0, 2), "to": round(length_ - 0.5, 2)})
        if length_ > 90:
            detectors.append({"id": f"{lane_id}_far", "lane": lane_id, "group": r["signalGroup"], "role": "far",
                              "from": round(length_ - 82.0, 2), "to": round(length_ - 80.0, 2)})
    else:
        role = "button" if kind == "foot" else "near"
        detectors.append({"id": f"{lane_id}_{role}", "lane": lane_id, "group": r["signalGroup"], "role": role,
                          "from": round(max(0.0, length_ - 6.0), 2), "to": round(length_, 2)})

# ---------------------------------------------------------------------------------------------
# Bus stop "Westeinde" (eastbound platform, OSM node / IFOPT NL:Q:20006400)
# ---------------------------------------------------------------------------------------------
bus_lane = net.lane_points("E_out_bus")
net.bus_stops.append({"id": "westeinde-east", "name": "Westeinde", "lane": "E_out_bus",
                      "s": round(project(bus_lane, (79.2, -25.1)), 2), "dwell": 20.0})

# ---------------------------------------------------------------------------------------------
# Approaches (for statistics and the demand model)
# ---------------------------------------------------------------------------------------------
approaches = {
    "W": {"name": "Harlingerstraatweg (from Harlingen)", "heading": "east"},
    "E": {"name": "Harlingerstraatweg (from the city centre)", "heading": "west"},
    "N": {"name": "Douwe Kalmaleane", "heading": "south"},
    "S": {"name": "Slauerhoffweg", "heading": "north"},
}

network = {
    "name": "Harlingerstraatweg / Douwe Kalmaleane / Slauerhoffweg, Leeuwarden",
    "origin": {"rdX": ORIGIN_RD[0], "rdY": ORIGIN_RD[1], "crs": "EPSG:28992"},
    "units": "metres; x east, y north",
    "sources": [
        "OpenStreetMap contributors (ODbL), https://www.openstreetmap.org/copyright",
        "Beeldmateriaal Nederland / PDOK aerial photo (CC BY 4.0)",
    ],
    "approaches": approaches,
    "lanes": list(net.lanes.values()),
    "routes": net.routes,
    "signalGroups": list(net.groups.values()),
    "detectors": detectors,
    "busStops": net.bus_stops,
}

if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT_FILE
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(network, indent=1))
    print(f"{len(net.lanes)} lanes, {len(net.routes)} routes, {len(net.groups)} signal groups, "
          f"{len(detectors)} detectors -> {out}")
