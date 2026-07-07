package com.solar.launcher.youtube;

/**
 * 2026-07-06 — IPC action names shared with SolarNotPipeBridge Xposed module.
 * Layman: the vocabulary Solar and notPipe use to talk over broadcasts.
 * Technical: must match solar-notpipe-bridge NotPipeIpc.java exactly.
 * Reversal: delete; NotPipeClient and bridge drift apart.
 */
public final class NotPipeIpc {

    public static final String NOTPIPE_PKG = "io.github.gohoski.notpipe";
    public static final String VIDEO_ACTIVITY = NOTPIPE_PKG + ".VideoActivity";

    public static final String ACTION_CMD = "com.solar.launcher.NOTPIPE_CMD";
    public static final String ACTION_RESULT = "com.solar.launcher.NOTPIPE_RESULT";
    public static final String ACTION_PLAYER_EXITED = "com.solar.launcher.NOTPIPE_PLAYER_EXITED";

    public static final String EXTRA_CMD = "cmd";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_PAYLOAD = "payload";
    public static final String EXTRA_OK = "ok";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_VIDEO_ID = "videoId";
    public static final String EXTRA_QUALITY = "quality";
    /** "video" (default) or "audio" for RESOLVE_STREAM. */
    public static final String EXTRA_STREAM_KIND = "streamKind";
    public static final String STREAM_KIND_VIDEO = "video";
    public static final String STREAM_KIND_AUDIO = "audio";
    public static final String EXTRA_SOLAR_HOSTED = "solar_hosted";
    /** Headless process wake — Xposed finishes MainActivity immediately. */
    public static final String EXTRA_SOLAR_WAKE_ONLY = "solar_wake_only";

    public static final String CMD_PROBE = "PROBE";
    public static final String CMD_POPULAR = "POPULAR";
    public static final String CMD_SEARCH = "SEARCH";
    public static final String CMD_RESOLVE_STREAM = "RESOLVE_STREAM";

    private NotPipeIpc() {}
}
