package com.solar.launcher.platform;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import com.solar.launcher.MainActivity;
import com.solar.launcher.PowerActions;
import com.solar.launcher.R;
import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-05 — Blocking prep wizard UI; runs SolarPlatformPrep ladder before MainActivity.
 * APK/ROM parity: user-facing face of APK self-heal; ROM flash already baked most steps at build time.
 * When changing: SolarPlatformPrep ladder order; wheel keys via Y1InputKeys; markDismissed vs prepVersion bump.
 * Reversal: remove activity; boot goes straight to MainActivity again.
 */
public class PlatformPrepWizardActivity extends Activity {

    /** Settings → manual platform repair sets this — only path that shows blocking wizard. */
    public static final String EXTRA_MANUAL_REPAIR = "solar_platform_prep_manual";
    /** Xposed/module repair finished — show reboot confirm only (2026-07-06). */
    public static final String EXTRA_REBOOT_ONLY = "solar_platform_prep_reboot_only";

    private static final int MAX_LOG_LINES = 40;
    /** Auto-enter Solar after partial/limited notice — user may skip with Back or Center. */
    private static final long AUTO_CONTINUE_MS = 3500L;

    private TextView titleView;
    private TextView subtitleView;
    private TextView percentView;
    private TextView logView;
    private ProgressBar progressBar;
    private Button actionButton;
    private ScrollView logScroll;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean prepRunning = true;
    private volatile boolean actionReady;
    private SolarPlatformPrep.PrepResult prepResult;
    private Runnable autoContinueRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean manual = getIntent().getBooleanExtra(EXTRA_MANUAL_REPAIR, false);
        final boolean rebootOnly = getIntent().getBooleanExtra(EXTRA_REBOOT_ONLY, false);
        if (rebootOnly) {
            setContentView(R.layout.activity_platform_prep);
            bindWizardViews();
            prepRunning = false;
            prepResult = new SolarPlatformPrep.PrepResult();
            prepResult.outcome = SolarPlatformPrep.PrepOutcome.REBOOT_PENDING;
            prepResult.rebootRequired = true;
            progressBar.setProgress(100);
            percentView.setText("100%");
            appendLog(getString(R.string.platform_prep_modules_ready));
            showOutcome();
            return;
        }
        // 2026-07-05 — Auto-launched wizard after prep already applied (legacy boot gate) → skip to Solar.
        try {
            PlatformPrepManifest manifest = PlatformPrepManifest.load(this);
            boolean needsPrep = PlatformPrepState.needsSilentPrep(this, manifest);
            // #region agent log
            JSONObject gate = new JSONObject();
            try {
                gate.put("manual", manual);
                gate.put("needsSilentPrep", needsPrep);
                gate.put("applied", PlatformPrepState.getAppliedVersion(this));
                gate.put("prepVersion", manifest.prepVersion);
                gate.put("outcome", PlatformPrepState.getOutcome(this).name());
            } catch (Exception ignored) {}
            PlatformPrepDebugLog.log(this, "PlatformPrepWizardActivity.onCreate", "gate", "H2", gate);
            // #endregion
            if (!manual && !needsPrep) {
                finishIntoSolar(false);
                return;
            }
        } catch (Exception e) {
            if (!manual) {
                finishIntoSolar(false);
                return;
            }
        }
        setContentView(R.layout.activity_platform_prep);
        bindWizardViews();
        startPrepThread();
    }

    /** Wire wizard layout views + theme — shared by full prep and reboot-only paths (2026-07-06). */
    private void bindWizardViews() {
        titleView = (TextView) findViewById(R.id.platform_prep_title);
        subtitleView = (TextView) findViewById(R.id.platform_prep_subtitle);
        percentView = (TextView) findViewById(R.id.platform_prep_percent);
        logView = (TextView) findViewById(R.id.platform_prep_log);
        progressBar = (ProgressBar) findViewById(R.id.platform_prep_progress);
        actionButton = (Button) findViewById(R.id.platform_prep_action);
        logScroll = (ScrollView) findViewById(R.id.platform_prep_log_scroll);

        ThemeManager.applyThemedTextStyle(titleView, ThemeManager.getTextColorPrimary());
        ThemeManager.applyThemedTextStyle(subtitleView, ThemeManager.getTextColorSecondary());
        ThemeManager.applyThemedTextStyle(logView, ThemeManager.getTextColorSecondary());
        ThemeManager.applyThemedTextStyle(actionButton, ThemeManager.getTextColorPrimary());

        actionButton.setFocusable(true);
        actionButton.setFocusableInTouchMode(true);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onActionPressed();
            }
        });
    }

    private void startPrepThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                prepResult = SolarPlatformPrep.run(
                        PlatformPrepWizardActivity.this,
                        new SolarPlatformPrep.ProgressListener() {
                            @Override
                            public void onProgress(final int percent, final String message) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateProgress(percent, message);
                                    }
                                });
                            }

                            @Override
                            public void onLogLine(final String line) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        appendLog(line);
                                    }
                                });
                            }
                        });
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        prepRunning = false;
                        showOutcome();
                    }
                });
            }
        }, "PlatformPrepWizard").start();
    }

    private void updateProgress(int percent, String message) {
        if (progressBar != null) progressBar.setProgress(percent);
        if (percentView != null) percentView.setText(percent + "%");
        if (subtitleView != null && message != null) subtitleView.setText(message);
    }

    private void appendLog(String line) {
        if (logView == null || line == null) return;
        String existing = logView.getText() != null ? logView.getText().toString() : "";
        String combined = existing.isEmpty() ? line : existing + "\n" + line;
        String[] lines = combined.split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                if (trimmed.length() > 0) trimmed.append('\n');
                trimmed.append(lines[i]);
            }
            combined = trimmed.toString();
        }
        logView.setText(combined);
        if (logScroll != null) {
            logScroll.post(new Runnable() {
                @Override
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private void showOutcome() {
        cancelAutoContinue();
        if (prepResult == null) {
            finishIntoSolar(false);
            return;
        }
        actionReady = true;
        actionButton.setVisibility(View.VISIBLE);
        actionButton.requestFocus();
        switch (prepResult.outcome) {
            case REBOOT_PENDING:
                titleView.setText(R.string.update_device_restarting);
                subtitleView.setText(R.string.update_reboot_eta);
                actionButton.setText(R.string.platform_prep_restart);
                break;
            case PARTIAL:
                titleView.setText(R.string.platform_prep_title);
                subtitleView.setText(getString(R.string.platform_prep_partial_message) + "\n\n"
                        + getString(R.string.platform_prep_continuing_soon));
                actionButton.setText(R.string.platform_prep_continue);
                scheduleAutoContinue();
                break;
            case LIMITED:
                titleView.setText(R.string.platform_prep_title);
                subtitleView.setText(getString(R.string.platform_prep_limited_message) + "\n\n"
                        + getString(R.string.platform_prep_continuing_soon));
                actionButton.setText(R.string.platform_prep_continue);
                scheduleAutoContinue();
                break;
            default:
                finishIntoSolar(false);
                return;
        }
    }

    private void scheduleAutoContinue() {
        cancelAutoContinue();
        autoContinueRunnable = new Runnable() {
            @Override
            public void run() {
                // #region agent log
                JSONObject d = new JSONObject();
                try {
                    d.put("actionReady", actionReady);
                    d.put("finishing", isFinishing());
                } catch (Exception ignored) {}
                PlatformPrepDebugLog.log(PlatformPrepWizardActivity.this,
                        "PlatformPrepWizardActivity.scheduleAutoContinue", "firing", "H3", d);
                // #endregion
                finishIntoSolar(false);
            }
        };
        // #region agent log
        PlatformPrepDebugLog.log(this, "PlatformPrepWizardActivity.scheduleAutoContinue",
                "scheduled", "H3", null);
        // #endregion
        mainHandler.postDelayed(autoContinueRunnable, AUTO_CONTINUE_MS);
    }

    private void cancelAutoContinue() {
        if (autoContinueRunnable != null) {
            mainHandler.removeCallbacks(autoContinueRunnable);
            autoContinueRunnable = null;
        }
    }

    private void onActionPressed() {
        if (!actionReady) return;
        cancelAutoContinue();
        if (prepResult != null && prepResult.outcome == SolarPlatformPrep.PrepOutcome.REBOOT_PENDING) {
            PowerActions.restart();
            return;
        }
        finishIntoSolar(false);
    }

    /** Enter MainActivity — dismissed=true when user skipped via Back. */
    private void finishIntoSolar(boolean dismissed) {
        cancelAutoContinue();
        // #region agent log
        JSONObject d = new JSONObject();
        try {
            d.put("dismissed", dismissed);
            d.put("outcome", prepResult != null ? prepResult.outcome.name() : "null");
        } catch (Exception ignored) {}
        PlatformPrepDebugLog.log(this, "PlatformPrepWizardActivity.finishIntoSolar", "exit wizard", "H3", d);
        // #endregion
        if (dismissed) {
            try {
                PlatformPrepManifest manifest = PlatformPrepManifest.load(this);
                PlatformPrepState.markDismissed(this, manifest.prepVersion);
            } catch (Exception ignored) {
                PlatformPrepState.markDismissed(this, PlatformPrepState.getAppliedVersion(this));
            }
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        dismissWizard();
    }

    private void dismissWizard() {
        finishIntoSolar(true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleWizardKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** Y2 wheel often delivers keys via onKeyUp — mirror MainActivity hardware routing. */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (handleWizardKey(event)) return true;
        return super.onKeyUp(keyCode, event);
    }

    /** Shared wheel/back/center handling for prep wizard exit. */
    private boolean handleWizardKey(KeyEvent event) {
        if (event == null) return false;
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (PlatformPrepWizardKeys.shouldDismissWizard(keyCode, action)) {
            dismissWizard();
            return true;
        }
        if (PlatformPrepWizardKeys.shouldActivateAction(keyCode, action) && actionReady) {
            onActionPressed();
            return true;
        }
        if (action == KeyEvent.ACTION_DOWN && PlatformPrepWizardKeys.shouldConsumeKeyDown(keyCode)) {
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        cancelAutoContinue();
        super.onDestroy();
    }
}
