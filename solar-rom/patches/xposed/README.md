# Xposed Dalvik framework (Y1/Y2 Solar ROM)

Solar bakes **rovo89 Xposed 2.x (Dalvik)** into all three ROM builds and provides matching **adb + su** patch scripts for lab devices without reflash.

Target devices: **Y1** (Android 4.2, API 17) and **Y2** (Android 4.4, API 19). Both use the same vendored **`app_process_xposed_sdk16`** binary (XposedInstaller bin v58).

## Required files for a working framework

Dalvik-era Xposed does **not** ship a separate `/system/lib/libxposed_dalvik.so` — hooks live inside modified **`/system/bin/app_process`**. Zygote loads **`XposedBridge.jar`** from the installer data path at runtime.

| Path | Role |
|------|------|
| `/system/bin/app_process` | Modified zygote entry (arm, root:shell, 755) |
| `/system/bin/app_process.orig` | Stock backup for uninstall / recovery |
| `/system/framework/XposedBridge.jar` | Persistent jar copy (survives userdata wipe) |
| `/system/etc/solar/XposedBridge.jar` | Seed copy for init.d |
| `/system/xposed.prop` | Version label (audit + verify scripts) |
| `/system/app/XposedInstaller.apk` | Module manager (`de.robv.android.xposed.installer`) |
| `/system/etc/init.d/99XposedInit.sh` | Boot: apply staged `app_process` + seed runtime jar |
| `/system/bin/app_process.xposed.staged` | *(adb only, transient)* When live zygote blocks overwrite — applied on next boot |
| `/data/data/de.robv.android.xposed.installer/bin/XposedBridge.jar` | **Runtime** path zygote reads (seeded on boot + adb install) |

## Quick commands

### ADB only (rooted device, no ROM flash)

When two devices share serial `0123456789ABCDEF`, target by transport id from `adb devices -l` and **confirm model** before writes:

```bash
adb devices -l
adb -t "$ANDROID_ADB_TRANSPORT" shell getprop ro.product.model   # Y1 or Y2
export SOLAR_EXPECT_MODEL=y1   # optional — preflight aborts on mismatch
```

```bash
# Y1 (API 17) — transport id from adb devices -l (model:Y1)
ANDROID_ADB_TRANSPORT=3 ./solar-rom/scripts/ensure-xposed-framework-adb.sh --api 17

# Y2 (API 19)
ANDROID_ADB_TRANSPORT=2 ./solar-rom/scripts/ensure-xposed-framework-adb.sh --api 19

# One-shot Y1 parity overlay (su boot chain + Xposed + Solar APK)
ANDROID_ADB_TRANSPORT=3 ./solar-rom/scripts/apply-y1-rom-patches-adb.sh --apk app/build/outputs/apk/release/app-release.apk

# Verify without reinstalling
./solar-rom/scripts/install-xposed-adb.sh --verify-only --api 17

# Repair Installer UI module toggles (root-owned modules.list after su seeding)
./solar-rom/scripts/fix-xposed-installer-data-adb.sh

# Boot-loop recovery
./solar-rom/scripts/uninstall-xposed-adb.sh
```

### Full /system overlay (parity with ROM build)

```bash
./solar-rom/scripts/apply-y1-rom-patches-adb.sh --apk app/build/outputs/apk/release/app-release.apk
./solar-rom/scripts/apply-y2-rom-patches-adb.sh --apk app/build/outputs/apk/release/app-release.apk
```

Both call **`install-xposed-adb.sh`** internally for the Xposed layer.

### ROM build (local + CI)

Xposed is installed in **`build-rom.sh`** for types **a**, **b**, and **y2** via **`install-xposed-system.sh`**.

```bash
./scripts/build-all-roms.sh   # runs verify-xposed-rom-contents.sh on each zip
```

CI (`.github/workflows/build-release.yml`) checks `solar-rom/vendor/xposed/api17-arm`, `api19-arm`, and builds module APKs if missing.

## Y1 vs Y2 — what works where

Both devices use **Dalvik Xposed 2.x** (not ART). The same vendored `app_process_xposed_sdk16` binary is used on API 17 and 19, but **module APKs and overlay triggers differ by device**.

