# Rockbox on Solar ROM

## Y1 (type A/B)

ROM build keeps `org.rockbox.apk` + `librockbox.so` from the rockbox-y1 base image unchanged.
(codec `.so` files live inside the APK under `lib/armeabi/`). Audit requires ≥35 native libs.

**Solar IME + Rockbox search/text:** `RockboxKeyboardInput` (native keyboard prompts) uses a stock Holo `AlertDialog` + `EditText`. Solar system IME types into the field; **`RockboxKeyboardImeOkHooks`** in the context bridge (Y1 + Y2) wires Enter → OK so the dialog accepts without a separate wheel tap. No APK smali patch — hook lives in `SolarContextBridgeY1/Y2.apk`.

## Y2 (MT6582)

Y2 ATA / jj_auto stock base may ship Rockbox or JJ; **Solar pack strips both** by default
(`build-rom.sh` force-rm unless `SOLAR_ROM_LEGACY_ROCKBOX=1`).

**2026-07-19 — Solar-only:** APK platform prep does **not** install `org.rockbox` or JJ.
Launch-if-present only (switch scripts when PM already sees the package). Was: `RockboxPlatformInstall`
from `assets/platform/rockbox/`. Reversal: restore sync_rockbox_platform_assets + install class body.

| Layer | What | Why |
|-------|------|-----|
| ROM pack | Strip `org.rockbox` + `com.themoon.y1` | Solar-focused Y2 image; no third-party HOME heal |
| Xposed | `RockboxCompatHooks` in `SolarRockboxCompat.apk` | Hooks only if Rockbox already installed |
| Xposed | `RockboxKeyboardImeOkHooks` in `SolarRockboxIme.apk` | Solar IME Enter → stock OK on Rockbox dialogs |
| Legacy bake | `SOLAR_ROM_LEGACY_ROCKBOX=1` | Optional ROM-time Rockbox for lab |

The shipped `org.rockbox.apk` on Y1 ROM keeps pristine `classes.dex` and in-APK `librockbox.so` (apktool `-s`).
Behavioral compat updates ship in the Xposed bridge APK — rebuild with `build-context-bridge-apk.sh`.

### Runtime asset bootstrap (when Rockbox already present)

Rockbox native code loads plugins from `/data/data/org.rockbox/app_rockbox/.rockbox/`,
but the tree is bundled inside `lib/armeabi/libmisc.so` (zip). When Rockbox is on `/system`
(Y1 bake or legacy), sync scripts under `/system/etc/solar/` (and `assets/y1/` fallback) refresh libs:

- **`sync-rockbox-libs.sh`** — copy staged libs into `/data/data/org.rockbox/lib/`

- **`sync-rockbox-assets.sh`** — copy staged `.rockbox` → sdcard0 + `app_rockbox`; apply Y2 config
- **`rockbox-y2-config.cfg`** — dual-storage config overlay

Build-time extraction: [`extract-rockbox-staged-assets.sh`](../scripts/extract-rockbox-staged-assets.sh)
(runs from `sync-platform-assets.sh` for APK bundle; legacy ROM path:
`install_rockbox_from_y1_base` during `build-rom.sh y2` when `SOLAR_ROM_LEGACY_ROCKBOX=1`).

### Y2 dual-storage policy

- `.rockbox` config tree: `/storage/sdcard0/.rockbox` (internal — matches Solar `getRockboxRoot()`)
- Default browse: `/storage/sdcard1/` (MicroSD)
- Database scan folders: user adds both volumes via **Select directories to scan** once plugins load

### Y2 power-hold over Rockbox

Hold **power** (not BACK) in Rockbox to open Solar's global quick menu. Implemented in
`SystemServerHooks` (Y2 bridge) + miss-gated root evdev fallback (`GlobalOverlayTriggerMain` scancode 116).
BACK-long stays in Rockbox on both device families.

### Dev testing (adb install)

Until reflashed with a patched ROM:

```bash
bash solar-rom/scripts/patch-rockbox-y2.sh reference.apk /tmp/org.rockbox-y2.apk
bash solar-rom/scripts/extract-rockbox-staged-assets.sh /tmp/org.rockbox-y2.apk /tmp/rockbox-staged-libs /tmp/rockbox-staged-dot-rockbox
bash solar-rom/scripts/build-context-bridge-apk.sh
adb install -r /tmp/org.rockbox-y2.apk
adb install -r solar-rom/vendor/xposed/solar-context-bridge/SolarContextBridgeY2.apk
adb push /tmp/rockbox-staged-libs/. /data/local/tmp/rockbox-libs/
adb push /tmp/rockbox-staged-dot-rockbox/. /data/local/tmp/rockbox-dot-rockbox/
adb push solar-rom/scripts/sync-rockbox-assets.sh /data/local/tmp/
adb push solar-rom/system/rockbox-y2-config.cfg /data/local/tmp/
adb shell su -c 'mkdir -p /system/etc/solar/rockbox-libs /system/etc/solar/rockbox-dot-rockbox && \
  cp -a /data/local/tmp/rockbox-libs/. /system/etc/solar/rockbox-libs/ && \
  cp -a /data/local/tmp/rockbox-dot-rockbox/. /system/etc/solar/rockbox-dot-rockbox/ && \
  cp /data/local/tmp/sync-rockbox-assets.sh /data/local/tmp/rockbox-y2-config.cfg /system/etc/solar/ && \
  chmod 755 /system/etc/solar/sync-rockbox-assets.sh && \
  sh /system/etc/solar/sync-rockbox-assets.sh'
```

## Keymap

See keylayout section in this directory's parent README. Y2 uses `Y2-Rockbox.kl`.

**Codec lib sync:** `sync-rockbox-libs.sh` copies staged `/system/etc/solar/rockbox-libs/` into
`/data/data/org.rockbox/lib/` when stale. Solar runs this on boot and before every
Switch to Rockbox (`RockboxLibSync.java`).
