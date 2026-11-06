"""Finds the trees around the crossing in the national height model (AHN, via PDOK).

The surface model (DSM) minus the terrain model (DTM) gives the height of everything that
stands on the ground. Buildings are removed using the 3D BAG footprints; what remains and is
taller than 2.5 m over a large enough area is tree canopy. Individual trees are the local
maxima of the canopy, and each gets the real height and an estimated crown radius.

usage: python fetch_trees.py <half-size metres> <buildings.bin> <out.json>
"""
import io
import json
import struct
import sys
import urllib.request
from pathlib import Path

import cv2
import numpy as np
import tifffile

ORIGIN = (180440.0, 579965.0)
WCS = "https://service.pdok.nl/rws/ahn/wcs/v1_0"
UA = {"User-Agent": "SimplifiedSimulator (github.com/tosjo/SimplifiedSimulator)"}
RES = 0.5
GROUND_NAP = 0.6
NETWORK = Path(__file__).resolve().parents[2] / "src/main/resources/crossing/network.json"


def on_road(x, y, lanes, margin=1.5):
    """True when (x, y) lies on a carriageway or path (lamp posts and signal masts end up here)."""
    for lane in lanes:
        pts = np.array(lane["points"])
        a, b = pts[:-1], pts[1:]
        ab = b - a
        t = np.clip(((x - a[:, 0]) * ab[:, 0] + (y - a[:, 1]) * ab[:, 1]) / np.maximum((ab ** 2).sum(1), 1e-9), 0, 1)
        d = np.hypot(a[:, 0] + t * ab[:, 0] - x, a[:, 1] + t * ab[:, 1] - y)
        if d.min() < lane["width"] / 2 + margin:
            return True
    return False


def coverage(cid, half):
    url = (f"{WCS}?service=WCS&version=2.0.1&request=GetCoverage&coverageId={cid}&format=image/tiff"
           f"&subset=x({ORIGIN[0] - half},{ORIGIN[0] + half})&subset=y({ORIGIN[1] - half},{ORIGIN[1] + half})")
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180) as resp:
        data = tifffile.imread(io.BytesIO(resp.read()))
    data = data.astype(np.float32)
    data[data > 1e30] = np.nan
    return data


def building_mask(path, shape, half):
    mask = np.zeros(shape, np.uint8)
    with open(path, "rb") as f:
        assert f.read(4) == b"BLD1"
        (n,) = struct.unpack("<i", f.read(4))
        for _ in range(n):
            _, _, _, nt = struct.unpack("<ifii", f.read(16))
            for _ in range(nt):
                surface, *v = struct.unpack("<B9f", f.read(37))
                pts = np.array([[(v[i] + half) / RES, (half - v[i + 1]) / RES] for i in (0, 3, 6)], np.int32)
                cv2.fillPoly(mask, [pts], 255)
    return cv2.dilate(mask, np.ones((5, 5), np.uint8))


def main():
    half, buildings, out = float(sys.argv[1]), sys.argv[2], sys.argv[3]
    dsm = coverage("dsm_05m", half)
    dtm = coverage("dtm_05m", half)
    # fill the terrain under buildings and trees with the nearby ground level
    ground = np.where(np.isnan(dtm), np.nan, dtm)
    filled = np.nan_to_num(ground, nan=GROUND_NAP)
    weights = (~np.isnan(ground)).astype(np.float32)
    blur = cv2.GaussianBlur(filled * weights, (0, 0), 8)
    wblur = cv2.GaussianBlur(weights, (0, 0), 8)
    ground = np.where(np.isnan(dtm), blur / np.maximum(wblur, 1e-3), dtm)
    height = np.nan_to_num(dsm - ground, nan=0.0)

    canopy = (height > 2.5).astype(np.uint8) * 255
    canopy[building_mask(buildings, canopy.shape, half) > 0] = 0
    canopy = cv2.morphologyEx(canopy, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(canopy)
    big = np.zeros(n, bool)
    big[stats[:, cv2.CC_STAT_AREA] >= 12] = True  # >= 3 m2: removes lamp posts and signs
    big[0] = False
    canopy = big[labels]

    smooth = cv2.GaussianBlur(height, (0, 0), 1.5)
    peaks = (smooth == cv2.dilate(smooth, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9)))) & canopy
    ys, xs = np.nonzero(peaks)
    trees = []
    lanes = json.loads(NETWORK.read_text())["lanes"]
    # crown radius: distance to the canopy edge, limited to a plausible range
    dist = cv2.distanceTransform(canopy.astype(np.uint8), cv2.DIST_L2, 5) * RES
    for y, x in zip(ys, xs):
        h = float(height[y, x])
        if h < 3 or h > 35:
            continue
        tx, ty = x * RES - half + RES / 2, half - y * RES - RES / 2
        if on_road(tx, ty, lanes):
            continue
        r = float(np.clip(dist[y, x] + 0.8, 1.5, min(9.0, 0.45 * h)))
        trees.append({"x": round(tx, 1), "y": round(ty, 1),
                      "height": round(h, 1), "crown": round(r, 1)})
    json.dump({"source": "AHN (PDOK), canopy minus 3D BAG buildings", "trees": trees}, open(out, "w"))
    print(f"{len(trees)} trees -> {out}")


if __name__ == "__main__":
    main()