| Feature | Y1 (Android 4.2.2, API 17) | Y2 (Android 4.4, API 19) |
|---------|------------------------------|---------------------------|
| Xposed vendor tree | `vendor/xposed/api17-arm/` | `vendor/xposed/api19-arm/` |
| Context bridge APK | `SolarContextBridgeY1.apk` (`com.solar.launcher.xposed.bridge.y1`) | `SolarContextBridgeY2.apk` (`com.solar.launcher.xposed.bridge.y2`) |
| Global overlay trigger | **BACK long-press** in third-party apps (no hardware power button) | **Power long-press** + BACK long-press; stock GlobalActions suppressed on power-hold |
| Rockbox BACK | Must never open global overlay | Same |
| Theme font module | `SolarThemeFont.apk` (shared); sidecar at `/storage/sdcard0/.solar/system-font.ttf` | Same APK; sidecar at `/storage/sdcard1/.solar/system-font.ttf` |
| Root | Inherited rockbox-y1 permissive `su` + `install-recovery.sh` early `app_process` swap | Y1 `su` vendored via `install-y1-su-system.sh`; **`daemonsu`** required under SELinux |
| AVRCP native patches | Baked in ROM | **Skipped** (different BT stack) |
| Rockbox | Stock from rockbox-y1 base | Patched from Y1 base (`patch-rockbox-y2.sh`, `solar-rb-launch`) |

**Not baked (adb lab only):** `PowerMenuTest.apk` — superseded by context bridge for production power-menu behavior.

**Requires ROM flash (not `adb install -r`):** platform-signed Solar APK on `/system/app` for `INJECT_EVENTS` handoff; Xposed modules on `/system/app` with `99XposedInit.sh` auto-enable on first boot.

## Script parity matrix

| Step | ROM (`build-rom.sh`) | ADB |
|------|----------------------|-----|
| Backup `app_process.orig` | `install-xposed-system.sh` | `install-xposed-adb.sh` (su cp) |
| Modified `app_process` | `install-xposed-system.sh` | `install-xposed-adb.sh` (su cat — avoids ETXTBUSY) |
| `XposedBridge.jar` on /system | `install-xposed-system.sh` | `install-xposed-adb.sh` |
| `xposed.prop` | same | same |
| `XposedInstaller.apk` | same | `pm install -r /system/app/XposedInstaller.apk` after push (never `pm uninstall`) |
| `99XposedInit.sh` | same | same + run immediately |
| `SolarContextBridgeY1/Y2.apk` | device-specific bake | `pm install -r` + auto-enable |
| `SolarThemeFont.apk` | baked all ROMs | `pm install -r` + auto-enable |
| Runtime jar in `/data/data/...` | First boot via init.d | `xposed_seed_runtime_via_adb` + init.d |
| Verify | `audit_rom_contents` + `verify-xposed-rom-contents.sh` | `install-xposed-adb.sh --verify-only` + `audit-device-parity.sh` |
| Uninstall | Reflash stock ROM | `uninstall-xposed-adb.sh` |

Shared implementation: **`lib-xposed-install.sh`** (path list + mount/adb installers).

## Vendor regeneration

```bash
# After successful hardware install:
./solar-rom/scripts/extract-xposed-vendor.sh --api 17
./solar-rom/scripts/extract-xposed-vendor.sh --api 19

# Rebuild XposedInstaller APK from rovo89 sources:
./solar-rom/scripts/build-xposed-installer-apk.sh

# MTK boot-loop fallback (requires local AOSP + XposedTools):
./solar-rom/scripts/build-xposed-native.sh arm:17
./solar-rom/scripts/build-xposed-native.sh arm:19
```

