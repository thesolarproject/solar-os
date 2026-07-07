# Rockbox on Solar ROM

## Y1 (type A/B)

ROM build keeps `org.rockbox.apk` + `librockbox.so` from the rockbox-y1 base image unchanged.
(codec `.so` files live inside the APK under `lib/armeabi/`). Audit requires ≥35 native libs.

**Solar IME + Rockbox search/text:** `RockboxKeyboardInput` (native keyboard prompts) uses a stock Holo `AlertDialog` + `EditText`. Solar system IME types into the field; **`RockboxKeyboardImeOkHooks`** in the context bridge (Y1 + Y2) wires Enter → OK so the dialog accepts without a separate wheel tap. No APK smali patch — hook lives in `SolarContextBridgeY1/Y2.apk`.

## Y2 (MT6582)

Y2 ATA stock base has no Rockbox. **Default (prep-delivered):** `sync-platform-assets.sh` bundles
manifest-patched APK + staged libs into the Solar APK; `RockboxPlatformInstall` stages on first
platform prep (no ROM-time patch chain in `build-rom.sh`). **Legacy ROM bake:** set
`SOLAR_ROM_LEGACY_ROCKBOX=1` to restore `install_rockbox_from_y1_base()` in `build-rom.sh`.

| Layer | What | Why |
|-------|------|-----|
| APK | Strip `android:sharedUserId` + platform sign | Y2 MTK platform cert rejects rockbox-y1 shared UID (install-time; Xposed cannot fix) |
| Platform prep | `RockboxPlatformInstall` from `assets/platform/rockbox/` | OTA-self-healable install to `/system/app/org.rockbox.apk` + staged trees |
| Xposed | `RockboxCompatHooks` in `SolarRockboxCompat.apk` | `Connectivity.execShell` → `/system/xbin/su -c` array exec; warm `daemonsu` on `setContext` |
| Xposed | `RockboxKeyboardImeOkHooks` in `SolarRockboxIme.apk` | Solar IME Enter → stock OK on `RockboxKeyboardInput` search/rename dialogs |
| Staged lib | `patch_librockbox_system.py` on bundled staged `librockbox.so` | Native `system("am start …")` → `solar-rb-launch` (Dalvik Xposed cannot hook native code) |
| ROM / prep | `solar-rb-launch`, `rockbox-y2-config.cfg`, `Y2-Rockbox.kl`, sync scripts | Root wrapper, dual-storage, GPIO map, no-unzip bootstrap |

The shipped `org.rockbox.apk` keeps pristine `classes.dex` and in-APK `librockbox.so` (apktool `-s`).
Behavioral compat updates ship in the Xposed bridge APK — rebuild with `build-context-bridge-apk.sh`.

### Runtime asset bootstrap

Rockbox native code loads plugins from `/data/data/org.rockbox/app_rockbox/.rockbox/`,
but the tree is bundled inside `lib/armeabi/libmisc.so` (zip). Y2 has no `unzip` on device,
so the ROM build **pre-extracts** assets at compile time into:

- `/system/etc/solar/rockbox-libs/` — JNI `.so` files (42 codecs + libmisc); **staged `librockbox.so` is native-patched**
- `/system/etc/solar/rockbox-dot-rockbox/` — full `.rockbox` tree including `db_folder_select.rock`

Scripts (installed to `/system/etc/solar/`):

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
