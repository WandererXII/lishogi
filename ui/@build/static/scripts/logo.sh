#!/bin/sh

file=assets/logo/lishogi-favicon-1024.png

for px in 512 256 192 128 64 32; do
  magick $file -strip -scale ${px}x${px} assets/logo/lishogi-favicon-${px}.png

magick $file -strip -scale 32x32 -channel RGB -negate +channel assets/logo/lishogi-favicon-32-invert.png

magick $file -strip -resize 128x128 assets/logo/favicon.ico

magick $file -strip -scale 180x180 assets/logo/apple-touch-icon.png

done
