#!/usr/bin/env bash
# Build XposedInstaller 2.7 (bin v58) APK for /system/app from rovo89 sources.
# Output: solar-rom/vendor/xposed/XposedInstaller.apk
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/xposed"
BUILD_DIR="$VENDOR/installer-build"
OUT_APK="$VENDOR/XposedInstaller.apk"
COMMIT="4cd1038"  # bin v58 — last 2.x with Dalvik assets
SRC_CACHE="${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/xposed-installer-src"

die() { echo "build-xposed-installer-apk: $*" >&2; exit 1; }

command -v git >/dev/null || die "missing git"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="${ANDROID_HOME}/build-tools/35.0.0"
[ -x "$BT/aapt" ] || die "missing $BT/aapt (install Android SDK build-tools 35.0.0)"

mkdir -p "$SRC_CACHE"
if [ ! -d "$SRC_CACHE/.git" ]; then
    git clone --depth 1 https://github.com/rovo89/XposedInstaller.git "$SRC_CACHE"
fi
git -C "$SRC_CACHE" fetch --depth 1 origin "$COMMIT" 2>/dev/null || git -C "$SRC_CACHE" fetch --unshallow 2>/dev/null || true
git -C "$SRC_CACHE" checkout "$COMMIT" 2>/dev/null || git -C "$SRC_CACHE" checkout "4cd1038" 2>/dev/null \
    || die "cannot checkout XposedInstaller $COMMIT"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/bin"
cp -a "$SRC_CACHE"/{AndroidManifest.xml,res,assets,src,libs,lib} "$BUILD_DIR/"
cp "$SRC_CACHE/project.properties" "$BUILD_DIR/" 2>/dev/null || true

# Solar Y1/Y2 overrides (wheel toggle, list padding, libsuperuser-in-dex build is separate).
INSTALLER_OVERRIDES="$SCRIPT_DIR/../patches/xposed/installer-overrides"
if [ -d "$INSTALLER_OVERRIDES" ]; then
    cp -a "$INSTALLER_OVERRIDES"/. "$BUILD_DIR/"
    echo "==> applied Solar Xposed Installer overrides"
fi

# Platform android.jar for API 19 (KitKat) — fallback to highest installed.
PLATFORM=""
for p in "$ANDROID_HOME/platforms/android-19/android.jar" \
         "$ANDROID_HOME/platforms/android-21/android.jar" \
         "$ANDROID_HOME/platforms/android-34/android.jar"; do
    [ -f "$p" ] && PLATFORM="$p" && break
done
[ -n "$PLATFORM" ] || die "install platforms;android-19 via sdkmanager"

echo "==> aapt package (platform $(basename "$(dirname "$PLATFORM")"))"
"$BT/aapt" package -f -M "$BUILD_DIR/AndroidManifest.xml" \
    -S "$BUILD_DIR/res" -A "$BUILD_DIR/assets" \
    -I "$PLATFORM" \
    -m -J "$BUILD_DIR/gen" \
    -F "$BUILD_DIR/bin/resources.ap_" \
    --auto-add-overlay

# Compile Java sources (API 19 language level).
JAVAC=$(command -v javac)
ANDROID_JAR="$PLATFORM"
CLASSPATH="$BUILD_DIR/libs/android-support-v13.jar:$BUILD_DIR/libs/libsuperuser-185868.jar:$BUILD_DIR/lib/AndroidHiddenAPI.jar:$BUILD_DIR/libs/StickyListHeaders-d7f6fc.jar:$ANDROID_JAR"

mapfile -t JAVA_FILES < <(find "$BUILD_DIR/src" -name '*.java')
mapfile -t GEN_FILES < <(find "$BUILD_DIR/gen" -name 'R.java' 2>/dev/null || true)
mkdir -p "$BUILD_DIR/classes"
echo "==> javac ${#JAVA_FILES[@]} sources + R.java"
"$JAVAC" -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$CLASSPATH" \
    -d "$BUILD_DIR/classes" "${GEN_FILES[@]}" "${JAVA_FILES[@]}" 2>"$BUILD_DIR/javac.log" \
    || { tail -30 "$BUILD_DIR/javac.log"; die "javac failed"; }

# d8/dx — convert app classes + bundled libs into one classes.dex.
jar cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
DEX_INPUT=(
    "$BUILD_DIR/classes.jar"
    "$BUILD_DIR/libs/android-support-v13.jar"
    "$BUILD_DIR/libs/libsuperuser-185868.jar"
    "$BUILD_DIR/libs/StickyListHeaders-d7f6fc.jar"
    "$BUILD_DIR/lib/AndroidHiddenAPI.jar"
)
if [ -x "$BT/d8" ]; then
    "$BT/d8" --lib "$ANDROID_JAR" --output "$BUILD_DIR/bin" "${DEX_INPUT[@]}" \
        2>"$BUILD_DIR/d8.log" \
        || { tail -20 "$BUILD_DIR/d8.log"; die "d8 failed"; }
elif [ -f "$BT/dx" ] || [ -f "$BT/lib/dx.jar" ]; then
    "$BT/dx" --dex --output="$BUILD_DIR/bin/classes.dex" "$BUILD_DIR/classes" "${DEX_INPUT[@]}" \
        2>"$BUILD_DIR/dx.log" || { tail -20 "$BUILD_DIR/dx.log"; die "dx failed"; }
else
    die "missing d8/dx in $BT"
fi
[ -f "$BUILD_DIR/bin/classes.dex" ] || die "classes.dex not produced"

# Sanity: libsuperuser must be in dex or InstallerFragment crashes (RootUtil$1 NoClassDefFoundError).
"$BT/dexdump" "$BUILD_DIR/bin/classes.dex" >"$BUILD_DIR/dexdump.txt" 2>&1 \
    || die "dexdump failed on classes.dex"
grep -q 'chainfire/libsuperuser/Shell' "$BUILD_DIR/dexdump.txt" \
    || die "classes.dex missing libsuperuser — dependency jars not merged"

echo "==> aapt add classes.dex + build unsigned APK"
cp "$BUILD_DIR/bin/resources.ap_" "$BUILD_DIR/bin/unsigned.apk"
(cd "$BUILD_DIR/bin" && "$BT/aapt" add unsigned.apk classes.dex >/dev/null)

# Sign with AOSP test key if available, else debug keystore.
KEYSTORE="${SOLAR_PLATFORM_KEYSTORE:-$BUILD_DIR/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT_APK" "$BUILD_DIR/bin/unsigned.apk" 2>/dev/null \
    || "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --out "$OUT_APK" "$BUILD_DIR/bin/unsigned.apk"

echo "==> $OUT_APK ($(wc -c < "$OUT_APK") bytes)"
