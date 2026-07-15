package com.solar.launcher.xposed.notpipe;

/**
 * 2026-07-06 — Broadcast IPC contract between Solar and hooked notPipe process.
 * Layman: action names Solar and the bridge agree on for search/play commands.
 * Technical: duplicated in app NotPipeClient — keep in sync when changing.
 * Reversal: delete; Solar YouTube and bridge stop talking.
 */
public final class NotPipeIpc {

    public static final String NOTPIPE_PKG = "io.github.gohoski.notpipe";

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
    public static final String EXTRA_SOLAR_WAKE_ONLY = "solar_wake_only";

    public static final String CMD_PROBE = "PROBE";
    public static final String CMD_POPULAR = "POPULAR";
    public static final String CMD_SEARCH = "SEARCH";
    public static final String CMD_RESOLVE_STREAM = "RESOLVE_STREAM";
    /** Load top-level comments for a video id (Metadata.getComments). */
    public static final String CMD_GET_COMMENTS = "GET_COMMENTS";

    private NotPipeIpc() {}
}
