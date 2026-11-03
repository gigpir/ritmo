#!/bin/zsh
# Builds an .ipa in build/, to install Ritmo without pressing Run in Xcode.
#
#   ./ipa.sh           build/Ritmo.ipa, not signed, for SideStore, AltStore or Sideloadly: they sign it with the
#                      Apple ID of whoever installs it, so it works on any iPhone. It uses the public
#                      app identifier from Signing.xcconfig and holds nothing about your account,
#                      so you can share it.
#   ./ipa.sh --signed  build/Ritmo-signed.ipa, signed with your team (Config/Local.xcconfig): it installs directly on your
#                      own iPhone, with Apple Configurator or `xcrun devicectl device install app`, until this
#                      week's signature expires. It contains your Apple ID and your iPhone's id:
#                      keep it to yourself.
#
# An .ipa is just a zip with the app inside a Payload folder.
set -euo pipefail
cd "${0:A:h}"

export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
public_prefix=$(sed -n 's/^RITMO_BUNDLE_PREFIX = //p' Config/Signing.xcconfig)
case "${1:-}" in
  --signed) mode=signed; ipa=build/Ritmo-signed.ipa; settings=(-allowProvisioningUpdates) ;;
  "") mode=unsigned; ipa=build/Ritmo.ipa; settings=(CODE_SIGNING_ALLOWED=NO "RITMO_BUNDLE_PREFIX=$public_prefix") ;;
  *) echo "Usage: ./ipa.sh [--signed]" >&2; exit 1 ;;
esac

# The build number is the date and time, shown in Settings, to tell which build is on the phone.
build_number=$(date +%Y%m%d.%H%M)

# Symbols stripped as in an App Store build: they would carry this Mac's file paths.
strip=(DEPLOYMENT_POSTPROCESSING=YES STRIP_INSTALLED_PRODUCT=YES STRIP_STYLE=all COPY_PHASE_STRIP=YES)

xcodebuild -project Ritmo.xcodeproj -scheme Ritmo -configuration Release \
  -destination "generic/platform=iOS" -derivedDataPath build "${settings[@]}" "${strip[@]}" "CURRENT_PROJECT_VERSION=$build_number" -quiet build

rm -rf build/ipa "$ipa"
mkdir -p build/ipa/Payload
cp -R build/Build/Products/Release-iphoneos/Ritmo.app build/ipa/Payload/
(cd build/ipa && zip -qry "../${ipa:t}" Payload)
rm -rf build/ipa
echo "$ipa ($(du -h "$ipa" | cut -f1), $mode, build $build_number)"
