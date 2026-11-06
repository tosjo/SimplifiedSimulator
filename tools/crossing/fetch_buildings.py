"""Downloads the buildings around the crossing from 3D BAG and writes them as a compact mesh.

3D BAG (https://3dbag.nl, CC BY 4.0, 3DGI and TU Delft) contains every building in the
Netherlands with its real roof shape, reconstructed from the national height model.

usage: python fetch_buildings.py <radius metres> <out.bin>

Output format (little-endian), read by simplifiedsimulator.render.BuildingMeshLoader:
  char[4]  magic "BLD1"
  int32    building count
  per building:
    int32    flags (bit 0: glass roof / greenhouse)
    float32  roof height above the ground (m)
    int32    construction year (0 when unknown)
    int32    triangle count
    per triangle: uint8 surface (0 roof, 1 wall), then 3 x (x, y, z) float32
Coordinates are local metres: x east, y north, z up from the ground at the crossing.
"""
import json
import math
import struct
import sys
import urllib.request

import mapbox_earcut as earcut
import numpy as np
from scipy.spatial import ConvexHull

ORIGIN = (180440.0, 579965.0)
API = "https://api.3dbag.nl/collections/pand/items"
UA = {"User-Agent": "SimplifiedSimulator (github.com/tosjo/SimplifiedSimulator)"}
GROUND_NAP = 0.6  # approximate ground level at the crossing, metres above NAP
# Glass buildings that 3D BAG does not mark as such, by footprint centre (local metres)
GLASS_LANDMARKS = {"Crystallic": (-60.0, -32.0)}


def fetch_all(radius):
    x0, y0 = ORIGIN[0] - radius, ORIGIN[1] - radius
    x1, y1 = ORIGIN[0] + radius, ORIGIN[1] + radius
    url = f"{API}?bbox={x0},{y0},{x1},{y1}&limit=100"
    while url:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as resp:
            page = json.load(resp)
        transform = page["metadata"]["transform"]
        for feature in page["features"]:
            yield feature, transform
        url = next((link["href"] for link in page.get("links", []) if link["rel"] == "next"), None)


def triangulate(ring_points):
    """Triangulates a planar polygon with holes, given as a list of rings of 3D points."""
    outer = np.array(ring_points[0])
    # plane normal (Newell's method)
    n = np.zeros(3)
    for i in range(len(outer)):
        a, b = outer[i], outer[(i + 1) % len(outer)]
        n += [(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])]
    if np.linalg.norm(n) < 1e-9:
        return []
    n /= np.linalg.norm(n)
    u = np.cross(n, [0, 0, 1] if abs(n[2]) < 0.9 else [1, 0, 0])
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    pts = np.concatenate([np.array(r) for r in ring_points])
    flat = np.stack([pts @ u, pts @ v], axis=1)
    ends = np.cumsum([len(r) for r in ring_points]).astype(np.uint32)
    idx = earcut.triangulate_float64(flat, ends)
    tris = []
    for i in range(0, len(idx), 3):
        tri = pts[idx[i:i + 3]]
        # keep the original winding (outward normal)
        if np.dot(np.cross(tri[1] - tri[0], tri[2] - tri[0]), n) < 0:
            tri = tri[[0, 2, 1]]
        tris.append(tri)
    return tris


def building_triangles(feature, transform):
    """Triangles of a building's detailed (LoD 2.2) solid, plus its attributes."""
    scale, translate = transform["scale"], transform["translate"]
    verts = np.array(feature["vertices"], dtype=np.float64) * scale + translate
    verts[:, 0] -= ORIGIN[0]
    verts[:, 1] -= ORIGIN[1]
    verts[:, 2] -= GROUND_NAP
    out = []
    attrs = {}
    for obj in feature["CityObjects"].values():
        if obj.get("attributes"):
            attrs.update(obj["attributes"])
        for geom in obj.get("geometry", []):
            if str(geom.get("lod")) != "2.2" or geom["type"] != "Solid":
                continue
            sem = geom.get("semantics", {})
            types = [s["type"] for s in sem.get("surfaces", [])]
            values = sem.get("values", [[]])[0]
            for i, surface in enumerate(geom["boundaries"][0]):
                kind = types[values[i]] if values and values[i] is not None else "WallSurface"
                if kind == "GroundSurface":
                    continue
                rings = [[verts[k] for k in ring] for ring in surface]
                for tri in triangulate(rings):
                    out.append((0 if kind == "RoofSurface" else 1, tri))
    return out, attrs


def convex_glass_shape(tris):
    """
    A clean, convex version of a glass building. The laser scan partly goes through glass,
    which leaves deep spikes in the reconstructed roof; glass halls such as Crystallic are
    convex, so the hull of the measured points (minus the top 1% outliers) is a faithful
    shape. Faces looking up become roof, the rest walls; the floor is dropped.
    """
    pts = np.concatenate([t[1] for t in tris])
    pts = pts[pts[:, 2] <= np.percentile(pts[:, 2], 99)]
    base = pts.copy()
    base[:, 2] = 0.0
    pts = np.unique(np.round(np.concatenate([pts, base]), 2), axis=0)
    hull = ConvexHull(pts)
    out = []
    for simplex, eq in zip(hull.simplices, hull.equations):
        normal = eq[:3]
        if normal[2] < -0.5:
            continue
        tri = pts[simplex]
        if np.dot(np.cross(tri[1] - tri[0], tri[2] - tri[0]), normal) < 0:
            tri = tri[[0, 2, 1]]
        out.append((0 if normal[2] > 0.3 else 1, tri))
    return out


def main():
    radius, path = float(sys.argv[1]), sys.argv[2]
    buildings = []
    for feature, transform in fetch_all(radius):
        tris, attrs = building_triangles(feature, transform)
        if not tris:
            continue
        centre = np.mean([t[1].mean(axis=0) for t in tris], axis=0)
        if math.hypot(centre[0], centre[1]) > radius:
            continue
        glass = bool(attrs.get("b3_is_glas_dak") or attrs.get("b3_kas_warenhuis"))
        roof = [t[1] for t in tris if t[0] == 0]
        if roof:
            xy = np.concatenate(roof)[:, :2]
            for name, (lx, ly) in GLASS_LANDMARKS.items():
                if xy[:, 0].min() < lx < xy[:, 0].max() and xy[:, 1].min() < ly < xy[:, 1].max() \
                        and math.hypot(centre[0] - lx, centre[1] - ly) < 120:
                    glass = True
        if glass:
            tris = convex_glass_shape(tris)
        height = float(attrs.get("b3_h_dak_max") or 0) - float(attrs.get("b3_h_maaiveld") or 0)
        year = int(attrs.get("oorspronkelijkbouwjaar") or 0)
        buildings.append((glass, height, year, tris))
    with open(path, "wb") as f:
        f.write(b"BLD1")
        f.write(struct.pack("<i", len(buildings)))
        for glass, height, year, tris in buildings:
            f.write(struct.pack("<ifii", 1 if glass else 0, height, year, len(tris)))
            for surface, tri in tris:
                f.write(struct.pack("<B9f", surface, *tri.astype(np.float32).ravel()))
    n_tris = sum(len(b[3]) for b in buildings)
    print(f"{len(buildings)} buildings, {n_tris} triangles, {sum(b[0] for b in buildings)} with glass roofs -> {path}")


if __name__ == "__main__":
    main()
