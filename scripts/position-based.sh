#!/usr/bin/env sh
DESTINATION="$HOME/Downloads/position-based-$(date -u "+%F-%H.%M.%S")"
curl -L https://anonymous.4open.science/api/repo/experiments-2026-seams-ac-sdf/zip -o "$DESTINATION.zip"
unzip -q "$DESTINATION.zip" -d "$DESTINATION"
cd "$DESTINATION"
chmod +x gradlew
./gradlew runDynamicPopulationPositionBasedGraphic
