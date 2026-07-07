package com.solar.launcher.platform;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-05 — Runtime parser for assets/platform/manifest.json (single source of truth for prep).
 * APK/ROM parity: modules[].device y1/y2/both must match XposedModuleRegistry + ROM bake paths.
 * When changing: edit sync-platform-assets.sh generator, not hand-edit manifest.json in VCS.
 * Reversal: delete; ensurer uses hard-coded /system/app paths only again.
 */
public final class PlatformPrepManifest {

    /** One production hook APK row from manifest.json. */
    public static final class ModuleEntry {
        public final String pkg;
        public final String asset;
        public final String systemApk;
        public final boolean required;
        public final String device;
        public final int versionCode;
        public final String sha256;

        public ModuleEntry(String pkg, String asset, String systemApk, boolean required,
                String device, int versionCode, String sha256) {
            this.pkg = pkg;
            this.asset = asset;
            this.systemApk = systemApk;
            this.required = required;
            this.device = device;
            this.versionCode = versionCode;
            this.sha256 = sha256;
        }
    }

    /** Framework asset paths for one API vendor tree. */
    public static final class FrameworkVendor {
        public final String appProcess;
        public final String bridgeJar;
        public final String xposedProp;

        FrameworkVendor(String appProcess, String bridgeJar, String xposedProp) {
            this.appProcess = appProcess;
            this.bridgeJar = bridgeJar;
            this.xposedProp = xposedProp;
        }
    }

    /** Canonical /system targets for Xposed framework bake. */
    public static final class FrameworkSystemPaths {
        public final String appProcess;
        public final String appProcessOrig;
        public final String bridgeJarFramework;
        public final String bridgeJarSolar;
        public final String xposedProp;
        public final String installerApk;
        public final String initHook;

        FrameworkSystemPaths(String appProcess, String appProcessOrig, String bridgeJarFramework,
                String bridgeJarSolar, String xposedProp, String installerApk, String initHook) {
            this.appProcess = appProcess;
            this.appProcessOrig = appProcessOrig;
            this.bridgeJarFramework = bridgeJarFramework;
            this.bridgeJarSolar = bridgeJarSolar;
            this.xposedProp = xposedProp;
            this.installerApk = installerApk;
            this.initHook = initHook;
        }
    }

    /** Optional static file copy row (init hook, etc.). */
    public static final class FileEntry {
        public final String asset;
        public final String dest;
        public final String mode;

        FileEntry(String asset, String dest, String mode) {
            this.asset = asset;
            this.dest = dest;
            this.mode = mode;
        }
    }

    /** Rockbox APK row — pristine Y1 or manifest-patched Y2. */
    public static final class RockboxApkEntry {
        public final String asset;
        public final String systemApk;
        public final String device;
        public final int versionCode;
        public final String sha256;

        RockboxApkEntry(String asset, String systemApk, String device,
                int versionCode, String sha256) {
            this.asset = asset;
            this.systemApk = systemApk;
            this.device = device;
            this.versionCode = versionCode;
            this.sha256 = sha256;
        }
    }

    /** /system/lib/librockbox.so copy for Y2. */
    public static final class RockboxSystemLib {
        public final String asset;
        public final String dest;
        public final String device;
        public final String sha256;

        RockboxSystemLib(String asset, String dest, String device, String sha256) {
            this.asset = asset;
            this.dest = dest;
            this.device = device;
            this.sha256 = sha256;
        }
    }

    /** Bundled Rockbox install + staged native assets (platform prep). */
    public static final class RockboxConfig {
        public final String pkg;
        public final String stageIndex;
        public final RockboxApkEntry y1Apk;
        public final RockboxApkEntry y2Apk;
        public final RockboxSystemLib systemLib;

