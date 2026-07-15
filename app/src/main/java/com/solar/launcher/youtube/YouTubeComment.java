package com.solar.launcher.youtube;

/**
 * 2026-07-10 — One YouTube comment row from notPipe Metadata.getComments.
 * Layman: author + text for the messaging-style comments list under a video.
 * Technical: parsed from bridge GET_COMMENTS JSON.
 * Reversal: delete; detail screen shows status only.
 */
public final class YouTubeComment {
    public final String author;
    public final String content;

    public YouTubeComment(String author, String content) {
        this.author = author != null ? author : "";
        this.content = content != null ? content : "";
    }

    /** List subtitle / single-line preview (clamped). */
    public String preview(int maxLen) {
        String t = content.replace('\n', ' ').trim();
        if (maxLen > 0 && t.length() > maxLen) {
            return t.substring(0, maxLen - 1) + "…";
        }
        return t;
    }
}
