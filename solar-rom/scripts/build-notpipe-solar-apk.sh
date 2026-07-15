#!/usr/bin/env bash
# 2026-07-14 — Rebuild Solarized notPipe 0.3.0 with SolarWakeService for headless IPC.
# Layman: bake an invisible keep-alive into the YouTube helper APK Solar ships.
# Technical: apktool inject Service + resign; copies to assets/thirdparty + reference.
# Reversal: restore upstream notPipe-0.3.0-release.upstream.apk (no WakeService).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ASSET="$ROOT/app/src/main/assets/platform/thirdparty/notPipe-0.3.0-release.apk"
REF="$ROOT/reference/NotPipe reference/notPipe-0.3.0-release.apk"
UPSTREAM="$ROOT/reference/NotPipe reference/notPipe-0.3.0-release.upstream.apk"
APKTOOL="${APKTOOL:-/home/deck/Documents/Rocksayr/tools/apktool-2.9.3.jar}"
KS="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
WORKDIR="${TMPDIR:-/tmp}/notpipe-solar-build-$$"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ZIPALIGN="$(ls "$ANDROID_HOME"/build-tools/*/zipalign 2>/dev/null | tail -1)"
APKSIGNER="$(ls "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | tail -1)"

die() { echo "$*" >&2; exit 1; }
[ -f "$APKTOOL" ] || die "missing apktool: $APKTOOL"
[ -n "$ZIPALIGN" ] && [ -n "$APKSIGNER" ] || die "missing zipalign/apksigner under $ANDROID_HOME"

# Prefer pristine upstream; snapshot current asset once if it is not already solarized.
if [ ! -f "$UPSTREAM" ] && [ -f "$ASSET" ]; then
  if ! python3 -c "import zipfile,sys;z=zipfile.ZipFile(sys.argv[1]);sys.exit(0 if b'SolarWakeService' in z.read('classes.dex') else 1)" "$ASSET"; then
    cp -f "$ASSET" "$UPSTREAM"
  fi
fi
BASE="$UPSTREAM"
[ -f "$BASE" ] || BASE="$ASSET"
[ -f "$BASE" ] || die "missing base notPipe APK"

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
java -jar "$APKTOOL" d -f -o "$WORKDIR/apk" "$BASE"

mkdir -p "$WORKDIR/apk/smali/io/github/gohoski/notpipe"
cat > "$WORKDIR/apk/smali/io/github/gohoski/notpipe/SolarWakeService.smali" <<'SMALI'
.class public Lio/github/gohoski/notpipe/SolarWakeService;
.super Landroid/app/Service;
.source "SolarWakeService.java"

# 2026-07-14 — Sticky service so Solar IPC wake keeps notPipe process alive.
.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Service;-><init>()V
    return-void
.end method

.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public onStartCommand(Landroid/content/Intent;II)I
    .locals 1
    const/4 v0, 0x1
    return v0
.end method
SMALI

# 2026-07-14 — Exported CMD receiver so API 17 am broadcast -n / Intent.setComponent works.
# Layman: a named mailbox for Solar YouTube commands (older Android has no am -p).
# Xposed bridge hooks onReceive; stub alone is a no-op.
cat > "$WORKDIR/apk/smali/io/github/gohoski/notpipe/SolarCmdReceiver.smali" <<'SMALI'
.class public Lio/github/gohoski/notpipe/SolarCmdReceiver;
.super Landroid/content/BroadcastReceiver;
.source "SolarCmdReceiver.java"

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V
    return-void
.end method

.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 0
    return-void
.end method
SMALI

python3 - <<PY
from pathlib import Path
p = Path("$WORKDIR/apk/AndroidManifest.xml")
t = p.read_text()
bits = []
if "SolarWakeService" not in t:
    bits.append('        <service android:exported="true" android:name="io.github.gohoski.notpipe.SolarWakeService"/>\n')
if "SolarCmdReceiver" not in t:
    bits.append(
        '        <receiver android:exported="true" android:name="io.github.gohoski.notpipe.SolarCmdReceiver">\n'
        '            <intent-filter>\n'
        '                <action android:name="com.solar.launcher.NOTPIPE_CMD"/>\n'
        '            </intent-filter>\n'
        '        </receiver>\n'
    )
if bits:
    t = t.replace("    </application>", "".join(bits) + "    </application>")
    p.write_text(t)
print("manifest patched")
PY

sed -i 's/versionCode: .*/versionCode: 9/' "$WORKDIR/apk/apktool.yml"
sed -i 's/versionName: .*/versionName: 0.3.0-solar/' "$WORKDIR/apk/apktool.yml"

java -jar "$APKTOOL" b -f -o "$WORKDIR/unsigned.apk" "$WORKDIR/apk"
"$ZIPALIGN" -f 4 "$WORKDIR/unsigned.apk" "$WORKDIR/aligned.apk"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkey -v -keystore "$KS" -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US"
fi
"$APKSIGNER" sign --ks "$KS" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$WORKDIR/notPipe-0.3.0-solar.apk" "$WORKDIR/aligned.apk"
"$APKSIGNER" verify "$WORKDIR/notPipe-0.3.0-solar.apk"

cp -f "$WORKDIR/notPipe-0.3.0-solar.apk" "$ASSET"
cp -f "$WORKDIR/notPipe-0.3.0-solar.apk" "$REF"
echo "==> $WORKDIR/notPipe-0.3.0-solar.apk"
echo "==> copied to assets/thirdparty + reference"
rm -rf "$WORKDIR"
