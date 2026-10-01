#!/bin/bash
# Builds the game and installs it on the phone — over the cable or, once the
# phone is paired for wireless debugging, over Wi-Fi. Saved games are kept.
#
#   Scripts/install-on-phone.sh
#
# Pairing, once per phone: Settings → Developer options → Wireless debugging →
# Pair device with pairing code, then run `adb pair <IP:port shown>` and type
# the code. After that the phone shows up by itself whenever wireless
# debugging is on and both are on the same Wi-Fi.
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

"$ADB" start-server >/dev/null 2>&1

# A real phone, not an emulator: over the cable or over Wi-Fi.
phone() { "$ADB" devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }'; }

device="$(phone)"
if [ -z "$device" ]; then
    # adb usually connects to a paired phone by itself; nudge it if it hasn't yet.
    for service in $("$ADB" mdns services 2>/dev/null | awk '/_adb-tls-connect/ { print $NF }'); do
        "$ADB" connect "$service" >/dev/null 2>&1 || true
    done
    sleep 1
    device="$(phone)"
fi
if [ -z "$device" ]; then
    echo "Phone not found. Turn on Wireless debugging (same Wi-Fi as this Mac) or plug the cable in." >&2
    exit 1
fi

./gradlew -q :app:assembleDebug
echo "Installing on $device…"
"$ADB" -s "$device" install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$device" shell am start --user 0 -n com.kirillrychkov.sashaspuzzles/.MainActivity >/dev/null
echo "Done."
