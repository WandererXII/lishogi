#!/usr/bin/env bash
set -euo pipefail

srcdir="assets/sound/sources"
outbase="assets/sound"
volumefile="assets/sound/volumes.json"

# jq is required
if ! command -v jq >/dev/null 2>&1; then
  echo "Error: jq is required" >&2
  exit 1
fi

# Read volume config into associative array
declare -A volume_map
while IFS="=" read -r key val; do
  volume_map["$key"]="$val"
done < <(jq -r 'to_entries | .[] | "\(.key)=\(.value)"' "$volumefile")

get_volume_factor() {
  local rel="$1"
  local best_match=""
  for k in "${!volume_map[@]}"; do
    if [[ "$rel" == "$k" || "$rel" == $k/* ]]; then
      # longest key wins
      if (( ${#k} > ${#best_match} )); then
        best_match="$k"
      fi
    fi
  done
  if [[ -n "$best_match" ]]; then
    echo "${volume_map[$best_match]}"
  else
    echo ""
  fi
}

for fmt in ogg mp3; do
  dstdir="$outbase/$fmt"
  rm -rf "$dstdir"
  mkdir -p "$dstdir"
  echo "Creating $dstdir"

  find "$srcdir" \( -type f -o -type l \) | while read -r file; do
    rel="${file#$srcdir/}"
    out="$dstdir/${rel%.*}.$fmt"
    mkdir -p "$(dirname "$out")"

    if [ -L "$file" ]; then
      target=$(readlink "$file")
      target_rel="${target%.*}.$fmt"
      ln -sf "$target_rel" "$out"
    else
      vol=$(get_volume_factor "$rel")
      ext="${file##*.}"

      if [[ "$ext" == "$fmt" && -z "$vol" ]]; then
        cp -p "$file" "$out"
      else
        if [[ -n "$vol" ]]; then
          ffmpeg -hide_banner -loglevel error -y -i "$file" -filter:a "volume=${vol}" "$out"
        else
          ffmpeg -hide_banner -loglevel error -y -i "$file" "$out"
        fi
      fi
    fi
  done

  echo "Done creating $fmt files!"
done