Sources: [Xposed](https://github.com/rovo89/Xposed), [XposedBridge](https://github.com/rovo89/XposedBridge), [XposedInstaller](https://github.com/rovo89/XposedInstaller) @ `4cd1038` (bin v58).

## Y1/Y2 wheel navigation (Installer UI)

Stock Xposed Installer expects touch on the module checkbox (unfocusable, often clipped under the status bar). Solar patches in **`patches/xposed/installer-overrides/`** (applied by `build-xposed-installer-apk.sh`):

- **Modules tab:** scroll to module row, press **center** to toggle enabled (row shows **✓** / **✗**).
- List top padding clears the status bar clock.
- CLI fallback: `./solar-rom/scripts/enable-xposed-module-adb.sh com.solar.launcher.xposed.powermenu on` then reboot.

## Solar Theme Font module (system-wide theme font)

Package: **`com.solar.launcher.xposed.themefont`**

Solar publishes the active theme font (TTF/OTF only) to primary storage when the user applies a theme or on boot. The Xposed module hooks `Typeface.create` / `defaultFromStyle` / `Paint.setTypeface` in every app process.

### Sidecar contract

| Path | Role |
|------|------|
| `{primaryStorage}/.solar/system-font.ttf` | World-readable font copy (Y1: `/storage/sdcard0`, Y2: `/storage/sdcard1`) |
| Written by | `SystemFontBridge.publish()` in Solar app (`ThemeManager.cacheActiveTheme` / `preferInternalCacheForActiveTheme`) |
| Formats | `.ttf`, `.otf` only — WOFF and missing fonts delete the sidecar (stock fonts) |

### Build and ROM bake-in

`SolarThemeFont.apk` is installed to **`/system/app/SolarThemeFont.apk`** on all three ROM zips by `install-xposed-system.sh` / `lib-xposed-install.sh`. **`99XposedInit.sh`** auto-enables the module on first boot (no adb toggle required).

```bash
./solar-rom/scripts/build-theme-font-apk.sh
# Output: solar-rom/vendor/xposed/solar-theme-font/SolarThemeFont.apk (built on demand if missing)
```

For adb-only lab devices without reflash:

```bash
adb install -r solar-rom/vendor/xposed/solar-theme-font/SolarThemeFont.apk
./solar-rom/scripts/enable-xposed-module-adb.sh com.solar.launcher.xposed.themefont on
adb reboot
```

After reboot, apply or re-open Solar so the sidecar is written, then open stock Settings to confirm the theme font.

Works on **Y1 (API 17)** and **Y2 (API 19)** — single APK (`minSdk 17`). Fail-open: if no sidecar exists, stock fonts are used.

### Holo fail-open cosmetics (dialog / menu skin)

When the context bridge **fail-opens** to stock Holo UI (progress dialogs, custom views, multi-choice lists), **`SolarThemeFont`** also registers **post-inflate** hooks at zygote init for:

- `alert_dialog_holo`
- `select_dialog_item_holo` / `select_dialog_singlechoice_holo`
- `popup_menu_item_layout`

Colors are read from `{primaryStorage}/.solar/theme-colors.json` (published by `ThemeColorBridge.publish()` when the user applies a theme). Fonts continue to use `system-font.ttf`. This is cosmetic only — wheel-friendly replacement remains the job of **`DialogHooks`** / **`AppMenuHooks`**.

## Dialog / menu replacement (context bridge)

Third-party app processes (not `com.solar.launcher*`) load **`DialogHooks`** and **`AppMenuHooks`** from the device bridge APK. Replacement UI is painted by **`SolarOverlayService`** using the same **`ThemedContextMenu`** stack as in-app Solar.

| Stock surface | Hook | Solar overlay mode |
|---------------|------|----------------------|
| `AlertDialog.show()` | `DialogHooks` | Native dialog (message + buttons) |
| `Dialog.show()` (non-AlertDialog with `mAlert`) | `DialogHooks` | Same when AlertController present |
| Simple item / single-choice list (`mItems[]`) | `DialogHooks` | App-menu list overlay |
| `MenuDialogHelper.show` / `MenuPopupHelper.show` | `AppMenuHooks` | App-menu list overlay |
| `AppErrorDialog.show` (system_server) | `AppErrorHooks` | Native dialog |
| `AppNotRespondingDialog.show` (system_server) | `AppAnrHooks` | Native dialog (Wait first); Solar pkg auto-WAIT; system/fail-open → stock Holo + `AnrDialogKeyForwarder` |
| Rockbox `RockboxKeyboardInput` (`KbdInput` AlertDialog) | `RockboxKeyboardImeOkHooks` | Stock Holo dialog + Solar IME Enter → OK (Y1 + Y2); denylisted in `DialogHooks` |
| `BluetoothPairingDialog.show()` (`com.android.settings` / `com.android.bluetooth`) | `BluetoothPairingHooks` | Global BT pairing overlay (PIN keyboard / passkey match / consent) |

### AMS crash / ANR tier ladder (Y1 + Y2)

| Tier | When | Behavior |
|------|------|----------|
| **1 — Xposed + overlay** | Bridge enabled; Solar or companion installed | `AppAnrHooks` / `AppErrorHooks` skip stock `show()`, `SolarOverlayClient.showNativeDialog()` → `OverlayModalHost.showNativeDialogMode()` (scrollable detail + action rows). User pick → `ACTION_DIALOG_RESULT` → AMS `mHandler` (same as Holo tap). |
| **2 — Stock Holo + wheel remap** | System/android ANR, or overlay start misses while Xposed hooks | Stock Holo paints; `AnrDialogKeyForwarder` injects DPAD on wheel/side/center UP (no polling). |
| **3 — Fail-open** | Xposed off, Solar missing, or `canDeliverOverlay()` false | Unmodified stock AMS dialogs. |

Performance: event-driven hooks only (no AMS polling); `warmOverlayProcess()` before native-dialog IPC; launcher transition suppress via `LauncherTransitionGuard`.

Sources: `AppAnrHooks.java`, `AppErrorHooks.java`, `AnrDialogKeyForwarder.java`, `extract/SystemErrorDialogRouting.java`.

### Fail-open (stock Holo unchanged)

| Shape | Reason |
|-------|--------|
| Multi-choice (`mIsMultiChoice`) | Checkbox state not yet replayed |
| Adapter / cursor / spinner lists | Items not in `mItems[]` |
| Progress / `ProgressDialog` | Blocking semantics + indeterminate UI |
| Custom-view-only (no message) | Cannot extract wheel-friendly copy |
| Rockbox `KbdInput` keyboard dialog | `RockboxKeyboardImeOkHooks` owns Enter→OK; overlay would break Solar IME |
| Xposed Installer package | AlertDialog hooks break module manager UI |
| Solar missing / overlay start fails | `SolarOverlayClient.canDeliverOverlay()` false |

### Denylist

- **`de.robv.android.xposed.installer`** — no `AlertDialog` / `Dialog.show` hooks
- Global overlay policy excludes Rockbox BACK-long, Solar, Innioasis shells (separate from per-app dialog hooks)

### Result delivery

- Plain dialogs: `ACTION_DIALOG_RESULT` → `AlertController` `Message` handlers (same as Holo button tap)
- List + menu picks: `ACTION_APP_MENU_RESULT` → `OnClickListener.onClick` or `Menu.performItemAction`
- Sessions keyed by UUID; fail-open when overlay cannot start (pending map entry removed)

Sources: `solar-rom/vendor/xposed/solar-context-bridge/src/DialogHooks.java`, `AppMenuHooks.java`, `BluetoothPairingHooks.java`, `extract/AlertDialogExtract.java`.

### adb automated audit

```bash
./solar-rom/scripts/audit-dialog-replacement-adb.sh [serial]
```

Checks Xposed framework, bridge + theme-font modules enabled, Solar installed, font sidecar, and logcat hook lines after launching Settings. Prints the manual wheel matrix for overflow menus, list dialogs, and fail-open cases.

## Boot-loop / MTK notes

MediaTek ROMs may reject generic AOSP-derived `app_process`. If the device boot-loops after install:

1. `adb wait-for-device` then `./solar-rom/scripts/uninstall-xposed-adb.sh`
2. Or restore `app_process.orig` manually: `su -c 'cp /system/bin/app_process.orig /system/bin/app_process'`
3. Build device-specific binary with **`build-xposed-native.sh`** and re-vendor via **`extract-xposed-vendor.sh`**

**Y2 SELinux:** Y2 requires **`daemonsu`** in the boot chain (`install-recovery.sh`, `99SolarInit.sh`) for `su -c` from app processes. If logcat shows `avc: denied` for zygote loading Xposed paths, verify setuid `su` paths with `verify-y2-rom-contents.sh`.

## Solar Context Bridge (global overlay)

Production global context / power menu uses **`SolarContextBridgeY1.apk`** or **`SolarContextBridgeY2.apk`** (not the legacy `PowerMenuTest` probe). Hooks in `SystemServerHooks` route overlay triggers to `SolarOverlayService` in the Solar APK.

## Test-only modules (adb lab, not in ROM)

- **`PowerMenuTest.apk`** (`com.solar.launcher.xposed.powermenu`) — proves stock GlobalActions suppression; superseded by context bridge in production ROMs. Deploy via `deploy-powermenu-test-adb.sh` only.

## Module stub

Placeholder module sources: `solar-rom/vendor/xposed/solar-xposed-module-stub/` — superseded for font work by `solar-theme-font/`.
