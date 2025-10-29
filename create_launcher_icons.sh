#!/bin/bash
# Script para crear iconos launcher placeholder usando ImageMagick o similar
# Este es un placeholder - idealmente deberías generar iconos reales

BASE_DIR="app/src/main/res"

# Colores
BG_COLOR="#1976D2"
FG_COLOR="#FFFFFF"

# Crear iconos para cada densidad
convert -size 48x48 xc:"$BG_COLOR" "$BASE_DIR/mipmap-mdpi/ic_launcher.png"
convert -size 48x48 xc:"$BG_COLOR" "$BASE_DIR/mipmap-mdpi/ic_launcher_round.png"

convert -size 72x72 xc:"$BG_COLOR" "$BASE_DIR/mipmap-hdpi/ic_launcher.png"
convert -size 72x72 xc:"$BG_COLOR" "$BASE_DIR/mipmap-hdpi/ic_launcher_round.png"

convert -size 96x96 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xhdpi/ic_launcher.png"
convert -size 96x96 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xhdpi/ic_launcher_round.png"

convert -size 144x144 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xxhdpi/ic_launcher.png"
convert -size 144x144 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xxhdpi/ic_launcher_round.png"

convert -size 192x192 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xxxhdpi/ic_launcher.png"
convert -size 192x192 xc:"$BG_COLOR" "$BASE_DIR/mipmap-xxxhdpi/ic_launcher_round.png"

echo "Iconos creados exitosamente"
