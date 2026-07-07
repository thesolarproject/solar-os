package com.solar.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-07-05 — Root read/write for hook module SharedPreferences XML (Xposed convention).
 * Layman: saves module toggles where the hook code expects to find them after reboot.
 * Technical: writes {@code /data/data/PKG/shared_prefs/PKG_preferences.xml} via shell.
 * Reversal: delete; inline config rows become no-ops without root.
 */
public final class XposedModuleConfigStore {

    private static final Pattern INT_VALUE = Pattern.compile(
            "<int\\s+name=\"([^\"]+)\"\\s+value=\"(-?\\d+)\"\\s*/>");

    private XposedModuleConfigStore() {}

    /** Read one boolean from module prefs — default when missing or unreadable. */
    public static boolean readBoolean(String pkg, String key, boolean defaultValue) {
        if (pkg == null || key == null) return defaultValue;
        String path = prefsPath(pkg);
        String xml = RootShell.runCapture("cat " + path + " 2>/dev/null");
        if (xml == null || xml.isEmpty()) return defaultValue;
        Matcher m = INT_VALUE.matcher(xml);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return !"0".equals(m.group(2));
            }
        }
        return defaultValue;
    }

    /** Persist one boolean — returns false without root. */
    public static boolean writeBoolean(String pkg, String key, boolean value) {
        if (pkg == null || key == null || !RootShell.canRun()) return false;
        String path = prefsPath(pkg);
        String dir = "/data/data/" + pkg + "/shared_prefs";
        String xml = RootShell.runCapture("cat " + path + " 2>/dev/null");
        String intLine = "    <int name=\"" + key + "\" value=\"" + (value ? "1" : "0") + "\" />";
        String body;
        if (xml == null || xml.isEmpty() || !xml.contains("<map>")) {
            body = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
                    + intLine + "\n</map>\n";
        } else if (xml.contains("name=\"" + key + "\"")) {
            body = xml.replaceAll(
                    "<int\\s+name=\"" + Pattern.quote(key) + "\"\\s+value=\"[^\"]*\"\\s*/>",
                    intLine.trim());
        } else {
            body = xml.replace("</map>", intLine + "\n</map>");
        }
        String sh = ""
                + "mkdir -p " + dir + "; "
                + "printf '%s' '" + body.replace("'", "'\\''") + "' > " + path + "; "
                + "chmod 660 " + path + "; "
                + "chown $(grep '^" + pkg + " ' /data/system/packages.list | awk '{print $2 "
                + "+ \":\" $3}') " + dir + " " + path + " 2>/dev/null || "
                + "chown $(grep '^" + pkg + " ' /data/system/packages.list | awk '{print $2}') "
                + dir + " " + path + " 2>/dev/null || true";
        return RootShell.run(sh);
    }

    /** Standard Xposed module prefs filename for package. */
    static String prefsPath(String pkg) {
        return "/data/data/" + pkg + "/shared_prefs/" + pkg + "_preferences.xml";
    }
}
