package com.solar.launcher.radio.fm;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.SystemClock;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.media.MediaSuiteHost;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 2026-07-06 — FM capture via MTK {@link MediaRecorder} FM audio source (JJ / stock FMRadio path).
 * Layman: saves what you hear on the radio into the FM Recordings folder.
 * Technical: THREE_GPP + AAC; folder from {@link DeviceFeatures#getFmRecordingRoots()}.
 */
public final class FmRecorder implements MediaRecorder.OnErrorListener {
    public static final int STATE_IDLE = 0;
    public static final int STATE_RECORDING = 1;
    public static final int STATE_STOPPED = 2;

    public interface Listener {
        void onStateChanged(int state);

        void onError(String message);
    }

    private static final String FILE_PREFIX = "FM_";
    private static final String FILE_EXT = ".3gpp";

    private final Context appCtx;
    private MediaRecorder recorder;
    private File recordFile;
    private int state = STATE_IDLE;
    private long recordStartElapsed;
    private long recordDurationMs;
    private Listener listener;

    public FmRecorder(Context ctx) {
        appCtx = ctx.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int state() {
        return state;
    }

    public boolean isRecording() {
        return state == STATE_RECORDING;
    }

    public File currentFile() {
        return recordFile;
    }

    /** Elapsed ms while recording; frozen after stop. */
    public long recordDurationMs() {
        if (state == STATE_RECORDING) {
            return SystemClock.elapsedRealtime() - recordStartElapsed;
        }
        return recordDurationMs;
    }

    /** Start capture — requires FM already powered and routed. */
    public synchronized void startRecording() {
        if (state == STATE_RECORDING) return;
        releaseRecorderQuiet();
        recordDurationMs = 0;
        File dir = MediaSuiteHost.fmRecordingsDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            notifyError("Could not create FM Recordings folder.");
            return;
        }
        if (dir.exists() && !dir.isDirectory()) {
            notifyError("FM Recordings path is blocked.");
            return;
        }
        String name = FILE_PREFIX + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date())
                + FILE_EXT;
        recordFile = new File(dir, name);
        try {
            if (!recordFile.createNewFile()) {
                // overwrite unlikely — still try
            }
            recorder = new MediaRecorder();
            recorder.setOnErrorListener(this);
            recorder.setAudioSource(resolveFmAudioSource());
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioChannels(2);
            recorder.setOutputFile(recordFile.getAbsolutePath());
            recorder.prepare();
            recordStartElapsed = SystemClock.elapsedRealtime();
            recorder.start();
            state = STATE_RECORDING;
            notifyState();
        } catch (IOException | RuntimeException e) {
            releaseRecorderQuiet();
            if (recordFile != null && recordFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                recordFile.delete();
            }
            recordFile = null;
            state = STATE_IDLE;
            notifyError(e.getMessage() != null ? e.getMessage() : "FM record failed");
        }
    }

    /** Stop capture; file remains on disk for browse / library. */
    public synchronized void stopRecording() {
        if (state != STATE_RECORDING) return;
        recordDurationMs = SystemClock.elapsedRealtime() - recordStartElapsed;
        releaseRecorderQuiet();
        state = STATE_STOPPED;
        notifyState();
    }

    /** Drop in-progress file and reset idle (Back / power-down). */
    public synchronized void discardRecording() {
        stopRecording();
        if (recordFile != null && recordFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            recordFile.delete();
        }
        recordFile = null;
        recordDurationMs = 0;
        state = STATE_IDLE;
        notifyState();
    }

    public synchronized void release() {
        if (state == STATE_RECORDING) {
            stopRecording();
        }
        recordFile = null;
        state = STATE_IDLE;
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        notifyError("Recorder error " + what + "/" + extra);
        discardRecording();
    }

    /** MTK hides FM source on public SDK — reflect FM / FM_TUNER, else JJ constant. */
    static int resolveFmAudioSource() {
        for (String fieldName : new String[] {"FM", "FM_TUNER"}) {
            try {
                Field f = MediaRecorder.AudioSource.class.getField(fieldName);
                return f.getInt(null);
            } catch (Exception ignored) {}
        }
        return 1998;
    }

    private void releaseRecorderQuiet() {
        if (recorder == null) return;
        try {
            recorder.stop();
        } catch (Exception ignored) {}
        try {
            recorder.reset();
        } catch (Exception ignored) {}
        try {
            recorder.release();
        } catch (Exception ignored) {}
        recorder = null;
    }

    private void notifyState() {
        if (listener != null) listener.onStateChanged(state);
    }

    private void notifyError(String msg) {
        if (listener != null) listener.onError(msg);
    }
}
