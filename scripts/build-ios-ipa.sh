#!/bin/sh
# Build a genuine device-arm64 app. Unsigned IPA is for local SideStore re-signing only.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
MODE=${1:-unsigned}
OUT=${IOS_BUILD_DIR:-"$ROOT/build/ios"}
case "$MODE" in
    unsigned|signed) ;;
    *) echo "Usage: $0 [unsigned|signed]" >&2; exit 2 ;;
esac
mkdir -p "$OUT"
if [ "$MODE" = signed ]; then
    : "${DEVELOPMENT_TEAM:?Set DEVELOPMENT_TEAM to your local Apple team identifier}"
    xcodebuild -project "$ROOT/iosApp/XJTUToolbox.xcodeproj" -scheme XJTUToolbox \
        -configuration Release -destination 'generic/platform=iOS' \
        -archivePath "$OUT/XJTUToolbox.xcarchive" -derivedDataPath "$OUT/DerivedData" \
        "DEVELOPMENT_TEAM=$DEVELOPMENT_TEAM" archive
    : "${EXPORT_OPTIONS_PLIST:?Set EXPORT_OPTIONS_PLIST to your local export options plist}"
    xcodebuild -exportArchive -archivePath "$OUT/XJTUToolbox.xcarchive" \
        -exportPath "$OUT/signed" -exportOptionsPlist "$EXPORT_OPTIONS_PLIST"
    echo "Signed export: $OUT/signed"
else
    xcodebuild -project "$ROOT/iosApp/XJTUToolbox.xcodeproj" -scheme XJTUToolbox \
        -configuration Release -destination 'generic/platform=iOS' \
        -derivedDataPath "$OUT/DerivedData" "CONFIGURATION_BUILD_DIR=$OUT/device" \
        CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
    APP="$OUT/device/XJTUToolbox.app"
    test -f "$APP/XJTUToolbox"
    xcrun vtool -show-build "$APP/XJTUToolbox" | tee "$OUT/device-platform.txt"
    # vtool distinguishes an arm64 simulator executable from an arm64 device executable.
    python3 - "$APP" "$OUT/XJTUToolbox-unsigned.ipa" <<'PY'
import pathlib, plistlib, subprocess, sys, zipfile
app, output = map(pathlib.Path, sys.argv[1:])
info = plistlib.loads((app / 'Info.plist').read_bytes())
executable = app / info['CFBundleExecutable']
assert subprocess.check_output(['xcrun', 'lipo', '-archs', str(executable)], text=True).strip() == 'arm64'
build = subprocess.check_output(['xcrun', 'vtool', '-show-build', str(executable)], text=True)
assert 'platform IOS\n' in build and 'IOSSIMULATOR' not in build, 'Not a device iOS app'
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(app.rglob('*')):
        if path.is_file():
            archive.write(path, pathlib.Path('Payload') / app.name / path.relative_to(app))
print(f'Unsigned device IPA (must re-sign in SideStore): {output}')
PY
fi
