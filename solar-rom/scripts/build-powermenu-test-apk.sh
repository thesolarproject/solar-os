#!/usr/bin/env bash
# Build the Y2 power-menu test Xposed module APK.
# Dalvik-era XposedBridge.jar ships only a runtime dex (no javac stubs), so we
# generate minimal compile-time API stubs here — used for the javac classpath only,
# never bundled (the framework supplies the real de.robv.* classes at runtime).
# Output: solar-rom/vendor/xposed/solar-powermenu-test/PowerMenuTest.apk
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$SCRIPT_DIR/../vendor/xposed/solar-powermenu-test"
BUILD_DIR="$MOD_DIR/build"
OUT_APK="$MOD_DIR/PowerMenuTest.apk"

die() { echo "build-powermenu-test-apk: $*" >&2; exit 1; }

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT=""
for v in 35.0.0 34.0.0 36.0.0 28.0.3; do
    [ -x "$ANDROID_HOME/build-tools/$v/aapt" ] && BT="$ANDROID_HOME/build-tools/$v" && break
done
[ -n "$BT" ] || die "no usable build-tools with aapt under $ANDROID_HOME/build-tools"

# KitKat platform for compile/link; fall back to highest installed.
PLATFORM=""
for p in android-19 android-34 android-35; do
    [ -f "$ANDROID_HOME/platforms/$p/android.jar" ] && PLATFORM="$ANDROID_HOME/platforms/$p/android.jar" && break
done
[ -n "$PLATFORM" ] || die "install platforms;android-19 via sdkmanager"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/stubs" "$BUILD_DIR/stubsrc" "$BUILD_DIR/bin"

echo "==> generate Xposed API compile stubs (classpath only, not shipped)"
mkdir -p "$BUILD_DIR/stubsrc/de/robv/android/xposed/callbacks"
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/XC_MethodHook.java" <<'EOF'
package de.robv.android.xposed;
public abstract class XC_MethodHook {
    public static class Unhook {}
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public void setResult(Object result) {}
        public Object getResult() { return null; }
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
EOF
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/XC_MethodReplacement.java" <<'EOF'
package de.robv.android.xposed;
public abstract class XC_MethodReplacement extends XC_MethodHook {
    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}
EOF
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/XposedBridge.java" <<'EOF'
package de.robv.android.xposed;
public final class XposedBridge {
    public static void log(String text) {}
    public static void log(Throwable t) {}
    public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Member hookMethod, XC_MethodHook callback) { return null; }
    public static void hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {}
}
EOF
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/XposedHelpers.java" <<'EOF'
package de.robv.android.xposed;
public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader cl) { return null; }
    public static Object findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
}
EOF
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/callbacks/XC_LoadPackage.java" <<'EOF'
package de.robv.android.xposed.callbacks;
public abstract class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
    }
}
EOF
cat > "$BUILD_DIR/stubsrc/de/robv/android/xposed/IXposedHookLoadPackage.java" <<'EOF'
package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
EOF

JAVAC=$(command -v javac) || die "missing javac"
mapfile -t STUB_FILES < <(find "$BUILD_DIR/stubsrc" -name '*.java')
"$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM" \
    -d "$BUILD_DIR/stubs" "${STUB_FILES[@]}" 2>"$BUILD_DIR/javac-stubs.log" \
    || { cat "$BUILD_DIR/javac-stubs.log"; die "stub javac failed"; }

echo "==> aapt package resources (platform $(basename "$(dirname "$PLATFORM")"))"
"$BT/aapt" package -f -M "$MOD_DIR/AndroidManifest.xml" \
    -A "$MOD_DIR/assets" \
    -I "$PLATFORM" \
    -F "$BUILD_DIR/bin/resources.ap_"

echo "==> javac module sources (against stubs)"
mapfile -t JAVA_FILES < <(find "$MOD_DIR/src" -name '*.java')
"$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM" \
    -classpath "$BUILD_DIR/stubs" \
    -d "$BUILD_DIR/classes" "${JAVA_FILES[@]}" 2>"$BUILD_DIR/javac.log" \
    || { cat "$BUILD_DIR/javac.log"; die "javac failed"; }

echo "==> d8 -> classes.dex (XposedBridge is provided by framework, not bundled)"
jar cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
jar cf "$BUILD_DIR/stubs.jar" -C "$BUILD_DIR/stubs" .
if [ -x "$BT/d8" ]; then
    "$BT/d8" --min-api 19 --lib "$PLATFORM" --classpath "$BUILD_DIR/stubs.jar" \
        --output "$BUILD_DIR/bin" "$BUILD_DIR/classes.jar" \
        2>"$BUILD_DIR/d8.log" || { cat "$BUILD_DIR/d8.log"; die "d8 failed"; }
elif [ -f "$BT/dx" ]; then
    "$BT/dx" --dex --output="$BUILD_DIR/bin/classes.dex" "$BUILD_DIR/classes" \
        2>"$BUILD_DIR/dx.log" || { cat "$BUILD_DIR/dx.log"; die "dx failed"; }
else
    die "missing d8/dx in $BT"
fi
[ -f "$BUILD_DIR/bin/classes.dex" ] || die "classes.dex not produced"

echo "==> assemble + sign APK"
cp "$BUILD_DIR/bin/resources.ap_" "$BUILD_DIR/bin/unsigned.apk"
(cd "$BUILD_DIR/bin" && "$BT/aapt" add unsigned.apk classes.dex >/dev/null)

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
