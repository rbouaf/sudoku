#!/bin/sh
# Builds the release APK (R8-minified, debug-keyed: a personal build for two phones) and installs
# it on every connected device, or on the one named by -s. Data survives updates.
#   tools/build_release.sh [-s SERIAL]
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
./gradlew -q assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
if [ "$1" = "-s" ]; then SERIALS="$2"; else SERIALS=$($ADB devices | awk 'NR>1 && $2=="device" {print $1}'); fi
for s in $SERIALS; do
  $ADB -s "$s" install -r "$APK" >/dev/null && echo "installed on $s"
  $ADB -s "$s" shell dumpsys package app.materialsudoku | grep lastUpdateTime | head -1
done
