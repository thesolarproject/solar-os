package com.solar.launcher.platform;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/**
 * 2026-07-05 — Silent background platform prep; blocking wizard is manual-only.
 * Reversal: restore launchIfRequired blocking wizard on every boot.
 */
public final class PlatformPrepLauncher {

    private static volatile boolean silentPrepScheduled;

    private PlatformPrepLauncher() {}

    /** Schedule silent prep when prepVersion ahead — no blocking UI on ROM-ready devices. */
    public static void ensureAsync(Context ctx) {
        if (ctx == null) return;
        try {
            PlatformPrepManifest manifest = PlatformPrepManifest.load(ctx);
            if (!PlatformPrepState.needsSilentPrep(ctx, manifest)) {
                silentPrepScheduled = false;
                // #region agent log
                JSONObject d = new JSONObject();
                try {
                    d.put("prepVersion", manifest.prepVersion);
                    d.put("applied", PlatformPrepState.getAppliedVersion(ctx));
                    d.put("skipped", true);
                } catch (Exception ignored) {}
                PlatformPrepDebugLog.log(ctx, "PlatformPrepLauncher.ensureAsync",
                        "ROM at prepVersion — skip", "H1", d);
                // #endregion
                return;
            }
        } catch (Exception e) {
            return;
        }
        if (silentPrepScheduled) return;
        silentPrepScheduled = true;
        SolarPlatformPrep.ensureAsync(ctx.getApplicationContext());
    }

    /** Manual repair from Settings — opens blocking wizard. */
    public static void launchManualWizard(Context ctx) {
        if (ctx == null) return;
        Intent i = new Intent(ctx, PlatformPrepWizardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(PlatformPrepWizardActivity.EXTRA_MANUAL_REPAIR, true);
        ctx.startActivity(i);
    }

    /**
     * 2026-07-06 — User-visible reboot after module install/enable — never silent reboot.
     * Layman: tells the user Solar updated hook modules and needs one restart.
     */
    public static void launchRebootWizard(Context ctx) {
        if (ctx == null) return;
        Intent i = new Intent(ctx, PlatformPrepWizardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra(PlatformPrepWizardActivity.EXTRA_REBOOT_ONLY, true);
        ctx.startActivity(i);
    }

    /** @deprecated Use {@link #ensureAsync} — blocking launch caused prep loops. */
    public static boolean launchIfRequired(Context ctx) {
        ensureAsync(ctx);
        return false;
    }
}
