package com.solar.launcher.media;

import android.app.Activity;
import android.content.Context;
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
import com.solar.launcher.youtube.NotPipeClient;
import com.solar.launcher.youtube.NotPipePmRegistrar;
import com.solar.launcher.youtube.YouTubeRecentSearches;
import com.solar.launcher.youtube.YouTubeResultJson;
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

    /** First FM recordings folder that exists, else primary volume default. */
    public static File fmRecordingsDir() {
        for (File dir : com.solar.launcher.DeviceFeatures.getFmRecordingRoots()) {
            if (dir.isDirectory()) return dir;
        }
        java.util.List<File> roots = com.solar.launcher.DeviceFeatures.getFmRecordingRoots();
        return roots.isEmpty() ? new File("/storage/sdcard0/FM Recordings") : roots.get(0);
    }

    private static final int NET_PAGE_SIZE = 40;
    private static final String[] FM_BAND_REGIONS = {"US", "EU", "JP", "AU", "KR", "RU"};

    /** Settings row keys — pair with {@link SettingsScreens}. */
    public static final String ROW_AUTO_DETECT = "radio.auto_detect";
    public static final String ROW_BUFFER_SD = "radio.buffer_sd";
    public static final String ROW_VIDEO_SLEEP = "video.sleep_during_playback";

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

        /** Open wheel keyboard for YouTube search — result delivered via {@link #onYouTubeSearchSubmitted}. */
        void openYouTubeSearchKeyboard(String prefill);

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
    private String youtubePendingSearch;
    private String youtubeNowPlayingTitle;
    private String youtubeNowPlayingId;
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

    public MediaSuiteHost(Host host) {
        this.host = host;
        Context ctx = host.context();
        fmEngine = new FmEngine(ctx);
        fmRdsPoller = new FmRdsPoller(fmEngine);
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
                        host.runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (host.playback().isFmActive()) {
                                            host.refreshPlayerUi();
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
                || state == STATE_RADIO_FM_PLAYER;
    }

    /** Browse/list screens that share MainActivity browser wheel + focus (not full-screen player/viewer). */
    public static boolean isMediaListBrowseState(int state) {
        return state == STATE_RADIO || state == STATE_RADIO_FM_BROWSE
                || state == STATE_RADIO_FM_PLAYER
                || state == STATE_RADIO_NET_BROWSE || state == STATE_VIDEOS
                || state == STATE_VIDEO_HUB || state == STATE_YOUTUBE_BROWSE
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
            case STATE_VIDEO_PLAYER:
                showVideoPlayerLayer(true);
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
                break;
            case STATE_VIDEO_PLAYER:
                releaseVideoPlayer();
                showVideoPlayerLayer(false);
                onVideoPlaybackStopped();
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
                releaseVideoPlayer();
                showVideoPlayerLayer(false);
                if (videoPlaybackYoutube) {
                    videoPlaybackYoutube = false;
                    host.changeScreen(STATE_YOUTUBE_BROWSE);
                } else {
                    host.changeScreen(STATE_VIDEOS);
                }
                return true;
            case STATE_YOUTUBE_BROWSE:
                host.changeScreen(STATE_VIDEO_HUB);
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
                if (!com.solar.launcher.radio.RadioExperiment.isInternetRadioEnabled(host.prefs())) {
                    host.exitToHomeMenu();
                    return true;
                }
                host.changeScreen(STATE_RADIO);
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
                host.exitToHomeMenu();
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
        focusFirstBrowserChild();
    }

    /** Home menu FM — import JJ presets once, open NP, auto-start last frequency. 2026-07-06 */
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
        int khz = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : defaultFmKhz();
        List<FmPresetStore.Preset> presets = fmPresets.listAll();
        if (!presets.isEmpty()) {
            khz = presets.get(0).freqKhz;
        }
        startFmStation(khz, null);
    }

    /** 2026-07-06 — JJ-style FM player: MHz panel, preset rows, settings entry. */
    private void buildFmPlayerUi() {
        host.applyReachBrowseLayoutMode();
        host.showReachBrowseList(true);
        prepareScrollBrowse();
        host.setBrowserStatusTitle(host.getString(R.string.status_radio_fm));
        fmSettingsMode = false;

        if (!fmEngine.isAvailable()) {
            addStatusRow(host.getString(R.string.radio_fm_unavailable));
            focusFirstBrowserChild();
            return;
        }

        final FmBandPlan plan = currentFmPlan();
        int khz = host.playback().isFmActive() ? currentFmFreqKhz() : radioTuneFreqKhz;
        if (khz <= 0) khz = defaultFmKhz();
        final String mhzLabel =
                FmBandPlan.formatMhz(khz / 1000f) + " MHz";
        final boolean powered = fmEngine.isPowerUp() || host.playback().isFmActive();

        addStatusRow(mhzLabel);
        if (powered) {
            String ps = cachedRdsPs;
            if (ps != null && !ps.isEmpty()) {
                addStatusRow(ps);
            }
        } else {
            addStatusRow(host.getString(R.string.radio_fm_power_off_hint));
        }

        final List<FmPresetStore.Preset> presets = fmPresets.listAll();
        for (final FmPresetStore.Preset p : presets) {
            String label =
                    p.label != null && !p.label.isEmpty()
                            ? p.label
                            : FmBandPlan.khzToFraction(p.freqKhz, plan);
            addActionRow(label, new Runnable() {
                @Override
                public void run() {
                    startFmStation(p.freqKhz, p.label);
                    buildFmPlayerUi();
                }
            });
        }

        addActionRow(host.getString(R.string.radio_fm_settings_row), new Runnable() {
            @Override
            public void run() {
                fmSettingsMode = true;
                radioSubMode = RADIO_FM_SETTINGS;
                buildFmSettingsSubmenuUi();
            }
        });

        if (!host.playback().isFmActive() && !fmEngine.isPowerUp()) {
            addActionRow(host.getString(R.string.radio_fm_power_on_row), new Runnable() {
                @Override
                public void run() {
                    int freq = radioTuneFreqKhz > 0 ? radioTuneFreqKhz : defaultFmKhz();
                    startFmStation(freq, null);
                }
            });
        }

        focusFirstBrowserChild();
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
                        if (fmEngine.isPowerUp()) {
                            stopFmRdsPolling();
                            fmEngine.powerDown();
                            host.playback().stopRadio();
                            fmMuted = false;
                        } else {
                            host.stopMusicPlayback();
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

        addActionRow(
                host.getString(R.string.radio_fm_audio_output_row)
                        + ": "
                        + host.getString(
                                fmEngine.isSpeakerOn()
                                        ? R.string.radio_fm_output_speaker
                                        : R.string.radio_fm_output_earphones),
                new Runnable() {
                    @Override
                    public void run() {
                        fmEngine.setSpeaker(!fmEngine.isSpeakerOn());
                        buildFmSettingsSubmenuUi();
                    }
                });

        addStatusRow(host.getString(R.string.radio_fm_onboarding_hint));
        focusFirstBrowserChild();
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
                        startFmStation(p.freqKhz, p.label);
                        fmSettingsMode = false;
                        buildFmPlayerUi();
                    }
                });
    }

    /** Tune ±step when settings tune mode active. */
    public boolean handleFmPlayerWheelTune(boolean up) {
        if (!fmSettingsMode || !fmTuningMode) return false;
        FmBandPlan plan = currentFmPlan();
        int khz = currentFmFreqKhz();
        if (khz <= 0) khz = radioTuneFreqKhz;
        khz = up ? khz + plan.stepKhz() : khz - plan.stepKhz();
        khz = plan.clampKhz(khz);
        radioTuneFreqKhz = khz;
        if (fmEngine.isPowerUp()) {
            fmEngine.tune(khz);
        }
        if (host.playback().isFmActive()) {
            host.refreshPlayerUi();
        }
        buildFmSettingsSubmenuUi();
        return true;
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
                                        startFmStation(first, null);
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
                startFmStation(p.freqKhz, p.label);
            }
        });
    }

    private void startFmScan() {
        radioSubMode = RADIO_FM_SCAN;
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
                                                            status.setText(
                                                                    host.getString(
                                                                            R.string.radio_fm_scan_done));
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

    private void openFmRecordingsFolder() {
        File dir = fmRecordingsDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Toast.makeText(host.context(), R.string.radio_fm_recordings_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        videoBrowseFolder = dir;
        host.changeScreen(STATE_VIDEOS);
    }

    /** Stop the other radio path before starting FM or internet playback. */
    private void stopOtherRadioPlayback(boolean startingFm) {
        if (startingFm) {
            internetRadioPlayer.stop();
        } else {
            stopFmRecordingQuiet();
            stopFmRdsPolling();
            fmEngine.powerDown();
            fmMuted = false;
        }
    }

    private void startFmStation(final int freqKhz, final String label) {
        final FmBandPlan plan = currentFmPlan();
        final int clampedKhz = plan.clampKhz(freqKhz);
        radioTuneFreqKhz = clampedKhz;
        if (!fmEngine.isAvailable()) {
            Toast.makeText(host.context(), R.string.toast_fm_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        stopOtherRadioPlayback(true);
        host.stopMusicPlayback();
        // 2026-07-06 — MTK bind/tune off UI thread; avoids ANR and browse crash on slow FMRadioService.
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        final boolean ok = fmEngine.playStation(clampedKhz);
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
                                        finishFmStationStart(clampedKhz, label, plan);
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
        host.playback().startRadioStation(PlayQueue.QueueItem.fmStation(freqKhz, label));
        if (!fmPresets.listAll().isEmpty()) {
            FmQueueSync.syncQueueFromPresets(host.playback(), fmPresets, freqKhz);
        }
        radioScrubMode = RadioScrubMode.NONE;
        fmTuneRevertKhz = freqKhz;
        ensureFmRdsPolling();
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
        host.stopMusicPlayback();
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

    /** Bind Now Playing title/artist lines for FM or internet radio queue items. */
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

        if (playback.isFmActive()) {
            String ps = cachedRdsPs;
            if (ps != null && !ps.isEmpty()) titleText = ps;
            String rt = cachedRdsRt;
            if (rt != null && !rt.isEmpty()) {
                artistText = rt;
            }
            FmBandPlan plan = currentFmPlan();
            // 2026-07-06 — tune wheel MHz beats queue row until user commits (MTK live dial).
            int khz = (radioScrubMode == RadioScrubMode.TUNE_FM && radioTuneFreqKhz > 0)
                    ? radioTuneFreqKhz
                    : fmFreqKhz();
            albumText = FmBandPlan.formatMhz(khz / 1000f);
            if (fmRecorder.isRecording()) {
                trackCountText = formatFmRecordingStatus();
            } else if (fmSeekBusy) {
                trackCountText = host.getString(R.string.radio_fm_seeking);
            } else if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                trackCountText = host.getString(R.string.radio_fm_tuning_hint);
            }
            showPause = fmMuted;
            android.widget.ImageView albumArt = host.findViewById(R.id.iv_album_art);
            if (albumArt != null) {
                albumArt.setImageResource(R.drawable.radio_fm_np_placeholder);
                albumArt.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            }
        } else if (playback.isInternetRadioActive()) {
            if (cur.radioSubtitle != null && !cur.radioSubtitle.isEmpty()) {
                artistText = cur.radioSubtitle;
            } else {
                artistText = host.getString(R.string.status_radio_internet);
            }
            showPause = !internetRadioPlayer.isPlaying();
        }

        if (title != null) title.setText(titleText);
        if (artist != null) artist.setText(artistText);
        if (album != null) album.setText(albumText);
        // 2026-07-06 — FM N/M lives in updateMusicTrackCountUi unless tune/rec hint here.
        if (trackCount != null
                && (!playback.isFmActive()
                        || radioScrubMode == RadioScrubMode.TUNE_FM
                        || fmRecorder.isRecording()
                        || fmSeekBusy)) {
            trackCount.setText(trackCountText);
        }
        if (vizTitle != null) vizTitle.setText(titleText);
        if (vizArtist != null) vizArtist.setText(artistText);
        if (vizAlbum != null) vizAlbum.setText(albumText);
        if (pauseOverlay != null) {
            pauseOverlay.setVisibility(showPause ? View.VISIBLE : View.GONE);
        }
        android.widget.ImageView albumArt = host.findViewById(R.id.iv_album_art);
        if (albumArt != null && !playback.isFmActive()) {
            albumArt.setAlpha(showPause ? 0.4f : 1.0f);
        } else if (albumArt != null) {
            albumArt.setAlpha(showPause ? 0.4f : 1.0f);
        }
        updateRadioPlayerProgress();
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
            float pos = RadioScrubMapping.khzToPosition(khz, plan);
            bar.setProgress(Math.round(pos * 100f));
            // 2026-07-06 — tune mode: keep MHz header + circle knob aligned with wheel (MTK scrub bar).
            if (radioScrubMode == RadioScrubMode.TUNE_FM) {
                host.syncFmTuneScrubUi();
            } else if (transport != null) {
                android.media.AudioManager am = (android.media.AudioManager) host.context().getSystemService(Context.AUDIO_SERVICE);
                int cur = am != null ? am.getStreamVolume(10) : 0;
                int max = am != null ? am.getStreamMaxVolume(10) : 1;
                transport.showFmNormalBar(cur, max);
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
            bindRadioNowPlayingUi();
            host.syncFmTuneScrubUi();
            host.refreshPlayerUi();
            // #region agent log
            try {
                DebugF9ef0bLog.log(
                        host.context(),
                        "MediaSuiteHost.handleRadioCenterOk",
                        "fm center ok",
                        "H2",
                        new org.json.JSONObject().put("scrubMode", radioScrubMode.name()));
            } catch (Exception ignored) {}
            // #endregion
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
                    startFmStation(item.fmFreqKhz, item.fmLabel);
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
        startFmStation(p.freqKhz, p.label);
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
        startFmStation(freqKhz, label);
    }

    public String[] getRadioContextMenuLabels() {
        PlaybackCoordinator playback = host.playback();
        if (playback.isFmActive()) {
            boolean saved = fmPresets.containsFreq(currentFmFreqKhz());
            String recordLabel =
                    fmRecorder.isRecording()
                            ? host.getString(R.string.radio_fm_record_stop)
                            : host.getString(R.string.radio_fm_record_start);
            return new String[] {
                recordLabel,
                host.getString(R.string.radio_ctx_save_preset),
                saved ? host.getString(R.string.radio_ctx_remove_preset)
                        : host.getString(R.string.radio_ctx_scan),
                host.getString(saved ? R.string.radio_ctx_scan : R.string.radio_ctx_open_fm_browse)
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
                case 3:
                    if (saved) {
                        host.changeScreen(STATE_RADIO_FM_BROWSE);
                        startFmScan();
                    } else {
                        host.changeScreen(STATE_RADIO_FM_BROWSE);
                    }
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

    /** UI-thread gate after background notPipe PM register attempt (2026-07-06). */
    private void openYouTubeAfterNotPipeReady() {
        final boolean pmInstalled = NotPipeClient.isNotPipeInstalled(host.context());
        if (!pmInstalled) {
            Toast.makeText(host.context(), R.string.youtube_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        final int probeGen = ++youtubeLoadGen;
        NotPipeClient.getInstance(host.context()).probe(new NotPipeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (probeGen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_VIDEO_HUB) return;
                host.changeScreen(STATE_YOUTUBE_BROWSE);
            }

            @Override
            public void onError(String message) {
                if (probeGen != youtubeLoadGen) return;
                Toast.makeText(host.context(), R.string.youtube_unavailable, Toast.LENGTH_LONG).show();
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
        addActionRow(host.getString(R.string.video_youtube_row), new Runnable() {
            @Override
            public void run() {
                if (!host.requireInternet(R.string.toast_internet_required)) return;
                // 2026-07-06 — PM may lag /system bake; register from asset before gate.
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NotPipePmRegistrar.ensureRegisteredBlocking(host.context());
                        host.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                openYouTubeAfterNotPipeReady();
                            }
                        });
                    }
                }, "YouTubeNotPipeGate").start();
            }
        });
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
                    playYouTubeVideo(youtubeVideos.get(row.videoIndex));
                }
                break;
            default:
                break;
        }
    }

    private void loadYouTubePopular() {
        youtubePendingSearch = null;
        youtubeLoading = true;
        final int gen = ++youtubeLoadGen;
        buildYouTubeBrowseUi();
        NotPipeClient.getInstance(host.context()).fetchPopular(new NotPipeClient.Callback() {
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
                buildYouTubeBrowseUi();
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                youtubeVideos.clear();
                buildYouTubeBrowseUi();
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
        if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
        NotPipeClient.getInstance(host.context()).search(query, new NotPipeClient.Callback() {
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
                buildYouTubeBrowseUi();
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                if (host.getCurrentScreenState() != STATE_YOUTUBE_BROWSE) return;
                youtubeLoading = false;
                youtubeVideos.clear();
                buildYouTubeBrowseUi();
                Toast.makeText(host.context(),
                        host.getString(R.string.youtube_error, message),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Solar-native playback — resolve stream via notPipe bridge, play in Solar IJK player. */
    private void playYouTubeVideo(final YouTubeVideo video) {
        if (video == null || video.id.isEmpty()) return;
        youtubeNowPlayingTitle = video.title;
        youtubeNowPlayingId = video.id;
        final int gen = ++youtubeLoadGen;
        youtubeLoading = true;
        rebuildYouTubeVirtualRows();
        if (virtualAdapter != null) virtualAdapter.notifyDataSetChanged();
        NotPipeClient.getInstance(host.context()).resolveStream(video.id, new NotPipeClient.Callback() {
            @Override
            public void onSuccess(String payloadJson) {
                if (gen != youtubeLoadGen) return;
                youtubeLoading = false;
                try {
                    youtubeStreamUrl = YouTubeResultJson.parseStreamUrl(payloadJson);
                } catch (Exception e) {
                    youtubeStreamUrl = null;
                }
                if (youtubeStreamUrl == null || youtubeStreamUrl.isEmpty()) {
                    if (host.getCurrentScreenState() == STATE_YOUTUBE_BROWSE) {
                        buildYouTubeBrowseUi();
                    }
                    Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                videoPlaybackYoutube = true;
                videoFiles.clear();
                host.changeScreen(STATE_VIDEO_PLAYER);
            }

            @Override
            public void onError(String message) {
                if (gen != youtubeLoadGen) return;
                youtubeLoading = false;
                if (host.getCurrentScreenState() == STATE_YOUTUBE_BROWSE) {
                    buildYouTubeBrowseUi();
                }
                Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
            }
        });
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

    public boolean isYouTubePlaybackActive() {
        return videoPlaybackYoutube;
    }

    /** Context action — play a YouTube row without OK tap. */
    public void playYouTubeFromContext(YouTubeVideo video) {
        playYouTubeVideo(video);
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
        host.pauseMusicPlayback();
        releaseVideoPlayer();
        FrameLayout surfaceHost = host.findViewById(R.id.video_surface_host);
        if (surfaceHost == null) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            return;
        }
        videoSurface = new SurfaceRenderView(host.context());
        surfaceHost.addView(videoSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoController = new VideoPlayerController();
        videoController.attachHolder(videoSurface.getHolder());
        try {
            videoController.open(videoFiles.get(videoIndex));
            videoController.play();
            startVideoProgressUpdates();
            updateVideoProgressUi(0);
        } catch (Exception e) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            host.changeScreen(STATE_VIDEOS);
        }
    }

    /** Solar IJK player — HTTP URL from notPipe RESOLVE_STREAM IPC. */
    private void startYoutubeStreamPlayback() {
        if (youtubeStreamUrl == null || youtubeStreamUrl.isEmpty()) {
            Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
            host.changeScreen(STATE_YOUTUBE_BROWSE);
            videoPlaybackYoutube = false;
            return;
        }
        host.pauseMusicPlayback();
        releaseVideoPlayer();
        FrameLayout surfaceHost = host.findViewById(R.id.video_surface_host);
        if (surfaceHost == null) {
            Toast.makeText(host.context(), R.string.video_play_error, Toast.LENGTH_SHORT).show();
            return;
        }
        videoSurface = new SurfaceRenderView(host.context());
        surfaceHost.addView(videoSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoController = new VideoPlayerController();
        videoController.attachHolder(videoSurface.getHolder());
        try {
            videoController.openUrl(youtubeStreamUrl);
            videoController.play();
            startVideoProgressUpdates();
            updateVideoProgressUi(0);
        } catch (Exception e) {
            Toast.makeText(host.context(), R.string.youtube_play_error, Toast.LENGTH_SHORT).show();
            videoPlaybackYoutube = false;
            host.changeScreen(STATE_YOUTUBE_BROWSE);
        }
    }

    private void releaseVideoPlayer() {
        stopVideoProgressUpdates();
        clearVideoScrubMode(false);
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

    /** Short-press prev/next file while playing video. */
    public void seekVideoFile(boolean next) {
        if (videoFiles.isEmpty()) return;
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

    public List<SettingsRow> buildFmBandSettingsRows() {
        List<SettingsRow> rows = new ArrayList<SettingsRow>();
        for (String region : FM_BAND_REGIONS) {
            rows.add(new SettingsRow("radio.fm_band." + region, labelResForRegion(region), false));
        }
        return rows;
    }

    public void applyFmBandRegion(String region) {
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
            applyFmBandRegion(RadioSettings.detectFmBandFromLocale(ctx));
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
                    row = (android.widget.LinearLayout) host.createTwoLineBrowseRow(title, subtitle);
                    row.setTag("solar_two_line_row");
                    tvTitle = (android.widget.TextView) row.getChildAt(0);
                    tvSub = (android.widget.TextView) row.getChildAt(1);
                }
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (statusRow) return;
                        host.clickFeedback();
                        handler.onClick(position);
                    }
                });
                if (statusRow) {
                    row.setFocusable(false);
                    row.setEnabled(false);
                } else {
                    row.setFocusable(true);
                    row.setEnabled(true);
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
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (statusRow) return;
                    host.clickFeedback();
                    handler.onClick(position);
                }
            });
            return btn;
        }
    }

    // --- Utility ---

    private FmBandPlan currentFmPlan() {
        return FmBandPlan.fromRegionCode(RadioSettings.getFmBandRegion(host.context()));
    }

    private int defaultFmKhz() {
        return currentFmPlan().clampKhz(Math.round(101.1f * 1000f));
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
