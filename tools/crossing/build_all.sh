#!/usr/bin/env bash
# Regenerates every data file in src/main/resources/crossing from the original sources.
# Needs Python 3.10+ with the packages in requirements.txt and internet access to
# api.openstreetmap.org, service.pdok.nl and api.3dbag.nl.
set -euo pipefail
cd "$(dirname "$0")"
OUT=../../src/main/resources/crossing
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "== OpenStreetMap extract"
curl -fsS -A "SimplifiedSimulator (github.com/tosjo/SimplifiedSimulator)" \
  "https://api.openstreetmap.org/api/0.6/map?bbox=5.7642,53.2035,5.7717,53.2080" -o osm-harlingerstraatweg.osm

echo "== Lane network"
python3 build_network.py

echo "== Aerial photos (newest year first; older years fill in where vehicles were)"
for year in 2026 2025 2024; do
  python3 fetch_aerial.py "${year}_orthoHR" 300 4096 "$TMP/a600_$year.png"
  python3 fetch_aerial.py "${year}_orthoHR" 80 2048 "$TMP/a160_$year.png"
done
python3 clean_aerial.py 300 "$OUT/aerial-600m.jpg" "$TMP"/a600_2026.png "$TMP"/a600_2025.png "$TMP"/a600_2024.png
python3 clean_aerial.py 80 "$OUT/aerial-160m.jpg" "$TMP"/a160_2026.png "$TMP"/a160_2025.png "$TMP"/a160_2024.png
python3 fetch_aerial.py Actueel_ortho25 1500 2048 "$TMP/wide.png"
python3 -c "from PIL import Image; Image.open('$TMP/wide.png').convert('RGB').save('$OUT/aerial-3km.jpg', quality=85)"

echo "== Buildings (3D BAG) and trees (AHN)"
python3 fetch_buildings.py 400 "$OUT/buildings.bin"
python3 fetch_trees.py 300 "$OUT/buildings.bin" "$OUT/trees.json"
echo "done"
