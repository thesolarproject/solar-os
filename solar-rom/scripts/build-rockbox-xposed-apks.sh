#!/usr/bin/env bash
# Build Solar Rockbox Xposed modules — IME (Y1+Y2) and Y2 compat APKs.
# Output:
#   solar-rom/vendor/xposed/solar-rockbox-ime/SolarRockboxIme.apk
#   solar-rom/vendor/xposed/solar-rockbox-compat/SolarRockboxCompat.apk
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/xposed"

die() { echo "build-rockbox-xposed-apks: $*" >&2; exit 1; }

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT=""
for v in 35.0.0 34.0.0 36.0.0 28.0.3; do
    [ -x "$ANDROID_HOME/build-tools/$v/aapt" ] && BT="$ANDROID_HOME/build-tools/$v" && break
done
[ -n "$BT" ] || die "no usable build-tools with aapt"

PLATFORM=""
for p in android-19 android-17 android-34; do
    [ -f "$ANDROID_HOME/platforms/$p/android.jar" ] && PLATFORM="$ANDROID_HOME/platforms/$p/android.jar" && break
done
[ -n "$PLATFORM" ] || die "install platforms;android-17 via sdkmanager"

JAVAC=$(command -v javac) || die "missing javac"

gen_stubs() {
    local stubdir="$1"
    mkdir -p "$stubdir/de/robv/android/xposed/callbacks"
    cat > "$stubdir/de/robv/android/xposed/XC_MethodHook.java" <<'EOF'
package de.robv.android.xposed;
public abstract class XC_MethodHook {
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
    cat > "$stubdir/de/robv/android/xposed/XC_MethodReplacement.java" <<'EOF'
package de.robv.android.xposed;
public abstract class XC_MethodReplacement extends XC_MethodHook {
    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}
EOF
    cat > "$stubdir/de/robv/android/xposed/XposedBridge.java" <<'EOF'
package de.robv.android.xposed;
public final class XposedBridge {
    public static void log(String text) {}
    public static void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {}
}
EOF
    cat > "$stubdir/de/robv/android/xposed/XposedHelpers.java" <<'EOF'
package de.robv.android.xposed;
public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader cl) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
}
EOF
    cat > "$stubdir/de/robv/android/xposed/callbacks/XC_LoadPackage.java" <<'EOF'
package de.robv.android.xposed.callbacks;
public abstract class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
    }
}
EOF
    cat > "$stubdir/de/robv/android/xposed/IXposedHookLoadPackage.java" <<'EOF'
package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
EOF
    mkdir -p "$stubdir/out"
    mapfile -t STUB_JAVA < <(find "$stubdir" -name '*.java' ! -path '*/out/*')
    "$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM" -d "$stubdir/out" \
        "${STUB_JAVA[@]}" || die "stub javac failed"
    jar cf "$stubdir/stubs.jar" -C "$stubdir/out" .
}

STUB_ROOT="$VENDOR/build/xposed-stubs-rockbox"
gen_stubs "$STUB_ROOT"
XPOSED_CP="$STUB_ROOT/stubs.jar"

build_one() {
    local mod_dir="$1" out_apk="$2" label="$3"
    local build="$mod_dir/build"
    rm -rf "$build"
    mkdir -p "$build/classes" "$build/bin" "$build/assets"
    cp "$mod_dir/AndroidManifest.xml" "$build/AndroidManifest.xml"
    cp "$mod_dir/assets/xposed_init" "$build/assets/xposed_init"

    echo "==> aapt package ($label)"
    "$BT/aapt" package -f -M "$build/AndroidManifest.xml" -A "$build/assets" -I "$PLATFORM" -F "$build/resources.ap_"

    echo "==> javac ($label)"
    mapfile -t JAVA_FILES < <(find "$mod_dir/src" -name '*.java')
    "$JAVAC" -source 8 -target 8 -bootclasspath "$PLATFORM" \
        -classpath "$XPOSED_CP" \
        -d "$build/classes" "${JAVA_FILES[@]}" 2>"$build/javac.log" \
        || { cat "$build/javac.log"; die "javac failed ($label)"; }

    jar cf "$build/classes.jar" -C "$build/classes" .
    "$BT/d8" --min-api 17 --lib "$PLATFORM" --classpath "$XPOSED_CP" \
        --output "$build/bin" "$build/classes.jar" 2>"$build/d8.log" \
        || { cat "$build/d8.log"; die "d8 failed ($label)"; }
    [ -f "$build/bin/classes.dex" ] || die "classes.dex missing ($label)"

    cp "$build/resources.ap_" "$build/unsigned.apk"
    (cd "$build/bin" && "$BT/aapt" add "$build/unsigned.apk" classes.dex >/dev/null)

    KEYSTORE="${SOLAR_PLATFORM_KEYSTORE:-$mod_dir/build/debug.keystore}"
    if [ ! -f "$KEYSTORE" ]; then
        keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
            -alias androiddebugkey -keyalg RSA -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
    fi
    "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
        --out "$out_apk" "$build/unsigned.apk" 2>/dev/null \
        || "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --out "$out_apk" "$build/unsigned.apk"
    echo "==> $out_apk ($(wc -c < "$out_apk") bytes)"
}

build_one "$VENDOR/solar-rockbox-ime" "$VENDOR/solar-rockbox-ime/SolarRockboxIme.apk" "SolarRockboxIme"
build_one "$VENDOR/solar-rockbox-compat" "$VENDOR/solar-rockbox-compat/SolarRockboxCompat.apk" "SolarRockboxCompat"
