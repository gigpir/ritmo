#!/bin/zsh
# Builds Ritmo and installs it on the iPhone paired with this Mac (by cable or on the same Wi-Fi),
# for example after a change to the code. Your workouts stay on the phone.
# It only works while this week's signature is valid: with Xcode 27 the command line
# can't use your Apple ID, so to renew the signature every 7 days press Run in Xcode.
# With more than one iPhone: IPHONE=<udid> ./install.sh (`xcrun devicectl list devices` shows them).
set -euo pipefail
cd "${0:A:h}"

export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
if [[ -z "${IPHONE:-}" ]]; then
  devices=$(mktemp)
  xcrun devicectl list devices --json-output "$devices" >/dev/null
  IPHONE=$(python3 -c '
import json, sys
for device in json.load(open(sys.argv[1]))["result"]["devices"]:
    hardware = device["hardwareProperties"]
    if hardware.get("deviceType") == "iPhone" and hardware.get("reality") == "physical":
        print(hardware["udid"])
        break
' "$devices")
  rm -f "$devices"
fi
[[ -n "$IPHONE" ]] || { echo "No iPhone found: connect it with the cable and unlock it." >&2; exit 1; }

# The build number is the date and time, shown in Settings, to tell which build is on the phone.
build_number=$(date +%Y%m%d.%H%M)

xcodebuild -project Ritmo.xcodeproj -scheme Ritmo -configuration Release \
  -destination "id=$IPHONE" -derivedDataPath build \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration "CURRENT_PROJECT_VERSION=$build_number" -quiet build
APP=build/Build/Products/Release-iphoneos/Ritmo.app
xcrun devicectl device install app --device "$IPHONE" "$APP" >/dev/null
EXPIRES=$(security cms -D -i "$APP/embedded.mobileprovision" | plutil -extract ExpirationDate raw -o - -)
echo "Ritmo $build_number is on your iPhone. The signature expires on ${EXPIRES%T*}: before then, press Run in Xcode."
