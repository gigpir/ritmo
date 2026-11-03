#!/bin/zsh
# Builds Ritmo for Android as build/Ritmo-<version>.apk, signed with your release key: the APK to send to
# friends or to attach to a GitHub release. A phone installs an update only if it carries the same
# signature as the app it has, so sign every version with this key.
#
# The key stays out of the repository, in ~/ritmo-release.jks (another one: RITMO_KEY=<path> ./apk.sh).
# The first run creates it and asks you to choose its password. Keep a copy of the file and the password,
# for example in your password manager: without them, the phones that have Ritmo can't install its updates.
set -euo pipefail
cd "${0:A:h}"

# Android Studio's Java, when it's there: the one Ritmo is built and tested with.
studio_java="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
[[ -d "$studio_java" ]] && export JAVA_HOME="$studio_java"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
key="${RITMO_KEY:-$HOME/ritmo-release.jks}"
build_tools=$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)

if [[ ! -f "$key" ]]; then
  echo "Creating your release key in $key: choose its password, at least 6 characters."
  # Anyone can read the name in the certificate from the APK: gigpir, as on GitHub.
  "${JAVA_HOME:+$JAVA_HOME/bin/}keytool" -genkeypair -keystore "$key" -alias ritmo -keyalg RSA -keysize 4096 \
    -validity 10000 -dname "CN=gigpir"
fi

(cd android && ./gradlew --quiet assembleRelease </dev/null)

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' android/app/build.gradle.kts)
apk=build/Ritmo-$version.apk
mkdir -p build
echo "Signing with $key: type its password."
"$build_tools/apksigner" sign --ks "$key" --ks-key-alias ritmo --v4-signing-enabled false --out "$apk" \
  android/app/build/outputs/apk/release/app-release-unsigned.apk
signer=$("$build_tools/apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate DN: //p')
echo "$apk ($(du -h "$apk" | cut -f1)), signed by $signer"
