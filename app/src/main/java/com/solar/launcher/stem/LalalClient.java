package com.solar.launcher.stem;

import com.solar.launcher.net.TlsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Lalal.ai API v1 — one multistem (≤6 allowed ids) + on-device Melody premix.
 * Layman: cloud peels vocals/drums/bass + piano/guitars; leftover + those mix into Melody pad
 * so the Y1 only plays four streams.
 * Technical: multistem stem_list enum from OpenAPI MultistemSplitterPresetsV1 (not full
 * stem_separator list — synthesizer/strings/wind are invalid here → HTTP 422).
 * Residual track {@code no_multistem} folds into Melody. Premix → melody.wav.
 * Was: BATCH_A/B with synthesizer/strings/wind. Reversal: MULTISTEM_IDS only.
 * Docs: https://www.lalal.ai/api/v1/docs/
 * 2026-07-19
 */
public final class LalalClient {
    public static final String BASE = "https://www.lalal.ai";

    /** UI zone labels — Gen1 Stem Player four pads. */
    public static final String[] STEM_LABELS = { "Vocals", "Drums", "Bass", "Melody" };

    /** Required isolates — one MediaPlayer each (zones 0–2). */
    public static final String[] CORE_IDS = { "vocals", "drum", "bass" };

    /**
     * Multistem-allowed Melody parts (OpenAPI enum — guitars + piano only).
     * 2026-07-19
     */
    public static final String[] OTHER_IDS = {
            "piano", "electric_guitar", "acoustic_guitar"
    };

    /**
     * Full multistem stem_list — exactly the OpenAPI enum (maxItems 6).
     * vocals, drum, piano, bass, electric_guitar, acoustic_guitar
     * 2026-07-19
     */
    public static final String[] MULTISTEM_IDS = {
            "vocals", "drum", "bass", "piano", "electric_guitar", "acoustic_guitar"
    };

    /**
     * Multistem residual label ({@code type:back}) — source minus selected stems.
     * Captures synth/strings/wind/etc that multistem cannot name. Zone 3.
     * 2026-07-19
     */
    public static final String RESIDUAL_ID = "no_multistem";

    /**
     * Sidecar / stem_separator-only ids — NOT valid in multistem stem_list.
     * 2026-07-19
     */
    public static final String[] EXTRA_OTHER_IDS = {
            "synthesizer", "strings", "wind"
    };

    /** All Melody/Other file ids we accept on disk (API others + residual + sidecar extras). */
    public static final String[] ALL_OTHER_IDS = concat(
            OTHER_IDS, concat(new String[] { RESIDUAL_ID }, EXTRA_OTHER_IDS));

    /** @deprecated Prefer {@link #MULTISTEM_IDS}. */
    public static final String[] BATCH_A = MULTISTEM_IDS;

    /** @deprecated Multistem is one request now — empty sentinel for tests. */
    public static final String[] BATCH_B = new String[0];

    /** @deprecated Prefer MULTISTEM_IDS. */
    public static final String[] STEM_IDS = MULTISTEM_IDS;

    /** Cache layout — live multi Melody or experimental premix (path suffix). 2026-07-19 */
    public static final String CACHE_LAYOUT = "v6";

    /**
     * Older layout prefixes still scanned so upgrades keep local stems.
     * Was: only current CACHE_LAYOUT leaf. Reversal: drop LEGACY_CACHE_LAYOUTS loop.
     * 2026-07-19
     */
    public static final String[] LEGACY_CACHE_LAYOUTS = { "v5", "v4", "v3" };

    /** Sidecar in a stem leaf — basename (+ size) so remounts still find the folder. 2026-07-19 */
    public static final String TRACK_MARKER = ".solar_src";

    /**
     * Folder leaf for new publishes: {@code v6_live_…} keyed by basename+size (path-stable).
     * Layman: same song file keeps its stem folder even if the card path string changes.
     * Was: path|mtime|size hash (broke on remount / touch). Reversal: cacheKeyFor in leaf.
     * 2026-07-19
     */
    public static String cacheLeaf(File track, boolean premix) {
        return CACHE_LAYOUT + (premix ? "_premix_" : "_live_") + cacheKeyStable(track);
    }

    /**
     * Basename + length — survives path remount and mtime bumps.
     * 2026-07-19
     */
    public static String cacheKeyStable(File track) {
        if (track == null) return "unknown";
        String base = trackBaseName(track).toLowerCase();
        long sz = track.length();
        return Integer.toHexString((base + "|" + sz).hashCode());
    }

    /** Strip directory + extension for marker / stable key. 2026-07-19 */
    public static String trackBaseName(File track) {
        if (track == null) return "";
        String name = track.getName();
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    /**
     * All leaf names that may hold stems for this track+mode (current + legacy layouts + keys).
     * 2026-07-19
     */
    public static java.util.List<String> cacheLeafAliases(File track, boolean premix) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        String mode = premix ? "_premix_" : "_live_";
        String[] keys = new String[] {
                cacheKeyStable(track),
                cacheKeyFor(track)
        };
        String[] layouts = new String[1 + LEGACY_CACHE_LAYOUTS.length];
        layouts[0] = CACHE_LAYOUT;
        for (int i = 0; i < LEGACY_CACHE_LAYOUTS.length; i++) {
            layouts[i + 1] = LEGACY_CACHE_LAYOUTS[i];
        }
        for (int li = 0; li < layouts.length; li++) {
            for (int ki = 0; ki < keys.length; ki++) {
                String leaf = layouts[li] + mode + keys[ki];
                if (seen.contains(leaf)) continue;
                seen.add(leaf);
                out.add(leaf);
            }
        }
        return out;
    }

    /** Lalal multistem stem_list hard cap (API 422 if exceeded). */
    public static final int MULTISTEM_MAX = 6;

    private final String licenseKey;
    private final OkHttpClient client;
    private volatile java.util.concurrent.atomic.AtomicBoolean cancelled;

