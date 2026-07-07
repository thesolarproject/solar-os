package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * 2026-07-05 — Tier-1 IME: wheel keyboard overlay + InputConnection text commits only.
 * Mutex: pauses when global overlay active; arbiter publishes route=ime while tray is visible.
 * Fail-open: if Solar IME unavailable, stock LatinIME and normal key delivery continue.
 * When changing: SolarImeRouteArbiter arm/disarm; never commit keys while overlay owns wheel.
 * Reversal: unregister IME in manifest; stock LatinIME becomes default again.
 */
public class SolarInputMethodService extends InputMethodService implements SolarImeKeyGate.Handler,
        SolarWheelKeyboardController.Listener {

    private final SolarWheelKeyboardController wheelKeyboard = new SolarWheelKeyboardController();
    private SolarImeFullscreenOverlay overlay;
    private String sessionTitle = "";
    private long ppDownAt;
    private boolean ppLongHandled;

    @Override
    public void onCreate() {
        super.onCreate();
        wheelKeyboard.setListener(this);
        setExtractViewShown(false);
    }

    @Override
    public View onCreateInputView() {
        View v = new View(this);
        v.setVisibility(View.GONE);
        return v;
    }

    @Override
    public View onCreateCandidatesView() {
        return null;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    /** Never paint stock framework IME chrome — Solar uses WM fullscreen shell only. */
    @Override
    public boolean onEvaluateInputViewShown() {
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("restarting", restarting);
            d.put("canArm", SolarImeRouteArbiter.canArm());
            d.put("pkg", attribute != null ? attribute.packageName : "");
            if (attribute != null) {
                d.put("fieldId", attribute.fieldId);
                d.put("label", attribute.label != null ? attribute.label.toString() : "");
                d.put("hint", attribute.hintText != null ? attribute.hintText.toString() : "");
                d.put("inputType", DebugImeLog.inputTypeFields(attribute.inputType));
            }
            DebugImeLog.log(this, "SolarInputMethodService.onStartInput", "enter", "H2", d);
        } catch (Exception ignored) {}
        // #endregion
        if (!SolarImeRouteArbiter.canArm()) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("overlayActive", OverlayKeyGate.isOverlayKeysActive());
                DebugImeLog.log(this, "SolarInputMethodService.onStartInput", "blocked canArm", "H2", d);
            } catch (Exception ignored) {}
            // #endregion
            dismissImeSession();
            return;
        }
        if (!SolarImeDismiss.shouldShowSystemImeTray(attribute)) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("pkg", attribute != null ? attribute.packageName : "");
                d.put("inputType", attribute != null ? attribute.inputType : 0);
                DebugImeLog.log(this, "SolarInputMethodService.onStartInput", "rejected target", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
            dismissImeSession();
            return;
        }
        super.onStartInput(attribute, restarting);
        sessionTitle = attribute != null && attribute.label != null
                ? attribute.label.toString() : getString(R.string.solar_ime_label);
        InputConnection icProbe = getCurrentInputConnection();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hasIc", icProbe != null);
            d.put("inputType", attribute != null ? attribute.inputType : 0);
            d.put("imeUi", SolarImeRouteArbiter.isTrayUiVisible());
            d.put("imeActiveProp", SolarImeRouteArbiter.isActive());
            DebugImeLog.log(this, "SolarInputMethodService.onStartInput", "will show overlay", "H1", d);
        } catch (Exception ignored) {}
        // #endregion
        wheelKeyboard.reset();
        seedBufferFromInputConnection();
        if (overlay == null) {
            overlay = new SolarImeFullscreenOverlay(this, wheelKeyboard);
        }
        if (!SolarImeKeyGate.arm(this)) {
            dismissImeSession();
            return;
        }
        overlay.show(sessionTitle);
        suppressFrameworkImeWindow();
        GlobalOverlayTrigger.ensureStarted(getApplicationContext());
    }

    @Override
    public void onFinishInput() {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("overlayShowing", overlay != null && overlay.isShowing());
            DebugImeLog.log(this, "SolarInputMethodService.onFinishInput", "tearDown", "H2", d);
        } catch (Exception ignored) {}
        // #endregion
        tearDown();
        super.onFinishInput();
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        suppressFrameworkImeWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && OverlayTriggers.ACTION_IME_KEY.equals(intent.getAction())) {
            int keyCode = intent.getIntExtra(OverlayTriggers.EXTRA_KEY_CODE, 0);
            int action = intent.getIntExtra(OverlayTriggers.EXTRA_KEY_ACTION, KeyEvent.ACTION_DOWN);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("action", action);
                d.put("hasHandler", SolarImeKeyGate.isActive());
                DebugImeLog.log(this, "SolarInputMethodService.onStartCommand", "IME_KEY", "H3", d);
            } catch (Exception ignored) {}
            // #endregion
            if (keyCode != 0) {
                // 2026-07-05 — Always handle on live service instance; static handler can go stale after :overlay recycle.
                SolarImeKeyGate.rebindHandler(this);
                boolean handled;
                if (action == KeyEvent.ACTION_DOWN) {
                    handled = onKeyDown(keyCode);
                } else if (action == KeyEvent.ACTION_UP) {
                    handled = onKeyUp(keyCode);
                } else {
                    handled = false;
                }
                if (!handled && SolarImeRouteArbiter.isActive()) {
                    // 2026-07-06 — Key reached service but tray did not consume — escalate to root.
                    SolarImeRouteArbiter.signalXposedMiss();
                }
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("keyCode", keyCode);
                    d.put("action", action);
                    d.put("handled", handled);
                    d.put("runId", "post-fix");
                    DebugImeLog.log(this, "SolarInputMethodService.onStartCommand", "IME_KEY handled", "H3", d);
                } catch (Exception ignored) {}
                // #endregion
            }
            return START_NOT_STICKY;
        }
        if (intent != null && OverlayTriggers.ACTION_IME_COMMIT.equals(intent.getAction())) {
            String text = intent.getStringExtra(OverlayTriggers.EXTRA_IME_TEXT);
            boolean delete = intent.getBooleanExtra(OverlayTriggers.EXTRA_IME_DELETE, false);
            if (delete) {
                commitDelete(1);
            } else if (text != null) {
                commitTextTiered(text);
            }
            return START_NOT_STICKY;
        }
        if (intent != null && OverlayTriggers.ACTION_IME_DISMISS.equals(intent.getAction())) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("overlayShowing", overlay != null && overlay.isShowing());
                d.put("imeActive", SolarImeRouteArbiter.isActive());
                DebugImeLog.log(this, "SolarInputMethodService.onStartCommand", "IME_DISMISS", "H2", d);
            } catch (Exception ignored) {}
            // #endregion
            dismissImeSession();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    /** BACK / dismiss — tear down WM overlay first, then tell framework IME to hide. */
    private void dismissImeSession() {
        tearDown();
        try {
            requestHideSelf(0);
        } catch (Exception ignored) {}
    }

    @Override
    public void onStateChanged() {
        if (overlay != null) {
            overlay.refresh(sessionTitle);
        }
    }

    @Override
    public void onEnterRequested() {
        sendDefaultEditorAction(false);
        dismissImeSession();
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        if (Y1InputKeys.isBackKey(keyCode)) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                DebugImeLog.log(this, "SolarInputMethodService.onKeyDown", "BACK dismiss", "H2", d);
            } catch (Exception ignored) {}
            // #endregion
            dismissImeSession();
            return true;
        }
        if (Y1InputKeys.isWheelUp(keyCode)) {
            wheelKeyboard.wheelUp();
            return true;
        }
        if (Y1InputKeys.isWheelDown(keyCode)) {
            wheelKeyboard.wheelDown();
            return true;
        }
        if (Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isPlayPauseKey(keyCode)) {
            if (ppDownAt == 0) {
                ppDownAt = SystemClock.uptimeMillis();
                ppLongHandled = false;
            }
            return true;
        }
        if (Y1InputKeys.isTrackPreviousKey(keyCode)) {
            deleteOneChar();
            return true;
        }
        if (Y1InputKeys.isTrackNextKey(keyCode)) {
            insertText(" ");
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode) {
        if (Y1InputKeys.isBackKey(keyCode)) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                DebugImeLog.log(this, "SolarInputMethodService.onKeyUp", "BACK dismiss", "H2", d);
            } catch (Exception ignored) {}
            // #endregion
            dismissImeSession();
            return true;
        }
        if (Y1InputKeys.isPlayPauseKey(keyCode) && !Y1InputKeys.isCenterKey(keyCode)) {
            long held = ppDownAt > 0 ? SystemClock.uptimeMillis() - ppDownAt : 0;
            ppDownAt = 0;
            if (!ppLongHandled && held >= 400L) {
                wheelKeyboard.playPauseLongPress();
                ppLongHandled = true;
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("keyCode", keyCode);
                    d.put("heldMs", held);
                    d.put("branch", "ppLongCharset");
                    DebugImeLog.log(this, "SolarInputMethodService.onKeyUp", "pp path", "H3", d);
                } catch (Exception ignored) {}
                // #endregion
                return true;
            }
            if (!ppLongHandled) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("keyCode", keyCode);
                    d.put("heldMs", held);
                    d.put("branch", "ppShortEnter");
                    d.put("wheelIndex", wheelKeyboard.getIndex());
                    d.put("wheelChar", SolarWheelKeyboardController.charAtIndex(wheelKeyboard.getIndex()));
                    DebugImeLog.log(this, "SolarInputMethodService.onKeyUp", "pp path", "H3", d);
                } catch (Exception ignored) {}
                // #endregion
                onEnterRequested();
            }
            ppLongHandled = false;
            return true;
        }
        if (Y1InputKeys.isCenterKey(keyCode)) {
            long held = ppDownAt > 0 ? SystemClock.uptimeMillis() - ppDownAt : 0;
            ppDownAt = 0;
            if (!ppLongHandled && held >= 400L) {
                wheelKeyboard.playPauseLongPress();
                ppLongHandled = true;
                return true;
            }
            if (!ppLongHandled) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("keyCode", keyCode);
                    d.put("branch", "centerShortSelect");
                    DebugImeLog.log(this, "SolarInputMethodService.onKeyUp", "center path", "H3", d);
                } catch (Exception ignored) {}
                // #endregion
                applyCenterSelection();
            }
            ppLongHandled = false;
            return true;
        }
        return false;
    }

    /** Center short — mirror in-app keyboard; inject matching text into the target field only. */
    private void applyCenterSelection() {
        String selected = SolarWheelKeyboardController.charAtIndex(wheelKeyboard.getIndex());
        if (SolarWheelKeyboardController.TOKEN_DEL.equals(selected)) {
            deleteOneChar();
        } else if (SolarWheelKeyboardController.TOKEN_CONN.equals(selected)) {
            onEnterRequested();
        } else if (SolarWheelKeyboardController.TOKEN_SPC.equals(selected)) {
            insertText(" ");
        } else {
            wheelKeyboard.centerPress();
            commitTextTiered(selected);
        }
    }

    private void insertText(String text) {
        wheelKeyboard.mediaSpace();
        commitTextTiered(text);
    }

    private void deleteOneChar() {
        if (wheelKeyboard.getBuffer().length() > 0) {
            wheelKeyboard.mediaDelete();
        }
        commitDelete(1);
    }

    private void suppressFrameworkImeWindow() {
        try {
            hideWindow();
        } catch (Exception ignored) {}
        try {
            Window w = getWindow() != null ? getWindow().getWindow() : null;
            if (w != null) {
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                if (lp != null) {
                    lp.dimAmount = 0f;
                    lp.width = 0;
                    lp.height = 0;
                    w.setAttributes(lp);
                }
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    WindowManager.LayoutParams after = w.getAttributes();
                    d.put("fwW", after != null ? after.width : -1);
                    d.put("fwH", after != null ? after.height : -1);
                    d.put("fwDim", after != null ? after.dimAmount : -1f);
                    d.put("overlayShowing", overlay != null && overlay.isShowing());
                    DebugImeLog.log(this, "SolarInputMethodService.suppressFrameworkImeWindow", "shrunk fw window", "H4", d);
                } catch (Exception ignored) {}
                // #endregion
            }
        } catch (Exception ignored) {}
    }

    private void tearDown() {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hadOverlay", overlay != null && overlay.isShowing());
            DebugImeLog.log(this, "SolarInputMethodService.tearDown", "exit", "H2", d);
        } catch (Exception ignored) {}
        // #endregion
        SolarImeKeyGate.disarm();
        if (overlay != null) {
            overlay.dismiss();
        }
        try {
            hideWindow();
        } catch (Exception ignored) {}
    }

    /** Seed preview line from focused editor text when available. */
    private void seedBufferFromInputConnection() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(512, 0);
            if (before != null && before.length() > 0) {
                wheelKeyboard.setBuffer(before.toString());
            }
        } catch (Exception ignored) {}
    }

    static boolean commitTextTiered(Context ctx, InputConnection ic, CharSequence text) {
        if (text == null) return true;
        if (ic != null) {
            try {
                if (ic.commitText(text, 1)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        SolarImeRouteArbiter.escalateToXposedSession();
        if (broadcastXposedCommit(ctx, text.toString(), false)) {
            return true;
        }
        SolarImeRouteArbiter.escalateToAccessibility();
        return SolarImeAccessibilityService.commitViaAccessibility(ctx, text.toString());
    }

    private boolean commitTextTiered(CharSequence text) {
        return commitTextTiered(this, getCurrentInputConnection(), text);
    }

    private void commitDelete(int count) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            try {
                if (ic.deleteSurroundingText(count, 0)) return;
            } catch (Exception ignored) {}
        }
        SolarImeRouteArbiter.escalateToXposedSession();
        if (broadcastXposedCommit(this, "", true)) return;
        SolarImeRouteArbiter.escalateToAccessibility();
        SolarImeAccessibilityService.commitViaAccessibility(this, "");
    }

    private static boolean broadcastXposedCommit(Context ctx, String text, boolean delete) {
        try {
            Intent i = new Intent(OverlayTriggers.ACTION_IME_COMMIT);
            i.putExtra(OverlayTriggers.EXTRA_IME_TEXT, text);
            i.putExtra(OverlayTriggers.EXTRA_IME_DELETE, delete);
            i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            ctx.sendBroadcast(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
