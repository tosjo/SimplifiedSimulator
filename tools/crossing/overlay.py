"""Draws network.json on top of the aerial photo, to check the lane geometry by eye.

usage: python overlay.py <aerial.jpg> <x0> <y0> <x1> <y1> <out.png> [network.json]
The aerial image must cover exactly the rectangle x0..x1, y0..y1 (local metres).
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

COLOURS = {"car": (255, 40, 40), "bus": (255, 200, 0), "bike": (0, 160, 255), "foot": (40, 255, 120)}


def main():
    img_path, x0, y0, x1, y1, out = sys.argv[1], *map(float, sys.argv[2:6]), sys.argv[6]
    net_path = Path(sys.argv[7]) if len(sys.argv) > 7 else \
        Path(__file__).resolve().parents[2] / "src/main/resources/crossing/network.json"
    net = json.loads(net_path.read_text())
    im = Image.open(img_path).convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im)

    def px(p):
        return (p[0] - x0) / (x1 - x0) * w, (y1 - p[1]) / (y1 - y0) * h

    for lane in net["lanes"]:
        pts = [px(p) for p in lane["points"]]
        colour = COLOURS[lane["kind"]]
        if len(pts) > 1:
            d.line(pts, fill=colour, width=2)
            # arrow head at the end
            d.ellipse([pts[-1][0] - 3, pts[-1][1] - 3, pts[-1][0] + 3, pts[-1][1] + 3], outline=colour)
    for r in net["routes"]:
        lane = next(l for l in net["lanes"] if l["id"] == r["stopLane"])
        p = px(lane["points"][-1])
        d.rectangle([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=(255, 255, 255))
    for g in net["signalGroups"]:
        for head in g["heads"]:
            p = px(head["position"])
            d.ellipse([p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4], fill=(255, 0, 255))
            d.text((p[0] + 5, p[1] - 5), g["id"], fill=(255, 255, 255))
    im.save(out)


if __name__ == "__main__":
    main()
