#!/usr/bin/env bash
# Opens the main screens on the EMULATOR (never a physical device) and fails on any crash in logcat.
# Needs a debug build installed. Usage: tools/smoke.sh
set -euo pipefail
ADB="adb -s ${EMULATOR:-emulator-5554}"
PKG=dev.codexsoft.codexcine
LAUNCH="$PKG/.ui.compose.DebugLaunchActivity"
WAIT="${WAIT:-9}"

$ADB get-state >/dev/null || { echo "No emulator at ${EMULATOR:-emulator-5554}"; exit 2; }
$ADB logcat -c

open() { # label, args...
  local label="$1"; shift
  $ADB shell am start -n "$LAUNCH" "$@" >/dev/null
  sleep "$WAIT"
  if $ADB logcat -d | grep -q "FATAL EXCEPTION"; then
    echo "FAIL  $label"; $ADB logcat -d | grep -A12 "FATAL EXCEPTION" | head -30; exit 1
  fi
  echo "ok    $label"
  $ADB shell input keyevent BACK; sleep 1
}

$ADB shell am force-stop $PKG
$ADB shell am start -n "$PKG/.ui.browse.MainActivity" >/dev/null; sleep $((WAIT + 6))
$ADB logcat -d | grep -q "FATAL EXCEPTION" && { echo "FAIL  home"; exit 1; }; echo "ok    home"
open "details movie"  --es screen details --es type movie --ei id 550 --es title "Fight Club"
open "details saga"   --es screen details --es type movie --ei id 155 --es title "The Dark Knight"
open "details tv"     --es screen details --es type tv --ei id 1399 --es title "Game of Thrones"
open "season"         --es screen season --ei id 1399 --ei n 1 --es title "Game of Thrones"
open "grid"           --es screen grid --es category POPULAR_MOVIES --es title "Films populaires"
open "person"         --es screen person --ei id 287 --es title "Brad Pitt"
open "search"         --es screen search
open "about"          --es screen about
echo "smoke: OK"
