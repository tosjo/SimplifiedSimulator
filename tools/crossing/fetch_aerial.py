"""Downloads aerial photos of the crossing from PDOK (Beeldmateriaal Nederland, CC BY 4.0).

usage: python fetch_aerial.py <layer> <half-size metres> <pixels> <out.png>
  layer: a PDOK WMS layer such as 2026_orthoHR, 2025_orthoHR or Actueel_ortho25
The image is centred on the crossing and covers 2 * half-size metres in each direction.
Large images are downloaded as 1024 x 1024 tiles and stitched together.
"""
import io
import sys
import urllib.request

from PIL import Image

ORIGIN = (180440.0, 579965.0)
WMS = "https://service.pdok.nl/hwh/luchtfotorgb/wms/v1_0"
TILE = 1024


def tile(layer, x0, y0, x1, y1, w, h):
    url = (f"{WMS}?service=WMS&version=1.3.0&request=GetMap&layers={layer}&styles=&crs=EPSG:28992"
           f"&bbox={ORIGIN[0] + x0},{ORIGIN[1] + y0},{ORIGIN[0] + x1},{ORIGIN[1] + y1}"
           f"&width={w}&height={h}&format=image/png")
    req = urllib.request.Request(url, headers={"User-Agent": "SimplifiedSimulator (github.com/tosjo/SimplifiedSimulator)"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return Image.open(io.BytesIO(resp.read())).convert("RGB")


def fetch(layer, half, pixels):
    n = max(1, pixels // TILE)
    size = 2 * half / n
    out = Image.new("RGB", (pixels, pixels))
    for i in range(n):
        for j in range(n):
            x0 = -half + i * size
            y1 = half - j * size
            out.paste(tile(layer, x0, y1 - size, x0 + size, y1, pixels // n, pixels // n),
                      (i * pixels // n, j * pixels // n))
    return out


if __name__ == "__main__":
    layer, half, pixels, out = sys.argv[1], float(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    fetch(layer, half, pixels).save(out)
    print(f"{layer}: {pixels}x{pixels} px, {2 * half:.0f} m -> {out}")
