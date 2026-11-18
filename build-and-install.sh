#!/usr/bin/env bash
# Собирает APK «Кнопки пульта» и ставит его на приставку.
#   ./build-and-install.sh <ip приставки>   собрать и установить
#   ./build-and-install.sh --build          только собрать
# Нужны: JDK 17, Android SDK (platforms;android-34, build-tools;34.0.0), adb.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
B=build

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
  -A assets --min-sdk-version 28 --target-sdk-version 30 "$B/res.zip"
javac -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" \
  $(find src "$B/gen" -name '*.java')
"$BT/d8" --min-api 28 --lib "$JAR" --output "$B/dex" $(find "$B/classes" -name '*.class')
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias tvkeys -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvkeys" >/dev/null 2>&1
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvkeys.apk" "$B/aligned.apk"
echo "built $B/tvkeys.apk ($(wc -c < "$B/tvkeys.apk") bytes)"
[ "${1:-}" = "--build" ] && exit 0

[ -n "${1:-}" ] || { echo "usage: $0 <ip приставки> | --build" >&2; exit 1; }
S="$1:5555"
SVC=local.tvhome.keys/.KeyService
adb connect "$S" >/dev/null
adb -s "$S" install -r "$B/tvkeys.apk"
# Re-toggle the service so it reloads keys.txt after an update.
adb -s "$S" shell settings delete secure enabled_accessibility_services >/dev/null
adb -s "$S" shell settings put secure enabled_accessibility_services "$SVC"
adb -s "$S" shell settings put secure accessibility_enabled 1
echo "installed, service enabled: $SVC"
