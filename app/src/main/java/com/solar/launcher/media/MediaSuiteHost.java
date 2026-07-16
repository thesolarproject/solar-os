package com.solar.launcher.media;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;
import com.solar.launcher.DebugAgentLog;
import com.solar.launcher.DebugF9ef0bLog;
import com.solar.launcher.FocusScrollHelper;
import com.solar.launcher.MoveRibbonTouch;
import com.solar.launcher.PlayQueue;
import com.solar.launcher.PlaybackCoordinator;
import com.solar.launcher.R;
import com.solar.launcher.SettingsScreens;
import com.solar.launcher.photos.PhotoLibrary;
import com.solar.launcher.photos.PhotoViewer;
import com.solar.launcher.photos.PhotoWallpaperHelper;
import com.solar.launcher.radio.FmBandPlan;
import com.solar.launcher.radio.RadioScrubMapping;
import com.solar.launcher.radio.RadioScrubMode;
import com.solar.launcher.radio.RadioSettings;
import com.solar.launcher.radio.fm.FmAirplaneModeHelper;
import com.solar.launcher.radio.fm.FmEngine;
import com.solar.launcher.radio.fm.FmJjPresetImport;
import com.solar.launcher.radio.fm.FmPresetStore;
import com.solar.launcher.radio.fm.FmQueueSync;
import com.solar.launcher.radio.fm.FmRecorder;
import com.solar.launcher.radio.fm.FmRdsPoller;
import com.solar.launcher.radio.net.InternetRadioFavorites;
import com.solar.launcher.radio.net.InternetRadioPlayer;
import com.solar.launcher.radio.net.RadioBrowserClient;
import com.solar.launcher.video.VideoLibrary;
import com.solar.launcher.video.VideoPlayerController;
import com.solar.launcher.youtube.YouTubeClient;
import com.solar.launcher.youtube.YouTubeComment;
import com.solar.launcher.youtube.YouTubeDownloader;
import com.solar.launcher.youtube.YouTubeProgressiveCache;
import com.solar.launcher.youtube.YouTubeRecentSearches;
import com.solar.launcher.youtube.YouTubeResultJson;
import com.solar.launcher.youtube.YouTubeSavePaths;
import com.solar.launcher.youtube.YouTubeVideo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tv.danmaku.ijk.media.example.widget.media.SurfaceRenderView;

/**
 * 2026-07-05 — Radio/video/photo browse host; Reach-like screens use full width (no preview pane).
 * Show loading placeholder rows before async work; cancel stale work on navigate-away.
 * When changing: new browse screens — hide settings preview pane, force full screen width.
 * Reversal: extract screens back to MainActivity monolith without layout policy comments.
 */
public final class MediaSuiteHost {

    // --- Screen states (keep in sync with MainActivity wiring) ---
    public static final int STATE_RADIO = 17;
    public static final int STATE_RADIO_FM_BROWSE = 18;
    public static final int STATE_RADIO_NET_BROWSE = 19;
    public static final int STATE_VIDEOS = 20;
    public static final int STATE_VIDEO_PLAYER = 21;
    public static final int STATE_PHOTOS = 22;
    public static final int STATE_PHOTO_VIEWER = 23;
    public static final int STATE_VIDEO_HUB = 27;
    public static final int STATE_YOUTUBE_BROWSE = 28;
    public static final int STATE_RADIO_FM_PLAYER = 29;
    /** Video detail + comments (messaging-style list) — never shows notPipe UI. */
    public static final int STATE_YOUTUBE_DETAIL = 30;

    /** MainActivity player screen — not owned here but used for radio handoff. */
    public static final int STATE_PLAYER = 3;

    // --- Radio browse sub-modes ---
    public static final int RADIO_UI_HUB = 0;
    public static final int RADIO_NET_COUNTRY = 1;
    public static final int RADIO_NET_STATE = 2;
    public static final int RADIO_NET_TAG = 3;
    public static final int RADIO_NET_STATIONS = 4;
    public static final int RADIO_NET_FAVORITES = 5;
    public static final int RADIO_FM_SCAN = 6;
    public static final int RADIO_FM_PRESETS = 7;
    public static final int RADIO_FM_SETTINGS = 8;
    public static final int RADIO_FM_SAVED_CHANNELS = 9;

    /**
     * FM recordings dir for new captures — prefers Primary storage pref volume.
     * 2026-07-15 — Was first existing folder / hardcoded sdcard0; now getNewMediaRoot first.
     */
    public static File fmRecordingsDir() {
        // null ctx still applies smart default (MicroSD if healthy, else Internal).
        File preferred = new File(com.solar.launcher.DeviceFeatures.getNewMediaRoot(null),
                "FM Recordings");
        if (!preferred.exists()) preferred.mkdirs();
        if (preferred.isDirectory()) return preferred;
        for (File dir : com.solar.launcher.DeviceFeatures.getFmRecordingRoots()) {
            if (dir.isDirectory()) return dir;
        }
        java.util.List<File> roots = com.solar.launcher.DeviceFeatures.getFmRecordingRoots();
        return roots.isEmpty()
                ? preferred
                : roots.get(0);
    }

    private static final int NET_PAGE_SIZE = 40;
    private static final String[] FM_BAND_REGIONS = {"US", "EU", "JP", "AU", "KR", "RU"};

    /** Settings row keys — pair with {@link SettingsScreens}. */
    public static final String ROW_AUTO_DETECT = "radio.auto_detect";
    public static final String ROW_BUFFER_SD = "radio.buffer_sd";
    public static final String ROW_VIDEO_SLEEP = "video.sleep_during_playback";
    /** 2026-07-15 — Letterbox vs crop-to-4:3 preference row. */
    public static final String ROW_VIDEO_CROP = "video.crop_mode";

    /** Now-playing scrub state — read/written by MainActivity wheel handlers. */
    public RadioScrubMode radioScrubMode = RadioScrubMode.NONE;
    public int radioTuneFreqKhz;
    private boolean fmSettingsMode;
    private boolean fmTuningMode;
    private long lastFmPowerToggleMs;
    private final List<Integer> fmScanResults = new ArrayList<Integer>();

    /** Host callback — MainActivity implements view + navigation chrome. */
    public interface Host {
        Context context();
        Activity activity();
        SharedPreferences prefs();
        PlaybackCoordinator playback();

        Button createListButton(String label);
        void clickFeedback();
        boolean requireInternet(int toastRes);
        void runOnUiThread(Runnable r);

        void changeScreen(int state);
        int getCurrentScreenState();
        void setBrowserStatusTitle(String title);

        View layoutBrowserMode();
        View layoutPlayerMode();
        View layoutMainMenu();
        View layoutSettingsMode();
        LinearLayout containerBrowserItems();
        ListView listVirtualSongs();

        int getScreenWidthPx();
        int y1RowHeightPx();
        int messagingRowWidthPx();

        void applyReachBrowseLayoutMode();
        void showReachBrowseList(boolean show);

        void pauseMusicPlayback();

        /** Stop and reset file music so radio streams / FM can take audio. */
        void stopMusicPlayback();

        /**
         * 2026-07-15 — Silence music / Deezer / podcast / YouTube / video before FM owns audio.
         * Does not touch the FM chip (caller powers up next).
         */
        void stopNonFmPlayback();

        MediaTransportBar playerTransportBar();

        MediaTransportBar videoTransportBar();

        void resetBrowserListHost();

        void showVirtualSongList(boolean virtual);
        /** Hide/show Solar status bar (clock, battery, Wi‑Fi). */
        void setStatusBarVisible(boolean visible);

        void refreshPlayerUi();

        /** Show FM MHz scrub marker when manual-tune mode is active. */
        void syncFmTuneScrubUi();

        /** Leave media browse and return to Solar home menu (MainActivity STATE_MENU). */
        void exitToHomeMenu();

        /**
         * 2026-07-15 — Leave Music→YouTube browse back to Music hub (STATE_BROWSER).
         * Was: Back always went to Videos hub. Reversal: changeScreen(STATE_VIDEO_HUB) always.
         */
        void exitYouTubeAudioToMusic();

        /** Open wheel keyboard for YouTube search — result delivered via {@link #onYouTubeSearchSubmitted}. */
        void openYouTubeSearchKeyboard(String prefill);

        /** Save YouTube stream via downloader (video or audio-only). */
        void requestYouTubeSave(YouTubeVideo video, boolean audioOnly);

        /**
         * 2026-07-15 — Play a local audio file in music Now Playing (YouTube Audio path).
         * Layman: open the song player with this file. Technical: playTrackList singleton.
         */
        void playAudioFileInNowPlaying(java.io.File file);

        /** Title + subtitle row for virtual browse lists (YouTube, podcasts pattern). */
        View createTwoLineBrowseRow(String title, String subtitle);

        /** Layered fallback — stock MTK FM when native engine fails. 2026-07-06 */
        void offerFmMtkFallback(String errorMessage);

        void showThemedConfirm(
                String title,
                String message,
                String confirmLabel,
                String cancelLabel,
                Runnable onConfirm,
                Runnable onCancel);

        /**
         * 2026-07-15 — True while user is actively typing/scrolling (InputPriorityGate).
         * Background media suite work (RDS JNI, etc.) should yield.
         */
        boolean isInputPriorityBusy();

        /** Ms until input has been idle long enough for background work. */
        long msUntilInputIdle();

        String getString(int resId);
        String getString(int resId, Object arg);
        String getString(int resId, Object arg1, Object arg2);
        android.content.res.Resources getResources();

        <T extends View> T findViewById(int id);
    }

    /** Row descriptor for Settings integration via {@link #buildRadioSettingsRows()}. */
    public static final class SettingsRow {
        public final String rowKey;
        public final int labelResId;
        public final boolean submenu;

        public SettingsRow(String rowKey, int labelResId, boolean submenu) {
            this.rowKey = rowKey;
            this.labelResId = labelResId;
            this.submenu = submenu;
        }
    }

    private final Host host;
    private final FmEngine fmEngine;
    private final FmRdsPoller fmRdsPoller;
    private final FmRecorder fmRecorder;
    private final Handler fmUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable fmRecordUiTick =
            new Runnable() {
                @Override
                public void run() {
                    if (!fmRecorder.isRecording()) return;
                    host.refreshPlayerUi();
                    fmUiHandler.postDelayed(this, 1000L);
                }
            };
    /** MHz before manual tune scrub — Back reverts without leaving NP. 2026-07-06 */
    private int fmTuneRevertKhz;
    /** 2026-07-15 — Headset plug → re-route FM to headphones unless Speaker chosen. */
    private BroadcastReceiver fmHeadsetReceiver;
    private boolean fmHeadsetRegistered;
    private final RadioBrowserClient radioBrowser;
    private final InternetRadioFavorites netFavorites;
    private final FmPresetStore fmPresets;
    private final InternetRadioPlayer internetRadioPlayer;
    private final PhotoViewer photoViewer = new PhotoViewer();

    private int radioSubMode = RADIO_UI_HUB;
    private int netLoadGen;
    private String netCountryCode = "";
    private String netCountryName = "";
    private String netStateName = "";
    private String netTagName = "";
    private List<RadioBrowserClient.Country> netCountries = new ArrayList<RadioBrowserClient.Country>();
    private List<RadioBrowserClient.State> netStates = new ArrayList<RadioBrowserClient.State>();
    private List<RadioBrowserClient.Tag> netTags = new ArrayList<RadioBrowserClient.Tag>();
    private List<RadioBrowserClient.Station> netStations = new ArrayList<RadioBrowserClient.Station>();
    private boolean netLoading;

    private File videoBrowseFolder;
    private List<File> videoFiles = new ArrayList<File>();
    private int videoIndex;
    private boolean videoPlaybackYoutube;
    private String youtubeStreamUrl;
    private final List<YouTubeVideo> youtubeVideos = new ArrayList<YouTubeVideo>();
    private int youtubeLoadGen;
    private boolean youtubeLoading;
    /** 2026-07-14 — Play resolve in progress (detail Play row subtitle); not browse list load. */
    private boolean youtubeResolvingStream;
    /**
     * 2026-07-15 — Human phase for resolve/save (“Getting 480p stream…”) instead of flat Resolving.
     * Empty when idle.
     */
    private String youtubeResolveStatus = "";
    /** 2026-07-14 — Quality used for current/last RESOLVE_STREAM (ladder retries). */
    private String youtubeStreamQuality;
    /** 2026-07-14 — Prevent double quality-fallback from rapid IJK error callbacks. */
    private boolean youtubeIjkFallbackPending;
    private String youtubePendingSearch;
    private String youtubeNowPlayingTitle;
    private String youtubeNowPlayingId;
    /**
     * 2026-07-15 — True when opened from Music hub / home YouTube Audio (music NP, not video).
     * Was: always video path from Videos hub. Reversal: force false; Play always video.
     */
    private boolean youtubeAudioMode;
    /** Focused video on detail/comments screen (Solar-only; notPipe never shown). */
    private YouTubeVideo youtubeDetailVideo;
    private final List<YouTubeComment> youtubeComments = new ArrayList<YouTubeComment>();
    private boolean youtubeCommentsLoading;
    private int youtubeCommentsGen;
    private final List<YoutubeDetailRow> youtubeDetailRows = new ArrayList<YoutubeDetailRow>();
    private VideoPlayerController videoController;
    private SurfaceRenderView videoSurface;
    private final Handler videoProgressHandler = new Handler(Looper.getMainLooper());
    private boolean videoScrubActive;
    private long videoScrubMs;

    private File photoBrowseFolder;
    private List<File> photoFiles = new ArrayList<File>();
    private List<File> photoFolders = new ArrayList<File>();
    private int photoLoadGen;

    private final List<String> virtualLabels = new ArrayList<String>();
    private final List<String> virtualSubtitles = new ArrayList<String>();
    /** Parallel row actions for YouTube virtual list — see {@link #rebuildYouTubeVirtualRows}. */
    private final List<YoutubeBrowseRow> youtubeBrowseRows = new ArrayList<YoutubeBrowseRow>();
    private SimpleListAdapter virtualAdapter;

    /** One YouTube browse list row — maps wheel position to action without fragile index math. */
    private static final class YoutubeBrowseRow {
        static final int KIND_BACK = 0;
        static final int KIND_SEARCH = 1;
        static final int KIND_CLEAR = 2;
        static final int KIND_RECENT = 3;
        static final int KIND_STATUS = 4;
        static final int KIND_VIDEO = 5;

        final int kind;
        final String recentQuery;
        final int videoIndex;

        YoutubeBrowseRow(int kind) {
            this(kind, null, -1);
        }

        YoutubeBrowseRow(int kind, String recentQuery, int videoIndex) {
            this.kind = kind;
            this.recentQuery = recentQuery;
            this.videoIndex = videoIndex;
        }
    }

    /**
     * Detail screen rows — Play / Save + comment thread (Soulseek messaging feel).
     * Layman: pick a video → chat-style comments, then Play stays in Solar.
     */
    private static final class YoutubeDetailRow {
        static final int KIND_BACK = 0;
        static final int KIND_PLAY = 1;
        static final int KIND_SAVE_VIDEO = 2;
        static final int KIND_SAVE_AUDIO = 3;
        static final int KIND_HEADER = 4;
        static final int KIND_COMMENT = 5;
        static final int KIND_STATUS = 6;

        final int kind;
        final int commentIndex;

        YoutubeDetailRow(int kind) {
            this(kind, -1);
        }

        YoutubeDetailRow(int kind, int commentIndex) {
            this.kind = kind;
            this.commentIndex = commentIndex;
        }
    }

    public MediaSuiteHost(Host host) {
        this.host = host;
        Context ctx = host.context();
        fmEngine = new FmEngine(ctx);
        fmRdsPoller = new FmRdsPoller(fmEngine);
        // 2026-07-15 — RDS JNI yields while the user is typing / wheeling elsewhere.
        fmRdsPoller.setDefer(
                new FmRdsPoller.Defer() {
                    @Override
                    public boolean shouldDefer() {
                        // Keep polling when FM NP is on screen — user is looking at the station.
                        int st = host.getCurrentScreenState();
                        if (st == STATE_PLAYER || st == STATE_RADIO_FM_PLAYER) {
                            return false;
                        }
                        return host.isInputPriorityBusy();
                    }

                    @Override
                    public long msUntilAllowed() {
                        return host.msUntilInputIdle();
                    }
                });
        fmRecorder = new FmRecorder(ctx);
        fmRecorder.setListener(
                new FmRecorder.Listener() {
                    @Override
                    public void onStateChanged(int state) {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (state == FmRecorder.STATE_RECORDING) {
                                            fmUiHandler.removeCallbacks(fmRecordUiTick);
                                            fmUiHandler.post(fmRecordUiTick);
                                        } else {
                                            fmUiHandler.removeCallbacks(fmRecordUiTick);
                                        }
                                        host.refreshPlayerUi();
                                    }
                                });
                    }

