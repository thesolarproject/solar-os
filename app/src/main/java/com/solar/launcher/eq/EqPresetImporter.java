package com.solar.launcher.eq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-07-15 — Parse Rockbox EQ .cfg and AutoEQ FixedBandEQ.txt into EqBandModel.
 * Layman: read headphone preset files from the card into Solar’s ten sliders.
 * Reversal: drop importer; manual band edit remains.
 */
public final class EqPresetImporter {

    private static final Pattern ROCKBOX_FILTER = Pattern.compile(
            "eq (low shelf filter|peak filter \\d+|high shelf filter):\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(-?\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROCKBOX_BAND_CUTOFF = Pattern.compile(
            "eq band (\\d+) cutoff:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROCKBOX_BAND_Q = Pattern.compile(
            "eq band (\\d+) q:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROCKBOX_BAND_GAIN = Pattern.compile(
            "eq band (\\d+) gain:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROCKBOX_PRECUT = Pattern.compile(
            "eq precut:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIXED_PREAMP = Pattern.compile(
            "Preamp:\\s*(-?[\\d.]+)\\s*dB", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIXED_FILTER = Pattern.compile(
            "Filter\\s+\\d+:\\s*ON\\s+PK\\s+Fc\\s+([\\d.]+)\\s*Hz\\s+Gain\\s+(-?[\\d.]+)\\s*dB",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRAPHIC_EQ = Pattern.compile(
            "GraphicEQ:\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private EqPresetImporter() {}

    /** Result of a successful parse. */
    public static final class ParsedPreset {
        public final String name;
        public final EqBandModel model;

        public ParsedPreset(String name, EqBandModel model) {
            this.name = name;
            this.model = model;
        }
    }

    /** Detect format from filename/content and parse. */
    public static ParsedPreset parseFile(File file) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalArgumentException("missing file");
        String text = readUtf8(file);
        String name = stripExt(file.getName());
        if (text.contains("eq low shelf filter") || text.contains("eq peak filter")
                || text.contains("eq band 0 cutoff") || text.contains("eq band 0 gain")) {
            return new ParsedPreset(name, parseRockboxCfg(text));
        }
        if (text.contains("Preamp:") || text.contains("Filter 1:") || text.contains("Filter 1 :")) {
            return new ParsedPreset(name, parseFixedBandEq(text));
        }
        if (text.toLowerCase(Locale.US).contains("graphiceq:")) {
            return new ParsedPreset(name, parseGraphicEq(text));
        }
        throw new IllegalArgumentException("unrecognised EQ file");
    }

    /**
     * Rockbox modern/legacy CFG — tenths for gain/precut; sample peaks at graphic centres.
     * Modern lines: eq peak filter N: Fc, Q×10, gain×10.
     */
    public static EqBandModel parseRockboxCfg(String text) {
        EqBandModel model = new EqBandModel();
        model.resetFlat();
        List<Peak> peaks = new ArrayList<Peak>();
        String[] lines = text.split("\n");
        float[] legacyCutoff = new float[10];
        float[] legacyGain = new float[10];
        boolean[] legacyHas = new boolean[10];
        ArraysFill(legacyCutoff, 0f);
        ArraysFill(legacyGain, 0f);

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Matcher precut = ROCKBOX_PRECUT.matcher(line);
            if (precut.find()) {
                // Rockbox precut is positive attenuation in tenths of dB.
                model.setPreampDb(-(Integer.parseInt(precut.group(1)) / 10f));
                continue;
            }
            Matcher mf = ROCKBOX_FILTER.matcher(line);
            if (mf.find()) {
                float fc = Float.parseFloat(mf.group(2));
                float q = Integer.parseInt(mf.group(3)) / 10f;
                float gain = Integer.parseInt(mf.group(4)) / 10f;
                peaks.add(new Peak(fc, q, gain));
                continue;
            }
            Matcher mc = ROCKBOX_BAND_CUTOFF.matcher(line);
            if (mc.find()) {
                int i = Integer.parseInt(mc.group(1));
                if (i >= 0 && i < 10) {
                    legacyCutoff[i] = Float.parseFloat(mc.group(2));
                    legacyHas[i] = true;
                }
                continue;
            }
            Matcher mg = ROCKBOX_BAND_GAIN.matcher(line);
            if (mg.find()) {
                int i = Integer.parseInt(mg.group(1));
                if (i >= 0 && i < 10) {
                    legacyGain[i] = Integer.parseInt(mg.group(2)) / 10f;
                    legacyHas[i] = true;
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            if (legacyHas[i] && legacyCutoff[i] > 0f) {
                peaks.add(new Peak(legacyCutoff[i], 1f, legacyGain[i]));
            }
        }
        if (peaks.isEmpty()) return model;
        // If exactly 10 peaks near graphic centres, map 1:1 by nearest centre.
        if (peaks.size() == EqBandModel.BAND_COUNT && centresAlign(peaks)) {
            for (int i = 0; i < EqBandModel.BAND_COUNT; i++) {
                model.setGainDb(i, nearestPeakGain(peaks, EqBandModel.CENTERS_HZ[i]));
            }
        } else {
            for (int i = 0; i < EqBandModel.BAND_COUNT; i++) {
                model.setGainDb(i, sampleCurveDb(peaks, EqBandModel.CENTERS_HZ[i]));
            }
        }
        return model;
    }

    /** AutoEQ FixedBandEQ.txt / parametric-style fixed centres. */
    public static EqBandModel parseFixedBandEq(String text) {
        EqBandModel model = new EqBandModel();
        model.resetFlat();
        List<Peak> peaks = new ArrayList<Peak>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            Matcher mp = FIXED_PREAMP.matcher(line);
            if (mp.find()) {
                model.setPreampDb(Float.parseFloat(mp.group(1)));
                continue;
            }
            Matcher mf = FIXED_FILTER.matcher(line);
            if (mf.find()) {
                peaks.add(new Peak(Float.parseFloat(mf.group(1)), 1.41f, Float.parseFloat(mf.group(2))));
            }
        }
        for (int i = 0; i < EqBandModel.BAND_COUNT; i++) {
            model.setGainDb(i, nearestPeakGain(peaks, EqBandModel.CENTERS_HZ[i]));
        }
        return model;
    }

    /** EqualizerAPO GraphicEQ: freq gain; freq gain; … */
    public static EqBandModel parseGraphicEq(String text) {
        EqBandModel model = new EqBandModel();
        model.resetFlat();
        List<Peak> peaks = new ArrayList<Peak>();
        for (String raw : text.split("\n")) {
            Matcher m = GRAPHIC_EQ.matcher(raw.trim());
            if (!m.find()) continue;
            String[] parts = m.group(1).split("[;,]");
            for (int i = 0; i + 1 < parts.length; i += 2) {
                try {
                    float fc = Float.parseFloat(parts[i].trim());
                    float g = Float.parseFloat(parts[i + 1].trim());
                    peaks.add(new Peak(fc, 1.41f, g));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (int i = 0; i < EqBandModel.BAND_COUNT; i++) {
            model.setGainDb(i, nearestPeakGain(peaks, EqBandModel.CENTERS_HZ[i]));
        }
        return model;
    }

    /** Write Rockbox-compatible modern CFG (gains in tenths). */
    public static String toRockboxCfg(EqBandModel model) {
        if (model == null) model = new EqBandModel();
        StringBuilder sb = new StringBuilder(512);
        sb.append("# Solar EQ export — Rockbox-compatible\n");
        sb.append("eq enabled: on\n");
        int precutTenths = Math.max(0, Math.round(-model.getPreampDb() * 10f));
        sb.append("eq precut: ").append(precutTenths).append('\n');
        // Low shelf ≈ first band; peaks 1–8; high shelf ≈ last.
        appendRbFilter(sb, "eq low shelf filter", EqBandModel.CENTERS_HZ[0], 7,
                Math.round(model.getGainDb(0) * 10f));
        for (int i = 1; i <= 8; i++) {
            appendRbFilter(sb, "eq peak filter " + i, EqBandModel.CENTERS_HZ[i], 10,
                    Math.round(model.getGainDb(i) * 10f));
        }
        appendRbFilter(sb, "eq high shelf filter", EqBandModel.CENTERS_HZ[9], 7,
                Math.round(model.getGainDb(9) * 10f));
        return sb.toString();
    }

    private static void appendRbFilter(StringBuilder sb, String key, int fc, int qTenths, int gainTenths) {
        sb.append(key).append(": ").append(fc).append(", ").append(qTenths)
                .append(", ").append(gainTenths).append('\n');
    }

    private static boolean centresAlign(List<Peak> peaks) {
        for (Peak p : peaks) {
            float best = Float.MAX_VALUE;
            for (int c : EqBandModel.CENTERS_HZ) {
                best = Math.min(best, Math.abs(p.fc - c) / Math.max(1f, c));
            }
            if (best > 0.35f) return false;
        }
        return true;
    }

    private static float nearestPeakGain(List<Peak> peaks, float hz) {
        if (peaks == null || peaks.isEmpty()) return 0f;
        Peak best = peaks.get(0);
        float bestDist = Math.abs(best.fc - hz);
        for (int i = 1; i < peaks.size(); i++) {
            Peak p = peaks.get(i);
            float d = Math.abs(p.fc - hz);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        // Ignore peaks whose centre is more than ~1.5 octaves away.
        if (bestDist / Math.max(1f, hz) > 0.75f) return 0f;
        return clampGain(best.gain);
    }

    /** Rough peaking sum at Hz (linear-ish blend of nearby filters). */
    private static float sampleCurveDb(List<Peak> peaks, float hz) {
        double lin = 1.0;
        for (Peak p : peaks) {
            double q = p.q > 0.01 ? p.q : 1.0;
            double oct = Math.log(hz / Math.max(1.0, p.fc)) / Math.log(2.0);
            double w = Math.exp(-0.5 * (oct * q) * (oct * q) * 4.0);
            lin *= Math.pow(10.0, (p.gain * w) / 20.0);
        }
        return clampGain((float) (20.0 * Math.log10(Math.max(1e-6, lin))));
    }

    private static float clampGain(float g) {
        if (g < EqBandModel.MIN_GAIN_DB) return EqBandModel.MIN_GAIN_DB;
        if (g > EqBandModel.MAX_GAIN_DB) return EqBandModel.MAX_GAIN_DB;
        return g;
    }

    private static String readUtf8(File file) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder(2048);
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            try {
                br.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String stripExt(String name) {
        if (name == null) return "preset";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void ArraysFill(float[] a, float v) {
        for (int i = 0; i < a.length; i++) a[i] = v;
    }

    private static final class Peak {
        final float fc;
        final float q;
        final float gain;

        Peak(float fc, float q, float gain) {
            this.fc = fc;
            this.q = q;
            this.gain = gain;
        }
    }
}
