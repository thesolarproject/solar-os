package com.solar.launcher;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-07-06 — y2-mtk-flash-manifest.txt must match vendored MT6582 scatter offsets
 * (MTKclient wo / SP Flash flashing tools). Blocks drift before rom_y2.zip ships.
 */
public class Y2MtkFlashManifestTest {

    private static File repoFile(String relative) {
        File f = new File(relative);
        if (!f.isFile()) {
            f = new File("../" + relative);
        }
        if (!f.isFile()) {
            throw new AssertionError("missing file: " + relative);
        }
        return f;
    }

    private static Map<String, String> readManifest(File manifest) throws Exception {
        Map<String, String> out = new HashMap<String, String>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(manifest), "UTF-8"));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                out.put(line.substring(0, colon), line.substring(colon + 1).trim().toLowerCase());
            }
        } finally {
            br.close();
        }
        return out;
    }

    /** Map manifest filename to scatter partition_name block. */
    private static String scatterPartForFile(String fname) {
        if ("logo.bin".equals(fname)) return "LOGO";
        if ("lk.bin".equals(fname)) return "UBOOT";
        if ("boot.img".equals(fname)) return "BOOTIMG";
        if ("recovery.img".equals(fname)) return "RECOVERY";
        if ("system.img".equals(fname)) return "ANDROID";
        if ("userdata.img".equals(fname)) return "USRDATA";
        return null;
    }

    private static Map<String, String> parseScatterLinearAddrs(File scatter) throws Exception {
        Map<String, String> out = new HashMap<String, String>();
        String body;
        InputStream in = new FileInputStream(scatter);
        try {
            Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
            body = s.hasNext() ? s.next() : "";
        } finally {
            in.close();
        }
        Pattern partPat = Pattern.compile("partition_name:\\s*(\\S+)");
        Pattern addrPat = Pattern.compile("linear_start_addr:\\s*(0x[0-9a-fA-F]+)");
        String currentPart = null;
        for (String line : body.split("\n")) {
            Matcher pm = partPat.matcher(line);
            if (pm.find()) {
                currentPart = pm.group(1);
                continue;
            }
            if (currentPart == null) {
                continue;
            }
            Matcher am = addrPat.matcher(line);
            if (am.find()) {
                out.put(currentPart, am.group(1).toLowerCase());
                currentPart = null;
            }
        }
        return out;
    }

    @Test
    public void manifestMatchesVendoredMt6582Scatter() throws Exception {
        File manifest = repoFile("solar-rom/config/y2-mtk-flash-manifest.txt");
        File scatter = repoFile("solar-rom/vendor/y2-flash/MT6582_Android_scatter.txt");
        Map<String, String> manifestEntries = readManifest(manifest);
        Map<String, String> scatterAddrs = parseScatterLinearAddrs(scatter);
        if (manifestEntries.isEmpty()) {
            throw new AssertionError("y2-mtk-flash-manifest.txt has no entries");
        }
        for (Map.Entry<String, String> e : manifestEntries.entrySet()) {
            String part = scatterPartForFile(e.getKey());
            if (part == null) {
                throw new AssertionError("unknown manifest file: " + e.getKey());
            }
            String scatterAddr = scatterAddrs.get(part);
            if (scatterAddr == null) {
                throw new AssertionError("scatter missing partition " + part + " for " + e.getKey());
            }
            if (!scatterAddr.equals(e.getValue().toLowerCase())) {
                throw new AssertionError(
                        e.getKey() + ": manifest " + e.getValue() + " != scatter " + scatterAddr);
            }
        }
    }
}
