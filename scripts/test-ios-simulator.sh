#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEVICE=${1:?Usage: sh scripts/test-ios-simulator.sh SIMULATOR_UDID}
case "$DEVICE" in
  *[!a-zA-Z0-9-]*) echo "Invalid simulator UDID" >&2; exit 2 ;;
esac
RESULT="$ROOT/build/ios/xctest-$DEVICE-$(date +%Y%m%d-%H%M%S).xcresult"
xcodegen generate --spec "$ROOT/iosApp/project.yml"
xcrun simctl bootstatus "$DEVICE" -b
# An unsigned process has no Keychain identity. The local simulator identity is
# selected only for iphonesimulator; device provisioning is a separate workflow.
xcodebuild -project "$ROOT/iosApp/XJTUToolbox.xcodeproj" -scheme XJTUToolbox \
  -configuration Debug -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=$DEVICE" \
  -derivedDataPath "$ROOT/build/ios/DerivedData" -resultBundlePath "$RESULT" \
  -collect-test-diagnostics never \
  CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY=- CODE_SIGNING_ALLOWED=YES CODE_SIGNING_REQUIRED=YES test
printf 'Simulator test result: %s\n' "$RESULT"
