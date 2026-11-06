# Crossing data tools

These scripts build the model of the real crossing of the Harlingerstraatweg with the
Douwe Kalmaleane and the Slauerhoffweg in Leeuwarden. Their output is committed in
`src/main/resources/crossing`, so you only need them to update or change the data.

```sh
pip install -r requirements.txt
./build_all.sh
```

| Script | Output | Source |
| --- | --- | --- |
| `build_network.py` | `network.json`: every lane, the paths across the crossing, stop lines, signal groups, signal heads, detectors and the bus stop | OpenStreetMap (lane counts, turn lanes, signal positions), checked against the aerial photo |
| `fetch_aerial.py` + `clean_aerial.py` | `aerial-160m.jpg`, `aerial-600m.jpg`, `aerial-3km.jpg`: ground textures | PDOK aerial photos, 8 cm per pixel (2024, 2025, 2026) |
| `fetch_buildings.py` | `buildings.bin`: 3D buildings with their real roof shapes | 3D BAG |
| `fetch_trees.py` | `trees.json`: position, height and crown size of every tree | AHN height model (surface minus terrain, buildings removed) |
| `overlay.py` | a picture of the network drawn over the aerial photo, to check the geometry | |

## Coordinates

Everything is in metres, with x pointing east and y pointing north. The origin is
RD (EPSG:28992) coordinate 180440, 579965, in the middle of the crossing.

## How the vehicles are removed from the aerial photo

The simulator drives its own traffic over the photo, so the cars that happened to be on
the road when it was taken have to go. `clean_aerial.py` uses the newest photo as the
base, finds vehicle-sized blobs on the carriageway, and replaces each one with the same
spot from an older photo where it was empty (the road layout has not changed since
2024). Spots that were occupied in every photo are painted over with OpenCV inpainting.

## Signal group numbering

Dutch numbering: 01-12 motor traffic, 2x cyclists, 3x pedestrians, 4x buses.

| Approach | Right | Straight on | Left | Bus | Cyclists | Pedestrians |
| --- | --- | --- | --- | --- | --- | --- |
| Harlingerstraatweg from the west | 01 | 02 | 03 | 41 | | |
| Douwe Kalmaleane (north) | 04 | 05 | 06 | | 22 | 32 |
| Harlingerstraatweg from the east | 07 | 08 | 09 | 42 | 24 | 34 |
| Slauerhoffweg (south) | 10 | 11 | 12 | | 26 | 36 |

## Licences

- Map data © OpenStreetMap contributors, available under the Open Database License (ODbL).
- Aerial photos: Beeldmateriaal Nederland via PDOK, CC BY 4.0.
- Buildings: 3D BAG by 3DGI and the 3D geoinformation group of TU Delft, CC BY 4.0.
- Height model: Actueel Hoogtebestand Nederland (AHN) via PDOK, CC0.
