#!/bin/bash
# Fetches the mods that dev runs need in run/mods (their jars are built with a newer Loom than this build uses):
# Enhanced Celestials 2 (core, default events, CorgiLib, Data Anchor), When Dungeons Arise, JourneyMap.
set -e
cd "$(dirname "$0")/.."
mkdir -p run/mods
for v in ogeGZbvu ETxcPIDO srVXMYcH 3Dc7hr5f g8oglmT0 i7LuKuDl; do
  url=$(curl -s "https://api.modrinth.com/v2/version/$v" | python3 -c "import json,sys;print(json.load(sys.stdin)['files'][0]['url'])")
  curl -sL -o "run/mods/$(basename "$url")" "$url"
done
ls run/mods
