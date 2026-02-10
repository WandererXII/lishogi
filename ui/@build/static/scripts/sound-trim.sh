#!/usr/bin/env bash
set -euo pipefail

srcdir="assets/sound/sources/clock"
outdir="assets/sound/trimmed-clock"

rm -rf "$outdir"
mkdir -p "$outdir"
echo "Creating $outdir"

find "$srcdir" \( -type f -o -type l \) | while read -r file; do
  rel="${file#$srcdir/}"
  out="$outdir/$rel"
  mkdir -p "$(dirname "$out")"

  if [ -L "$file" ]; then
    ln -sf "$(readlink "$file")" "$out"
  else
    ffmpeg -hide_banner -loglevel error -y -i "$file" \
      -af "silenceremove=start_periods=1:start_threshold=-60dB:start_silence=0" \
      "$out"
  fi
done

echo "Done trimming silence!"