        RockboxConfig(String pkg, String stageIndex, RockboxApkEntry y1Apk,
                RockboxApkEntry y2Apk, RockboxSystemLib systemLib) {
            this.pkg = pkg;
            this.stageIndex = stageIndex;
            this.y1Apk = y1Apk;
            this.y2Apk = y2Apk;
            this.systemLib = systemLib;
        }

        /** APK entry for this device family. */
        public RockboxApkEntry apkForDevice(boolean y2) {
            return y2 ? y2Apk : y1Apk;
        }
    }

    public final int prepVersion;
    public final FrameworkVendor api17;
    public final FrameworkVendor api19;
    public final String installerAsset;
    public final String initHookAsset;
    public final FrameworkSystemPaths systemPaths;
    public final List<ModuleEntry> modules;
    public final List<FileEntry> files;
    public final List<PlatformDeprecationCleaner.DeprecatedEntry> deprecated;
    public final RockboxConfig rockbox;

    private PlatformPrepManifest(int prepVersion, FrameworkVendor api17, FrameworkVendor api19,
            String installerAsset, String initHookAsset, FrameworkSystemPaths systemPaths,
            List<ModuleEntry> modules, List<FileEntry> files,
            List<PlatformDeprecationCleaner.DeprecatedEntry> deprecated,
            RockboxConfig rockbox) {
        this.prepVersion = prepVersion;
        this.api17 = api17;
        this.api19 = api19;
        this.installerAsset = installerAsset;
        this.initHookAsset = initHookAsset;
        this.systemPaths = systemPaths;
        this.modules = modules;
        this.files = files;
        this.deprecated = deprecated;
        this.rockbox = rockbox;
    }