    public LalalClient(String licenseKey) {
        this.licenseKey = licenseKey != null ? licenseKey.trim() : "";
        TlsHelper.ensureSecurityProvider();
        // Allow every stem MP3 at once (≤6: core + other).
        okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
        dispatcher.setMaxRequests(12);
        dispatcher.setMaxRequestsPerHost(6);
        this.client = TlsHelper.client().newBuilder()
                .dispatcher(dispatcher)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /** Wire host cancel flag so exit aborts poll/download. */
    public void setCancelled(java.util.concurrent.atomic.AtomicBoolean flag) {
        this.cancelled = flag;
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled != null && cancelled.get()) {
            throw new IOException("Cancelled");
        }
    }

    public interface Progress {
        /** phase: upload|split|download|mix|publish|ready; detail optional human step. */
        void onProgress(String phase, int percent, String detail);
    }

    /**
     * One downloaded stem file mapped to a Stem Player zone (0..3).
     * Zone 3 may have many files (piano+guitars+…); mixer shares one gain.
     */
    public static final class StemFile {
        public final String id;
        public final String label;
        public final File file;
        /** 0=Vocals 1=Drums 2=Bass 3=Melody/Other. */
        public final int zone;

        public StemFile(String id, String label, File file, int zone) {
            this.id = id;
            this.label = label;
            this.file = file;
            this.zone = zone;
        }
    }

    /**
     * Upload → multistem → download on workDir → optional experimental premix → publish.
     * Default: keep all Melody/Other MP3s for synced multi-player (zone 3).
     * 2026-07-19
     */
    public List<StemFile> separateToMp3(File source, File workDir, File durableDir,
            boolean premixExperimental, Progress progress) throws Exception {
        if (licenseKey.length() < 8) throw new IOException("Lalal license key missing");
        if (source == null || !source.isFile()) throw new IOException("Source track missing");
        if (workDir == null) throw new IOException("Work dir missing");
        workDir.mkdirs();

        throwIfCancelled();
        emit(progress, "upload", 0, "Connecting…");
        String sourceId = upload(source);
        throwIfCancelled();
        emit(progress, "upload", 8, "Uploaded");
        emit(progress, "split", 10, "Starting separation…");

        String taskId = startMultistem(sourceId, MULTISTEM_IDS);
        throwIfCancelled();
        Map<String, String> urls = pollUntilAllDone(new String[] { taskId }, progress);
        throwIfCancelled();
        emit(progress, "download", 70, "Fetching stems…");
        List<StemFile> downloaded = downloadStemsParallel(urls, workDir, progress);
        throwIfCancelled();

        List<StemFile> pads;
        if (premixExperimental) {
            emit(progress, "mix", 88, "Premix Melody (experimental)…");
            pads = premixToFourPads(downloaded, workDir, progress);
        } else {
            emit(progress, "mix", 90, "Keeping Melody stems live…");
            pads = downloaded;
            emit(progress, "mix", 96, "Live multi-player Melody");
        }
        throwIfCancelled();

        File finalDir = durableDir != null ? durableDir : workDir;
        if (!sameDir(workDir, finalDir)) {
            emit(progress, "publish", 97, "Copying to storage…");
            pads = publishStems(pads, finalDir, source);
            clearDirQuiet(workDir);
        } else {
            // Same folder — still stamp basename so remounts find this leaf. 2026-07-19
            writeTrackMarker(finalDir, source);
        }
        emit(progress, "ready", 100, "Ready");
        return pads;
    }

    /** @deprecated Prefer work+durable+premix overload. */
    public List<StemFile> separateToMp3(File source, File workDir, File durableDir,
            Progress progress) throws Exception {
        return separateToMp3(source, workDir, durableDir, false, progress);
    }

    /** @deprecated Prefer work+durable overload. */
    public List<StemFile> separateToMp3(File source, File outDir, Progress progress)
            throws Exception {
        return separateToMp3(source, outDir, outDir, false, progress);
    }

    private static void emit(Progress progress, String phase, int percent, String detail) {
        if (progress == null) return;
        try {
            progress.onProgress(phase, percent, detail);
        } catch (Exception ignored) {}
    }

    private static boolean sameDir(File a, File b) {
        if (a == null || b == null) return a == b;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (Exception e) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }

    /**
     * Work scratch — always app internal cache (faster than MicroSD).
     * 2026-07-19
     */
    public static File workStemDir(android.content.Context ctx, File track, boolean premix) {
        if (ctx == null) return null;
        return new File(new File(ctx.getCacheDir(), "lalal_work"), cacheLeaf(track, premix));
    }

    /** @deprecated Prefer overload with premix flag. */
    public static File workStemDir(android.content.Context ctx, File track) {
        return workStemDir(ctx, track, false);
    }

    /**
     * Durable stem home — internal cache when room; else preferred media volume cache.
     * 2026-07-19
     */
    public static File durableStemDir(android.content.Context ctx, File track, boolean premix) {
        if (ctx == null) return null;
        long need = premix ? 48L * 1024L * 1024L : 80L * 1024L * 1024L;
        File internalRoot = ctx.getCacheDir();
        File internal = stemCacheDir(internalRoot, track, premix);
        if (com.solar.launcher.StreamCacheRoot.hasSpace(internalRoot, need)) {
            return internal;
        }
        File media = com.solar.launcher.DeviceFeatures.getNewMediaRoot(ctx);
        if (media == null) return internal;
        File overflow = new File(new File(media,
                "Android/data/" + ctx.getPackageName() + "/cache/lalal_stems"),
                cacheLeaf(track, premix));
        overflow.mkdirs();
        return overflow;
    }

    /** @deprecated Prefer overload with premix flag. */
    public static File durableStemDir(android.content.Context ctx, File track) {
        return durableStemDir(ctx, track, false);
    }

    /** Copy stem files into durableDir; return StemFiles pointing there. 2026-07-19 */
    public static List<StemFile> publishStems(List<StemFile> stems, File durableDir)
            throws IOException {
        return publishStems(stems, durableDir, null);
    }

