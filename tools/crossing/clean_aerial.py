"""Removes the vehicles that were on the road when the aerial photos were taken.

The simulator drives its own traffic over the photo, so the parked and moving cars in it
would otherwise look like ghosts. The newest photo is used as the base. Vehicles are found
as compact blobs on the carriageway that differ from the surrounding asphalt; each blob is
replaced by the same spot from an older photo (same road layout) where it was empty, after
matching the colours. Whatever is left is painted over with OpenCV's inpainting. Thin
features such as road markings are kept.

usage: python clean_aerial.py <half-size metres> <out.jpg> <newest.png> [older.png ...]
"""
import json
import sys
from pathlib import Path

import cv2
import numpy as np

NETWORK = Path(__file__).resolve().parents[2] / "src/main/resources/crossing/network.json"


def road_mask(shape, half):
    h, w = shape
    scale = w / (2 * half)
    net = json.loads(NETWORK.read_text())
    road = np.zeros((h, w), np.uint8)
    for lane in net["lanes"]:
        if lane["kind"] not in ("car", "bus"):
            continue
        pts = np.array([[(x + half) * scale, (half - y) * scale] for x, y in lane["points"]], np.int32)
        cv2.polylines(road, [pts], False, 255, int((lane["width"] + 1.2) * scale))
    close = int(6 * scale) | 1
    return cv2.morphologyEx(road, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (close, close))), scale


def vehicles(img, road, scale):
    """Mask of vehicle-sized blobs (with their shadows) on the road."""
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    k = int(6 * scale) | 1
    background = cv2.medianBlur(img, min(k, 255 if k > 255 else k))
    diff = np.abs(lab - cv2.cvtColor(background, cv2.COLOR_BGR2LAB).astype(np.float32))
    score = diff[..., 0] + 2.0 * (diff[..., 1] + diff[..., 2])
    candidate = ((score > 22) & (road > 0)).astype(np.uint8) * 255
    k = max(3, int(0.9 * scale)) | 1
    blobs = cv2.morphologyEx(candidate, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (k, k)))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(blobs)
    keep = np.zeros(n, bool)
    areas = stats[:, cv2.CC_STAT_AREA]
    keep[(areas >= (1.2 * scale) ** 2) & (areas <= 80 * scale ** 2)] = True
    keep[0] = False
    mask = keep[labels].astype(np.uint8) * 255
    grow = int(1.6 * scale) | 1
    return cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (grow, grow)))


def match_colours(src, ref, region):
    """Linear per-channel colour transfer from src to ref, fitted on the given region."""
    out = src.astype(np.float32)
    sel = region > 0
    for c in range(3):
        s, r = out[..., c][sel], ref[..., c].astype(np.float32)[sel]
        if s.size < 100:
            continue
        out[..., c] = (out[..., c] - s.mean()) / (s.std() + 1e-6) * r.std() + r.mean()
    return out.clip(0, 255).astype(np.uint8)


def main():
    half, dst, paths = float(sys.argv[1]), sys.argv[2], sys.argv[3:]
    images = [cv2.imread(p) for p in paths]
    base = images[0].copy()
    road, scale = road_mask(base.shape[:2], half)
    remaining = vehicles(base, road, scale)
    total = remaining.sum() / 255
    asphalt = cv2.erode(road, np.ones((5, 5), np.uint8)) & ~remaining

    for older in images[1:]:
        older = match_colours(older, base, asphalt)
        busy = vehicles(older, road, scale)
        usable = (remaining > 0) & (busy == 0)
        soft = cv2.GaussianBlur(usable.astype(np.float32), (0, 0), 0.4 * scale)[..., None]
        base = (base * (1 - soft) + older * soft).astype(np.uint8)
        remaining[usable] = 0

    left = remaining.sum() / 255
    out = cv2.inpaint(base, remaining, 7, cv2.INPAINT_TELEA) if left else base
    cv2.imwrite(dst, out, [cv2.IMWRITE_JPEG_QUALITY, 88])
    print(f"vehicles on {total / scale ** 2:.0f} m2; {left / scale ** 2:.0f} m2 had to be inpainted")


if __name__ == "__main__":
    main()