    /** Load manifest from APK assets (platform/manifest.json). */
    public static PlatformPrepManifest load(Context ctx) throws Exception {
        InputStream in = ctx.getAssets().open("platform/manifest.json");
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return parse(sb.toString());
        } finally {
            in.close();
        }
    }

    /** Parse manifest JSON — unit-test entry without Context. */
    public static PlatformPrepManifest parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int prepVersion = root.optInt("prepVersion", 0);
        JSONObject fw = root.getJSONObject("framework");
        FrameworkVendor api17 = parseVendor(fw.getJSONObject("api17"));
        FrameworkVendor api19 = parseVendor(fw.getJSONObject("api19"));
        String installerAsset = fw.getString("installerApk");
        String initHookAsset = fw.getString("initHook");
        JSONObject sp = fw.getJSONObject("systemPaths");
        FrameworkSystemPaths systemPaths = new FrameworkSystemPaths(
                sp.getString("appProcess"),
                sp.getString("appProcessOrig"),
                sp.getString("bridgeJarFramework"),
                sp.getString("bridgeJarSolar"),
                sp.getString("xposedProp"),
                sp.getString("installerApk"),
                sp.getString("initHook"));

        List<ModuleEntry> modules = new ArrayList<ModuleEntry>();
        JSONArray modArr = root.getJSONArray("modules");
        for (int i = 0; i < modArr.length(); i++) {
            JSONObject m = modArr.getJSONObject(i);
            modules.add(new ModuleEntry(
                    m.getString("pkg"),
                    m.getString("asset"),
                    m.getString("systemApk"),
                    m.optBoolean("required", false),
                    m.optString("device", "both"),
                    m.optInt("versionCode", 1),
                    m.optString("sha256", "")));
        }

        List<FileEntry> files = new ArrayList<FileEntry>();
        if (root.has("files")) {
            JSONArray fileArr = root.getJSONArray("files");
            for (int i = 0; i < fileArr.length(); i++) {
                JSONObject f = fileArr.getJSONObject(i);
                files.add(new FileEntry(
                        f.getString("asset"),
                        f.getString("dest"),
                        f.optString("mode", "644")));
            }
        }

        List<PlatformDeprecationCleaner.DeprecatedEntry> deprecated =
                new ArrayList<PlatformDeprecationCleaner.DeprecatedEntry>();
        if (root.has("deprecated")) {
            JSONArray depArr = root.getJSONArray("deprecated");
            for (int i = 0; i < depArr.length(); i++) {
                JSONObject d = depArr.getJSONObject(i);
                deprecated.add(new PlatformDeprecationCleaner.DeprecatedEntry(
                        d.optString("systemApk", null),
                        d.optString("pkg", null),
                        d.optString("device", "both"),
                        d.optString("reason", "")));
            }
        }

        RockboxConfig rockbox = null;
        if (root.has("rockbox")) {
            rockbox = parseRockbox(root.getJSONObject("rockbox"));
        }

        return new PlatformPrepManifest(prepVersion, api17, api19, installerAsset, initHookAsset,
                systemPaths, modules, files, deprecated, rockbox);
    }

    private static RockboxApkEntry parseRockboxApk(JSONObject o) throws Exception {
        return new RockboxApkEntry(
                o.getString("asset"),
                o.getString("systemApk"),
                o.optString("device", "both"),
                o.optInt("versionCode", 1),
                o.optString("sha256", ""));
    }

    private static RockboxConfig parseRockbox(JSONObject o) throws Exception {
        RockboxApkEntry y1 = o.has("y1Apk") ? parseRockboxApk(o.getJSONObject("y1Apk")) : null;
        RockboxApkEntry y2 = o.has("y2Apk") ? parseRockboxApk(o.getJSONObject("y2Apk")) : null;
        RockboxSystemLib sysLib = null;
        if (o.has("systemLib")) {
            JSONObject sl = o.getJSONObject("systemLib");
            sysLib = new RockboxSystemLib(
                    sl.getString("asset"),
                    sl.getString("dest"),
                    sl.optString("device", "y2"),
                    sl.optString("sha256", ""));
        }
        return new RockboxConfig(
                o.optString("pkg", "org.rockbox"),
                o.optString("stageIndex", ""),
                y1, y2, sysLib);
    }

    private static FrameworkVendor parseVendor(JSONObject o) throws Exception {
        return new FrameworkVendor(
                o.getString("appProcess"),
                o.getString("bridgeJar"),
                o.getString("xposedProp"));
    }

    /** Vendor tree for this device — Y1/JB uses api17, Y2/KK uses api19. */
    public FrameworkVendor vendorForDevice(boolean y2) {
        return y2 ? api19 : api17;
    }

    /** Modules that apply to this device family and are marked required. */
    public List<ModuleEntry> requiredModulesForDevice(boolean y2) {
        List<ModuleEntry> out = new ArrayList<ModuleEntry>();
        for (ModuleEntry m : modules) {
            if (!m.required) continue;
            if (!moduleAppliesToDevice(m, y2)) continue;
            out.add(m);
        }
        return out;
    }

    /** Deprecated artifacts to remove on this device family. */
    public List<PlatformDeprecationCleaner.DeprecatedEntry> deprecatedForDevice(boolean y2) {
        List<PlatformDeprecationCleaner.DeprecatedEntry> out =
                new ArrayList<PlatformDeprecationCleaner.DeprecatedEntry>();
        if (deprecated == null) return out;
        for (PlatformDeprecationCleaner.DeprecatedEntry d : deprecated) {
            if (moduleAppliesToDeviceTag(d.device, y2)) out.add(d);
        }
        return out;
    }

    static boolean moduleAppliesToDeviceTag(String device, boolean y2) {
        if (device == null || "both".equalsIgnoreCase(device)) return true;
        if (y2) return "y2".equalsIgnoreCase(device);
        return "y1".equalsIgnoreCase(device);
    }

    /** True when manifest device tag matches Y1 or Y2. */
    public static boolean moduleAppliesToDevice(ModuleEntry m, boolean y2) {
        if (m == null) return false;
        if ("both".equalsIgnoreCase(m.device)) return true;
        if (y2) return "y2".equalsIgnoreCase(m.device);
        return "y1".equalsIgnoreCase(m.device);
    }
}