    /**
     * Publish stems and stamp {@link #TRACK_MARKER} when source track is known.
     * 2026-07-19
     */
    public static List<StemFile> publishStems(List<StemFile> stems, File durableDir, File sourceTrack)
            throws IOException {
        if (stems == null || durableDir == null) throw new IOException("publish missing args");
        durableDir.mkdirs();
        List<StemFile> out = new ArrayList<StemFile>(stems.size());
        for (int i = 0; i < stems.size(); i++) {
            StemFile s = stems.get(i);
            if (s == null || s.file == null || !s.file.isFile()) continue;
            File dest = new File(durableDir, s.file.getName());
            if (!sameDir(s.file.getParentFile(), durableDir)) {
                copyFile(s.file, dest);
            } else {
                dest = s.file;
            }
            out.add(new StemFile(s.id, s.label, dest, s.zone));
        }
        if (out.size() < 4) throw new IOException("publish incomplete (" + out.size() + ")");
        if (sourceTrack != null) writeTrackMarker(durableDir, sourceTrack);
        return out;
    }

    /**
     * Write basename + size into a stem leaf so later opens find it after path/mtime drift.
     * Layman: sticky note on the stem folder naming the song.
     * 2026-07-19
     */
    public static void writeTrackMarker(File dir, File track) {
        if (dir == null || track == null) return;
        try {
            if (!dir.isDirectory()) dir.mkdirs();
            File marker = new File(dir, TRACK_MARKER);
            FileOutputStream out = new FileOutputStream(marker);
            try {
                String line = trackBaseName(track) + "\n" + track.length() + "\n";
                out.write(line.getBytes("UTF-8"));
            } finally {
                try { out.close(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * True when {@link #TRACK_MARKER} names this track (basename; size soft-check).
     * 2026-07-19
     */
    public static boolean markerMatchesTrack(File dir, File track) {
        if (dir == null || track == null) return false;
        File marker = new File(dir, TRACK_MARKER);
        if (!marker.isFile() || marker.length() < 1) return false;
        try {
            FileInputStream in = new FileInputStream(marker);
            byte[] buf = new byte[(int) Math.min(marker.length(), 512)];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return false;
            String text = new String(buf, 0, n, "UTF-8");
            String[] lines = text.split("\n");
            if (lines.length < 1) return false;
            String base = lines[0].trim();
            if (!base.equalsIgnoreCase(trackBaseName(track))) return false;
            if (lines.length >= 2) {
                try {
                    long sz = Long.parseLong(lines[1].trim());
                    // Soft: size match preferred; basename-only still ok if size line junk. 2026-07-19
                    if (sz > 0 && track.length() > 0 && sz != track.length()) return false;
                } catch (Exception ignored) {}
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void copyFile(File from, File to) throws IOException {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
            try { out.close(); } catch (Exception ignored) {}
        }
    }

    static void clearDirQuiet(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isFile()) kids[i].delete();
        }
        dir.delete();
    }

    /**
     * Collapse many zone-3 files into melody.wav; keep vocals/drum/bass MP3s.
     * Y1 plays four streams only. 2026-07-19
     */
    public List<StemFile> premixToFourPads(List<StemFile> downloaded, File outDir,
            Progress progress) throws Exception {
        return premixToFourPadsStatic(downloaded, outDir, cancelled, progress);
    }

    public List<StemFile> premixToFourPads(List<StemFile> downloaded, File outDir)
            throws Exception {
        return premixToFourPadsStatic(downloaded, outDir, cancelled, null);
    }

    /** Static so user-sidecar load can premix without a client instance. */
    public static List<StemFile> premixToFourPadsStatic(List<StemFile> downloaded, File outDir,
            AtomicBoolean cancelled) throws Exception {
        return premixToFourPadsStatic(downloaded, outDir, cancelled, null);
    }

    public static List<StemFile> premixToFourPadsStatic(List<StemFile> downloaded, File outDir,
            AtomicBoolean cancelled, final Progress progress) throws Exception {
        if (downloaded == null || downloaded.isEmpty()) {
            throw new IOException("No stems to premix");
        }
        StemFile vocals = null;
        StemFile drum = null;
        StemFile bass = null;
        List<File> others = new ArrayList<File>();
        for (int i = 0; i < downloaded.size(); i++) {
            StemFile s = downloaded.get(i);
            if (s == null || s.file == null || !s.file.isFile()) continue;
            if (s.zone == 0) vocals = s;
            else if (s.zone == 1) drum = s;
            else if (s.zone == 2) bass = s;
            else others.add(s.file);
        }
        if (vocals == null || drum == null || bass == null) {
            throw new IOException("Missing core stems for premix");
        }
        if (others.isEmpty()) {
            throw new IOException("No Melody/Other stems to premix");
        }
        List<StemFile> out = new ArrayList<StemFile>(4);
        out.add(vocals);
        out.add(drum);
        out.add(bass);
        if (others.size() == 1) {
            File only = others.get(0);
            String id = only.getName().toLowerCase().endsWith(".wav") ? "melody" : stripExt(only.getName());
            out.add(new StemFile(id, "Melody", only, 3));
            return out;
        }
        File melodyWav = new File(outDir, StemOtherPremix.MELODY_WAV);
        StemOtherPremix.MixProgress mixCb = progress == null ? null
                : new StemOtherPremix.MixProgress() {
                    @Override
                    public void onMixProgress(int within0to100, String detail) {
                        // Map premix 0–100 → overall 88–96.
                        int pct = 88 + (within0to100 * 8) / 100;
                        if (pct > 96) pct = 96;
                        emit(progress, "mix", pct, detail != null ? detail : "Mixing…");
                    }
                };
        StemOtherPremix.mixToMonoWav(others, melodyWav, cancelled, mixCb);
        for (int i = 0; i < others.size(); i++) {
            File f = others.get(i);
            if (f != null && !f.equals(melodyWav) && f.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        out.add(new StemFile("melody", "Melody", melodyWav, 3));
        return out;
    }

    /** Strip trailing .mp3 / .wav for StemFile id. 2026-07-19 */
    private static String stripExt(String name) {
        if (name == null) return "melody";
        String n = name;
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.isEmpty() ? "melody" : n;
    }

    /**
     * Fetch every returned stem MP3 in parallel; skip missing optional “other” URLs.
     * Core vocals/drum/bass must succeed. 2026-07-19
     */
    List<StemFile> downloadStemsParallel(Map<String, String> urls, File outDir, Progress progress)
            throws Exception {
        final List<String> want = new ArrayList<String>();
        for (String id : CORE_IDS) {
            if (urls == null || urls.get(id) == null || urls.get(id).isEmpty()) {
                throw new IOException("Missing stem URL for " + id);
            }
            want.add(id);
        }
        for (String id : OTHER_IDS) {
            if (urls != null && urls.get(id) != null && !urls.get(id).isEmpty()) {
                want.add(id);
            }
        }
        // Residual = “everything else” (synth/strings/wind…) for Melody premix.
        if (urls != null && urls.get(RESIDUAL_ID) != null && !urls.get(RESIDUAL_ID).isEmpty()) {
            want.add(RESIDUAL_ID);
        }
        // Sidecar-style extras if somehow present in the map.
        for (String id : EXTRA_OTHER_IDS) {
            if (urls != null && urls.get(id) != null && !urls.get(id).isEmpty()) {
                want.add(id);
            }
        }
        if (want.size() <= CORE_IDS.length) {
            throw new IOException("No Melody/Other stems returned (need piano/guitar/residual)");
        }

        final int n = want.size();
        final StemFile[] slots = new StemFile[n];
        final AtomicInteger finished = new AtomicInteger(0);
        final AtomicReference<Exception> firstErr = new AtomicReference<Exception>();
        final CountDownLatch latch = new CountDownLatch(n);
        final long t0 = System.currentTimeMillis();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("stemCount", n);
            d.put("otherCount", n - CORE_IDS.length);
            d.put("parallel", true);
            com.solar.launcher.Debug543e15Log.log(
                    "LalalClient.downloadStemsParallel:begin",
                    "parallel stem download start",
                    "STEM_DL",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(n, 6));
        try {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            throwIfCancelled();
                            if (firstErr.get() != null) return;
                            String id = want.get(idx);
                            String url = urls.get(id);
                            File dest = new File(outDir, id + ".mp3");
                            long stemT0 = System.currentTimeMillis();
                            download(url, dest);
                            int zone = zoneForId(id);
                            slots[idx] = new StemFile(id, labelForStemId(id), dest, zone);
                            int done = finished.incrementAndGet();
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("id", id);
                                d.put("zone", zone);
                                d.put("ms", System.currentTimeMillis() - stemT0);
                                d.put("bytes", dest.length());
                                d.put("done", done);
                                com.solar.launcher.Debug543e15Log.log(
                                        "LalalClient.downloadStemsParallel:one",
                                        "stem downloaded",
                                        "STEM_DL",
                                        d);
                            } catch (Exception ignored) {}
                            // #endregion
                            if (progress != null) {
                                // Download band 70–87.
                                int pct = 70 + (done * 17) / n;
                                if (pct > 87) pct = 87;
                                emit(progress, "download", pct,
                                        "Downloaded " + done + "/" + n + " · " + id);
                            }
                        } catch (Exception e) {
                            firstErr.compareAndSet(null, e);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            if (!latch.await(10, TimeUnit.MINUTES)) {
                throw new IOException("Stem download timed out");
            }
        } finally {
            pool.shutdownNow();
        }
        Exception err = firstErr.get();
        if (err != null) throw err;
        List<StemFile> out = new ArrayList<StemFile>(n);
        for (int i = 0; i < n; i++) {
            if (slots[i] == null) throw new IOException("Missing stem file for " + want.get(i));
            out.add(slots[i]);
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("totalMs", System.currentTimeMillis() - t0);
            d.put("count", out.size());
            com.solar.launcher.Debug543e15Log.log(
                    "LalalClient.downloadStemsParallel:end",
                    "parallel stem download done",
                    "STEM_DL",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        return out;
    }

    /** Binary upload — Content-Disposition filename; returns source id. */
    String upload(File file) throws Exception {
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        String name = file.getName();
        if (name == null || name.isEmpty()) name = "track.mp3";
        Request req = new Request.Builder()
                .url(BASE + "/api/v1/upload/")
                .header("X-License-Key", licenseKey)
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .post(body)
                .build();
        Response resp = client.newCall(req).execute();
        try {
            String text = bodyString(resp);
            if (!resp.isSuccessful()) {
                throw new IOException("Upload HTTP " + resp.code() + ": " + text);
            }
            JSONObject json = new JSONObject(text);
            String id = json.optString("id", "");
            if (id.isEmpty()) throw new IOException("Upload missing id: " + text);
            return id;
        } finally {
            resp.close();
        }
    }

    /**
     * Start multistem job; stem_list must be OpenAPI enum only (≤ MULTISTEM_MAX).
     * 2026-07-19
     */
    String startMultistem(String sourceId, String[] stemList) throws Exception {
        if (stemList == null || stemList.length == 0) {
            throw new IOException("Empty stem list");
        }
        if (stemList.length > MULTISTEM_MAX) {
            throw new IOException("stem_list has " + stemList.length
                    + " items; Lalal allows at most " + MULTISTEM_MAX);
        }
        JSONObject presets = new JSONObject();
        JSONArray list = new JSONArray();
        StringBuilder listCsv = new StringBuilder();
        for (int i = 0; i < stemList.length; i++) {
            list.put(stemList[i]);
            if (i > 0) listCsv.append(',');
            listCsv.append(stemList[i]);
        }
        presets.put("stem_list", list);
        presets.put("encoder_format", "mp3");
        presets.put("splitter", "auto");
        presets.put("extraction_level", "clear_cut");

        JSONObject body = new JSONObject();
        body.put("source_id", sourceId);
        body.put("presets", presets);

        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("stem_list", listCsv.toString());
            d.put("count", stemList.length);
            d.put("sourceIdLen", sourceId != null ? sourceId.length() : 0);
            com.solar.launcher.Debug543e15Log.log(
                    "LalalClient.startMultistem:request",
                    "multistem stem_list about to POST",
                    "H-A",
                    d);
        } catch (Exception ignored) {}
        // #endregion

        Request req = new Request.Builder()
                .url(BASE + "/api/v1/split/multistem/")
                .header("X-License-Key", licenseKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"), body.toString()))
                .build();
        Response resp = client.newCall(req).execute();
        try {
            String text = bodyString(resp);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("http", resp.code());
                d.put("stem_list", listCsv.toString());
                d.put("bodyHead", text != null && text.length() > 240
                        ? text.substring(0, 240) : text);
                com.solar.launcher.Debug543e15Log.log(
                        "LalalClient.startMultistem:response",
                        resp.isSuccessful() ? "multistem accepted" : "multistem rejected",
                        resp.code() == 422 ? "H-A" : "H-C",
                        d);
            } catch (Exception ignored) {}
            // #endregion
            if (!resp.isSuccessful()) {
                throw new IOException("Multistem HTTP " + resp.code() + ": " + text);
            }
            JSONObject json = new JSONObject(text);
            String taskId = json.optString("task_id", "");
            if (taskId.isEmpty()) throw new IOException("No task_id: " + text);
            return taskId;
        } finally {
            resp.close();
        }
    }

    /** @deprecated Use {@link #startMultistem(String, String[])}. */
    String startMultistem(String sourceId) throws Exception {
        return startMultistem(sourceId, MULTISTEM_IDS);
    }

    /**
     * Poll until every task succeeds; merge stem→url maps.
     * 2026-07-19
     */
    Map<String, String> pollUntilAllDone(String[] taskIds, Progress progress) throws Exception {
        if (taskIds == null || taskIds.length == 0) {
            throw new IOException("No stem tasks");
        }
        Map<String, String> merged = new HashMap<String, String>();
        boolean[] done = new boolean[taskIds.length];
        int finished = 0;
        long deadline = System.currentTimeMillis() + 20L * 60L * 1000L;
        while (finished < taskIds.length && System.currentTimeMillis() < deadline) {
            throwIfCancelled();
            JSONObject body = new JSONObject();
            JSONArray ids = new JSONArray();
            for (int i = 0; i < taskIds.length; i++) {
                if (!done[i]) ids.put(taskIds[i]);
            }
            body.put("task_ids", ids);

            Request req = new Request.Builder()
                    .url(BASE + "/api/v1/check/")
                    .header("X-License-Key", licenseKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .post(RequestBody.create(
                            MediaType.parse("application/json; charset=utf-8"), body.toString()))
                    .build();
            Response resp = client.newCall(req).execute();
            String text;
            try {
                text = bodyString(resp);
                if (!resp.isSuccessful()) {
                    throw new IOException("Check HTTP " + resp.code() + ": " + text);
                }
            } finally {
                resp.close();
            }

            JSONObject root = new JSONObject(text);
            JSONObject resultMap = root.optJSONObject("result");
            if (resultMap == null) throw new IOException("Check missing result: " + text);

            int minPct = 89;
            for (int i = 0; i < taskIds.length; i++) {
                if (done[i]) continue;
                JSONObject task = resultMap.optJSONObject(taskIds[i]);
                if (task == null) continue;
                String status = task.optString("status", "");
                if ("progress".equals(status)) {
                    int pct = task.optInt("progress", 10);
                    if (pct < minPct) minPct = pct;
                    continue;
                }
                if ("success".equals(status)) {
                    JSONObject payload = task.optJSONObject("result");
                    if (payload == null) throw new IOException("Success without result");
                    JSONArray tracks = payload.optJSONArray("tracks");
                    if (tracks == null) throw new IOException("Success without tracks");
                    for (int t = 0; t < tracks.length(); t++) {
                        JSONObject tr = tracks.getJSONObject(t);
                        String type = tr.optString("type", "");
                        String label = tr.optString("label", "");
                        String url = tr.optString("url", "");
                        if (label.isEmpty() || url.isEmpty()) continue;
                        // Stems + residual back track (no_multistem) for Melody premix.
                        if ("stem".equals(type) || "back".equals(type)) {
                            merged.put(label, url);
                        }
                    }
                    done[i] = true;
                    finished++;
                    continue;
                }
                if ("error".equals(status) || "server_error".equals(status)
                        || "cancelled".equals(status)) {
                    String err = task.optString("error", status);
                    throw new IOException("Lalal " + status + ": " + err);
                }
            }
            if (finished < taskIds.length) {
                if (progress != null) {
                    // Split band 10–69.
                    int pct = 10 + (finished * 30) / taskIds.length + Math.max(0, minPct) / 3;
                    if (pct < 10) pct = 10;
                    if (pct > 69) pct = 69;
                    emit(progress, "split", pct, "Separating… " + minPct + "%");
                }
                Thread.sleep(2500);
            }
        }
        if (finished < taskIds.length) {
            throw new IOException("Stem separation timed out");
        }
        return merged;
    }

    /** Poll /check until success; map stem label → download URL. */
    Map<String, String> pollUntilDone(String taskId, Progress progress) throws Exception {
        return pollUntilAllDone(new String[] { taskId }, progress);
    }

    void download(String url, File dest) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("X-License-Key", licenseKey)
                .get()
                .build();
        Response resp = client.newCall(req).execute();
        try {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("Download HTTP " + resp.code());
            }
            InputStream in = resp.body().byteStream();
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    throwIfCancelled();
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            resp.close();
        }
    }

    private static String bodyString(Response resp) throws IOException {
        if (resp.body() == null) return "";
        return resp.body().string();
    }

    /** Cache folder name from track path + mtime. */
    public static String cacheKeyFor(File track) {
        if (track == null) return "unknown";
        String path = track.getAbsolutePath();
        long mt = track.lastModified();
        long sz = track.length();
        return Integer.toHexString((path + "|" + mt + "|" + sz).hashCode());
    }

    /** Stem cache under layout + live/premix mode. 2026-07-19 */
    public static File stemCacheDir(File appCache, File track, boolean premix) {
        return new File(new File(appCache, "lalal_stems"), cacheLeaf(track, premix));
    }

    /** @deprecated Prefer overload with premix flag. */
    public static File stemCacheDir(File appCache, File track) {
        return stemCacheDir(appCache, track, false);
    }

    /**
     * Ready when core three MP3s + at least one Melody/Other file (wav or live MP3s).
     * 2026-07-19
     */
    public static boolean cacheReady(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        for (String id : CORE_IDS) {
            File f = new File(dir, id + ".mp3");
            if (!f.isFile() || f.length() < 100) return false;
        }
        File mel = new File(dir, StemOtherPremix.MELODY_WAV);
        if (mel.isFile() && mel.length() >= 100) return true;
        return countOtherStemFiles(dir) >= 1;
    }

    /** How many Melody/Other files are on disk (paths de-duped). 2026-07-19 */
    static int countOtherStemFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File mel = new File(dir, StemOtherPremix.MELODY_WAV);
        if (mel.isFile() && mel.length() >= 100) return 1;
        java.util.HashSet<String> paths = new java.util.HashSet<String>();
        File alias = resolveMelodyFile(dir);
        if (alias != null) paths.add(alias.getAbsolutePath());
        for (String id : ALL_OTHER_IDS) {
            File f = resolveStemFile(dir, id);
            if (f != null) paths.add(f.getAbsolutePath());
        }
        return paths.size();
    }

    /**
     * Load cache for playback. Live = all files; premixExperimental forces blend if needed.
     * 2026-07-19
     */
    public static List<StemFile> loadCached(File dir, boolean premixExperimental) {
        List<StemFile> flex = loadStemDirFlexible(dir);
        if (!premixExperimental) return flex;
        int others = 0;
        for (int i = 0; i < flex.size(); i++) {
            if (flex.get(i).zone == 3) others++;
        }
        if (others <= 1) return flex;
        try {
            return premixToFourPadsStatic(flex, dir, null, null);
        } catch (Exception e) {
            return flex;
        }
    }

    /** @deprecated Prefer overload with premix flag (defaults live). */
    public static List<StemFile> loadCached(File dir) {
        return loadCached(dir, false);
    }

    public static String labelForZone(int zone) {
        if (zone < 0 || zone >= STEM_LABELS.length) return "";
        return STEM_LABELS[zone];
    }

    /**
     * Map Lalal stem id → Stem Player zone (0..3). Unknown → Melody/Other.
     * 2026-07-19
     */
    public static int zoneForId(String id) {
        if ("vocals".equals(id)) return 0;
        if ("drum".equals(id) || "drums".equals(id)) return 1;
        if ("bass".equals(id)) return 2;
        return 3;
    }

    /**
     * User-prepared stems folder next to the track: {@code Song.mp3} → {@code Song.stems/}.
     * Layman: drop MP3s beside the song so Stem Player skips the cloud split.
     * 2026-07-19
     */
    public static File userStemsDir(File track) {
        if (track == null) return null;
        File parent = track.getParentFile();
        if (parent == null) return null;
        String name = track.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(parent, base + ".stems");
    }

    /**
     * True when a file or folder belongs to Stem Player downloads — never library-ingest.
     * Layman: vocals/drums under Song.stems or lalal_stems are pads, not songs.
     * Tech: path segment ends with {@code .stems}, or equals {@code lalal_stems}/{@code lalal_work}.
     * Was: Music walk indexed stem MP3s as tracks. Reversal: remove this gate.
     * 2026-07-19
     */
    public static boolean isStemLibraryArtifact(File f) {
        if (f == null) return false;
        File cur = f;
        // Walk parents so Song.stems/vocals.mp3 and Song.stems itself both match.
        while (cur != null) {
            String name = cur.getName();
            if (name != null && name.length() > 0) {
                if (name.endsWith(".stems")) return true;
                if ("lalal_stems".equals(name) || "lalal_work".equals(name)) return true;
            }
            File parent = cur.getParentFile();
            if (parent == null || parent.equals(cur)) break;
            cur = parent;
        }
        return false;
    }

    /**
     * True when a sidecar {@code *.stems} folder has vocals + drums + bass + Melody/Other.
     * Melody may be one file ({@code melody.mp3} / {@code other.mp3} / …) or Lalal other ids.
     * 2026-07-19
     */
    public static boolean userStemsReady(File track) {
        return cacheReadyFlexible(userStemsDir(track));
    }

    /**
     * True when this track already has playable stems on disk (skip Lalal).
     * Layman: user folder or any previous download — live or premix, internal or card.
     * Technical: probe user + every cache home for live AND premix leaves (space-flip safe).
     * Was: only durableStemDir(premix) + legacy (missed overflow when space recovered).
     * Reversal: check single durableStemDir(ctx,track,premix) only.
     * 2026-07-19
     */
    public static boolean trackStemsReady(android.content.Context ctx, File track,
            boolean premix, File appCache) {
        return findReadyStemDir(ctx, track, premix, appCache) != null;
    }

    /**
     * First ready stem folder for this track (prefer requested premix mode, then the other).
     * Probe order: user sidecar → exact/alias leaves → basename marker scan → duration heuristic.
     * @return null if none — caller may Lalal
     * Was: only current cacheLeaf under a few roots. Reversal: drop scanStemRootsForTrack.
     * 2026-07-19
     */
    public static File findReadyStemDir(android.content.Context ctx, File track,
            boolean preferPremix, File appCache) {
        if (track == null || !track.isFile()) return null;
        if (userStemsReady(track)) return userStemsDir(track);
        // Prefer matching mode, then opposite — never re-upload when either exists. 2026-07-19
        File hit = firstReadyAmong(stemCacheCandidates(ctx, track, preferPremix, appCache));
        if (hit != null) {
            writeTrackMarker(hit, track); // migrate unmarked legacy leaves. 2026-07-19
            return hit;
        }
        hit = firstReadyAmong(stemCacheCandidates(ctx, track, !preferPremix, appCache));
        if (hit != null) {
            writeTrackMarker(hit, track);
            return hit;
        }
        hit = scanStemRootsForTrack(ctx, track, appCache, preferPremix);
        if (hit != null) {
            writeTrackMarker(hit, track);
            return hit;
        }
        hit = scanStemRootsForTrack(ctx, track, appCache, !preferPremix);
        if (hit != null) writeTrackMarker(hit, track);
        return hit;
    }

    private static File firstReadyAmong(java.util.List<File> dirs) {
        if (dirs == null) return null;
        for (int i = 0; i < dirs.size(); i++) {
            File d = dirs.get(i);
            if (cacheReady(d) || cacheReadyFlexible(d)) return d;
        }
        return null;
    }

    /**
     * All places we may have published stems for this track+mode (deduped).
     * Includes every layout×key alias leaf under app/cache/work/overflow homes.
     * Always includes overflow media path — do not gate on free space (read path).
     * 2026-07-19
     */
    public static java.util.List<File> stemCacheCandidates(android.content.Context ctx,
            File track, boolean premix, File appCache) {
        java.util.ArrayList<File> out = new java.util.ArrayList<File>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        java.util.List<String> leaves = cacheLeafAliases(track, premix);
        java.util.List<File> roots = stemCacheRoots(ctx, appCache);
        for (int ri = 0; ri < roots.size(); ri++) {
            File root = roots.get(ri);
            if (root == null) continue;
            for (int li = 0; li < leaves.size(); li++) {
                addCandidate(out, seen, new File(root, leaves.get(li)));
            }
        }
        // Work dir uses same leaves (scratch that may still hold ready pads). 2026-07-19
        if (ctx != null) {
            File workRoot = new File(ctx.getCacheDir(), "lalal_work");
            for (int li = 0; li < leaves.size(); li++) {
                addCandidate(out, seen, new File(workRoot, leaves.get(li)));
            }
        }
        return out;
    }

    /**
     * Parent folders that may contain stem leaf dirs (internal + overflow + host appCache).
     * 2026-07-19
     */
    public static java.util.List<File> stemCacheRoots(android.content.Context ctx, File appCache) {
        java.util.ArrayList<File> roots = new java.util.ArrayList<File>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        addRoot(roots, seen, appCache != null ? new File(appCache, "lalal_stems") : null);
        if (ctx != null) {
            File cache = ctx.getCacheDir();
            addRoot(roots, seen, cache != null ? new File(cache, "lalal_stems") : null);
            try {
                File media = com.solar.launcher.DeviceFeatures.getNewMediaRoot(ctx);
                if (media != null) {
                    addRoot(roots, seen, new File(media,
                            "Android/data/" + ctx.getPackageName() + "/cache/lalal_stems"));
                }
                // Peer MicroSD / internal — both volume caches when dual-mounted. 2026-07-19
                File micro = com.solar.launcher.DeviceFeatures.getMicroSdRoot();
                File internal = com.solar.launcher.DeviceFeatures.getInternalStorageRoot();
                if (micro != null) {
                    addRoot(roots, seen, new File(micro,
                            "Android/data/" + ctx.getPackageName() + "/cache/lalal_stems"));
                }
                if (internal != null) {
                    addRoot(roots, seen, new File(internal,
                            "Android/data/" + ctx.getPackageName() + "/cache/lalal_stems"));
                }
            } catch (Exception ignored) {}
        }
        return roots;
    }

    private static void addRoot(java.util.ArrayList<File> roots, java.util.HashSet<String> seen,
            File root) {
        if (root == null) return;
        String p = root.getAbsolutePath();
        if (seen.contains(p)) return;
        seen.add(p);
        roots.add(root);
    }

    /**
     * Walk stem roots for a ready leaf matching this track (marker, then duration).
     * Layman: last-chance look through downloaded stem folders by song name.
     * Technical: prefer .solar_src basename; else vocals duration ≈ track duration.
     * 2026-07-19
     */
    public static File scanStemRootsForTrack(android.content.Context ctx, File track,
            File appCache, boolean premixHint) {
        if (track == null) return null;
        String wantMode = premixHint ? "_premix_" : "_live_";
        File bestMarked = null;
        long bestMarkedMt = -1L;
        File bestDur = null;
        long bestDurMt = -1L;
        int trackMs = probeDurationMsQuiet(track);
        java.util.List<File> roots = stemCacheRoots(ctx, appCache);
        // Also scan lalal_work. 2026-07-19
        if (ctx != null && ctx.getCacheDir() != null) {
            File wr = new File(ctx.getCacheDir(), "lalal_work");
            String p = wr.getAbsolutePath();
            boolean have = false;
            for (int i = 0; i < roots.size(); i++) {
                if (p.equals(roots.get(i).getAbsolutePath())) { have = true; break; }
            }
            if (!have) roots.add(wr);
        }
        for (int ri = 0; ri < roots.size(); ri++) {
            File root = roots.get(ri);
            if (root == null || !root.isDirectory()) continue;
            File[] kids = root.listFiles();
            if (kids == null) continue;
            for (int ki = 0; ki < kids.length; ki++) {
                File d = kids[ki];
                if (d == null || !d.isDirectory()) continue;
                String leaf = d.getName();
                // Soft prefer matching live/premix in the leaf name. 2026-07-19
                boolean modeOk = leaf != null && leaf.indexOf(wantMode) >= 0;
                if (!cacheReady(d) && !cacheReadyFlexible(d)) continue;
                if (markerMatchesTrack(d, track)) {
                    long mt = d.lastModified();
                    if (modeOk || bestMarked == null) {
                        if (mt >= bestMarkedMt) {
                            bestMarked = d;
                            bestMarkedMt = mt;
                        }
                    }
                    continue;
                }
                // Unmarked legacy leaf: duration of vocals ≈ source track. 2026-07-19
                if (trackMs > 1000 && stemDirDurationMatches(d, trackMs)) {
                    long mt = d.lastModified();
                    if (modeOk || bestDur == null) {
                        if (mt >= bestDurMt) {
                            bestDur = d;
                            bestDurMt = mt;
                        }
                    }
                }
            }
        }
        if (bestMarked != null) return bestMarked;
        return bestDur;
    }

    /**
     * True when a stem leaf’s vocals duration is within ~2.5s of the source track.
     * 2026-07-19
     */
    static boolean stemDirDurationMatches(File dir, int trackMs) {
        if (dir == null || trackMs < 1000) return false;
        File vocals = resolveStemFile(dir, "vocals");
        if (vocals == null) return false;
        int vMs = probeDurationMsQuiet(vocals);
        if (vMs < 1000) return false;
        return Math.abs(vMs - trackMs) <= 2500;
    }

    /** Best-effort duration; 0 on failure (unit tests / missing MMR). 2026-07-19 */
    static int probeDurationMsQuiet(File f) {
        if (f == null || !f.isFile()) return 0;
        android.media.MediaMetadataRetriever mmr = null;
        try {
            mmr = new android.media.MediaMetadataRetriever();
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                int ms = Integer.parseInt(d);
                if (ms > 0) return ms;
            }
        } catch (Throwable ignored) {
        } finally {
            if (mmr != null) {
                try { mmr.release(); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static void addCandidate(java.util.ArrayList<File> out,
            java.util.HashSet<String> seen, File dir) {
        if (dir == null) return;
        String p = dir.getAbsolutePath();
        if (seen.contains(p)) return;
        seen.add(p);
        out.add(dir);
    }

    /**
     * Load sidecar user stems. Premix only when experimental flag is on.
     * 2026-07-19
     */
    public static List<StemFile> loadUserStems(File track, boolean premixExperimental) {
        File dir = userStemsDir(track);
        List<StemFile> raw = loadStemDirFlexible(dir);
        if (raw.isEmpty() || !premixExperimental) return raw;
        int otherCount = 0;
        for (int i = 0; i < raw.size(); i++) {
            if (raw.get(i).zone == 3) otherCount++;
        }
        if (otherCount <= 1) return raw;
        try {
            return premixToFourPadsStatic(raw, dir, null, null);
        } catch (Exception e) {
            return raw;
        }
    }

    /** @deprecated Prefer overload with premix flag. */
    public static List<StemFile> loadUserStems(File track) {
        return loadUserStems(track, false);
    }

    /**
     * Ready check that accepts {@code drums.mp3} and a single {@code melody.mp3} (or aliases)
     * instead of requiring Lalal {@code OTHER_IDS}. Used for user sidecars and v2 cache.
     * 2026-07-19
     */
    public static boolean cacheReadyFlexible(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        if (resolveStemFile(dir, "vocals") == null) return false;
        if (resolveStemFile(dir, "drum") == null && resolveStemFile(dir, "drums") == null) {
            return false;
        }
        if (resolveStemFile(dir, "bass") == null) return false;
        if (resolveMelodyFile(dir) != null) return true;
        for (String id : ALL_OTHER_IDS) {
            if (resolveStemFile(dir, id) != null) return true;
        }
        File mel = new File(dir, StemOtherPremix.MELODY_WAV);
        return mel.isFile() && mel.length() >= 100;
    }

    /** Load every recognised stem MP3 in a folder (core + other + melody aliases). 2026-07-19 */
    public static List<StemFile> loadStemDirFlexible(File dir) {
        List<StemFile> out = new ArrayList<StemFile>();
        if (dir == null || !dir.isDirectory()) return out;
        addIfPresent(out, dir, "vocals", 0);
        if (!addIfPresent(out, dir, "drum", 1)) {
            addIfPresent(out, dir, "drums", 1);
        }
        addIfPresent(out, dir, "bass", 2);
        File melodyWav = new File(dir, StemOtherPremix.MELODY_WAV);
        if (melodyWav.isFile() && melodyWav.length() >= 100) {
            out.add(new StemFile("melody", "Melody", melodyWav, 3));
            return out;
        }
        File melody = resolveMelodyFile(dir);
        if (melody != null) {
            String id = stripMp3(melody.getName());
            out.add(new StemFile(id, "Melody", melody, 3));
        }
        for (String id : ALL_OTHER_IDS) {
            if (melody != null && melody.equals(resolveStemFile(dir, id))) continue;
            addIfPresent(out, dir, id, 3);
        }
        return out;
    }

    private static boolean addIfPresent(List<StemFile> out, File dir, String id, int zone) {
        File f = resolveStemFile(dir, id);
        if (f == null) return false;
        out.add(new StemFile(id, labelForStemId(id), f, zone));
        return true;
    }

    /** {@code id.mp3} if present and non-tiny. */
    public static File resolveStemFile(File dir, String id) {
        if (dir == null || id == null) return null;
        File f = new File(dir, id + ".mp3");
        if (f.isFile() && f.length() >= 100) return f;
        return null;
    }

    /**
     * Single pre-mixed Melody/Other pad: melody, other, instruments, or samples.
     * 2026-07-19
     */
    public static File resolveMelodyFile(File dir) {
        String[] aliases = { "melody", "other", "instruments", "samples" };
        for (int i = 0; i < aliases.length; i++) {
            File f = resolveStemFile(dir, aliases[i]);
            if (f != null) return f;
        }
        return null;
    }

    private static String stripMp3(String name) {
        if (name == null) return "";
        if (name.length() > 4 && name.toLowerCase().endsWith(".mp3")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    /** Short label for status / logs. */
    public static String labelForStemId(String id) {
        if ("vocals".equals(id)) return "Vocals";
        if ("drum".equals(id) || "drums".equals(id)) return "Drums";
        if ("bass".equals(id)) return "Bass";
        if ("melody".equals(id) || "other".equals(id)
                || "instruments".equals(id) || "samples".equals(id)) {
            return "Melody";
        }
        if ("piano".equals(id)) return "Piano";
        if ("synthesizer".equals(id)) return "Synth";
        if ("electric_guitar".equals(id)) return "E.Guitar";
        if ("acoustic_guitar".equals(id)) return "A.Guitar";
        if ("strings".equals(id)) return "Strings";
        if ("wind".equals(id)) return "Wind";
        if ("no_multistem".equals(id)) return "Residual";
        return id != null ? id : "";
    }

    private static String[] concat(String[] a, String[] b) {
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