                    @Override
                    public void onError(final String message) {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(
                                                        host.context(),
                                                        message,
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        host.refreshPlayerUi();
                                    }
                                });
                    }
                });
        fmRdsPoller.setListener(
                new FmRdsPoller.Listener() {
                    @Override
                    public void onRdsChanged(String ps, String rt) {
                        cachedRdsPs = ps;
                        cachedRdsRt = rt;
                        if (ps != null && !ps.isEmpty() && host.playback().isFmActive()) {
                            host.playback()
                                    .updateCurrentFmMeta(currentFmFreqKhz(), ps);
                        }
                        // 2026-07-15 — Light bind only (not full refreshPlayerUi — was freezing NP).
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (host.playback().isFmActive()) {
                                            bindRadioNowPlayingUi();
                                        }
                                    }
                                });
                    }
                });
        radioBrowser = new RadioBrowserClient(ctx);
        netFavorites = InternetRadioFavorites.getInstance(ctx);
        fmPresets = FmPresetStore.getInstance(ctx);
        internetRadioPlayer = new InternetRadioPlayer(ctx);
        internetRadioPlayer.setListener(
                new InternetRadioPlayer.Listener() {
                    @Override
                    public void onPrepared() {}

                    @Override
                    public void onPlaying() {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        host.refreshPlayerUi();
                                    }
                                });
                    }

                    @Override
                    public void onStopped() {}

                    @Override
                    public void onError(final String reason) {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        // #region agent log
                                        try {
                                            DebugAgentLog.log(
                                                    host.context(),
                                                    "InternetRadioPlayer",
                                                    "error",
                                                    "E",
                                                    new org.json.JSONObject().put("reason", reason));
                                        } catch (Exception ignored) {}
                                        // #endregion
                                        if (host.playback().isInternetRadioActive()) {
                                            Toast.makeText(
                                                            host.context(),
                                                            R.string.radio_net_play_error,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                        }
                                    }
                                });
                    }
                });
        radioTuneFreqKhz = defaultFmKhz();
    }

    public static boolean isMediaSuiteState(int state) {
        return (state >= STATE_RADIO && state <= STATE_PHOTO_VIEWER)
                || state == STATE_VIDEO_HUB || state == STATE_YOUTUBE_BROWSE
                || state == STATE_YOUTUBE_DETAIL
                || state == STATE_RADIO_FM_PLAYER;
    }

    /** Browse/list screens that share MainActivity browser wheel + focus (not full-screen player/viewer). */
    public static boolean isMediaListBrowseState(int state) {
        return state == STATE_RADIO || state == STATE_RADIO_FM_BROWSE
                || state == STATE_RADIO_FM_PLAYER
                || state == STATE_RADIO_NET_BROWSE || state == STATE_VIDEOS
                || state == STATE_VIDEO_HUB || state == STATE_YOUTUBE_BROWSE
                || state == STATE_YOUTUBE_DETAIL
                || state == STATE_PHOTOS;
    }

    public int radioSubMode() {
        return radioSubMode;
    }

    public InternetRadioPlayer internetRadioPlayer() {
        return internetRadioPlayer;
    }

    public FmEngine fmEngine() {
        return fmEngine;
    }

    // --- Lifecycle ---

    public void onScreenEnter(int state) {
        hideVideoAndPhotoLayers();
        switch (state) {
            case STATE_RADIO:
                radioSubMode = RADIO_UI_HUB;
                buildRadioHubUi();
                break;
            case STATE_RADIO_FM_BROWSE:
                radioSubMode = RADIO_UI_HUB;
                buildFmBrowseUi();
                break;
            case STATE_RADIO_FM_PLAYER:
                fmSettingsMode = false;
                fmTuningMode = false;
                buildFmPlayerUi();
                break;
            case STATE_RADIO_NET_BROWSE:
                if (radioSubMode == RADIO_UI_HUB) radioSubMode = RADIO_NET_COUNTRY;
                buildNetBrowseUi();
                break;
            case STATE_VIDEO_HUB:
                buildVideoHubUi();
                break;
            case STATE_VIDEOS:
                buildVideosUi();
                break;
            case STATE_YOUTUBE_BROWSE:
                buildYouTubeBrowseUi();
                break;
            case STATE_YOUTUBE_DETAIL:
                buildYouTubeDetailUi();
                break;
            case STATE_VIDEO_PLAYER:
                showVideoPlayerLayer(true);
                beginVideoForceLandscapeSession();
                startVideoPlayback();
                break;
            case STATE_PHOTOS:
                buildPhotosUi();
                break;
            case STATE_PHOTO_VIEWER:
                showPhotoViewerLayer(true);
                bindPhotoViewerImage();
                break;
            default:
                break;
        }
    }

    public void onScreenExit(int state) {
        switch (state) {
            case STATE_RADIO_NET_BROWSE:
                netLoadGen++;
                netLoading = false;
                break;
            case STATE_YOUTUBE_BROWSE:
                youtubeLoadGen++;
                youtubeLoading = false;
                youtubeResolvingStream = false;
                break;
            case STATE_YOUTUBE_DETAIL:
                youtubeCommentsGen++;
                youtubeCommentsLoading = false;
                youtubeResolvingStream = false;
                break;
            case STATE_VIDEO_PLAYER:
                releaseVideoPlayer();
                showVideoPlayerLayer(false);
                onVideoPlaybackStopped();
                endVideoForceLandscapeSession();
                break;
            case STATE_PHOTO_VIEWER:
                showPhotoViewerLayer(false);
                break;
            case STATE_RADIO_FM_BROWSE:
                if (radioSubMode == RADIO_FM_SCAN) {
                    fmEngine.stopScan();
                    radioSubMode = RADIO_UI_HUB;
                }
                break;
            case STATE_RADIO_FM_PLAYER:
                if (radioSubMode == RADIO_FM_SCAN) {
                    fmEngine.stopScan();
                }
                break;
            default:
                break;
        }
        clearVirtualList();
    }

    /** Rebuild visible tier after rotation or state restore. */
    public void rebuildUi(int state) {
        onScreenEnter(state);
    }

    public String statusTitleForState(int state) {
        switch (state) {
            case STATE_RADIO:
                return host.getString(R.string.status_radio);
            case STATE_RADIO_FM_BROWSE:
                return host.getString(R.string.status_radio_fm);
            case STATE_RADIO_FM_PLAYER:
                return host.getString(R.string.status_radio_fm);
            case STATE_RADIO_NET_BROWSE:
                return host.getString(R.string.status_radio_internet);
            case STATE_VIDEO_HUB:
                return host.getString(R.string.status_videos);
            case STATE_VIDEOS:
                return host.getString(R.string.status_videos);
            case STATE_YOUTUBE_BROWSE:
                return host.getString(R.string.status_youtube);
            case STATE_YOUTUBE_DETAIL:
                if (youtubeDetailVideo != null && youtubeDetailVideo.title.length() > 0) {
                    return youtubeDetailVideo.title;
                }
                return host.getString(R.string.status_youtube_detail);
            case STATE_VIDEO_PLAYER:
                if (videoPlaybackYoutube && youtubeNowPlayingTitle != null
                        && youtubeNowPlayingTitle.length() > 0) {
                    return youtubeNowPlayingTitle;
                }
                return host.getString(R.string.status_video_player);
            case STATE_PHOTOS:
                return host.getString(R.string.status_photos);
            case STATE_PHOTO_VIEWER:
                return host.getString(R.string.status_photo_viewer);
            default:
                return "";
        }
    }

    // --- Back navigation ---

    public boolean handleBack() {
        int state = host.getCurrentScreenState();
        switch (state) {
            case STATE_PHOTO_VIEWER:
                host.changeScreen(STATE_PHOTOS);
                return true;
            case STATE_PHOTOS:
                if (photoBrowseFolder != null) {
                    photoBrowseFolder = null;
                    buildPhotosUi();
                    return true;
                }
                host.exitToHomeMenu();
                return true;
            case STATE_VIDEO_PLAYER:
                if (videoScrubActive) {
                    cancelVideoScrub();
                    return true;
                }
                leaveVideoPlayerToBrowse();
                return true;
            case STATE_YOUTUBE_DETAIL:
                youtubeDetailVideo = null;
                youtubeComments.clear();
                host.changeScreen(STATE_YOUTUBE_BROWSE);
                return true;
            case STATE_YOUTUBE_BROWSE:
                // 2026-07-15 — Music entry returns to Music hub; Videos entry to video hub.
                if (youtubeAudioMode) {
                    host.exitYouTubeAudioToMusic();
                } else {
                    host.changeScreen(STATE_VIDEO_HUB);
                }
                return true;
            case STATE_VIDEOS:
                if (videoBrowseFolder == null) {
                    videoBrowseFolder = VideoLibrary.ROOT;
                }
                File parent = VideoLibrary.browseParent(videoBrowseFolder);
                if (parent != null) {
                    videoBrowseFolder = parent;
                    buildVideosUi();
                    return true;
                }
                videoBrowseFolder = null;
                host.changeScreen(STATE_VIDEO_HUB);
                return true;
            case STATE_VIDEO_HUB:
                host.exitToHomeMenu();
                return true;
            case STATE_RADIO_FM_BROWSE:
                if (radioSubMode == RADIO_FM_SCAN) {
                    fmEngine.stopScan();
                    radioSubMode = RADIO_UI_HUB;
                    buildFmBrowseUi();
                    return true;
                }
                if (radioSubMode == RADIO_FM_PRESETS) {
                    radioSubMode = RADIO_UI_HUB;
                    buildFmBrowseUi();
                    return true;
                }
                // 2026-07-15 — Leave FM domain only after Exit confirm when hardware is live.
                requestExitFmThen(new Runnable() {
                    @Override
                    public void run() {
                        if (!com.solar.launcher.radio.RadioExperiment.isInternetRadioEnabled(
                                host.prefs())) {
                            host.exitToHomeMenu();
                        } else {
                            host.changeScreen(STATE_RADIO);
                        }
                    }
                });
                return true;
            case STATE_RADIO_FM_PLAYER:
                if (radioSubMode == RADIO_FM_SAVED_CHANNELS) {
                    radioSubMode = RADIO_FM_SETTINGS;
                    fmSettingsMode = true;
                    buildFmSettingsSubmenuUi();
                    return true;
                }
                if (radioSubMode == RADIO_FM_SCAN) {
                    fmEngine.stopScan();
                    radioSubMode = RADIO_FM_SETTINGS;
                    fmSettingsMode = true;
                    buildFmSettingsSubmenuUi();
                    return true;
                }
                if (fmSettingsMode) {
                    fmSettingsMode = false;
                    fmTuningMode = false;
                    buildFmPlayerUi();
                    return true;
                }
                // 2026-07-15 — Root of FM shell: Exit confirm so chip powers down cleanly.
                requestExitFmThen(new Runnable() {
                    @Override
                    public void run() {
                        host.exitToHomeMenu();
                    }
                });
                return true;
            case STATE_RADIO_NET_BROWSE:
                return handleNetBrowseBack();
            case STATE_RADIO:
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("action", "exitToHomeMenu");
                    DebugAgentLog.log(host.context(), "MediaSuiteHost.handleBack", "radio root back", "H-BACK", d);
                } catch (Exception ignored) {}
                // #endregion
                host.exitToHomeMenu();
                return true;
            default:
                return false;
        }
    }

    private boolean handleNetBrowseBack() {
        switch (radioSubMode) {
            case RADIO_NET_STATIONS:
                if (netTagName != null && !netTagName.isEmpty()) {
                    radioSubMode = RADIO_NET_TAG;
                } else if (netStateName != null && !netStateName.isEmpty()) {
                    radioSubMode = RADIO_NET_STATE;
                } else {
                    radioSubMode = RADIO_NET_COUNTRY;
                }
                buildNetBrowseUi();
                return true;
            case RADIO_NET_TAG:
                if (netStateName != null && !netStateName.isEmpty()) {
                    radioSubMode = RADIO_NET_STATE;
                } else {
                    radioSubMode = RADIO_NET_COUNTRY;
                }
                buildNetBrowseUi();
                return true;
            case RADIO_NET_STATE:
                radioSubMode = RADIO_NET_COUNTRY;
                buildNetBrowseUi();
                return true;
            case RADIO_NET_FAVORITES:
            case RADIO_NET_COUNTRY:
                host.changeScreen(STATE_RADIO);
                return true;
            default:
                host.changeScreen(STATE_RADIO);
                return true;
        }
    }

    // --- Radio hub ---

    private void buildRadioHubUi() {
        prepareScrollBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(false);
        host.setBrowserStatusTitle(host.getString(R.string.status_radio));
        addBackRow(host.getString(R.string.radio_back_home));

        addActionRow(host.getString(R.string.radio_fm_row), new Runnable() {
            @Override
            public void run() {
                host.changeScreen(STATE_RADIO_FM_BROWSE);
            }
        });
        if (com.solar.launcher.radio.RadioExperiment.isInternetRadioEnabled(host.prefs())) {
            addActionRow(host.getString(R.string.radio_internet_row), new Runnable() {
                @Override
                public void run() {
                    if (!host.requireInternet(R.string.toast_internet_required)) return;
                    radioSubMode = RADIO_NET_COUNTRY;
                    host.changeScreen(STATE_RADIO_NET_BROWSE);
                }
            });
        }
        focusFirstBrowserChild();
    }

    // --- FM browse ---

    private void buildFmBrowseUi() {
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.status_radio_fm));
        addBackRow(host.getString(R.string.common_back_short));

        if (!fmEngine.isAvailable()) {
            addStatusRow(host.getString(R.string.radio_fm_unavailable));
            focusFirstBrowserChild();
            return;
        }

        addActionRow(host.getString(R.string.radio_fm_tune_manual), new Runnable() {
            @Override
            public void run() {
                FmBandPlan plan = currentFmPlan();
                radioTuneFreqKhz = plan.clampKhz(radioTuneFreqKhz > 0 ? radioTuneFreqKhz : defaultFmKhz());
                startFmStation(radioTuneFreqKhz, FmBandPlan.khzToFraction(radioTuneFreqKhz, plan));
            }
        });
        addActionRow(host.getString(R.string.radio_fm_scan), new Runnable() {
            @Override
            public void run() {
                startFmScan();
            }
        });
        addActionRow(host.getString(R.string.radio_fm_presets), new Runnable() {
            @Override
            public void run() {
                buildFmPresetsUi();
            }
        });
        if (host.playback().isFmActive() && fmEngine.isPowerUp()) {
            addActionRow(
                    fmRecorder.isRecording()
                            ? host.getString(R.string.radio_fm_record_stop)
                            : host.getString(R.string.radio_fm_record_start),
                    new Runnable() {
                        @Override
                        public void run() {
                            toggleFmRecording();
                        }
                    });
        }
        addActionRow(host.getString(R.string.radio_fm_recordings), new Runnable() {
            @Override
            public void run() {
                openFmRecordingsFolder();
            }
        });
        if (isFmSessionLive()) {
            addActionRow(host.getString(R.string.radio_fm_exit_row), new Runnable() {
                @Override
                public void run() {
                    promptExitFmToHome();
                }
            });
        }
        focusFirstBrowserChild();
    }

    /**
     * Home menu FM — import JJ presets once, open NP, auto-start last frequency.
     * 2026-07-15 — Prefer last tuned kHz (then session dial, then first preset, then band default).
     * Was: always jumped to first preset when any existed. Reversal: presets.get(0) wins again.
     */
    public void openFmFromHome() {
        FmJjPresetImport.importIfEmpty(host.context());
        if (!fmEngine.isAvailable()) {
            host.offerFmMtkFallback(host.getString(R.string.radio_fm_unavailable));
            return;
        }
        if (host.playback().isFmActive()) {
            host.changeScreen(STATE_PLAYER);
            host.refreshPlayerUi();
            return;
        }
        int khz = resolvePreferredFmKhz();
        startFmStation(khz, null);
    }

    /**
     * 2026-07-15 — True when FM chip/session is live (user must Exit to leave Solar FM).
     * Layman: radio is on or Solar is holding the FM RF session.
     */
    public boolean isFmSessionLive() {
        return host.playback().isFmActive()
                || fmEngine.isPowerUp()
                || FmAirplaneModeHelper.isSessionActive();
    }

    /**
     * 2026-07-15 — Back from shared Now Playing while FM is active → FM shell (not home).
     * Keeps hardware up; Exit from shell powers down.
     */
    public void leaveFmNowPlayingToShell() {
        fmSettingsMode = false;
        fmTuningMode = false;
        radioSubMode = RADIO_UI_HUB;
        host.changeScreen(STATE_RADIO_FM_PLAYER);
        buildFmPlayerUi();
    }

    /**
     * 2026-07-15 — Power down FM hardware + clear radio queue (no UI).
     * Layman: turn the radio off and free Wi‑Fi/airplane snapshot.
     * Technical: stop record/RDS/headset, powerDown (ends airplane session), stopRadio.
     */
    public void shutdownFmSession() {
        try {
            stopFmRecordingQuiet();
        } catch (Throwable ignored) {}
        try {
            stopFmRdsPolling();
        } catch (Throwable ignored) {}
        try {
            releaseFmHeadsetRouting();
        } catch (Throwable ignored) {}
        try {
            if (fmEngine.isPowerUp() || FmAirplaneModeHelper.isSessionActive()) {
                fmEngine.powerDown();
            }
        } catch (Throwable ignored) {}
        try {
            host.playback().stopRadio();
        } catch (Throwable ignored) {}
        fmMuted = false;
        fmTuningMode = false;
        radioScrubMode = RadioScrubMode.NONE;
        cachedRdsPs = null;
        cachedRdsRt = null;
    }

    /**
     * 2026-07-15 — If FM is live, show "Exit FM Radio?" then run {@code afterExit}; else run now.
     * Used for Back from FM menus/player and the Exit row.
     */
    public void requestExitFmThen(final Runnable afterExit) {
        if (!isFmSessionLive()) {
            if (afterExit != null) afterExit.run();
            return;
        }
        host.showThemedConfirm(
                host.getString(R.string.radio_fm_exit_title),
                host.getString(R.string.radio_fm_exit_message),
                host.getString(R.string.radio_fm_exit_confirm),
                host.getString(R.string.common_cancel),
                new Runnable() {
                    @Override
                    public void run() {
                        shutdownFmSession();
                        if (afterExit != null) afterExit.run();
                    }
                },
                null);
    }

    /** Explicit Exit row / context action — confirm then home. */
    public void promptExitFmToHome() {
        requestExitFmThen(new Runnable() {
            @Override
            public void run() {
                host.exitToHomeMenu();
            }
        });
    }

    /**
     * 2026-07-15 — Best FM dial for cold start: last saved → session → first preset → band mid.
     * Layman: reopen radio where you left it.
     */
    private int resolvePreferredFmKhz() {
        FmBandPlan plan = currentFmPlan();
        int last = RadioSettings.getLastFmKhz(host.context());
        if (last > 0) return plan.clampKhz(last);
        if (radioTuneFreqKhz > 0) return plan.clampKhz(radioTuneFreqKhz);
        List<FmPresetStore.Preset> presets = fmPresets.listAll();
        if (!presets.isEmpty()) return plan.clampKhz(presets.get(0).freqKhz);
        return defaultFmKhz();
    }

    /**
     * 2026-07-15 — FM player shell inspired by JJ: neon dial, candy presets, bottom settings.
     * Layman: big station number, saved channels, then power / fine-tune / audio / settings.
     * Technical: theme tokens only; wheel keeps list; candy strip for quick presets (JJ).
     * Fine tune = TUNE_FM scrub on wheel (same as NP).
     */
    private void buildFmPlayerUi() {
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.status_radio_fm));
        if (radioSubMode != RADIO_FM_SETTINGS && radioSubMode != RADIO_FM_SAVED_CHANNELS
                && radioSubMode != RADIO_FM_SCAN) {
            fmSettingsMode = false;
        }

        if (!fmEngine.isAvailable()) {
            addStatusRow(host.getString(R.string.radio_fm_unavailable));
            focusFirstBrowserChild();
            return;
        }

        // Headphone jack → assert headphone route whenever we paint the shell.
        if (fmEngine.isPowerUp()) {
            fmEngine.onHeadsetPlug(fmEngine.isWiredHeadsetOn());
        }

        final FmBandPlan plan = currentFmPlan();
        int khz = host.playback().isFmActive() ? currentFmFreqKhz() : radioTuneFreqKhz;
        if (khz <= 0) khz = resolvePreferredFmKhz();
        if (fmTuningMode && radioTuneFreqKhz > 0) khz = radioTuneFreqKhz;
        final boolean powered = fmEngine.isPowerUp() || host.playback().isFmActive();
        final float mhzF = khz / 1000f;

        // JJ layout order: dial → RDS → candy presets → actions → settings last.
        addFmDialPanel(mhzF, powered || fmTuningMode);

        if (powered) {
            String ps = cachedRdsPs;
            if (ps != null && !ps.isEmpty()) {
                addStatusRow(ps);
            } else if (fmEngine.isPowerUp()) {
                addStatusRow(host.getString(
                        fmEngine.isStereo() ? R.string.radio_fm_stereo : R.string.radio_fm_mono));
            }
            if (!fmEngine.isAudioPlaying() && fmEngine.isPowerUp()) {
                addStatusRow(host.getString(R.string.radio_fm_audio_silent_hint));
            }
        } else if (!fmTuningMode) {
            addStatusRow(host.getString(R.string.radio_fm_power_off_hint));
        }

        final List<FmPresetStore.Preset> presets = fmPresets.listAll();
        // JJ always shows candy when stations exist (touch + visual; wheel uses list below).
        if (!presets.isEmpty()) {
            addFmPresetCandyStrip(presets, plan, khz);
        }

        if (!powered) {
            addActionRow(host.getString(R.string.radio_fm_power_on_row), new Runnable() {
                @Override
                public void run() {
                    startFmStation(resolvePreferredFmKhz(), null);
                }
            });
        }

        final String tuneLabel = fmTuningMode
                ? host.getString(R.string.radio_fm_fine_tune_active)
                : host.getString(R.string.radio_fm_fine_tune);
        addActionRow(tuneLabel, new Runnable() {
            @Override
            public void run() {
                toggleFmFineTuneFromPlayer();
            }
        });

        // Audio: Wired / Bluetooth / Speaker — always visible on main shell (JJ speaker toggle).
        addActionRow(
                host.getString(R.string.radio_fm_audio_output_row) + ": " + fmAudioOutputLabel(),
                new Runnable() {
                    @Override
                    public void run() {
                        fmEngine.cycleAudioOutput();
                        // If jack is in, cycling away from Speaker still lands on headphones.
                        if (fmEngine.isWiredHeadsetOn()
                                && fmEngine.audioOutput()
                                        != com.solar.launcher.radio.fm.FmAudioRouter.Output.SPEAKER) {
                            fmEngine.setAudioOutput(
                                    com.solar.launcher.radio.fm.FmAudioRouter.Output.WIRED);
                        }
                        buildFmPlayerUi();
                        if (host.playback().isFmActive()) host.refreshPlayerUi();
                    }
                });

        addActionRow(host.getString(R.string.radio_fm_scan), new Runnable() {
            @Override
            public void run() {
                if (!fmEngine.isPowerUp()) {
                    startFmStationThenScan();
                    return;
                }
                startFmScanReplacePresets();
            }
        });

        if (presets.isEmpty()) {
            addStatusRow(host.getString(R.string.radio_fm_no_presets));
        } else {
            addStatusRow(host.getString(R.string.radio_fm_presets_header, presets.size()));
            for (final FmPresetStore.Preset p : presets) {
                String label =
                        p.label != null && !p.label.isEmpty()
                                ? p.label
                                : FmBandPlan.khzToFraction(p.freqKhz, plan) + " MHz";
                boolean onAir = Math.abs(p.freqKhz - khz) < 50 && powered;
                if (onAir) label = "▶ " + label;
                addActionRow(label, new Runnable() {
                    @Override
                    public void run() {
                        fmTuningMode = false;
                        radioScrubMode = RadioScrubMode.NONE;
                        startFmStation(p.freqKhz, p.label, true);
                    }
                });
            }
        }

        // 2026-07-15 — Explicit Exit so hardware power-down is intentional (Wi‑Fi restore).
        if (powered) {
            addActionRow(host.getString(R.string.radio_fm_exit_row), new Runnable() {
                @Override
                public void run() {
                    promptExitFmToHome();
                }
            });
        }

        // JJ: settings button sits at bottom of player shell.
        addActionRow(host.getString(R.string.radio_fm_settings_row), new Runnable() {
            @Override
            public void run() {
                fmSettingsMode = true;
                radioSubMode = RADIO_FM_SETTINGS;
                buildFmSettingsSubmenuUi();
            }
        });

        // JJ focuses the bottom controls after paint.
        LinearLayout box = host.containerBrowserItems();
        if (box != null && box.getChildCount() > 0) {
            final View last = box.getChildAt(box.getChildCount() - 1);
            box.post(new Runnable() {
                @Override
                public void run() {
                    if (last != null) last.requestFocus();
                }
            });
        } else {
            focusFirstBrowserChild();
        }
    }

    /**
     * 2026-07-15 — Enter/exit fine-tune from the FM list (same TUNE_FM scrub as Now Playing).
     * Layman: wheel becomes a dial; OK again saves the frequency.
     */
    private void toggleFmFineTuneFromPlayer() {
        fmTuningMode = !fmTuningMode;
        if (fmTuningMode) {
            radioScrubMode = RadioScrubMode.TUNE_FM;
            radioTuneFreqKhz = currentFmFreqKhz();
            if (radioTuneFreqKhz <= 0) radioTuneFreqKhz = resolvePreferredFmKhz();
            fmTuneRevertKhz = radioTuneFreqKhz;
            if (!fmEngine.isPowerUp()) {
                // Need chip live to hear while scrubbing.
                startFmStation(radioTuneFreqKhz, null);
            }
            if (host.playback().isFmActive()) {
                host.syncFmTuneScrubUi();
            }
        } else {
            radioScrubMode = RadioScrubMode.NONE;
            commitFmTuneScrub();
            if (host.playback().isFmActive()) {
                host.refreshPlayerUi();
            }
        }
        buildFmPlayerUi();
    }

    /** Power on then replace-presets scan (browse path). */
    private void startFmStationThenScan() {
        final int khz = resolvePreferredFmKhz();
        final FmBandPlan plan = currentFmPlan();
        radioTuneFreqKhz = plan.clampKhz(khz);
        if (!fmEngine.isAvailable()) {
            Toast.makeText(host.context(), R.string.toast_fm_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        stopOtherRadioPlayback(true);
        host.stopNonFmPlayback();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = fmEngine.playStation(radioTuneFreqKhz);
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            String err = fmEngine.lastError();
                            if (err == null || err.isEmpty()) {
                                err = host.getString(R.string.radio_fm_play_error);
                            }
                            Toast.makeText(host.context(), err, Toast.LENGTH_LONG).show();
                            host.offerFmMtkFallback(err);
                            return;
                        }
                        finishFmStationStart(radioTuneFreqKhz, null, plan);
                        startFmScanReplacePresets();
                    }
                });
            }
        }, "FmStartScan").start();
    }

    /**
     * 2026-07-15 — JJ neon dial: large MHz readout using theme focus colour when powered.
     * Layman: big station number that lights up when the radio is on.
     * Technical: ThemeManager list-focus colours + GradientDrawable panel; no hard-coded brand palette.
     */
    private void addFmDialPanel(float mhz, boolean powered) {
        Context ctx = host.context();
        float density = ctx.getResources().getDisplayMetrics().density;
        FrameLayout freqPanel = new FrameLayout(ctx);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Tighter on A5 240p; roomier on Y1/Y2 480×360.
        int side = com.solar.launcher.DeviceFeatures.isA5()
                ? Math.round(8 * density) : Math.round(14 * density);
        int vPad = com.solar.launcher.DeviceFeatures.isA5()
                ? Math.round(10 * density) : Math.round(28 * density);
        panelLp.setMargins(side, Math.round(10 * density), side, Math.round(8 * density));
        freqPanel.setLayoutParams(panelLp);

        int themeHighlight = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setShape(GradientDrawable.RECTANGLE);
        panelBg.setCornerRadius(12 * density);
        if (powered) {
            int backlit = (themeHighlight & 0x00FFFFFF) | 0x42000000;
            panelBg.setColor(backlit);
            panelBg.setStroke(Math.max(1, Math.round(3 * density)), themeHighlight);
        } else {
            // Dim chrome when off — still themed secondary, not pure black invent.
            int dim = ThemeManager.getListButtonNormalBg();
            if ((dim & 0xFF000000) == 0) dim = 0x22FFFFFF;
            panelBg.setColor(dim);
            panelBg.setStroke(Math.max(1, Math.round(1 * density)), 0x33FFFFFF);
        }
        freqPanel.setBackground(panelBg);

        TextView tvFreq = new TextView(ctx);
        tvFreq.setTag("fm_dial_freq");
        tvFreq.setText(String.format(java.util.Locale.US, "%.1f MHz", mhz));
        tvFreq.setTextColor(powered ? themeHighlight : ThemeManager.getTextColorSecondary());
        // JJ uses ~54sp on Y1; A5 240p stays smaller.
        tvFreq.setTextSize(com.solar.launcher.DeviceFeatures.isA5() ? 28f : 50f);
        tvFreq.setGravity(Gravity.CENTER);
        try {
            tvFreq.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
        } catch (Throwable ignored) {
            tvFreq.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        tvFreq.setPadding(0, vPad, 0, vPad);
        // Dial is chrome, not a focus trap — wheel keeps list rows.
        tvFreq.setFocusable(false);
        tvFreq.setClickable(false);
        freqPanel.setFocusable(false);
        freqPanel.addView(tvFreq);
        host.containerBrowserItems().addView(freqPanel);
    }

    /**
     * 2026-07-15 — JJ candy presets: horizontal themed pills for A5 touch.
     * Layman: swipe/tap saved stations like JJ.
     * Technical: HorizontalScrollView; each pill starts FM; current freq uses focus colours.
     */
    private void addFmPresetCandyStrip(
            List<FmPresetStore.Preset> presets, final FmBandPlan plan, int currentKhz) {
        Context ctx = host.context();
        float density = ctx.getResources().getDisplayMetrics().density;
        android.widget.HorizontalScrollView hzScroll = new android.widget.HorizontalScrollView(ctx);
        hzScroll.setHorizontalScrollBarEnabled(false);
        hzScroll.setClipChildren(false);
        hzScroll.setClipToPadding(false);
        hzScroll.setFillViewport(true);
        hzScroll.setPadding(0, Math.round(6 * density), 0, Math.round(6 * density));
        hzScroll.setFocusable(false);

        LinearLayout candyContainer = new LinearLayout(ctx);
        candyContainer.setOrientation(LinearLayout.HORIZONTAL);
        candyContainer.setGravity(Gravity.CENTER_VERTICAL);

        int themeHighlight = ThemeManager.getListButtonFocusedBg() | 0xFF000000;
        int focusedText = ThemeManager.getListButtonFocusedTextColor();
        int normalBg = ThemeManager.getListButtonNormalBg();
        int secondary = ThemeManager.getTextColorSecondary();

        View targetScrollChild = null;
        for (int i = 0; i < presets.size(); i++) {
            final FmPresetStore.Preset p = presets.get(i);
            final int pkhz = p.freqKhz;
            String label =
                    p.label != null && !p.label.isEmpty()
                            ? p.label
                            : FmBandPlan.khzToFraction(pkhz, plan);
            TextView tvCandy = new TextView(ctx);
            tvCandy.setText(label);
            tvCandy.setTextSize(com.solar.launcher.DeviceFeatures.isA5() ? 14f : 16f);
            tvCandy.setGravity(Gravity.CENTER);
            tvCandy.setPadding(
                    Math.round(12 * density), Math.round(5 * density),
                    Math.round(12 * density), Math.round(5 * density));
            try {
                tvCandy.setTypeface(ThemeManager.getCustomFont(), android.graphics.Typeface.BOLD);
            } catch (Throwable ignored) {}
            tvCandy.setFocusable(true);
            tvCandy.setClickable(true);

            GradientDrawable candyBg = new GradientDrawable();
            candyBg.setCornerRadius(16 * density);
            boolean selected = Math.abs(currentKhz - pkhz) < 50;
            if (selected) {
                candyBg.setColor(themeHighlight);
                tvCandy.setTextColor(focusedText);
                targetScrollChild = tvCandy;
            } else {
                candyBg.setColor(normalBg);
                tvCandy.setTextColor(secondary);
            }
            tvCandy.setBackground(candyBg);

            LinearLayout.LayoutParams candyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            candyLp.setMargins(Math.round(4 * density), 0, Math.round(4 * density), 0);
            tvCandy.setLayoutParams(candyLp);
            tvCandy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.clickFeedback();
                    startFmStation(pkhz, p.label, true);
                    buildFmPlayerUi();
                }
            });
            candyContainer.addView(tvCandy);
        }

        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hzScroll.addView(candyContainer, containerLp);
        host.containerBrowserItems().addView(hzScroll);

        if (targetScrollChild != null) {
            final View focusChild = targetScrollChild;
            final android.widget.HorizontalScrollView scroll = hzScroll;
            hzScroll.post(new Runnable() {
                @Override
                public void run() {
                    int scrollX = focusChild.getLeft() - (scroll.getWidth() / 2)
                            + (focusChild.getWidth() / 2);
                    if (scrollX < 0) scrollX = 0;
                    scroll.scrollTo(scrollX, 0);
                }
            });
        }
    }

    /** FM settings submenu — power, tune, save, scan, speaker (JJ parity). */
    private void buildFmSettingsSubmenuUi() {
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.radio_fm_settings_title));
        addBackRow(host.getString(R.string.common_back_short));

        final FmBandPlan plan = currentFmPlan();
        int khz = currentFmFreqKhz();
        if (khz <= 0) khz = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : defaultFmKhz();

        final boolean powered = fmEngine.isPowerUp();
        addActionRow(
                host.getString(R.string.radio_fm_power_row)
                        + ": "
                        + host.getString(powered ? R.string.common_on : R.string.common_off),
                new Runnable() {
                    @Override
                    public void run() {
                        long now = System.currentTimeMillis();
                        if (now - lastFmPowerToggleMs < 1500L) {
                            Toast.makeText(
                                            host.context(),
                                            R.string.radio_fm_power_wait,
                                            Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }
                        lastFmPowerToggleMs = now;
                        if (fmEngine.isPowerUp() || host.playback().isFmActive()) {
                            // Power off = full session exit (Wi‑Fi restore); stay on settings shell.
                            shutdownFmSession();
                        } else {
                            int f = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : defaultFmKhz();
                            startFmStation(f, null);
                        }
                        buildFmSettingsSubmenuUi();
                    }
                });

        String tuneHint =
                fmTuningMode
                        ? host.getString(R.string.radio_fm_tuning_active)
                        : host.getString(R.string.radio_fm_tune_click);
        addActionRow(host.getString(R.string.radio_fm_tune_row) + " — " + tuneHint, new Runnable() {
            @Override
            public void run() {
                fmTuningMode = !fmTuningMode;
                if (fmTuningMode) {
                    radioScrubMode = RadioScrubMode.TUNE_FM;
                    radioTuneFreqKhz = currentFmFreqKhz();
                    fmTuneRevertKhz = radioTuneFreqKhz;
                } else {
                    radioScrubMode = RadioScrubMode.NONE;
                }
                buildFmSettingsSubmenuUi();
                // 2026-07-06 — NP transport scrub mirrors settings tune toggle when FM is foreground.
                if (host.playback().isFmActive()) {
                    if (fmTuningMode) {
                        host.syncFmTuneScrubUi();
                    } else {
                        host.refreshPlayerUi();
                    }
                }
            }
        });

        final int saveKhz = khz;
        final boolean isSaved = fmPresets.containsFreq(saveKhz);
        addActionRow(
                host.getString(isSaved ? R.string.radio_fm_channel_saved : R.string.radio_fm_save_channel),
                new Runnable() {
                    @Override
                    public void run() {
                        if (isSaved) {
                            fmPresets.delete(saveKhz);
                            Toast.makeText(host.context(), R.string.radio_fm_channel_removed, Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            fmPresets.upsert(
                                    saveKhz, FmBandPlan.khzToFraction(saveKhz, plan));
                            Toast.makeText(host.context(), R.string.radio_ctx_preset_saved, Toast.LENGTH_SHORT)
                                    .show();
                        }
                        buildFmSettingsSubmenuUi();
                    }
                });

        addActionRow(host.getString(R.string.radio_fm_saved_channels_row), new Runnable() {
            @Override
            public void run() {
                radioSubMode = RADIO_FM_SAVED_CHANNELS;
                buildFmSavedChannelsUi();
            }
        });

        addActionRow(host.getString(R.string.radio_fm_scan_all_row), new Runnable() {
            @Override
            public void run() {
                if (!fmEngine.isPowerUp()) {
                    Toast.makeText(host.context(), R.string.radio_fm_scan_power_first, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                startFmScanReplacePresets();
            }
        });

        // 2026-07-15 — Wired / Bluetooth / Speaker (stock MTK force-use + user pick).
        addActionRow(
                host.getString(R.string.radio_fm_audio_output_row)
                        + ": "
                        + fmAudioOutputLabel(),
                new Runnable() {
                    @Override
                    public void run() {
                        fmEngine.cycleAudioOutput();
                        Toast.makeText(
                                        host.context(),
                                        host.getString(R.string.radio_fm_output_cycle_hint),
                                        Toast.LENGTH_SHORT)
                                .show();
                        buildFmSettingsSubmenuUi();
                        if (host.playback().isFmActive()) {
                            host.refreshPlayerUi();
                        }
                    }
                });

        addStatusRow(host.getString(R.string.radio_fm_onboarding_hint));
        focusFirstBrowserChild();
    }

    /** Label for current FM output mode (Wired / Bluetooth / Speaker). */
    private String fmAudioOutputLabel() {
        com.solar.launcher.radio.fm.FmAudioRouter.Output o = fmEngine.audioOutput();
        if (o == com.solar.launcher.radio.fm.FmAudioRouter.Output.SPEAKER) {
            return host.getString(R.string.radio_fm_output_speaker);
        }
        if (o == com.solar.launcher.radio.fm.FmAudioRouter.Output.BLUETOOTH) {
            return host.getString(R.string.radio_fm_output_bluetooth);
        }
        return host.getString(R.string.radio_fm_output_wired);
    }

    private void buildFmSavedChannelsUi() {
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        final List<FmPresetStore.Preset> presets = fmPresets.listAll();
        final FmBandPlan plan = currentFmPlan();
        for (int i = 0; i < presets.size(); i++) {
            FmPresetStore.Preset p = presets.get(i);
            String label =
                    p.label != null && !p.label.isEmpty()
                            ? p.label
                            : FmBandPlan.khzToFraction(p.freqKhz, plan);
            if (fmPresetMoveFrom >= 0 && i == fmPresetMoveFrom) {
                label = "↕ " + label;
            }
            virtualLabels.add(label);
        }
        if (presets.isEmpty()) {
            virtualLabels.add(host.getString(R.string.radio_fm_no_presets));
        }
        bindVirtualAdapter(
                new VirtualClickHandler() {
                    @Override
                    public void onClick(int position) {
                        if (position == 0) {
                            radioSubMode = RADIO_FM_SETTINGS;
                            buildFmSettingsSubmenuUi();
                            return;
                        }
                        if (presets.isEmpty()) return;
                        int idx = position - 1;
                        if (idx < 0 || idx >= presets.size()) return;
                        final FmPresetStore.Preset p = presets.get(idx);
                        startFmStation(p.freqKhz, p.label, true);
                        fmSettingsMode = false;
                        buildFmPlayerUi();
                    }
                });
    }

    /**
     * Tune ±step while fine-tune is active (player list or settings).
     * 2026-07-15 — Was settings-only; player Fine tune now uses the same path + NP scrub.
     */
    public boolean handleFmPlayerWheelTune(boolean up) {
        if (!fmTuningMode) return false;
        FmBandPlan plan = currentFmPlan();
        int khz = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : currentFmFreqKhz();
        if (khz <= 0) khz = resolvePreferredFmKhz();
        khz = up ? khz + plan.stepKhz() : khz - plan.stepKhz();
        khz = plan.clampKhz(khz);
        radioTuneFreqKhz = khz;
        radioScrubMode = RadioScrubMode.TUNE_FM;
        if (fmEngine.isPowerUp()) {
            fmEngine.tune(khz);
        }
        // Live dial label without full list rebuild (keeps focus on Fine tune row).
        updateFmDialLabel(khz);
        if (host.playback().isFmActive()) {
            host.syncFmTuneScrubUi();
            host.refreshPlayerUi();
        }
        return true;
    }

    /** Update JJ dial MHz text in place during fine-tune wheel steps. */
    private void updateFmDialLabel(int khz) {
        LinearLayout container = host.containerBrowserItems();
        if (container == null) return;
        String text = String.format(java.util.Locale.US, "%.1f MHz", khz / 1000f);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            TextView dial = findTaggedTextView(child, "fm_dial_freq");
            if (dial != null) {
                dial.setText(text);
                return;
            }
        }
    }

    private static TextView findTaggedTextView(View root, String tag) {
        if (root == null) return null;
        if (root instanceof TextView && tag.equals(root.getTag())) {
            return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView found = findTaggedTextView(vg.getChildAt(i), tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void startFmScanReplacePresets() {
        radioSubMode = RADIO_FM_SCAN;
        fmScanResults.clear();
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.radio_fm_scanning));
        addBackRow(host.getString(R.string.common_cancel_back));
        final Button status = addStatusButton(host.getString(R.string.radio_fm_scan_starting));
        fmEngine.startScan(
                new FmEngine.ScanCallback() {
                    @Override
                    public void onStationFound(int freqKhz, int signal, boolean stereo) {
                        fmScanResults.add(freqKhz);
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        status.setText(
                                                host.getString(
                                                        R.string.radio_fm_scan_found,
                                                        FmBandPlan.khzToFraction(freqKhz, currentFmPlan())));
                                    }
                                });
                    }

                    @Override
                    public void onScanComplete() {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (fmScanResults.isEmpty()) {
                                            Toast.makeText(
                                                            host.context(),
                                                            R.string.radio_fm_scan_none,
                                                            Toast.LENGTH_LONG)
                                                    .show();
                                            radioSubMode = RADIO_FM_SETTINGS;
                                            buildFmSettingsSubmenuUi();
                                            return;
                                        }
                                        List<FmPresetStore.Preset> next = new ArrayList<FmPresetStore.Preset>();
                                        FmBandPlan plan = currentFmPlan();
                                        for (int khz : fmScanResults) {
                                            next.add(
                                                    new FmPresetStore.Preset(
                                                            0,
                                                            khz,
                                                            FmBandPlan.khzToFraction(khz, plan)));
                                        }
                                        fmPresets.replaceAll(next);
                                        int first = fmScanResults.get(0);
                                        FmQueueSync.syncQueueFromPresets(
                                                host.playback(), fmPresets, first);
                                        // Scan already found live hits — land exact on first.
                                        startFmStation(first, null, true);
                                        fmSettingsMode = false;
                                        buildFmPlayerUi();
                                        Toast.makeText(
                                                        host.context(),
                                                        host.getString(
                                                                R.string.radio_fm_scan_saved,
                                                                fmScanResults.size()),
                                                        Toast.LENGTH_LONG)
                                                .show();
                                    }
                                });
                    }

                    @Override
                    public void onError(final String reason) {
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        status.setText(
                                                host.getString(R.string.radio_fm_scan_error, reason));
                                    }
                                });
                    }
                });
        focusFirstBrowserChild();
    }

    private void buildFmPresetsUi() {
        radioSubMode = RADIO_FM_PRESETS;
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        final List<FmPresetStore.Preset> presets = fmPresets.listAll();
        for (int i = 0; i < presets.size(); i++) {
            FmPresetStore.Preset p = presets.get(i);
            String label = p.label != null && !p.label.isEmpty()
                    ? p.label : FmBandPlan.khzToFraction(p.freqKhz, currentFmPlan());
            if (fmPresetMoveFrom >= 0 && i == fmPresetMoveFrom) {
                label = "↕ " + label;
            }
            virtualLabels.add(label);
        }
        if (presets.isEmpty()) {
            virtualLabels.add(host.getString(R.string.radio_fm_no_presets));
        }
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    radioSubMode = RADIO_UI_HUB;
                    buildFmBrowseUi();
                    return;
                }
                if (presets.isEmpty()) return;
                int idx = position - 1;
                if (idx < 0 || idx >= presets.size()) return;
                FmPresetStore.Preset p = presets.get(idx);
                startFmStation(p.freqKhz, p.label, true);
            }
        });
    }

    /**
     * Browse-path band scan — collect hits, then show a pickable list (does not wipe presets).
     * 2026-07-15 — Was status-only with no selectable results. Reversal: drop fmScanResults usage here.
     */
    private void startFmScan() {
        radioSubMode = RADIO_FM_SCAN;
        fmScanResults.clear();
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.radio_fm_scanning));
        addBackRow(host.getString(R.string.common_cancel_back));
        final Button status = addStatusButton(host.getString(R.string.radio_fm_scan_starting));
        // #region agent log
        try {
            DebugF9ef0bLog.log(
                    host.context(),
                    "MediaSuiteHost.startFmScan",
                    "scan requested",
                    "H4",
                    new org.json.JSONObject().put("available", fmEngine.isAvailable()));
        } catch (Exception ignored) {}
        // #endregion
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fmEngine.startScan(
                                    new FmEngine.ScanCallback() {
                                        @Override
                                        public void onStationFound(final int freqKhz, int signal, boolean stereo) {
                                            fmScanResults.add(freqKhz);
                                            host.runOnUiThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (host.getCurrentScreenState()
                                                                            != STATE_RADIO_FM_BROWSE
                                                                    || radioSubMode != RADIO_FM_SCAN)
                                                                return;
                                                            status.setText(
                                                                    host.getString(
                                                                            R.string.radio_fm_scan_found,
                                                                            FmBandPlan.khzToFraction(
                                                                                    freqKhz, currentFmPlan())));
                                                        }
                                                    });
                                        }

                                        @Override
                                        public void onScanComplete() {
                                            host.runOnUiThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (host.getCurrentScreenState()
                                                                            != STATE_RADIO_FM_BROWSE
                                                                    || radioSubMode != RADIO_FM_SCAN)
                                                                return;
                                                            showFmScanResultsUi();
                                                        }
                                                    });
                                        }

                                        @Override
                                        public void onError(final String reason) {
                                            // #region agent log
                                            try {
                                                DebugF9ef0bLog.log(
                                                        host.context(),
                                                        "MediaSuiteHost.startFmScan",
                                                        "scan error",
                                                        "H4",
                                                        new org.json.JSONObject().put("reason", reason));
                                            } catch (Exception ignored) {}
                                            // #endregion
                                            host.runOnUiThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (host.getCurrentScreenState()
                                                                    != STATE_RADIO_FM_BROWSE) return;
                                                            status.setText(
                                                                    host.getString(
                                                                            R.string.radio_fm_scan_error,
                                                                            reason));
                                                        }
                                                    });
                                        }
                                    });
                        } catch (Throwable t) {
                            // #region agent log
                            try {
                                DebugF9ef0bLog.log(
                                        host.context(),
                                        "MediaSuiteHost.startFmScan",
                                        "scan crash",
                                        "H4",
                                        new org.json.JSONObject()
                                                .put("err", t.getClass().getSimpleName()));
                            } catch (Exception ignored) {}
                            // #endregion
                            host.runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            status.setText(
                                                    host.getString(
                                                            R.string.radio_fm_scan_error,
                                                            t.getClass().getSimpleName()));
                                        }
                                    });
                        }
                    }
                },
                "FmScan")
                .start();
        focusFirstBrowserChild();
    }

    /**
     * 2026-07-15 — After browse scan: list found MHz for OK-to-play (presets unchanged).
     * Layman: pick a station the scan just found without wiping your saved list.
     */
    private void showFmScanResultsUi() {
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.radio_fm_scan_results_title));
        addBackRow(host.getString(R.string.common_back_short));
        if (fmScanResults.isEmpty()) {
            addStatusRow(host.getString(R.string.radio_fm_scan_none));
            focusFirstBrowserChild();
            return;
        }
        addStatusRow(host.getString(R.string.radio_fm_scan_done));
        final FmBandPlan plan = currentFmPlan();
        for (final Integer khzObj : fmScanResults) {
            final int khz = khzObj != null ? khzObj.intValue() : 0;
            if (khz <= 0) continue;
            final String label = FmBandPlan.khzToFraction(khz, plan) + " MHz";
            addActionRow(label, new Runnable() {
                @Override
                public void run() {
                    startFmStation(khz, label, true);
                }
            });
        }
        focusFirstBrowserChild();
    }

    private void openFmRecordingsFolder() {
        File dir = fmRecordingsDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(host.context(), R.string.radio_fm_recordings_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        final File openDir = dir;
        // 2026-07-15 — Videos browse is outside FM; Exit first so hardware shuts down.
        requestExitFmThen(new Runnable() {
            @Override
            public void run() {
                videoBrowseFolder = openDir;
                host.changeScreen(STATE_VIDEOS);
            }
        });
    }

    /** Stop the other radio path before starting FM or internet playback. */
    private void stopOtherRadioPlayback(boolean startingFm) {
        if (startingFm) {
            internetRadioPlayer.stop();
        } else {
            // Internet radio takes over — full FM shutdown (chip + session).
            shutdownFmSession();
        }
    }

    /**
     * 2026-07-15 — Listen for earphone plug so FM re-routes off the speaker (stock behaviour).
     * Layman: plug headphones in → sound leaves the speaker unless you chose Speaker.
     */
    private void ensureFmHeadsetRouting() {
        if (fmHeadsetRegistered) return;
        fmHeadsetReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent == null || !Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                            return;
                        }
                        int state = intent.getIntExtra("state", 0);
                        boolean in = state == 1;
                        fmEngine.onHeadsetPlug(in);
                        if (host.playback().isFmActive()) {
                            host.refreshPlayerUi();
                        }
                        if (host.getCurrentScreenState() == STATE_RADIO_FM_PLAYER) {
                            buildFmPlayerUi();
                        }
                    }
                };
        try {
            host.context()
                    .registerReceiver(fmHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            fmHeadsetRegistered = true;
        } catch (Throwable ignored) {
            fmHeadsetRegistered = false;
        }
    }

    private void releaseFmHeadsetRouting() {
        if (!fmHeadsetRegistered || fmHeadsetReceiver == null) return;
        try {
            host.context().unregisterReceiver(fmHeadsetReceiver);
        } catch (Throwable ignored) {}
        fmHeadsetRegistered = false;
        fmHeadsetReceiver = null;
    }

    /**
     * Power on and play. Auto-seeks to a live station when {@code exactStation} is false
     * (cold start / power button) — car-stereo behaviour.
     */
    private void startFmStation(final int freqKhz, final String label) {
        startFmStation(freqKhz, label, false /* exact — auto-seek if dead air */);
    }

    /**
     * @param exactStation true = user/preset/queue picked this MHz (do not auto-seek away)
     */
    private void startFmStation(final int freqKhz, final String label, final boolean exactStation) {
        final FmBandPlan plan = currentFmPlan();
        final int clampedKhz = plan.clampKhz(freqKhz);
        radioTuneFreqKhz = clampedKhz;
        if (!fmEngine.isAvailable()) {
            Toast.makeText(host.context(), R.string.toast_fm_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // 2026-07-15 — FM exclusive: stop internet radio + music/Deezer/YouTube/video/podcast first.
        stopOtherRadioPlayback(true);
        host.stopNonFmPlayback();
        // 2026-07-06 — MTK bind/tune off UI thread; avoids ANR and browse crash on slow FMRadioService.
        // 2026-07-15 — After power, auto-seek if dead air (car radio / handheld auto-seek).
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = fmEngine.playStation(clampedKhz);
                        int landed = clampedKhz;
                        if (ok && !exactStation) {
                            int sought = fmEngine.seekFirstStationIfWeak(clampedKhz, plan);
                            if (sought > 0) landed = sought;
                        }
                        final int finalKhz = landed;
                        final String finalLabel =
                                (label != null && !label.isEmpty())
                                        ? label
                                        : FmBandPlan.khzToFraction(finalKhz, plan);
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!ok) {
                                            String err = fmEngine.lastError();
                                            if (err == null || err.isEmpty()) {
                                                err = host.getString(R.string.radio_fm_play_error);
                                            }
                                            Toast.makeText(host.context(), err, Toast.LENGTH_LONG).show();
                                            host.offerFmMtkFallback(err);
                                            return;
                                        }
                                        radioTuneFreqKhz = finalKhz;
                                        finishFmStationStart(finalKhz, finalLabel, plan);
                                    }
                                });
                    }
                },
                "FmStart")
                .start();
    }

    /** UI thread — FM powered; land on Now Playing with volume wheel (OK enters tune). 2026-07-06 */
    private void finishFmStationStart(int freqKhz, String label, FmBandPlan plan) {
        fmMuted = false;
        cachedRdsPs = null;
        cachedRdsRt = null;
        fmRdsPoller.invalidateCache();
        if (label == null || label.isEmpty()) {
            label = FmBandPlan.khzToFraction(freqKhz, plan);
        }
        // 2026-07-15 — Remember dial for next cold start (last station restore).
        RadioSettings.setLastFmKhz(host.context(), freqKhz);
        host.playback().startRadioStation(PlayQueue.QueueItem.fmStation(freqKhz, label));
        if (!fmPresets.listAll().isEmpty()) {
            FmQueueSync.syncQueueFromPresets(host.playback(), fmPresets, freqKhz);
        }
        radioScrubMode = RadioScrubMode.NONE;
        fmTuneRevertKhz = freqKhz;
        ensureFmRdsPolling();
        ensureFmHeadsetRouting();
        // Jack in → headphones unless user locked Speaker.
        fmEngine.onHeadsetPlug(fmEngine.isWiredHeadsetOn());
        primeFmRdsCacheAsync();
        if (host.getCurrentScreenState() == STATE_RADIO_FM_PLAYER) {
            buildFmPlayerUi();
        } else {
            host.changeScreen(STATE_PLAYER);
        }
        host.refreshPlayerUi();
        // #region agent log
        try {
            DebugF9ef0bLog.log(
                    host.context(),
                    "MediaSuiteHost.startFmStation",
                    "fm np ready volume wheel",
                    "H5",
                    new org.json.JSONObject()
                            .put("freqKhz", freqKhz)
                            .put("scrubMode", radioScrubMode.name()));
        } catch (Exception ignored) {}
        // #endregion
    }

    /** First RDS read off UI thread — avoids ANR right after tune. 2026-07-06 */
    private void primeFmRdsCacheAsync() {
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        final String ps = fmEngine.getRdsPs();
                        final String rt = fmEngine.getRdsRt();
                        fmRdsPoller.primeCache(ps, rt);
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        cachedRdsPs = ps;
                                        cachedRdsRt = rt;
                                        if (ps != null && !ps.isEmpty() && host.playback().isFmActive()) {
                                            host.playback().updateCurrentFmMeta(currentFmFreqKhz(), ps);
                                        }
                                        host.refreshPlayerUi();
                                    }
                                });
                    }
                },
                "FmRdsPrime")
                .start();
    }

    /** Live FM frequency for NP scrub UI — tune mode uses wheel scratch MHz, not queue row. 2026-07-06 */
    public int fmFreqKhz() {
        if (radioScrubMode == RadioScrubMode.TUNE_FM && radioTuneFreqKhz > 0) {
            return radioTuneFreqKhz;
        }
        return currentFmFreqKhz();
    }

    public FmBandPlan fmBandPlan() {
        return currentFmPlan();
    }

    // --- Internet radio browse ---

    private void buildNetBrowseUi() {
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        switch (radioSubMode) {
            case RADIO_NET_FAVORITES:
                buildNetFavoritesUi();
                break;
            case RADIO_NET_STATIONS:
                showNetStationsUi();
                break;
            case RADIO_NET_TAG:
                loadNetTagsAsync();
                break;
            case RADIO_NET_STATE:
                loadNetStatesAsync();
                break;
            case RADIO_NET_COUNTRY:
            default:
                buildNetCountryHubUi();
                break;
        }
    }

    private void buildNetCountryHubUi() {
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualLabels.add(host.getString(R.string.radio_net_favorites));
        virtualLabels.add(host.getString(R.string.radio_net_search));
        virtualLabels.add(host.getString(R.string.radio_net_loading_countries));
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    host.changeScreen(STATE_RADIO);
                    return;
                }
                if (position == 1) {
                    radioSubMode = RADIO_NET_FAVORITES;
                    buildNetFavoritesUi();
                    return;
                }
                if (position == 2) {
                    Toast.makeText(host.context(), R.string.radio_net_search_hint, Toast.LENGTH_SHORT).show();
                    return;
                }
                int countryIdx = position - 3;
                if (countryIdx >= 0 && countryIdx < netCountries.size()) {
                    RadioBrowserClient.Country c = netCountries.get(countryIdx);
                    netCountryCode = c.isoCode;
                    netCountryName = c.name;
                    netStateName = "";
                    netTagName = "";
                    radioSubMode = RADIO_NET_STATE;
                    loadNetStatesAsync();
                }
            }
        });
        loadNetCountriesAsync();
    }

    private void loadNetCountriesAsync() {
        final int gen = ++netLoadGen;
        netLoading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<RadioBrowserClient.Country> loaded = new ArrayList<RadioBrowserClient.Country>();
                String err = null;
                try {
                    loaded = radioBrowser.listCountries();
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final List<RadioBrowserClient.Country> fLoaded = loaded;
                final String fErr = err;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != netLoadGen || radioSubMode != RADIO_NET_COUNTRY) return;
                        netLoading = false;
                        netCountries = fLoaded;
                        virtualLabels.clear();
                        virtualLabels.add(host.getString(R.string.common_back_short));
                        virtualLabels.add(host.getString(R.string.radio_net_favorites));
                        virtualLabels.add(host.getString(R.string.radio_net_search));
                        if (fErr != null) {
                            virtualLabels.add(host.getString(R.string.radio_net_load_error, fErr));
                        } else if (fLoaded.isEmpty()) {
                            virtualLabels.add(host.getString(R.string.radio_net_no_countries));
                        } else {
                            for (RadioBrowserClient.Country c : fLoaded) {
                                virtualLabels.add(c.name + " (" + c.stationcount + ")");
                            }
                        }
                        if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void loadNetStatesAsync() {
        final int gen = ++netLoadGen;
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualLabels.add(host.getString(R.string.radio_net_loading_states));
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    radioSubMode = RADIO_NET_COUNTRY;
                    buildNetCountryHubUi();
                    return;
                }
                int idx = position - 2;
                if (idx >= 0 && idx < netStates.size()) {
                    netStateName = netStates.get(idx).name;
                    radioSubMode = RADIO_NET_TAG;
                    loadNetTagsAsync();
                } else if (netStates.isEmpty() && position == 2) {
                    radioSubMode = RADIO_NET_TAG;
                    loadNetTagsAsync();
                }
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<RadioBrowserClient.State> loaded = new ArrayList<RadioBrowserClient.State>();
                String err = null;
                try {
                    loaded = radioBrowser.listStates(netCountryCode);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final List<RadioBrowserClient.State> fLoaded = loaded;
                final String fErr = err;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != netLoadGen) return;
                        netStates = fLoaded;
                        virtualLabels.clear();
                        virtualLabels.add(host.getString(R.string.common_back_short));
                        virtualLabels.add(host.getString(R.string.radio_net_country_header, netCountryName));
                        if (fErr != null) {
                            virtualLabels.add(host.getString(R.string.radio_net_load_error, fErr));
                        } else if (fLoaded.isEmpty()) {
                            virtualLabels.add(host.getString(R.string.radio_net_skip_states));
                            radioSubMode = RADIO_NET_TAG;
                            loadNetTagsAsync();
                            return;
                        } else {
                            for (RadioBrowserClient.State s : fLoaded) {
                                virtualLabels.add(s.name + " (" + s.stationcount + ")");
                            }
                        }
                        if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void loadNetTagsAsync() {
        final int gen = ++netLoadGen;
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualLabels.add(host.getString(R.string.radio_net_loading_tags));
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    if (netStates.isEmpty()) {
                        radioSubMode = RADIO_NET_COUNTRY;
                        buildNetCountryHubUi();
                    } else {
                        radioSubMode = RADIO_NET_STATE;
                        loadNetStatesAsync();
                    }
                    return;
                }
                int idx = position - 2;
                if (idx >= 0 && idx < netTags.size()) {
                    netTagName = netTags.get(idx).name;
                    radioSubMode = RADIO_NET_STATIONS;
                    loadNetStationsAsync();
                }
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<RadioBrowserClient.Tag> loaded = new ArrayList<RadioBrowserClient.Tag>();
                String err = null;
                try {
                    loaded = radioBrowser.listTags(60);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final List<RadioBrowserClient.Tag> fLoaded = loaded;
                final String fErr = err;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != netLoadGen) return;
                        netTags = fLoaded;
                        virtualLabels.clear();
                        virtualLabels.add(host.getString(R.string.common_back_short));
                        virtualLabels.add(host.getString(R.string.radio_net_tags_header, netCountryName));
                        if (fErr != null) {
                            virtualLabels.add(host.getString(R.string.radio_net_load_error, fErr));
                        } else if (fLoaded.isEmpty()) {
                            virtualLabels.add(host.getString(R.string.radio_net_no_tags));
                        } else {
                            for (RadioBrowserClient.Tag t : fLoaded) {
                                virtualLabels.add(t.name + " (" + t.stationcount + ")");
                            }
                        }
                        if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void loadNetStationsAsync() {
        final int gen = ++netLoadGen;
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualLabels.add(host.getString(R.string.radio_net_loading_stations));
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    radioSubMode = RADIO_NET_TAG;
                    loadNetTagsAsync();
                    return;
                }
                int idx = position - 2;
                if (idx >= 0 && idx < netStations.size()) {
                    startInternetStation(netStations.get(idx));
                }
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<RadioBrowserClient.Station> loaded = new ArrayList<RadioBrowserClient.Station>();
                String err = null;
                try {
                    loaded = radioBrowser.searchStations(netCountryCode,
                            netStateName.isEmpty() ? null : netStateName,
                            netTagName.isEmpty() ? null : netTagName,
                            NET_PAGE_SIZE, 0);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final List<RadioBrowserClient.Station> fLoaded = loaded;
                final String fErr = err;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != netLoadGen) return;
                        netStations = fLoaded;
                        showNetStationsUi();
                    }
                });
            }
        }).start();
    }

    private void showNetStationsUi() {
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        String header = netTagName != null && !netTagName.isEmpty()
                ? netTagName : netCountryName;
        virtualLabels.add(host.getString(R.string.radio_net_stations_header, header));
        if (netStations.isEmpty()) {
            virtualLabels.add(host.getString(R.string.radio_net_no_stations));
        } else {
            for (RadioBrowserClient.Station s : netStations) {
                virtualLabels.add(s.name);
            }
        }
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    radioSubMode = RADIO_NET_TAG;
                    loadNetTagsAsync();
                    return;
                }
                int idx = position - 2;
                if (idx >= 0 && idx < netStations.size()) {
                    startInternetStation(netStations.get(idx));
                }
            }
        });
    }

    private void buildNetFavoritesUi() {
        prepareVirtualListBrowse();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        final List<InternetRadioFavorites.Favorite> favs = netFavorites.listAll();
        if (favs.isEmpty()) {
            virtualLabels.add(host.getString(R.string.radio_net_no_favorites));
        } else {
            for (InternetRadioFavorites.Favorite f : favs) {
                virtualLabels.add(f.name);
            }
        }
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    radioSubMode = RADIO_NET_COUNTRY;
                    buildNetCountryHubUi();
                    return;
                }
                if (favs.isEmpty()) return;
                int idx = position - 1;
                if (idx < 0 || idx >= favs.size()) return;
                InternetRadioFavorites.Favorite f = favs.get(idx);
                RadioBrowserClient.Station s = new RadioBrowserClient.Station(
                        f.stationuuid, f.name, f.url, f.countrycode, "", "");
                startInternetStation(s);
            }
        });
    }

    private void startInternetStation(final RadioBrowserClient.Station station) {
        if (station == null || station.urlResolved == null || station.urlResolved.isEmpty()) {
            Toast.makeText(host.context(), R.string.radio_net_play_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!host.requireInternet(R.string.toast_internet_required)) return;
        stopOtherRadioPlayback(false);
        host.stopNonFmPlayback();
        // #region agent log
        try {
            DebugAgentLog.log(
                    host.context(),
                    "MediaSuiteHost.startInternetStation",
                    "play",
                    "E",
                    new org.json.JSONObject()
                            .put("name", station.name)
                            .put("urlLen", station.urlResolved.length()));
        } catch (Exception ignored) {}
        // #endregion
        try {
            internetRadioPlayer.play(station.urlResolved);
        } catch (Exception e) {
            Toast.makeText(host.context(), R.string.radio_net_play_error, Toast.LENGTH_SHORT).show();
            // #region agent log
            try {
                DebugAgentLog.log(
                        host.context(),
                        "MediaSuiteHost.startInternetStation",
                        "play threw",
                        "E",
                        new org.json.JSONObject().put("err", e.getClass().getSimpleName()));
            } catch (Exception ignored2) {}
            // #endregion
            return;
        }
        host.playback().startRadioStation(PlayQueue.QueueItem.internetRadio(
                station.stationuuid, station.name, station.urlResolved,
                station.countrycode, station.favicon));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    radioBrowser.reportClick(station.stationuuid);
                } catch (Exception ignored) {}
            }
        }).start();
        host.changeScreen(STATE_PLAYER);
        host.refreshPlayerUi();
    }

    // --- Radio now playing helpers ---

    /** Cached NP strings — avoid setText/JNI when nothing changed (FM NP perf). */
    private String npBoundTitle = "";
    private String npBoundArtist = "";
    private String npBoundAlbum = "";
    private String npBoundTrack = "";
    private int npBoundKhz = -1;
    private boolean npBoundPause;
    private boolean npFmArtSet;
    private int npBoundVol = -1;
    private int npBoundVolMax = -1;

    /**
     * Bind Now Playing title/artist lines for FM or internet radio.
     * 2026-07-15 — Skip setText / isStereo / art reload when unchanged (was freezing wheel on FM NP).
     */
    public void bindRadioNowPlayingUi() {
        PlaybackCoordinator playback = host.playback();
        if (!playback.isRadioActive()) return;
        PlayQueue.QueueItem cur = playback.unifiedQueue().current();
        if (cur == null) return;

        android.widget.TextView title = host.findViewById(R.id.tv_player_title);
        android.widget.TextView artist = host.findViewById(R.id.tv_player_artist);
        android.widget.TextView album = host.findViewById(R.id.tv_player_album);
        android.widget.TextView trackCount = host.findViewById(R.id.tv_player_track_count);
        android.widget.TextView vizTitle = host.findViewById(R.id.tv_viz_title);
        android.widget.TextView vizArtist = host.findViewById(R.id.tv_viz_artist);
        android.widget.TextView vizAlbum = host.findViewById(R.id.tv_viz_album);
        android.widget.ImageView pauseOverlay = host.findViewById(R.id.iv_pause_overlay);

        String titleText = cur.streamMeta();
        String artistText = "";
        String albumText = "";
        String trackCountText = "";
        boolean showPause = false;
        int khz = 0;

        if (playback.isFmActive()) {
            String ps = cachedRdsPs;
            if (ps != null && !ps.isEmpty()) titleText = ps;
            String rt = cachedRdsRt;
            if (rt != null && !rt.isEmpty()) {
                artistText = rt;
            }
            // 2026-07-06 — tune wheel MHz beats queue row until user commits (MTK live dial).
            khz = (radioScrubMode == RadioScrubMode.TUNE_FM && radioTuneFreqKhz > 0)
                    ? radioTuneFreqKhz
                    : fmFreqKhz();
            String mhz = FmBandPlan.formatMhz(khz / 1000f);
            // isStereo is JNI — only re-query when MHz changes (not every RDS/volume tick).
            if (fmEngine.isPowerUp()) {
                if (khz != npBoundKhz || npBoundAlbum.isEmpty()) {
                    albumText = host.getString(
                            fmEngine.isStereo()
                                    ? R.string.radio_fm_mhz_stereo
                                    : R.string.radio_fm_mhz_mono,
                            mhz);
                } else {
                    albumText = npBoundAlbum;
                }
            } else {
                albumText = mhz;
            }
            if (fmRecorder.isRecording()) {
                trackCountText = formatFmRecordingStatus();
            } else if (fmSeekBusy) {
                trackCountText = host.getString(R.string.radio_fm_seeking);
            } else if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                trackCountText = host.getString(R.string.radio_fm_tuning_hint);
            }
            showPause = fmMuted;
            // Placeholder art once per FM session — not every bind/RDS tick.
            if (!npFmArtSet) {
                android.widget.ImageView albumArt = host.findViewById(R.id.iv_album_art);
                if (albumArt != null) {
                    albumArt.setImageResource(R.drawable.radio_fm_np_placeholder);
                    albumArt.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                }
                npFmArtSet = true;
            }
        } else if (playback.isInternetRadioActive()) {
            npFmArtSet = false;
            if (cur.radioSubtitle != null && !cur.radioSubtitle.isEmpty()) {
                artistText = cur.radioSubtitle;
            } else {
                artistText = host.getString(R.string.status_radio_internet);
            }
            showPause = !internetRadioPlayer.isPlaying();
        }

        setTextIfChanged(title, titleText);
        setTextIfChanged(artist, artistText);
        setTextIfChanged(album, albumText);
        if (trackCount != null
                && (!playback.isFmActive()
                        || radioScrubMode == RadioScrubMode.TUNE_FM
                        || fmRecorder.isRecording()
                        || fmSeekBusy)) {
            setTextIfChanged(trackCount, trackCountText);
        }
        setTextIfChanged(vizTitle, titleText);
        setTextIfChanged(vizArtist, artistText);
        setTextIfChanged(vizAlbum, albumText);
        if (pauseOverlay != null && showPause != npBoundPause) {
            pauseOverlay.setVisibility(showPause ? View.VISIBLE : View.GONE);
        }
        android.widget.ImageView albumArt = host.findViewById(R.id.iv_album_art);
        if (albumArt != null && showPause != npBoundPause) {
            albumArt.setAlpha(showPause ? 0.4f : 1.0f);
        }
        npBoundTitle = titleText != null ? titleText : "";
        npBoundArtist = artistText != null ? artistText : "";
        npBoundAlbum = albumText != null ? albumText : "";
        npBoundTrack = trackCountText != null ? trackCountText : "";
        npBoundKhz = khz;
        npBoundPause = showPause;
        // Progress/volume only when scrubbing or volume may have changed — not full JNI path.
        updateRadioPlayerProgress();
    }

    private static void setTextIfChanged(android.widget.TextView tv, String text) {
        if (tv == null) return;
        String t = text != null ? text : "";
        CharSequence cur = tv.getText();
        if (cur != null && t.contentEquals(cur)) return;
        tv.setText(t);
    }

    /** Start RDS polls while FM is on Now Playing — idempotent. */
    public void ensureFmRdsPolling() {
        if (!fmRdsPoller.isRunning()) {
            fmRdsPoller.start();
        }
    }

    public boolean isFmRdsPolling() {
        return fmRdsPoller.isRunning();
    }

    /** @deprecated use {@link #ensureFmRdsPolling()} — kept for callers outside updatePlayerUI. */
    public void startFmRdsPolling() {
        ensureFmRdsPolling();
    }

    /** Stop RDS polls when leaving FM NP or powering down. */
    public void stopFmRdsPolling() {
        fmRdsPoller.stop();
        cachedRdsPs = null;
        cachedRdsRt = null;
        npFmArtSet = false;
        npBoundVol = -1;
        npBoundKhz = -1;
    }

    public void updateRadioPlayerProgress() {
        MediaTransportBar transport = host.playerTransportBar();
        ProgressBar bar = transport != null ? transport.progressBar() : null;
        if (bar == null) return;
        PlaybackCoordinator playback = host.playback();
        if (playback.isFmActive()) {
            FmBandPlan plan = currentFmPlan();
            int khz = (radioScrubMode == RadioScrubMode.TUNE_FM && radioTuneFreqKhz > 0)
                    ? radioTuneFreqKhz
                    : fmFreqKhz();
            if (khz <= 0) khz = defaultFmKhz();
            // 2026-07-06 — tune mode: keep MHz header + circle knob aligned with wheel.
            if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                float pos = RadioScrubMapping.khzToPosition(khz, plan);
                int prog = Math.round(pos * 100f);
                if (bar.getProgress() != prog) bar.setProgress(prog);
                host.syncFmTuneScrubUi();
            } else if (transport != null) {
                // Idle FM: only volume strip — skip if level unchanged (RDS was re-entering every 2s).
                android.media.AudioManager am =
                        (android.media.AudioManager)
                                host.context().getSystemService(Context.AUDIO_SERVICE);
                int stream =
                        fmEngine != null && fmEngine.audioStreamType() > 0
                                ? fmEngine.audioStreamType()
                                : android.media.AudioManager.STREAM_MUSIC;
                int cur = am != null ? am.getStreamVolume(stream) : 0;
                int max = am != null ? am.getStreamMaxVolume(stream) : 1;
                if (max <= 0) max = 1;
                if (cur != npBoundVol || max != npBoundVolMax) {
                    npBoundVol = cur;
                    npBoundVolMax = max;
                    transport.showFmNormalBar(cur, max);
                }
            }
            return;
        }
        if (playback.isInternetRadioActive()) {
            long buffered = internetRadioPlayer.getBufferedDurationMs();
            long live = internetRadioPlayer.getLivePositionMs();
            float pos = radioScrubMode == RadioScrubMode.REWIND_BUFFER
                    ? RadioScrubMapping.bufferMsToPosition(live, buffered)
                    : 1f;
            bar.setProgress(Math.round(pos * 100f));
        }
    }

    public void handleRadioCenterOk() {
        PlaybackCoordinator playback = host.playback();
        if (playback.isFmActive()) {
            RadioScrubMode before = radioScrubMode;
            radioScrubMode = radioScrubMode.toggleFmTuneOnCenterOk();
            if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                radioTuneFreqKhz = currentFmFreqKhz();
                fmTuneRevertKhz = radioTuneFreqKhz;
            } else if (before == RadioScrubMode.TUNE_FM) {
                commitFmTuneScrub();
            }
            // 2026-07-15 — One light bind (was bind + full refreshPlayerUi thrash).
            bindRadioNowPlayingUi();
            host.syncFmTuneScrubUi();
            return;
        }
        if (playback.isInternetRadioActive()) {
            if (radioScrubMode == RadioScrubMode.REWIND_BUFFER) {
                radioScrubMode = RadioScrubMode.NONE;
            } else if (internetRadioPlayer.getBufferedDurationMs() > 0) {
                radioScrubMode = RadioScrubMode.REWIND_BUFFER;
            }
        }
    }

    /** @param next true for next, false for previous; longPress for MHz hold-step (not station scan) */
    public void handleRadioPrevNext(boolean next, boolean longPress) {
        PlaybackCoordinator playback = host.playback();
        FmBandPlan plan = currentFmPlan();

        if (playback.isFmActive()) {
            if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                // 2026-07-06 — Fine MHz scrub: wheel/transport only; faster step when held.
                int mult = longPress ? 10 : 1;
                int khz = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : currentFmFreqKhz();
                khz = next ? khz + plan.stepKhz() * mult : khz - plan.stepKhz() * mult;
                khz = plan.clampKhz(khz);
                radioTuneFreqKhz = khz;
                host.syncFmTuneScrubUi();
                return;
            }
            if (longPress) {
                // 2026-07-06 — Hold = fast MHz stepping without full station restart.
                int mult = 8;
                int khz = currentFmFreqKhz();
                khz = next ? khz + plan.stepKhz() * mult : khz - plan.stepKhz() * mult;
                khz = plan.clampKhz(khz);
                radioTuneFreqKhz = khz;
                tuneFmAsync(khz, false);
                host.playback().updateCurrentFmMeta(khz, FmBandPlan.khzToFraction(khz, plan));
                return;
            }
            // 2026-07-06 — Single tap = preset skip or auto-scan (not slow MHz step).
            List<FmPresetStore.Preset> presets = fmPresets.listAll();
            if (presets.size() >= 2) {
                PlayQueue.QueueItem item = playback.fmItemAtWrappedIndex(next ? 1 : -1);
                if (item != null) {
                    startFmStation(item.fmFreqKhz, item.fmLabel, true);
                    return;
                }
            }
            fmSeekScanAsync(next);
            return;
        }

        if (playback.isInternetRadioActive() && radioScrubMode == RadioScrubMode.REWIND_BUFFER) {
            long buffered = internetRadioPlayer.getBufferedDurationMs();
            if (buffered <= 0) return;
            long offset = internetRadioPlayer.getLivePositionMs();
            long delta = longPress ? 30_000L : 10_000L;
            offset = next ? offset + delta : offset - delta;
            if (offset < 0) offset = 0;
            if (offset > buffered) offset = buffered;
            internetRadioPlayer.seekBufferedMs(offset);
            updateRadioPlayerProgress();
        }
    }

    /** 2026-07-06 — NP prev/next single tap: seek next valid station off UI thread. */
    private void fmSeekScanAsync(final boolean forward) {
        if (fmSeekBusy || !host.playback().isFmActive() || !fmEngine.isPowerUp()) return;
        fmSeekBusy = true;
        host.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        bindRadioNowPlayingUi();
                    }
                });
        final int startKhz = currentFmFreqKhz();
        final FmBandPlan plan = currentFmPlan();
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        final int found = fmEngine.seekStationKhz(startKhz, forward, plan);
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        fmSeekBusy = false;
                                        if (!host.playback().isFmActive()) return;
                                        if (found > 0 && found != startKhz) {
                                            finishFmSeekFound(found, plan);
                                        } else if (found > 0) {
                                            host.refreshPlayerUi();
                                        } else {
                                            Toast.makeText(
                                                            host.context(),
                                                            R.string.radio_fm_scan_none,
                                                            Toast.LENGTH_SHORT)
                                                    .show();
                                            host.refreshPlayerUi();
                                        }
                                    }
                                });
                    }
                },
                "FmSeekScan")
                .start();
    }

    /** Tune after seek without full power cycle — refresh RDS + queue row. 2026-07-06 */
    private void finishFmSeekFound(int freqKhz, FmBandPlan plan) {
        radioTuneFreqKhz = freqKhz;
        // 2026-07-15 — Seek lands also update last-station memory.
        RadioSettings.setLastFmKhz(host.context(), freqKhz);
        cachedRdsPs = null;
        cachedRdsRt = null;
        fmRdsPoller.invalidateCache();
        String label = FmBandPlan.khzToFraction(freqKhz, plan);
        host.playback().updateCurrentFmMeta(freqKhz, label);
        tuneFmAsync(freqKhz, false);
        primeFmRdsCacheAsync();
        host.refreshPlayerUi();
    }

    /** Play/pause for FM mute or internet stream pause. */
    public void toggleRadioPlayPause() {
        if (host.playback().isInternetRadioActive()) {
            if (internetRadioPlayer.isPlaying()) internetRadioPlayer.pause();
            else internetRadioPlayer.resume();
            return;
        }
        if (host.playback().isFmActive()) {
            fmMuted = !fmMuted;
            tuneFmAsync(currentFmFreqKhz(), true);
        }
    }

    /** 2026-07-06 — Chip tune off UI thread; mute-only skips hardware tune. */
    private void tuneFmAsync(final int freqKhz, final boolean muteOnly) {
        final int khz = freqKhz;
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (muteOnly) {
                            fmEngine.mute(fmMuted);
                        } else {
                            fmEngine.tune(khz);
                        }
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!host.playback().isFmActive()) return;
                                        host.refreshPlayerUi();
                                        host.syncFmTuneScrubUi();
                                        updateRadioPlayerProgress();
                                    }
                                });
                    }
                },
                muteOnly ? "FmMute" : "FmTune")
                .start();
    }

    /** Second OK in tune mode — persist MHz + RDS title on the queue row. */
    private void commitFmTuneScrub() {
        int khz = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : currentFmFreqKhz();
        String label = cachedRdsPs;
        if (label == null || label.isEmpty()) {
            label = FmBandPlan.khzToFraction(khz, currentFmPlan());
        }
        // 2026-07-15 — Commit also updates last-station restore.
        RadioSettings.setLastFmKhz(host.context(), khz);
        host.playback().updateCurrentFmMeta(khz, label);
        fmTuneRevertKhz = khz;
        tuneFmAsync(khz, false);
    }

    /** Back during tune scrub — restore MHz before tune mode began. */
    public void revertFmTuneScrub() {
        if (radioScrubMode != RadioScrubMode.TUNE_FM) return;
        radioScrubMode = RadioScrubMode.NONE;
        int revert = fmTuneRevertKhz > 0 ? fmTuneRevertKhz : currentFmFreqKhz();
        radioTuneFreqKhz = revert;
        tuneFmAsync(revert, false);
        bindRadioNowPlayingUi();
    }

    public boolean isFmRecording() {
        return fmRecorder.isRecording();
    }

    /** Now Playing track-count line while REC is active. */
    public String fmRecordingStatusLabel() {
        return formatFmRecordingStatus();
    }

    /** Context menu / NP — start or stop FM capture. */
    public void toggleFmRecording() {
        if (!host.playback().isFmActive() || !fmEngine.isPowerUp()) {
            Toast.makeText(host.context(), R.string.radio_fm_record_power_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (fmRecorder.isRecording()) {
            fmRecorder.stopRecording();
            Toast.makeText(host.context(), R.string.radio_fm_record_saved, Toast.LENGTH_SHORT).show();
        } else {
            fmRecorder.startRecording();
            Toast.makeText(host.context(), R.string.radio_fm_record_started, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopFmRecordingQuiet() {
        fmUiHandler.removeCallbacks(fmRecordUiTick);
        if (fmRecorder.isRecording()) {
            fmRecorder.stopRecording();
        } else {
            fmRecorder.release();
        }
    }

    private String formatFmRecordingStatus() {
        long ms = fmRecorder.recordDurationMs();
        int totalSec = (int) (ms / 1000L);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return host.getString(R.string.radio_fm_recording_status, min, sec);
    }

    private boolean fmMuted;
    private String cachedRdsPs;
    private String cachedRdsRt;
    /** 2026-07-06 — Blocks overlapping NP prev/next auto-scan threads. */
    private volatile boolean fmSeekBusy;

    /** OK-hold move in FM presets / saved-channels virtual lists. 2026-07-06 */
    private int fmPresetMoveFrom = -1;
    private java.util.ArrayList<FmPresetStore.Preset> fmPresetMoveSnapshot;

    /** True on FM preset browse screens that support reorder. */
    public boolean isFmPresetListActive() {
        return radioSubMode == RADIO_FM_PRESETS || radioSubMode == RADIO_FM_SAVED_CHANNELS;
    }

    public boolean isFmPresetMoveActive() {
        return fmPresetMoveFrom >= 0 && isFmPresetListActive();
    }

    public int fmPresetMoveFrom() {
        return fmPresetMoveFrom;
    }

    /** Preset list index (0..n-1) from virtual list position (row 0 = Back). */
    public int fmPresetDataIndexFromVirtualPosition(int position) {
        return position - 1;
    }

    public void handleFmPresetListCenterActivate(int virtualPosition, boolean longPress) {
        if (!isFmPresetListActive()) return;
        if (virtualPosition <= 0) return;
        int idx = fmPresetDataIndexFromVirtualPosition(virtualPosition);
        List<FmPresetStore.Preset> presets = fmPresets.listAll();
        if (idx < 0 || idx >= presets.size()) return;
        if (fmPresetMoveFrom >= 0) {
            if (fmPresetMoveFrom == idx) {
                confirmFmPresetMove();
            } else {
                applyFmPresetMove(fmPresetMoveFrom, idx);
            }
            return;
        }
        if (longPress) {
            beginFmPresetMove(idx);
            return;
        }
        FmPresetStore.Preset p = presets.get(idx);
        startFmStation(p.freqKhz, p.label, true);
    }

    public boolean handleFmPresetMoveWheel(int delta) {
        if (!isFmPresetMoveActive() || delta == 0) return false;
        List<FmPresetStore.Preset> presets = fmPresets.listAll();
        int count = presets.size();
        if (count <= 1) return false;
        int newIdx = fmPresetMoveFrom + delta;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= count) newIdx = count - 1;
        if (newIdx == fmPresetMoveFrom) return true;
        applyFmPresetMove(fmPresetMoveFrom, newIdx);
        return true;
    }

    public void cancelFmPresetMove() {
        if (fmPresetMoveSnapshot != null) {
            fmPresets.replaceAll(fmPresetMoveSnapshot);
        }
        fmPresetMoveFrom = -1;
        fmPresetMoveSnapshot = null;
        rebuildFmPresetListUi();
    }

    private void beginFmPresetMove(int pickIndex) {
        fmPresetMoveFrom = pickIndex;
        fmPresetMoveSnapshot = new java.util.ArrayList<FmPresetStore.Preset>(fmPresets.listAll());
        rebuildFmPresetListUi();
        host.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FocusScrollHelper.focusListPosition(host.listVirtualSongs(), pickIndex + 1);
            }
        });
    }

    private void applyFmPresetMove(int from, int to) {
        if (from == to) return;
        fmPresets.reorder(from, to);
        fmPresetMoveFrom = to;
        if (host.playback().isFmActive()) {
            FmQueueSync.syncQueueFromPresets(host.playback(), fmPresets, currentFmFreqKhz());
        }
        rebuildFmPresetListUi();
        host.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                FocusScrollHelper.focusListPosition(host.listVirtualSongs(), to + 1);
            }
        });
    }

    private void confirmFmPresetMove() {
        fmPresetMoveFrom = -1;
        fmPresetMoveSnapshot = null;
        if (host.playback().isFmActive()) {
            FmQueueSync.syncPresetsFromQueue(host.playback(), fmPresets);
            FmQueueSync.syncQueueFromPresets(host.playback(), fmPresets, currentFmFreqKhz());
        }
        rebuildFmPresetListUi();
    }

    private void rebuildFmPresetListUi() {
        if (radioSubMode == RADIO_FM_PRESETS) buildFmPresetsUi();
        else if (radioSubMode == RADIO_FM_SAVED_CHANNELS) buildFmSavedChannelsUi();
    }

    /** Tune to frequency — queue sync after power-on. 2026-07-06 */
    public void playFmStation(int freqKhz, String label) {
        // Exact MHz from queue/external — do not car-seek away.
        startFmStation(freqKhz, label, true);
    }

    public String[] getRadioContextMenuLabels() {
        PlaybackCoordinator playback = host.playback();
        if (playback.isFmActive()) {
            boolean saved = fmPresets.containsFreq(currentFmFreqKhz());
            String recordLabel =
                    fmRecorder.isRecording()
                            ? host.getString(R.string.radio_fm_record_stop)
                            : host.getString(R.string.radio_fm_record_start);
            // 2026-07-15 — Context: cycle audio destination (Wired / BT / Speaker).
            String audioLabel =
                    host.getString(R.string.radio_ctx_audio_output, fmAudioOutputLabel());
            return new String[] {
                recordLabel,
                host.getString(R.string.radio_ctx_save_preset),
                audioLabel,
                saved ? host.getString(R.string.radio_ctx_remove_preset)
                        : host.getString(R.string.radio_ctx_scan),
                host.getString(saved ? R.string.radio_ctx_scan : R.string.radio_ctx_open_fm_browse),
                // 2026-07-15 — Context Exit path (same confirm as shell Back).
                host.getString(R.string.radio_fm_exit_row)
            };
        }
        if (playback.isInternetRadioActive()) {
            PlayQueue.QueueItem cur = playback.unifiedQueue().current();
            boolean fav = cur != null && netFavorites.isFavorite(cur.radioStationUuid);
            return new String[] {
                fav ? host.getString(R.string.radio_ctx_remove_favorite)
                        : host.getString(R.string.radio_ctx_add_favorite),
                host.getString(R.string.radio_ctx_open_net_browse)
            };
        }
        return new String[0];
    }

    public boolean handleRadioContextAction(int index) {
        PlaybackCoordinator playback = host.playback();
        if (playback.isFmActive()) {
            boolean saved = fmPresets.containsFreq(currentFmFreqKhz());
            switch (index) {
                case 0:
                    toggleFmRecording();
                    return true;
                case 1:
                    int khz = currentFmFreqKhz();
                    fmPresets.upsert(khz, FmBandPlan.khzToFraction(khz, currentFmPlan()));
                    FmQueueSync.syncQueueFromPresets(playback, fmPresets, khz);
                    Toast.makeText(host.context(), R.string.radio_ctx_preset_saved, Toast.LENGTH_SHORT).show();
                    return true;
                case 2:
                    // 2026-07-15 — Cycle Wired → Bluetooth → Speaker and re-route live audio.
                    fmEngine.cycleAudioOutput();
                    host.refreshPlayerUi();
                    Toast.makeText(
                                    host.context(),
                                    host.getString(R.string.radio_ctx_audio_output, fmAudioOutputLabel()),
                                    Toast.LENGTH_SHORT)
                            .show();
                    return true;
                case 3:
                    if (saved) {
                        fmPresets.delete(currentFmFreqKhz());
                        FmQueueSync.syncQueueFromPresets(playback, fmPresets, currentFmFreqKhz());
                        Toast.makeText(host.context(), R.string.radio_fm_channel_removed, Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        host.changeScreen(STATE_RADIO_FM_BROWSE);
                        startFmScan();
                    }
                    return true;
                case 4:
                    if (saved) {
                        host.changeScreen(STATE_RADIO_FM_BROWSE);
                        startFmScan();
                    } else {
                        host.changeScreen(STATE_RADIO_FM_BROWSE);
                    }
                    return true;
                case 5:
                    promptExitFmToHome();
                    return true;
                default:
                    return false;
            }
        }
        if (playback.isInternetRadioActive()) {
            PlayQueue.QueueItem cur = playback.unifiedQueue().current();
            if (cur == null) return false;
            switch (index) {
                case 0:
                    if (netFavorites.isFavorite(cur.radioStationUuid)) {
                        netFavorites.remove(cur.radioStationUuid);
                        Toast.makeText(host.context(), R.string.radio_ctx_favorite_removed,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        netFavorites.add(cur.radioStationUuid, cur.radioName, cur.radioUrl,
                                cur.radioSubtitle);
                        Toast.makeText(host.context(), R.string.radio_ctx_favorite_added,
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 1:
                    if (!host.requireInternet(R.string.toast_internet_required)) return true;
                    radioSubMode = RADIO_NET_COUNTRY;
                    host.changeScreen(STATE_RADIO_NET_BROWSE);
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    // --- Video hub + YouTube ---

    /**
     * 2026-07-15 — Open Solar YouTube browse (native Invidious/Piped backends).
     * Layman: go straight into Solar’s YouTube list; no NotPipe app needed.
     * Was: wake NotPipe + probe bridge. Now: native YouTubeClient soft probe.
     * Reversal: restore NotPipePmRegistrar + openYouTubeAfterNotPipeReady.
     */
    private void openYouTubeBrowse() {
        youtubeAudioMode = false;
        openYouTubeBrowseInternal();
    }

    /**
     * 2026-07-15 — Music hub / home tile entry: same browse UI, audio plays in music Now Playing.
     * Layman: YouTube as songs, not videos. Technical: youtubeAudioMode + resolveAudioStream.
     * Reversal: call openYouTubeBrowse() (video mode).
     */
    public void openYouTubeAudioBrowse() {
        youtubeAudioMode = true;
        openYouTubeBrowseInternal();
    }

    private void openYouTubeBrowseInternal() {
        host.changeScreen(STATE_YOUTUBE_BROWSE);
        final int probeGen = ++youtubeLoadGen;
        YouTubeClient.getInstance(host.context()).probe(new YouTubeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                // Backend pool ready — popular load already triggered by browse enter.
            }

            @Override
            public void onError(String message) {
                if (probeGen != youtubeLoadGen) return;
                // Soft fail: browse can still retry popular/search; toast once.
                Toast.makeText(host.context(), R.string.youtube_backend_not_ready,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void buildVideoHubUi() {
        prepareScrollBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(false);
        host.setBrowserStatusTitle(host.getString(R.string.status_videos));
        addBackRow(host.getString(R.string.radio_back_home));

        addActionRow(host.getString(R.string.video_my_videos_row), new Runnable() {
            @Override
            public void run() {
                videoBrowseFolder = VideoLibrary.ROOT;
                host.changeScreen(STATE_VIDEOS);
            }
        });
        // 2026-07-14 — Hub row when kill switch on (default); Debug can hide for A/B.
        if (com.solar.launcher.youtube.YouTubeExperiment.isEnabled(host.prefs())) {
            addActionRow(host.getString(R.string.video_youtube_row), new Runnable() {
                @Override
                public void run() {
                    if (!host.requireInternet(R.string.toast_internet_required)) return;
                    openYouTubeBrowse();
                }
            });
        }
        focusFirstBrowserChild();
    }

    private void buildYouTubeBrowseUi() {
        prepareVirtualListBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        updateYouTubeStatusPath();
        rebuildYouTubeVirtualRows();
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                handleYouTubeRowClick(position);
            }
        });
        if (youtubeVideos.isEmpty() && !youtubeLoading
                && (youtubePendingSearch == null || youtubePendingSearch.isEmpty())) {
            loadYouTubePopular();
        }
    }

    private void updateYouTubeStatusPath() {
        if (youtubePendingSearch != null && !youtubePendingSearch.isEmpty()) {
            host.setBrowserStatusTitle(host.getString(R.string.status_youtube_results,
                    youtubePendingSearch));
        } else {
            // Music audio mode and Videos mode share the "YouTube" status label.
            host.setBrowserStatusTitle(host.getString(R.string.status_youtube));
        }
    }

    /** Layman: builds Back, Search, suggestions, and video rows for the wheel list. */
    private void rebuildYouTubeVirtualRows() {
        virtualLabels.clear();
        virtualSubtitles.clear();
        youtubeBrowseRows.clear();

        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualSubtitles.add("");
        youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_BACK));

        virtualLabels.add(host.getString(R.string.youtube_search_row));
        if (youtubePendingSearch != null && !youtubePendingSearch.isEmpty()) {
            virtualSubtitles.add(host.getString(R.string.youtube_search_query_subtitle,
                    youtubePendingSearch));
        } else {
            virtualSubtitles.add(host.getString(R.string.youtube_search_hint));
        }
        youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_SEARCH));

        if (youtubePendingSearch != null && !youtubePendingSearch.isEmpty()) {
            virtualLabels.add(host.getString(R.string.youtube_show_popular));
            virtualSubtitles.add(host.getString(R.string.youtube_show_popular_sub));
            youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_CLEAR));
        } else {
            List<String> recent = YouTubeRecentSearches.get(host.context());
            for (String q : recent) {
                virtualLabels.add(q);
                virtualSubtitles.add(host.getString(R.string.youtube_recent_subtitle));
                youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_RECENT, q, -1));
            }
        }

        if (youtubeLoading) {
            virtualLabels.add(host.getString(R.string.youtube_loading));
            virtualSubtitles.add("");
            youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_STATUS));
            return;
        }

        if (youtubeVideos.isEmpty()) {
            virtualLabels.add(youtubePendingSearch != null && !youtubePendingSearch.isEmpty()
                    ? host.getString(R.string.youtube_empty)
                    : host.getString(R.string.youtube_popular_empty));
            virtualSubtitles.add("");
            youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_STATUS));
            return;
        }

        if (youtubePendingSearch == null || youtubePendingSearch.isEmpty()) {
            virtualLabels.add(host.getString(R.string.youtube_popular_header));
        } else {
            virtualLabels.add(host.getString(R.string.youtube_results_header));
        }
        virtualSubtitles.add("");
        youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_STATUS));

        for (int i = 0; i < youtubeVideos.size(); i++) {
            YouTubeVideo v = youtubeVideos.get(i);
            virtualLabels.add(v.title);
            virtualSubtitles.add(v.subtitle());
            youtubeBrowseRows.add(new YoutubeBrowseRow(YoutubeBrowseRow.KIND_VIDEO, null, i));
        }
    }

    private void handleYouTubeRowClick(int position) {
        if (position < 0 || position >= youtubeBrowseRows.size()) return;
        YoutubeBrowseRow row = youtubeBrowseRows.get(position);
        switch (row.kind) {
            case YoutubeBrowseRow.KIND_BACK:
                handleBack();
                break;
            case YoutubeBrowseRow.KIND_SEARCH:
                host.openYouTubeSearchKeyboard(
                        youtubePendingSearch != null ? youtubePendingSearch : "");
                break;
            case YoutubeBrowseRow.KIND_CLEAR:
                youtubePendingSearch = null;
                youtubeVideos.clear();
                loadYouTubePopular();
                break;
            case YoutubeBrowseRow.KIND_RECENT:
                if (row.recentQuery != null && row.recentQuery.length() > 0) {
                    youtubePendingSearch = row.recentQuery;
                    loadYouTubeSearch(row.recentQuery);
                }
                break;
            case YoutubeBrowseRow.KIND_STATUS:
                break;
            case YoutubeBrowseRow.KIND_VIDEO:
                if (row.videoIndex >= 0 && row.videoIndex < youtubeVideos.size()) {
                    // Detail + comments first (messaging-style); Play is an action there.
                    openYouTubeDetail(youtubeVideos.get(row.videoIndex));
                }
                break;
            default:
                break;
        }
    }

    /** Open detail/comments for a video — Solar list only, notPipe invisible. */
    private void openYouTubeDetail(YouTubeVideo video) {
        if (video == null || video.id.isEmpty()) return;
        youtubeDetailVideo = video;
        youtubeComments.clear();
        youtubeCommentsLoading = true;
        host.changeScreen(STATE_YOUTUBE_DETAIL);
        loadYouTubeComments(video.id);
    }

    private void buildYouTubeDetailUi() {
        prepareVirtualListBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        if (youtubeDetailVideo != null) {
            host.setBrowserStatusTitle(youtubeDetailVideo.title);
        } else {
            host.setBrowserStatusTitle(host.getString(R.string.status_youtube_detail));
        }
        rebuildYouTubeDetailRows();
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                handleYouTubeDetailRowClick(position);
            }
        });
    }

    /**
     * Messaging-style layout: actions on top, then a Comments header, then author/body rows.
     * Same wheel list pattern as Soulseek conversations (title + subtitle).
     */
    private void rebuildYouTubeDetailRows() {
        virtualLabels.clear();
        virtualSubtitles.clear();
        youtubeDetailRows.clear();

        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualSubtitles.add("");
        youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_BACK));

        if (youtubeDetailVideo != null) {
            // 2026-07-15 — Music hub is audio-only; Videos hub keeps Play/Save video rows.
            // Was: always "Play video" + Save video + Save audio. Reversal: drop youtubeAudioMode branches.
            if (youtubeAudioMode) {
                virtualLabels.add(host.getString(R.string.youtube_detail_play_audio));
                if (youtubeResolvingStream) {
                    virtualSubtitles.add(youtubeResolveStatusText());
                } else {
                    virtualSubtitles.add(youtubeDetailVideo.subtitle().length() > 0
                            ? youtubeDetailVideo.subtitle()
                            : host.getString(R.string.youtube_detail_play_audio_sub));
                }
                youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_PLAY));

                virtualLabels.add(host.getString(R.string.youtube_detail_save));
                virtualSubtitles.add(youtubeDetailVideo.author);
                youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_SAVE_AUDIO));
            } else {
                virtualLabels.add(host.getString(R.string.youtube_detail_play));
                // 2026-07-15 — Staged status (Looking up / Getting 480p…) not flat “Resolving”.
                if (youtubeResolvingStream) {
                    virtualSubtitles.add(youtubeResolveStatusText());
                } else {
                    virtualSubtitles.add(youtubeDetailVideo.subtitle().length() > 0
                            ? youtubeDetailVideo.subtitle()
                            : host.getString(R.string.youtube_detail_play_sub));
                }
                youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_PLAY));

                virtualLabels.add(host.getString(R.string.youtube_detail_save_video));
                virtualSubtitles.add(youtubeDetailVideo.author);
                youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_SAVE_VIDEO));

                virtualLabels.add(host.getString(R.string.youtube_detail_save_audio));
                virtualSubtitles.add(youtubeDetailVideo.author);
                youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_SAVE_AUDIO));
            }
        }

        virtualLabels.add(host.getString(R.string.youtube_comments_header));
        virtualSubtitles.add("");
        youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_HEADER));

        if (youtubeCommentsLoading) {
            virtualLabels.add(host.getString(R.string.youtube_comments_loading));
            virtualSubtitles.add("");
            youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_STATUS));
            return;
        }

        if (youtubeComments.isEmpty()) {
            virtualLabels.add(host.getString(R.string.youtube_comments_empty));
            virtualSubtitles.add("");
            youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_STATUS));
            return;
        }

        for (int i = 0; i < youtubeComments.size(); i++) {
            YouTubeComment c = youtubeComments.get(i);
            String author = c.author.length() > 0 ? c.author : "…";
            virtualLabels.add(author);
            virtualSubtitles.add(c.preview(120));
            youtubeDetailRows.add(new YoutubeDetailRow(YoutubeDetailRow.KIND_COMMENT, i));
        }
    }

    private void handleYouTubeDetailRowClick(int position) {
        if (position < 0 || position >= youtubeDetailRows.size()) return;
        YoutubeDetailRow row = youtubeDetailRows.get(position);
        switch (row.kind) {
            case YoutubeDetailRow.KIND_BACK:
                handleBack();
                break;
            case YoutubeDetailRow.KIND_PLAY:
                // 2026-07-14 — Ignore re-taps while resolve is already running.
                // 2026-07-15 — Audio mode → music Now Playing; Videos hub stays video IJK.
                if (youtubeDetailVideo != null && !youtubeResolvingStream) {
                    if (youtubeAudioMode) {
                        playYouTubeAudio(youtubeDetailVideo);
                    } else {
                        playYouTubeVideo(youtubeDetailVideo);
                    }
                }
                break;
            case YoutubeDetailRow.KIND_SAVE_VIDEO:
                if (youtubeDetailVideo != null) {
                    host.requestYouTubeSave(youtubeDetailVideo, false);
                }
                break;
            case YoutubeDetailRow.KIND_SAVE_AUDIO:
                if (youtubeDetailVideo != null) {
                    host.requestYouTubeSave(youtubeDetailVideo, true);
                }
                break;
            case YoutubeDetailRow.KIND_COMMENT:
                // Full comment body as toast — keeps list wheel-friendly (like long message peek).
                if (row.commentIndex >= 0 && row.commentIndex < youtubeComments.size()) {
                    YouTubeComment c = youtubeComments.get(row.commentIndex);
                    String body = c.content;
                    if (body != null && body.length() > 0) {
                        Toast.makeText(host.context(), body, Toast.LENGTH_LONG).show();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void loadYouTubeComments(final String videoId) {
        youtubeCommentsLoading = true;
        final int gen = ++youtubeCommentsGen;
        if (host.getCurrentScreenState() == STATE_YOUTUBE_DETAIL) {
            rebuildYouTubeDetailRows();
            if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
        }
        YouTubeClient.getInstance(host.context()).fetchComments(videoId, new YouTubeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (gen != youtubeCommentsGen) return;
                youtubeCommentsLoading = false;
                youtubeComments.clear();
                try {
                    youtubeComments.addAll(YouTubeResultJson.parseComments(payloadJson));
                } catch (Exception e) {
                    youtubeComments.clear();
                }
                if (host.getCurrentScreenState() == STATE_YOUTUBE_DETAIL) {
                    buildYouTubeDetailUi();
                }
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeCommentsGen) return;
                youtubeCommentsLoading = false;
                youtubeComments.clear();
                if (host.getCurrentScreenState() == STATE_YOUTUBE_DETAIL) {
                    buildYouTubeDetailUi();
                    Toast.makeText(host.context(), R.string.youtube_comments_error,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadYouTubePopular() {
        youtubePendingSearch = null;
        youtubeLoading = true;
        final int gen = ++youtubeLoadGen;
        updateYouTubeStatusPath();
        rebuildYouTubeVirtualRows();
        if (virtualAdapter == null) {
            buildYouTubeBrowseUi();
        } else {
            notifyVirtualDataChangedPreserveFocus();
        }
        YouTubeClient.getInstance(host.context()).fetchPopular(new YouTubeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                try {
                    youtubeVideos.clear();
                    youtubeVideos.addAll(YouTubeResultJson.parseVideos(payloadJson));
                } catch (Exception e) {
                    youtubeVideos.clear();
                }
                updateYouTubeStatusPath();
                rebuildYouTubeVirtualRows();
                notifyVirtualDataChangedPreserveFocus();
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                youtubeVideos.clear();
                updateYouTubeStatusPath();
                rebuildYouTubeVirtualRows();
                notifyVirtualDataChangedPreserveFocus();
                Toast.makeText(host.context(),
                        host.getString(R.string.youtube_error, message),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadYouTubeSearch(final String query) {
        youtubeLoading = true;
        final int gen = ++youtubeLoadGen;
        updateYouTubeStatusPath();
        rebuildYouTubeVirtualRows();
        // Keep focus on Search/Back while results load (was always reset to row 0).
        notifyVirtualDataChangedPreserveFocus();
        YouTubeClient.getInstance(host.context()).search(query, new YouTubeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                try {
                    youtubeVideos.clear();
                    youtubeVideos.addAll(YouTubeResultJson.parseVideos(payloadJson));
                } catch (Exception e) {
                    youtubeVideos.clear();
                }
                // Prefer incremental notify over full rebind (keeps selection).
                updateYouTubeStatusPath();
                rebuildYouTubeVirtualRows();
                notifyVirtualDataChangedPreserveFocus();
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                youtubeVideos.clear();
                updateYouTubeStatusPath();
                rebuildYouTubeVirtualRows();
                notifyVirtualDataChangedPreserveFocus();
                Toast.makeText(host.context(),
                        host.getString(R.string.youtube_error, message),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Solar-native playback — resolve stream via YouTubeClient, play in Solar IJK player.
     * 2026-07-15 — Was notPipe bridge IPC; now native Invidious/Piped/YtApiLegacy.
     * Layman: ask Solar’s backends for a playable link; try lower quality if the first fails.
     * Reversal: single resolveStream(id) then openUrl with no fallback.
     */
    private void playYouTubeVideo(final YouTubeVideo video) {
        if (video == null || video.id.isEmpty()) return;
        playYouTubeVideoAtQuality(video, YouTubeClient.preferredVideoQuality(), false);
    }

    /**
     * 2026-07-15 — Music YouTube Audio: resolve/save audio → music STATE_PLAYER (not video IJK).
     * Layman: download the soundtrack and play it like a normal song.
     * Technical: YouTubeDownloader.saveAudio (uses resolveAudioStream) then playTrackList.
     * Reversal: playYouTubeVideo; Files remain on disk under Music/YouTube.
     */
    private void playYouTubeAudio(final YouTubeVideo video) {
        if (video == null || video.id == null || video.id.isEmpty()) return;
        youtubeNowPlayingTitle = video.title;
        youtubeNowPlayingId = video.id;
        final int gen = ++youtubeLoadGen;
        youtubeResolvingStream = true;
        setYoutubeResolveStatus(host.getString(R.string.youtube_resolve_looking_up));
        refreshYouTubeResolveUi();
        File existing = YouTubeSavePaths.findSavedAudio(host.context(), video);
        if (existing != null && existing.length() > 1024L) {
            youtubeResolvingStream = false;
            youtubeLoading = false;
            youtubeResolveStatus = "";
            clearYouTubeResolveUi();
            host.playAudioFileInNowPlaying(existing);
            return;
        }
        YouTubeDownloader.saveAudio(host.context(), video, new YouTubeDownloader.Callback() {
            @Override
            public void onProgress(String phase, int percent, long doneBytes, long totalBytes) {
                if (gen != youtubeLoadGen) return;
                // 2026-07-15 — Live % while audio file is written (not a frozen “Resolving…”).
                int pct = Math.max(0, Math.min(100, percent));
                setYoutubeResolveStatus(host.getString(R.string.youtube_resolve_saving, pct));
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != youtubeLoadGen) return;
                        refreshYouTubeResolveUi();
                    }
                });
            }

            @Override
            public void onComplete(final File savedFile) {
                if (gen != youtubeLoadGen) return;
                youtubeResolvingStream = false;
                youtubeLoading = false;
                youtubeResolveStatus = "";
                clearYouTubeResolveUi();
                if (savedFile != null && savedFile.isFile()) {
                    host.playAudioFileInNowPlaying(savedFile);
                } else {
                    toastYouTubePlayError(null);
                }
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                youtubeResolvingStream = false;
                youtubeLoading = false;
                youtubeResolveStatus = "";
                clearYouTubeResolveUi();
                toastYouTubePlayError(message);
            }
        });
    }

    /**
     * 2026-07-14 — Resolve + open player at quality; optionally silent when retrying from IJK error.
     */
    private void playYouTubeVideoAtQuality(final YouTubeVideo video, final String quality,
            final boolean fromIjkFallback) {
        if (video == null || video.id.isEmpty()) return;
        youtubeNowPlayingTitle = video.title;
        youtubeNowPlayingId = video.id;
        youtubeStreamQuality = quality;
        youtubeIjkFallbackPending = false;
        final int gen = ++youtubeLoadGen;
        youtubeResolvingStream = true;
        String qLabel = quality != null && quality.length() > 0 ? quality : "stream";
        setYoutubeResolveStatus(host.getString(R.string.youtube_resolve_getting_stream, qLabel));
        refreshYouTubeResolveUi();
        YouTubeClient.getInstance(host.context()).resolveStream(video.id, quality,
                new YouTubeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (gen != youtubeLoadGen) return;
                youtubeResolvingStream = false;
                youtubeLoading = false;
                youtubeResolveStatus = "";
                try {
                    youtubeStreamUrl = YouTubeResultJson.parseStreamUrl(payloadJson);
                } catch (Exception e) {
                    youtubeStreamUrl = null;
                }
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("quality", quality != null ? quality : "");
                    d.put("fromIjkFallback", fromIjkFallback);
                    d.put("urlPrefix", youtubeStreamUrl != null && youtubeStreamUrl.length() > 96
                            ? youtubeStreamUrl.substring(0, 96) : youtubeStreamUrl);
                    d.put("emptyUrl", youtubeStreamUrl == null || youtubeStreamUrl.isEmpty());
                    d.put("isDirectUrlApi", youtubeStreamUrl != null
                            && youtubeStreamUrl.indexOf("/direct_url") >= 0);
                    com.solar.launcher.Debug9d82a5Log.log(host.context(),
                            "MediaSuiteHost.resolve.onSuccess", "ui has stream url", "C", d);
                } catch (Exception ignored) {}
                // #endregion
                if (youtubeStreamUrl == null || youtubeStreamUrl.isEmpty()) {
                    // Fail-open: YtApi always has a constructible progressive URL.
                    String seed = com.solar.launcher.youtube.api.InstancesConfig.DEFAULT_YTAPI;
                    String q = quality != null && quality.length() > 0 ? quality : "360";
                    youtubeStreamUrl = seed + "/direct_url?video_id="
                            + com.solar.launcher.youtube.api.YoutubeApiUtil.urlEncode(video.id)
                            + "&quality="
                            + com.solar.launcher.youtube.api.YoutubeApiUtil.urlEncode(q);
                    android.util.Log.w("SolarYouTube", "empty parse — using YtApi seed url");
                }
                if (youtubeStreamUrl == null || youtubeStreamUrl.isEmpty()) {
                    String next = YouTubeClient.fallbackVideoQuality(quality);
                    if (next != null) {
                        playYouTubeVideoAtQuality(video, next, fromIjkFallback);
                        return;
                    }
                    clearYouTubeResolveUi();
                    toastYouTubePlayError(null);
                    return;
                }
                videoPlaybackYoutube = true;
                videoFiles.clear();
                host.changeScreen(STATE_VIDEO_PLAYER);
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                youtubeResolvingStream = false;
                youtubeLoading = false;
                youtubeResolveStatus = "";
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("quality", quality != null ? quality : "");
                    d.put("message", message != null ? message : "");
                    com.solar.launcher.Debug9d82a5Log.log(host.context(),
                            "MediaSuiteHost.resolve.onError", "resolve failed", "A", d);
                } catch (Exception ignored) {}
                // #endregion
                String next = YouTubeClient.fallbackVideoQuality(quality);
                if (next != null) {
                    playYouTubeVideoAtQuality(video, next, fromIjkFallback);
                    return;
                }
                // Final quality exhausted — high-impact stream failure (buffered resolve already waited).
                try {
                    com.solar.launcher.soulseek.SolarDeveloperImpactPing.mediaFailed(
                            host.context(),
                            com.solar.launcher.soulseek.SolarDeveloperImpactPing.MediaInfo
                                    .of("youtube")
                                    .id(video != null ? video.id : youtubeNowPlayingId)
                                    .title(video != null ? video.title : youtubeNowPlayingTitle)
                                    .artist(video != null ? video.author : "")
                                    .quality(quality)
                                    .reason(message != null ? message : "stream resolve failed"));
                } catch (Throwable ignored) {}
                clearYouTubeResolveUi();
                toastYouTubePlayError(message);
            }
        });
    }

    /** 2026-07-15 — Staged status line for Play row / future HUD. */
    private void setYoutubeResolveStatus(String status) {
        youtubeResolveStatus = status != null ? status : "";
    }

    private String youtubeResolveStatusText() {
        if (youtubeResolveStatus != null && youtubeResolveStatus.length() > 0) {
            return youtubeResolveStatus;
        }
        return host.getString(R.string.youtube_resolve_looking_up);
    }

    /** Rebuild list so Play subtitle shows current resolve phase. */
    private void refreshYouTubeResolveUi() {
        int state = host.getCurrentScreenState();
        if (state == STATE_YOUTUBE_BROWSE) {
            youtubeLoading = youtubeResolvingStream;
            rebuildYouTubeVirtualRows();
            notifyVirtualDataChangedPreserveFocus();
            updateYouTubeStatusPath(); // also refreshes status-bar search throbber
        } else if (state == STATE_YOUTUBE_DETAIL) {
            rebuildYouTubeDetailRows();
            notifyVirtualDataChangedPreserveFocus();
        }
    }

    /** 2026-07-14 — Refresh browse/detail after resolve failure without yanking screen. */
    private void clearYouTubeResolveUi() {
        youtubeResolveStatus = "";
        int state = host.getCurrentScreenState();
        if (state == STATE_YOUTUBE_BROWSE) {
            buildYouTubeBrowseUi();
        } else if (state == STATE_YOUTUBE_DETAIL) {
            rebuildYouTubeDetailRows();
            notifyVirtualDataChangedPreserveFocus();
        }
    }

    /**
     * 2026-07-14 — Short toast; append bridge reason when short enough for 2.4" display.
     */
    private void toastYouTubePlayError(String bridgeMessage) {
        if (bridgeMessage != null && bridgeMessage.length() > 0
                && bridgeMessage.length() <= 48
                && !"timeout".equals(bridgeMessage)) {
            Toast.makeText(host.context(),
                    host.getString(R.string.youtube_play_error_detail, bridgeMessage),
                    Toast.LENGTH_SHORT).show();
        } else if ("timeout".equals(bridgeMessage)) {
            Toast.makeText(host.context(), R.string.youtube_play_timeout, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
        }
    }

    /** Wheel keyboard submitted a YouTube search query. */
    public void onYouTubeSearchSubmitted(String query) {
        if (query == null || query.trim().isEmpty()) return;
        youtubePendingSearch = query.trim();
        YouTubeRecentSearches.remember(host.context(), youtubePendingSearch);
        if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) {
            host.changeScreen(STATE_YOUTUBE_BROWSE);
        }
        loadYouTubeSearch(youtubePendingSearch);
    }

    /** Context menu — focused wheel row on YouTube browse, or null. */
    public YouTubeVideo getFocusedYouTubeVideo() {
        if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return null;
        android.widget.ListView lv = host.listVirtualSongs();
        if (lv == null) return null;
        int pos = lv.getSelectedItemPosition();
        if (pos < 0 || pos >= youtubeBrowseRows.size()) return null;
        YoutubeBrowseRow row = youtubeBrowseRows.get(pos);
        if (row.kind != YoutubeBrowseRow.KIND_VIDEO || row.videoIndex < 0) return null;
        if (row.videoIndex >= youtubeVideos.size()) return null;
        return youtubeVideos.get(row.videoIndex);
    }

    /** Context menu — currently streaming YouTube item in Solar player. */
    public YouTubeVideo getYouTubeNowPlayingVideo() {
        if (!videoPlaybackYoutube || youtubeNowPlayingId == null
                || youtubeNowPlayingId.isEmpty()) {
            return null;
        }
        return new YouTubeVideo(youtubeNowPlayingId,
                youtubeNowPlayingTitle != null ? youtubeNowPlayingTitle : "", "", "");
    }

    /**
     * 2026-07-15 — True when browse/detail came from Music→YouTube (audio Now Playing path).
     * Layman: Music hub YouTube, not Videos. Technical: youtubeAudioMode flag for labels/ctx.
     */
    public boolean isYouTubeAudioMode() {
        return youtubeAudioMode;
    }

    public boolean isYouTubePlaybackActive() {
        return videoPlaybackYoutube;
    }

    public boolean isVideoPlaying() {
        return (videoController != null && videoController.isPlaying()) || videoPlaybackYoutube;
    }

    /**
     * 2026-07-15 — Silence video / live YouTube stream before music or Deezer takes over.
     * Layman: starting a song must kill the video player if it was still making noise.
     * Technical: bump youtubeLoadGen so in-flight resolve/saveAudio callbacks no-op;
     * release VideoPlayerController; clear stream flags. Does not change screen.
     * Was: only music MediaPlayer was reset — YouTube video IJK kept playing under music.
     * Reversal: empty method body.
     */
    public void stopVideoAndYoutubeStream() {
        youtubeLoadGen++;
        youtubeResolvingStream = false;
        youtubeLoading = false;
        youtubeResolveStatus = "";
        youtubeStreamUrl = null;
        videoPlaybackYoutube = false;
        youtubeIjkFallbackPending = false;
        releaseVideoPlayer();
        showVideoPlayerLayer(false);
    }

    /** Context action — play a YouTube row without OK tap. */
    public void playYouTubeFromContext(YouTubeVideo video) {
        // 2026-07-15 — Audio mode from Music hub keeps context Play on music NP.
        if (youtubeAudioMode) {
            playYouTubeAudio(video);
        } else {
            playYouTubeVideo(video);
        }
    }

    /** Context — open detail/comments for a browse row. */
    public void openYouTubeDetailFromContext(YouTubeVideo video) {
        openYouTubeDetail(video);
    }

    /** Context — video currently shown on detail screen. */
    public YouTubeVideo getYouTubeDetailVideo() {
        return youtubeDetailVideo;
    }

    // --- Videos ---

    private void buildVideosUi() {
        prepareVirtualListBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        host.setBrowserStatusTitle(host.getString(R.string.status_videos));
        if (videoBrowseFolder == null) {
            videoBrowseFolder = VideoLibrary.ROOT;
        }
        videoFiles.clear();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        virtualLabels.add(host.getString(R.string.video_folder_header, videoBrowseFolder.getName()));

        List<File> folders = VideoLibrary.listChildFoldersWithVideos(videoBrowseFolder);
        videoFiles = VideoLibrary.listInFolder(videoBrowseFolder);
        for (File f : folders) {
            virtualLabels.add(host.getString(R.string.video_folder_row, f.getName()));
        }
        final boolean atVideosRoot = videoBrowseFolder.equals(VideoLibrary.ROOT);
        final boolean showAllVideosRow = atVideosRoot && (!folders.isEmpty() || !videoFiles.isEmpty()
                || !VideoLibrary.scanAll().isEmpty());
        if (showAllVideosRow) {
            virtualLabels.add(host.getString(R.string.video_all_videos));
        }
        if (videoFiles.isEmpty() && folders.isEmpty()) {
            virtualLabels.add(host.getString(
                    atVideosRoot ? R.string.video_none_found : R.string.video_none_in_folder));
        } else {
            for (File f : videoFiles) virtualLabels.add(f.getName());
        }

        final int folderStart = 2;
        final int folderCount = folders.size();
        final int allVideosPos = showAllVideosRow ? folderStart + folderCount : -1;
        final int fileStart = folderStart + folderCount + (showAllVideosRow ? 1 : 0);

        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    handleBack();
                    return;
                }
                if (position == 1) return;
                if (position >= folderStart && position < folderStart + folderCount) {
                    videoBrowseFolder = folders.get(position - folderStart);
                    buildVideosUi();
                    return;
                }
                if (allVideosPos >= 0 && position == allVideosPos) {
                    showAllVideosFlatList();
                    return;
                }
                int fileIdx = position - fileStart;
                if (fileIdx >= 0 && fileIdx < videoFiles.size()) {
                    videoIndex = fileIdx;
                    host.changeScreen(STATE_VIDEO_PLAYER);
                }
            }
        });
    }

    private void showAllVideosFlatList() {
        final List<File> all = VideoLibrary.scanAll();
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));
        if (all.isEmpty()) {
            virtualLabels.add(host.getString(R.string.video_none_found));
        } else {
            for (File f : all) virtualLabels.add(f.getName());
        }
        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    buildVideosUi();
                    return;
                }
                int idx = position - 1;
                if (idx >= 0 && idx < all.size()) {
                    videoFiles = all;
                    videoIndex = idx;
                    host.changeScreen(STATE_VIDEO_PLAYER);
                }
            }
        });
    }

    private void showVideoPlayerLayer(boolean show) {
        FrameLayout layout = host.findViewById(R.id.layout_video_mode);
        if (layout != null) {
            layout.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                layout.bringToFront();
            }
        }
        MediaTransportBar transport = host.videoTransportBar();
        if (transport != null) {
            if (show) {
                transport.styleVideoChrome();
                transport.setVideoOverlayMode(true);
                transport.setVisible(false);
            } else {
                transport.setVideoOverlayMode(false);
                transport.hideVolumePulse();
                transport.setVisible(false);
            }
        }
        if (host.layoutBrowserMode() != null) {
            host.layoutBrowserMode().setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (show) {
            host.setStatusBarVisible(false);
        }
    }

    private final Runnable videoProgressTick =
            new Runnable() {
                @Override
                public void run() {
                    if (videoController == null || !videoController.isPrepared()) {
                        videoProgressHandler.postDelayed(this, 500);
                        return;
                    }
                    if (!videoScrubActive) {
                        updateVideoProgressUi(videoController.getCurrentPosition());
                    }
                    videoProgressHandler.postDelayed(this, 500);
                }
            };

    public boolean isVideoScrubActive() {
        return videoScrubActive;
    }

    /** Back during fine scrub — discard cursor, keep playback position. */
    public void cancelVideoScrub() {
        clearVideoScrubMode(true);
    }

    /** Show iPod-style video transport overlay (volume / scrub / skip). */
    public void pulseVideoTransport() {
        MediaTransportBar transport = host.videoTransportBar();
        if (transport != null) transport.pulseVideoOverlay();
    }

    /** Center OK — enter scrub cursor or commit seek (matches Now Playing progress bar). */
    public void handleVideoCenterOk() {
        if (videoController == null || !videoController.isPrepared()) return;
        if (videoScrubActive) {
            commitVideoScrub();
        } else {
            enterVideoScrubMode();
        }
    }

    public void moveVideoScrubCursor(int deltaMs) {
        if (!videoScrubActive || videoController == null) return;
        long dur = videoDurationMs();
        if (dur <= 0) return;
        videoScrubMs = clampVideoScrubMs(videoScrubMs + deltaMs, dur);
        updateVideoProgressUi(videoScrubMs);
        updateVideoScrubMarker();
        pulseVideoTransport();
    }

    private void enterVideoScrubMode() {
        if (videoController == null || !videoController.isPrepared()) return;
        long dur = videoDurationMs();
        if (dur <= 0) return;
        videoScrubMs = clampVideoScrubMs(videoController.getCurrentPosition(), dur);
        videoScrubActive = true;
        updateVideoProgressUi(videoScrubMs);
        updateVideoScrubMarker();
        pulseVideoTransport();
    }

    private void commitVideoScrub() {
        if (!videoScrubActive || videoController == null) return;
        videoController.seekTo(videoScrubMs);
        clearVideoScrubMode(false);
    }

    private void clearVideoScrubMode(boolean restoreLive) {
        videoScrubActive = false;
        View marker = host.videoTransportBar() != null ? host.videoTransportBar().scrubMarker() : null;
        if (marker != null) marker.setVisibility(View.GONE);
        if (restoreLive && videoController != null && videoController.isPrepared()) {
            updateVideoProgressUi(videoController.getCurrentPosition());
        }
    }

    private void updateVideoProgressUi(long positionMs) {
        long dur = videoDurationMs();
        MediaTransportBar transport = host.videoTransportBar();
        ProgressBar bar = transport != null ? transport.progressBar() : null;
        TextView cur = transport != null ? transport.timeCurrent() : null;
        TextView tot = transport != null ? transport.timeTotal() : null;
        if (dur > 0 && bar != null) {
            int pct = (int) Math.min(100, (positionMs * 100L) / dur);
            bar.setProgress(pct);
        }
        if (cur != null) cur.setText(formatVideoTime(positionMs));
        if (tot != null) tot.setText(dur > 0 ? formatVideoTime(dur) : "00:00");
    }

    private void updateVideoScrubMarker() {
        MediaTransportBar transport = host.videoTransportBar();
        if (transport == null) return;
        View marker = transport.scrubMarker();
        ProgressBar bar = transport.progressBar();
        if (marker == null || bar == null) return;
        if (!videoScrubActive) {
            marker.setVisibility(View.GONE);
            return;
        }
        long dur = videoDurationMs();
        if (dur <= 0) {
            marker.setVisibility(View.GONE);
            return;
        }
        int trackW = bar.getWidth();
        if (trackW <= 0) {
            bar.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            updateVideoScrubMarker();
                        }
                    });
            return;
        }
        float frac = (float) videoScrubMs / (float) dur;
        float density = host.getResources().getDisplayMetrics().density;
        int markerW = marker.getWidth() > 0 ? marker.getWidth() : (int) (10 * density);
        int x = (int) (frac * trackW) - markerW / 2;
        x = Math.max(0, Math.min(x, trackW - markerW));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) marker.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(markerW, markerW);
        }
        lp.width = markerW;
        lp.height = markerW;
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        lp.leftMargin = x;
        marker.setLayoutParams(lp);
        marker.setVisibility(View.VISIBLE);
    }

    private long videoDurationMs() {
        return videoController != null ? videoController.getDuration() : 0L;
    }

    private static long clampVideoScrubMs(long ms, long dur) {
        if (ms < 0) return 0;
        if (dur > 0 && ms > dur) return dur;
        return ms;
    }

    private static String formatVideoTime(long ms) {
        int s = (int) ((ms / 1000) % 60);
        int m = (int) ((ms / (1000 * 60)) % 60);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void startVideoProgressUpdates() {
        videoProgressHandler.removeCallbacks(videoProgressTick);
        videoProgressHandler.post(videoProgressTick);
    }

    private void stopVideoProgressUpdates() {
        videoProgressHandler.removeCallbacks(videoProgressTick);
    }

    /**
     * 2026-07-15 — Exit video player the same way Back does.
     * Layman: leave the watching screen and go back to the list.
     */
    private void leaveVideoPlayerToBrowse() {
        boolean wasYt = videoPlaybackYoutube;
        releaseVideoPlayer();
        showVideoPlayerLayer(false);
        endVideoForceLandscapeSession();
        if (wasYt) {
            videoPlaybackYoutube = false;
            if (youtubeDetailVideo != null) {
                host.changeScreen(STATE_YOUTUBE_DETAIL);
            } else {
                host.changeScreen(STATE_YOUTUBE_BROWSE);
            }
        } else {
            host.changeScreen(STATE_VIDEOS);
        }
    }

    /**
     * 2026-07-15 — Natural end with nothing next → leave player (no wrap).
     * Layman: when the clip finishes and there is no later file, go back.
     * Was: freeze on last frame until Back.
     */
    private void handleVideoPlaybackEnded() {
        if (videoPlaybackYoutube) {
            leaveVideoPlayerToBrowse();
            return;
        }
        if (videoFiles.isEmpty() || videoIndex < 0) {
            leaveVideoPlayerToBrowse();
            return;
        }
        if (videoIndex + 1 < videoFiles.size()) {
            videoIndex = videoIndex + 1;
            pulseVideoTransport();
            startVideoPlayback();
            return;
        }
        leaveVideoPlayerToBrowse();
    }

    /**
     * 2026-07-15 — Force landscape for non-portrait video on A5 / portrait experiment.
     * Layman: turn the device on its side to watch wide videos.
     */
    private void beginVideoForceLandscapeSession() {
        com.solar.launcher.LandscapeOrientationGuard.setForceLandscapeVideoSession(true);
        applyOrientationGuard();
    }

    /** 2026-07-15 — Clear forced landscape when leaving the player. */
    private void endVideoForceLandscapeSession() {
        com.solar.launcher.LandscapeOrientationGuard.setForceLandscapeVideoSession(false);
        applyOrientationGuard();
    }

    /** 2026-07-15 — Re-run activity orientation after video session flag flips. */
    private void applyOrientationGuard() {
        Context ctx = host.context();
        if (ctx instanceof Activity) {
            com.solar.launcher.LandscapeOrientationGuard.enforceForDevice((Activity) ctx);
        }
    }

    /**
     * MATCH_PARENT surface with CENTER gravity — letterbox bars even top/bottom (and sides).
     * 2026-07-15 — Was plain MATCH_PARENT (FrameLayout defaults to top-left → picture sat high).
     */
    private static FrameLayout.LayoutParams centeredVideoSurfaceLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        return lp;
    }

    /** Show / hide centered load-buffer label over the video panel. */
    private void setVideoStatusText(String text) {
        TextView tv = host.findViewById(R.id.tv_video_status);
        if (tv == null) return;
        if (text == null || text.isEmpty()) {
            tv.setVisibility(View.GONE);
            tv.setText("");
            return;
        }
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
    }

    private void clearVideoStatusText() {
        setVideoStatusText(null);
    }

    /** Buffering % from IJK while the first frames fill. */
    private VideoPlayerController.BufferingListener videoBufferingListener() {
        return new VideoPlayerController.BufferingListener() {
            @Override
            public void onBuffering(final int percent) {
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (host.getCurrentScreenState() != STATE_VIDEO_PLAYER) return;
                        if (percent >= 100 || (videoController != null && videoController.isPlaying())) {
                            clearVideoStatusText();
                            return;
                        }
                        setVideoStatusText(host.getString(R.string.youtube_resolve_buffering, percent));
                    }
                });
            }

            @Override
            public void onReadyToPlay() {
                if (videoPlaybackYoutube) {
                    try {
                        com.solar.launcher.soulseek.SolarDeveloperImpactPing.mediaOk(
                                host.context(),
                                com.solar.launcher.soulseek.SolarDeveloperImpactPing.MediaInfo
                                        .of("youtube")
                                        .id(youtubeNowPlayingId)
                                        .title(youtubeNowPlayingTitle)
                                        .quality(youtubeStreamQuality)
                                        .reason("playback started")
                                        .ok(true));
                    } catch (Throwable ignored) {}
                }
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clearVideoStatusText();
                    }
                });
            }
        };
    }

    /**
     * 2026-07-15 — After decode size known: letterbox/crop surface; drop force if source is tall.
     */
    private void onVideoDecodedSize(int width, int height) {
        if (videoSurface != null && width > 0 && height > 0) {
            videoSurface.setVideoSize(width, height);
            videoSurface.setAspectRatio(
                    com.solar.launcher.video.VideoSettings.ijkAspectRatio(host.context()));
            // Re-assert center gravity after size-driven requestLayout.
            videoSurface.setLayoutParams(centeredVideoSurfaceLp());
            clearVideoStatusText();
        }
        boolean portraitSource = height > width;
        if (portraitSource) {
            if (com.solar.launcher.LandscapeOrientationGuard.isForceLandscapeVideoSession()) {
                com.solar.launcher.LandscapeOrientationGuard.setForceLandscapeVideoSession(false);
                applyOrientationGuard();
            }
        } else if (host.getCurrentScreenState() == STATE_VIDEO_PLAYER) {
            if (!com.solar.launcher.LandscapeOrientationGuard.isForceLandscapeVideoSession()) {
                com.solar.launcher.LandscapeOrientationGuard.setForceLandscapeVideoSession(true);
                applyOrientationGuard();
            }
        }
    }

    /** Apply crop pref + shared completion/size listener for local + YouTube players. */
    private VideoPlayerController.PlaybackListener videoPlaybackListener() {
        return new VideoPlayerController.PlaybackListener() {
            @Override
            public void onError(int what, int extra) {
                if (!videoPlaybackYoutube) return;
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("what", what);
                    d.put("extra", extra);
                    d.put("quality", youtubeStreamQuality != null ? youtubeStreamQuality : "");
                    com.solar.launcher.Debug9d82a5Log.log(host.context(),
                            "MediaSuiteHost.videoListener.onError", "ijk error → fallback",
                            "E", d);
                } catch (Exception ignored) {}
                // #endregion
                // During playback/buffering — natural wait window; damped one-liner to SolarDev.
                try {
                    com.solar.launcher.soulseek.SolarDeveloperImpactPing.mediaFailed(
                            host.context(),
                            com.solar.launcher.soulseek.SolarDeveloperImpactPing.MediaInfo
                                    .of("youtube")
                                    .id(youtubeNowPlayingId)
                                    .title(youtubeNowPlayingTitle)
                                    .quality(youtubeStreamQuality)
                                    .reason("ijk what=" + what + " extra=" + extra));
                } catch (Throwable ignored) {}
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleYoutubeIjkError();
                    }
                });
            }

            @Override
            public void onCompletion() {
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleVideoPlaybackEnded();
                    }
                });
            }

            @Override
            public void onVideoSize(final int width, final int height) {
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onVideoDecodedSize(width, height);
                    }
                });
            }
        };
    }

    private void startVideoPlayback() {
        if (videoPlaybackYoutube) {
            startYoutubeStreamPlayback();
            return;
        }
        if (videoFiles.isEmpty() || videoIndex < 0 || videoIndex >= videoFiles.size()) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            host.changeScreen(STATE_VIDEOS);
            return;
        }
        // 2026-07-15 — One activity only: video owns the speaker (stop music/FM, not pause).
        // Layman: starting a video mutes any song that was playing.
        // Was: pauseMusicPlayback → music IJK could resume under video.
        host.stopMusicPlayback();
        host.stopNonFmPlayback();
        releaseVideoPlayer();
        FrameLayout surfaceHost = host.findViewById(R.id.video_surface_host);
        if (surfaceHost == null) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            return;
        }
        // 2026-07-15 — Clear OS ~80% headphone lock so volume wheel can climb full range.
        com.solar.launcher.HearingSafetyVolume.ensureFullVolumeRange(host.context());
        videoSurface = new SurfaceRenderView(host.context());
        videoSurface.setAspectRatio(
                com.solar.launcher.video.VideoSettings.ijkAspectRatio(host.context()));
        // Letterbox must sit in the middle of the black panel (FrameLayout default is top-left).
        surfaceHost.addView(videoSurface, centeredVideoSurfaceLp());
        videoController = new VideoPlayerController(host.context());
        videoController.setPlaybackListener(videoPlaybackListener());
        videoController.setBufferingListener(videoBufferingListener());
        videoController.attachHolder(videoSurface.getHolder());
        try {
            setVideoStatusText(host.getString(R.string.youtube_resolve_opening));
            videoController.open(videoFiles.get(videoIndex));
            videoController.play();
            startVideoProgressUpdates();
            updateVideoProgressUi(0);
        } catch (Exception e) {
            clearVideoStatusText();
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            host.changeScreen(STATE_VIDEOS);
        }
    }

    /**
     * YouTube playback — notPipe-aligned: prefer progressive download then local play on Y1.
     * 2026-07-16 — CRITICAL: do NOT call stopNonFmPlayback() here. That path calls
     * stopVideoAndYoutubeStream() and wipes youtubeStreamUrl → "Could not play: no url".
     * Capture the URL first; only stop music; releaseVideoPlayer handles prior video.
     */
    private void startYoutubeStreamPlayback() {
        // Capture before any stop helpers — stopNonFmPlayback used to null this field.
        final String url = youtubeStreamUrl;
        final String vid = youtubeNowPlayingId != null ? youtubeNowPlayingId : "yt";
        final String q = youtubeStreamQuality != null ? youtubeStreamQuality : "360";
        if (url == null || url.isEmpty()) {
            android.util.Log.e("SolarYouTube", "start play aborted: empty stream url id=" + vid);
            Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
            leaveYouTubePlayerOnError();
            return;
        }
        // Keep field in sync in case a later stop wiped it; download uses captured url.
        youtubeStreamUrl = url;
        videoPlaybackYoutube = true;
        android.util.Log.i("SolarYouTube", "start play q=" + q
                + " url=" + (url.length() > 120 ? url.substring(0, 120) : url));
        // Music only — never stopNonFmPlayback (clears YouTube stream state).
        host.stopMusicPlayback();
        releaseVideoPlayer();
        com.solar.launcher.HearingSafetyVolume.ensureFullVolumeRange(host.context());
        FrameLayout surfaceHost = host.findViewById(R.id.video_surface_host);
        if (surfaceHost == null) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            return;
        }
        videoSurface = new SurfaceRenderView(host.context());
        videoSurface.setAspectRatio(
                com.solar.launcher.video.VideoSettings.ijkAspectRatio(host.context()));
        surfaceHost.addView(videoSurface, centeredVideoSurfaceLp());
        videoController = new VideoPlayerController(host.context());
        videoController.setPlaybackListener(videoPlaybackListener());
        videoController.setBufferingListener(videoBufferingListener());
        videoController.attachHolder(videoSurface.getHolder());

        // 2026-07-16 — Real-time JIT streaming via SolarStreamProxy without pre-downloading entire file.
        // If already cached on disk from prior run, open local file instantly.
        final int gen = ++youtubeLoadGen;
        final File cached = YouTubeProgressiveCache.cacheFile(host.context(), vid, q);
        if (YouTubeProgressiveCache.isUsable(cached) && cached.length() > 1024L * 1024L) {
            openYoutubeCachedFile(cached);
            return;
        }
        setVideoStatusText(host.getString(R.string.youtube_resolve_opening));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.solar.launcher.net.SolarStreamProxy.ensureStarted(host.context());
                    final String proxyUrl = com.solar.launcher.net.SolarStreamProxy.proxyUrl(url);
                    if (gen != youtubeLoadGen) return;
                    host.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != youtubeLoadGen) return;
                            try {
                                if (videoController == null) {
                                    toastYouTubePlayError(null);
                                    leaveYouTubePlayerOnError();
                                    return;
                                }
                                setVideoStatusText(host.getString(R.string.youtube_resolve_opening));
                                videoController.openUrl(proxyUrl);
                                videoController.play();
                                startVideoProgressUpdates();
                                updateVideoProgressUi(0);
                            } catch (Exception e) {
                                toastYouTubePlayError(e.getMessage());
                                leaveYouTubePlayerOnError();
                            }
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("SolarYouTube", "proxy start failed", e);
                    if (gen != youtubeLoadGen) return;
                    host.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != youtubeLoadGen) return;
                            try {
                                setVideoStatusText(host.getString(R.string.youtube_resolve_connecting));
                                if (videoController == null) {
                                    toastYouTubePlayError(e.getMessage());
                                    leaveYouTubePlayerOnError();
                                    return;
                                }
                                videoController.openUrl(url);
                                videoController.play();
                                startVideoProgressUpdates();
                                updateVideoProgressUi(0);
                            } catch (Exception e2) {
                                toastYouTubePlayError(e.getMessage());
                                leaveYouTubePlayerOnError();
                            }
                        }
                    });
                }
            }
        }, "YouTubeProxyPlay").start();
    }

    /**
     * ADB/automated play: resolve + play a video id (Videos YouTube path, not audio-only).
     * Usage: am start … --ez solar_adb_play_youtube true --es solar_adb_youtube_id VIDEO_ID
     */
    public void adbPlayYouTubeVideo(String videoId, String title) {
        if (videoId == null || videoId.trim().isEmpty()) {
            android.util.Log.e("SolarYouTube", "adb play: empty id");
            return;
        }
        youtubeAudioMode = false;
        String t = title != null && title.length() > 0 ? title : videoId;
        playYouTubeVideo(new YouTubeVideo(videoId.trim(), t, "", ""));
    }

    /**
     * Play cached progressive MP4 with MediaPlayer (local path — notPipe style).
     * Layman: file is already on disk with Solar TLS; stock player reads the file.
     */
    private void openYoutubeCachedFile(File playFile) {
        if (playFile == null || !playFile.isFile() || playFile.length() < 64 * 1024L) {
            toastYouTubePlayError(null);
            leaveYouTubePlayerOnError();
            return;
        }
        try {
            setVideoStatusText(host.getString(R.string.youtube_resolve_opening));
            if (videoController == null) {
                videoController = new VideoPlayerController(host.context());
                videoController.setPlaybackListener(videoPlaybackListener());
                videoController.setBufferingListener(videoBufferingListener());
                if (videoSurface != null) {
                    videoController.attachHolder(videoSurface.getHolder());
                }
            }
            // Surface may still be settling after long download — open then force play-on-ready.
            videoController.open(playFile);
            videoController.play();
            startVideoProgressUpdates();
            updateVideoProgressUi(0);
            clearVideoStatusText();
            android.util.Log.i("SolarYouTube", "playing cached " + playFile.getName()
                    + " bytes=" + playFile.length());
        } catch (Exception e) {
            android.util.Log.e("SolarYouTube", "open cached failed", e);
            toastYouTubePlayError(e.getMessage());
            leaveYouTubePlayerOnError();
        }
    }

    /**
     * 2026-07-14 — IJK failed current URL; try lower quality once, else leave player.
     * Layman: if 480p link is broken, ask for 360p; if that fails too, go back.
     */
    private void handleYoutubeIjkError() {
        if (!videoPlaybackYoutube) return;
        if (youtubeIjkFallbackPending) return;
        final String next = YouTubeClient.fallbackVideoQuality(youtubeStreamQuality);
        if (next != null && youtubeNowPlayingId != null && youtubeNowPlayingId.length() > 0) {
            youtubeIjkFallbackPending = true;
            final YouTubeVideo retry = new YouTubeVideo(youtubeNowPlayingId,
                    youtubeNowPlayingTitle != null ? youtubeNowPlayingTitle : "", "", "");
            releaseVideoPlayer();
            videoPlaybackYoutube = false;
            youtubeStreamUrl = null;
            // Stay / return to detail so resolve UX is visible, then re-enter player.
            if (host.getCurrentScreenState() == STATE_VIDEO_PLAYER) {
                if (youtubeDetailVideo != null
                        && youtubeNowPlayingId.equals(youtubeDetailVideo.id)) {
                    host.changeScreen(STATE_YOUTUBE_DETAIL);
                } else {
                    host.changeScreen(STATE_YOUTUBE_BROWSE);
                }
            }
            playYouTubeVideoAtQuality(retry, next, true);
            return;
        }
        toastYouTubePlayError(null);
        leaveYouTubePlayerOnError();
    }

    /** 2026-07-14 — Exit video player after YouTube stream failure. */
    private void leaveYouTubePlayerOnError() {
        videoPlaybackYoutube = false;
        youtubeStreamUrl = null;
        youtubeResolvingStream = false;
        releaseVideoPlayer();
        endVideoForceLandscapeSession();
        if (youtubeDetailVideo != null
                && youtubeNowPlayingId != null
                && youtubeNowPlayingId.equals(youtubeDetailVideo.id)) {
            host.changeScreen(STATE_YOUTUBE_DETAIL);
        } else {
            host.changeScreen(STATE_YOUTUBE_BROWSE);
        }
    }

    private void releaseVideoPlayer() {
        stopVideoProgressUpdates();
        clearVideoScrubMode(false);
        clearVideoStatusText();
        if (videoController != null) {
            if (videoSurface != null) {
                videoController.detachHolder(videoSurface.getHolder());
            }
            videoController.release();
            videoController = null;
        }
        FrameLayout surfaceHost = host.findViewById(R.id.video_surface_host);
        if (surfaceHost != null) surfaceHost.removeAllViews();
        videoSurface = null;
    }

    public void toggleVideoPlayPause() {
        if (videoController != null) videoController.togglePlayPause();
    }

    public void onVideoPlaybackStopped() {
        stopVideoProgressUpdates();
        clearVideoScrubMode(false);
        host.setStatusBarVisible(true);
    }

    /**
     * 2026-07-15 — Short Prev/Next: flip file list, or ±5s seek when streaming (YouTube).
     * Was: empty videoFiles return only — YT short press was a silent no-op.
     * Reversal: restore early return when videoFiles.isEmpty().
     */
    public void seekVideoFile(boolean next) {
        if (videoFiles.isEmpty()) {
            // Stream-only session (YouTube): treat short side press like one scrub step.
            seekVideoMs(next ? 5000L : -5000L);
            return;
        }
        videoIndex = next ? videoIndex + 1 : videoIndex - 1;
        if (videoIndex < 0) videoIndex = videoFiles.size() - 1;
        if (videoIndex >= videoFiles.size()) videoIndex = 0;
        pulseVideoTransport();
        startVideoPlayback();
    }

    public void seekVideoMs(long deltaMs) {
        if (videoController == null || !videoController.isPrepared()) return;
        if (videoScrubActive) {
            moveVideoScrubCursor((int) deltaMs);
            return;
        }
        long pos = videoController.getCurrentPosition() + deltaMs;
        if (pos < 0) pos = 0;
        long dur = videoDurationMs();
        if (dur > 0 && pos > dur) pos = dur;
        videoController.seekTo(pos);
        updateVideoProgressUi(pos);
        pulseVideoTransport();
    }

    // --- Photos ---

    private void buildPhotosUi() {
        prepareVirtualListBrowse();
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        host.setBrowserStatusTitle(host.getString(R.string.status_photos));
        virtualLabels.clear();
        virtualLabels.add(host.getString(R.string.common_back_short));

        if (photoBrowseFolder == null) {
            photoFolders = PhotoLibrary.listFolders();
            if (photoFolders.isEmpty()) {
                virtualLabels.add(host.getString(R.string.photo_no_folders));
            } else {
                for (File f : photoFolders) virtualLabels.add(f.getName());
            }
        } else {
            virtualLabels.add(host.getString(R.string.photo_folder_header, photoBrowseFolder.getName()));
            photoFiles = PhotoLibrary.listImagesInFolder(photoBrowseFolder);
            if (photoFiles.isEmpty()) {
                virtualLabels.add(host.getString(R.string.photo_none_in_folder));
            } else {
                for (File f : photoFiles) virtualLabels.add(f.getName());
            }
        }

        bindVirtualAdapter(new VirtualClickHandler() {
            @Override
            public void onClick(int position) {
                if (position == 0) {
                    handleBack();
                    return;
                }
                if (photoBrowseFolder == null) {
                    int idx = position - 1;
                    if (idx >= 0 && idx < photoFolders.size()) {
                        photoBrowseFolder = photoFolders.get(idx);
                        buildPhotosUi();
                    }
                } else {
                    int idx = position - 2;
                    if (idx >= 0 && idx < photoFiles.size()) {
                        photoViewer.setFolder(photoFiles, idx);
                        host.changeScreen(STATE_PHOTO_VIEWER);
                    }
                }
            }
        });
    }

    private void showPhotoViewerLayer(boolean show) {
        FrameLayout layout = host.findViewById(R.id.layout_photo_viewer);
        if (layout != null) {
            layout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (host.layoutBrowserMode() != null) {
            host.layoutBrowserMode().setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void hideVideoAndPhotoLayers() {
        showVideoPlayerLayer(false);
        showPhotoViewerLayer(false);
    }

    private void bindPhotoViewerImage() {
        final ImageView iv = host.findViewById(R.id.iv_photo_viewer);
        if (iv == null) return;
        final File file = photoViewer.currentFile();
        if (file == null) {
            iv.setImageBitmap(null);
            return;
        }
        final int gen = ++photoLoadGen;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = decodeSampled(file, host.getScreenWidthPx(), host.getScreenWidthPx());
                final Bitmap fBmp = bmp;
                host.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != photoLoadGen || host.getCurrentScreenState() != STATE_PHOTO_VIEWER) return;
                        iv.setImageBitmap(fBmp);
                    }
                });
            }
        }).start();
    }

    public void photoViewerNext() {
        if (!photoViewer.hasNext()) return;
        photoViewer.next();
        bindPhotoViewerImage();
    }

    public void photoViewerPrev() {
        if (!photoViewer.hasPrev()) return;
        photoViewer.prev();
        bindPhotoViewerImage();
    }

    public void setPhotoAsWallpaper() {
        File file = photoViewer.currentFile();
        if (file == null) return;
        if (PhotoWallpaperHelper.applyAsBackground(host.context(), file, host.prefs())) {
            Toast.makeText(host.context(), R.string.toast_bg_applied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(host.context(), R.string.photo_wallpaper_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public String photoViewerPositionLabel() {
        return String.format(Locale.US, "%d / %d",
                photoViewer.getIndex() + 1, Math.max(1, photoViewer.getCount()));
    }

    // --- Settings rows ---

    public List<SettingsRow> buildFmSettingsRows() {
        List<SettingsRow> rows = new ArrayList<SettingsRow>();
        rows.add(new SettingsRow(SettingsScreens.RADIO_FM_BAND, R.string.radio_settings_fm_band, true));
        rows.add(new SettingsRow(ROW_AUTO_DETECT, R.string.radio_settings_auto_region, false));
        return rows;
    }

    public List<SettingsRow> buildRadioSettingsRows() {
        List<SettingsRow> rows = new ArrayList<SettingsRow>(buildFmSettingsRows());
        rows.add(new SettingsRow(SettingsScreens.RADIO_INTERNET_COUNTRY,
                R.string.radio_settings_internet_country, true));
        rows.add(new SettingsRow(ROW_BUFFER_SD, R.string.radio_settings_buffer_sd, false));
        return rows;
    }

    public List<SettingsRow> buildVideoSettingsRows() {
        List<SettingsRow> rows = new ArrayList<SettingsRow>();
        rows.add(new SettingsRow(ROW_VIDEO_SLEEP, R.string.video_settings_sleep_during_playback, false));
        // 2026-07-15 — Letterbox (default) vs crop-to-fill for 4:3 panels.
        rows.add(new SettingsRow(ROW_VIDEO_CROP, R.string.video_settings_crop_mode, false));
        return rows;
    }

    public boolean toggleSleepDuringPlayback() {
        Context ctx = host.context();
        boolean next = !com.solar.launcher.video.VideoSettings.getSleepDuringPlayback(ctx);
        com.solar.launcher.video.VideoSettings.setSleepDuringPlayback(ctx, next);
        return next;
    }

    public boolean isSleepDuringPlaybackEnabled() {
        return com.solar.launcher.video.VideoSettings.getSleepDuringPlayback(host.context());
    }

    /** 2026-07-15 — Cycle letterbox ↔ crop 4:3; returns new mode key. */
    public String cycleVideoCropMode() {
        return com.solar.launcher.video.VideoSettings.cycleCropMode(host.context());
    }

    /** Short label for settings state column. */
    public String videoCropModeLabel() {
        return com.solar.launcher.video.VideoSettings.cropModeLabel(host.context());
    }

    public List<SettingsRow> buildFmBandSettingsRows() {
        List<SettingsRow> rows = new ArrayList<SettingsRow>();
        for (String region : FM_BAND_REGIONS) {
            rows.add(new SettingsRow("radio.fm_band." + region, labelResForRegion(region), false));
        }
        return rows;
    }

    /**
     * User picked a band in Settings — remember it and stop auto-detect.
     * 2026-07-15 — Clearing auto-detect keeps manual choice when getFmBandRegion honors locale.
     * Reversal: set region only (old); auto-detect stayed on and overwrote dial limits.
     */
    public void applyFmBandRegion(String region) {
        RadioSettings.setAutoDetectRegion(host.context(), false);
        RadioSettings.setFmBandRegion(host.context(), region);
        radioTuneFreqKhz = currentFmPlan().clampKhz(radioTuneFreqKhz);
    }

    public void applyInternetCountry(String isoCode) {
        RadioSettings.setInternetRadioCountry(host.context(), isoCode);
        netCountryCode = isoCode;
    }

    public boolean toggleAutoDetectRegion() {
        Context ctx = host.context();
        boolean next = !RadioSettings.getAutoDetectRegion(ctx);
        RadioSettings.setAutoDetectRegion(ctx, next);
        if (next) {
            // Cache detected band without clearing auto-detect (applyFmBandRegion turns it off).
            RadioSettings.setFmBandRegion(ctx, RadioSettings.detectFmBandFromLocale(ctx));
            radioTuneFreqKhz = currentFmPlan().clampKhz(radioTuneFreqKhz);
        }
        return next;
    }

    public boolean toggleBufferOnSd() {
        Context ctx = host.context();
        boolean next = !RadioSettings.getBufferOnSd(ctx);
        RadioSettings.setBufferOnSd(ctx, next);
        return next;
    }

    public String fmBandRegionLabel() {
        return labelForRegion(RadioSettings.getFmBandRegion(host.context()));
    }

    public String internetCountryLabel() {
        return RadioSettings.getInternetRadioCountry(host.context());
    }

    public boolean isAutoDetectRegionEnabled() {
        return RadioSettings.getAutoDetectRegion(host.context());
    }

    public boolean isBufferOnSdEnabled() {
        return RadioSettings.getBufferOnSd(host.context());
    }

    public void release() {
        netLoadGen++;
        stopFmRdsPolling();
        fmEngine.release();
        internetRadioPlayer.stop();
        releaseVideoPlayer();
    }

    // --- Browser chrome helpers ---

    private void prepareScrollBrowse() {
        host.resetBrowserListHost();
        host.showVirtualSongList(false);
        View scroll = host.findViewById(R.id.scroll_view_browser);
        if (scroll != null) scroll.setVisibility(View.VISIBLE);
    }

    private void prepareVirtualListBrowse() {
        host.resetBrowserListHost();
        host.showVirtualSongList(true);
        View scroll = host.findViewById(R.id.scroll_view_browser);
        if (scroll != null) scroll.setVisibility(View.GONE);
    }

    private void clearVirtualList() {
        host.listVirtualSongs().setVisibility(View.GONE);
        host.listVirtualSongs().setAdapter(null);
        host.containerBrowserItems().removeAllViews();
        virtualLabels.clear();
        virtualSubtitles.clear();
        youtubeBrowseRows.clear();
    }

    private void addBackRow(String label) {
        Button back = host.createListButton(label);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.clickFeedback();
                handleBack();
            }
        });
        host.containerBrowserItems().addView(back);
    }

    private void addActionRow(String label, final Runnable action) {
        Button row = host.createListButton(label);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.clickFeedback();
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("label", label);
                    d.put("screen", host.getCurrentScreenState());
                    DebugAgentLog.log(host.context(), "MediaSuiteHost.actionRow",
                            "scroll row click", "H-D", d);
                } catch (Exception ignored) {}
                // #endregion
                action.run();
            }
        });
        host.containerBrowserItems().addView(row);
    }

    private void addStatusRow(String text) {
        addStatusButton(text);
    }

    private Button addStatusButton(String text) {
        Button row = host.createListButton(text);
        row.setEnabled(false);
        row.setFocusable(false);
        host.containerBrowserItems().addView(row);
        return row;
    }

    private void focusFirstBrowserChild() {
        if (host.containerBrowserItems().getChildCount() > 0) {
            host.containerBrowserItems().getChildAt(0).requestFocus();
        }
    }

    private interface VirtualClickHandler {
        void onClick(int position);
    }

    private void bindVirtualAdapter(final VirtualClickHandler handler) {
        final int rowW = host.messagingRowWidthPx();
        virtualAdapter = new SimpleListAdapter(rowW, handler);
        host.listVirtualSongs().setAdapter(virtualAdapter);
        host.listVirtualSongs().post(new Runnable() {
            @Override
            public void run() {
                if (host.listVirtualSongs().getChildCount() > 0) {
                    host.listVirtualSongs().getChildAt(0).requestFocus();
                } else {
                    FocusScrollHelper.focusListPosition(host.listVirtualSongs(), 0);
                }
            }
        });
    }

    /**
     * 2026-07-15 — Refresh virtual list without yanking focus to row 0 (search streaming).
     * Layman: keep the blue bar on the row you were on while results fill in.
     * Only re-focus when selection was lost after notify (never fight live DPAD/wheel).
     */
    private void notifyVirtualDataChangedPreserveFocus() {
        final ListView lv = host.listVirtualSongs();
        if (lv == null || virtualAdapter == null) return;
        int pos = lv.getSelectedItemPosition();
        if (pos < 0) {
            View foc = host.activity() != null ? host.activity().getCurrentFocus() : null;
            if (foc != null) {
                for (int i = 0; i < lv.getChildCount(); i++) {
                    View child = lv.getChildAt(i);
                    if (child == foc || isDescendant(child, foc)) {
                        pos = lv.getFirstVisiblePosition() + i;
                        break;
                    }
                }
            }
        }
        final int restore = pos;
        virtualAdapter.notifyDataSetChanged();
        if (restore < 0) return;
        lv.post(new Runnable() {
            @Override
            public void run() {
                if (virtualAdapter == null) return;
                int count = virtualAdapter.getCount();
                if (count <= 0) return;
                // If focus already sits on a list child, leave A5 face/wheel alone.
                View foc = host.activity() != null ? host.activity().getCurrentFocus() : null;
                if (foc != null) {
                    for (int i = 0; i < lv.getChildCount(); i++) {
                        if (isDescendant(lv.getChildAt(i), foc) || lv.getChildAt(i) == foc) {
                            return;
                        }
                    }
                }
                int selected = lv.getSelectedItemPosition();
                if (selected >= 0 && selected < count) return;
                int target = restore >= count ? count - 1 : restore;
                FocusScrollHelper.focusListPosition(lv, target);
            }
        });
    }

    private static boolean isDescendant(View root, View child) {
        if (root == null || child == null) return false;
        View p = child;
        while (p != null) {
            if (p == root) return true;
            android.view.ViewParent vp = p.getParent();
            p = vp instanceof View ? (View) vp : null;
        }
        return false;
    }

    /** True while YouTube browse is fetching search/popular results. */
    public boolean isYoutubeBrowseLoading() {
        int st = host.getCurrentScreenState();
        return (st == STATE_YOUTUBE_BROWSE || st == STATE_YOUTUBE_DETAIL) && youtubeLoading;
    }

    private final class SimpleListAdapter extends BaseAdapter {
        private final int rowWidth;
        private final VirtualClickHandler handler;

        SimpleListAdapter(int rowWidth, VirtualClickHandler handler) {
            this.rowWidth = rowWidth;
            this.handler = handler;
        }

        @Override
        public int getCount() {
            return virtualLabels.size();
        }

        @Override
        public Object getItem(int position) {
            return virtualLabels.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final String title = virtualLabels.get(position);
            final String subtitle = position < virtualSubtitles.size()
                    ? virtualSubtitles.get(position) : "";
            final boolean statusRow = position < youtubeBrowseRows.size()
                    && youtubeBrowseRows.get(position).kind == YoutubeBrowseRow.KIND_STATUS;

            if (subtitle != null && subtitle.length() > 0) {
                android.widget.LinearLayout row;
                android.widget.TextView tvTitle;
                android.widget.TextView tvSub;
                if (convertView instanceof android.widget.LinearLayout
                        && "solar_two_line_row".equals(convertView.getTag())
                        && ((android.widget.LinearLayout) convertView).getChildCount() >= 2) {
                    row = (android.widget.LinearLayout) convertView;
                    tvTitle = (android.widget.TextView) row.getChildAt(0);
                    tvSub = (android.widget.TextView) row.getChildAt(1);
                    tvTitle.setText(title);
                    tvSub.setText(subtitle);
                } else {
                    // 2026-07-15 — Guard cast: createTwoLineBrowseRow must be LinearLayout
                    // (used to return Button when full_width_menus off → crash).
                    View created = host.createTwoLineBrowseRow(title, subtitle);
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("createdClass", created != null
                                ? created.getClass().getName() : "null");
                        d.put("isLinear", created instanceof android.widget.LinearLayout);
                        com.solar.launcher.Debug9d82a5Log.log(host.context(),
                                "SimpleListAdapter.getView", "two-line create", "F", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    if (!(created instanceof android.widget.LinearLayout)
                            || ((android.widget.LinearLayout) created).getChildCount() < 2) {
                        // Safe degrade: single Button with title (never ClassCast).
                        Button btn = host.createListButton(title);
                        btn.setLayoutParams(new ListView.LayoutParams(
                                rowWidth, host.y1RowHeightPx()));
                        btn.setEnabled(!statusRow);
                        if (!statusRow) {
                            btn.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    host.clickFeedback();
                                    handler.onClick(position);
                                }
                            });
                        }
                        return btn;
                    }
                    row = (android.widget.LinearLayout) created;
                    row.setTag("solar_two_line_row");
                    tvTitle = (android.widget.TextView) row.getChildAt(0);
                    tvSub = (android.widget.TextView) row.getChildAt(1);
                }
                // 2026-07-14 — A5 two-tap on two-line YouTube/browse rows (was raw one-tap).
                if (statusRow) {
                    row.setOnClickListener(null);
                    row.setFocusable(false);
                    row.setEnabled(false);
                } else {
                    com.solar.launcher.A5FocusConfirm.setOnClickListener(row, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            host.clickFeedback();
                            handler.onClick(position);
                        }
                    });
                    row.setFocusable(true);
                    row.setEnabled(true);
                    attachFmPresetTouchReorder(row, position);
                }
                return row;
            }

            Button btn;
            if (convertView instanceof Button) {
                btn = (Button) convertView;
            } else {
                btn = host.createListButton("");
                btn.setLayoutParams(new ListView.LayoutParams(rowWidth, host.y1RowHeightPx()));
            }
            btn.setText(title);
            btn.setEnabled(!statusRow);
            // createListButton already wraps; keep assign for recycle rebinds.
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (statusRow) return;
                    host.clickFeedback();
                    handler.onClick(position);
                }
            });
            if (!statusRow) attachFmPresetTouchReorder(btn, position);
            return btn;
        }
    }

    /**
     * 2026-07-15 — Touch long-press / drag for FM preset reorder (OK-hold unchanged).
     * Reversal: no-op method body.
     */
    private void attachFmPresetTouchReorder(final View row, final int virtualPosition) {
        if (row == null || !isFmPresetListActive() || virtualPosition <= 0) return;
        if (!MoveRibbonTouch.touchReorderEnabled()) return;
        final int dataIdx = fmPresetDataIndexFromVirtualPosition(virtualPosition);
        if (dataIdx < 0) return;
        if (fmPresetMoveFrom >= 0 && dataIdx == fmPresetMoveFrom) {
            MoveRibbonTouch.attachActiveDrag(row, host.y1RowHeightPx() + 2,
                    new MoveRibbonTouch.Callbacks() {
                        @Override
                        public void onLift() {}

                        @Override
                        public void onStep(int delta) {
                            handleFmPresetMoveWheel(delta);
                        }

                        @Override
                        public void onConfirm() {
                            confirmFmPresetMove();
                        }
                    });
            return;
        }
        if (fmPresetMoveFrom >= 0) return;
        MoveRibbonTouch.attachBrowseLift(row, MoveRibbonTouch.LIFT_HOLD_MS,
                new MoveRibbonTouch.Callbacks() {
                    @Override
                    public void onLift() {
                        beginFmPresetMove(dataIdx);
                        host.clickFeedback();
                    }

                    @Override
                    public void onStep(int delta) {}

                    @Override
                    public void onConfirm() {}
                });
    }

    // --- Utility ---

    private String lastFmBandDebugKey = "";

    private FmBandPlan currentFmPlan() {
        // #region agent log
        try {
            android.content.Context c = host.context();
            boolean auto = RadioSettings.getAutoDetectRegion(c);
            String effective = RadioSettings.getFmBandRegion(c);
            String detected = RadioSettings.detectFmBandFromLocale(c);
            String key = auto + "|" + effective + "|" + detected;
            if (!key.equals(lastFmBandDebugKey)) {
                lastFmBandDebugKey = key;
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("auto", auto);
                d.put("effective", effective);
                d.put("detected", detected);
                com.solar.launcher.debug.SessionDebugLog.log(c, "MediaSuiteHost.currentFmPlan",
                        "band resolve", "F2", d);
            }
        } catch (Exception ignored) {}
        // #endregion
        return FmBandPlan.fromRegionCode(RadioSettings.getFmBandRegion(host.context()));
    }

    /**
     * Band default when nothing is remembered — last dial if any, else 101.1 MHz.
     * 2026-07-15 — Was always 101.1. Reversal: hardcode 101.1 again.
     */
    private int defaultFmKhz() {
        FmBandPlan plan = currentFmPlan();
        int last = RadioSettings.getLastFmKhz(host.context());
        if (last > 0) return plan.clampKhz(last);
        return plan.clampKhz(Math.round(101.1f * 1000f));
    }

    private int currentFmFreqKhz() {
        PlayQueue.QueueItem cur = host.playback().unifiedQueue().current();
        if (cur != null && cur.fmFreqKhz > 0) return cur.fmFreqKhz;
        if (radioTuneFreqKhz > 0) return radioTuneFreqKhz;
        return defaultFmKhz();
    }

    private static int presetIndexForFreq(List<FmPresetStore.Preset> presets, int khz) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).freqKhz == khz) return i;
        }
        return 0;
    }

    private static int labelResForRegion(String region) {
        if ("EU".equals(region)) return R.string.radio_band_eu;
        if ("JP".equals(region)) return R.string.radio_band_jp;
        if ("AU".equals(region)) return R.string.radio_band_au;
        if ("KR".equals(region)) return R.string.radio_band_kr;
        if ("RU".equals(region)) return R.string.radio_band_ru;
        return R.string.radio_band_us;
    }

    private static String labelForRegion(String region) {
        if (region == null) return "US";
        return region.toUpperCase(Locale.US);
    }

    private static Bitmap decodeSampled(File file, int reqWidth, int reqHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
