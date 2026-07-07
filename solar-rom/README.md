# Solar ROM builder (Innioasis Y1 + Y2)

Builds flashable **Y1 type A**, **Y1 type B**, and **Y2 ATA** firmware images from stock bases, with **Solar** (`com.solar.launcher`) as the system launcher.

Release source: [github.com/thatwitchgirl/solar](https://github.com/thatwitchgirl/solar)

## Branches

| Branch | Releases | Versioning |
|--------|----------|------------|
| `nightly` | Every push | Tags `nightly-YYYYMMDD-HHMM` (UTC) — pre-release on GitHub |
| `main` | Every push | Tags `YYYYMMDD-HHMM` (same timestamp as nightly for the same commit; no prefix) — latest release on GitHub |

Both branches share the same `versionCode` (minutes since 2020-01-01 UTC). Day-to-day development targets **`nightly`**; **`main`** will become the stable channel later — for now both are timestamped unstable builds.

## Local build

```bash
./scripts/build.sh                    # signed app-release.apk
./solar-rom/scripts/build-rom.sh a --apk app/build/outputs/apk/release/app-release.apk rom.zip
./solar-rom/scripts/build-rom.sh b --apk app/build/outputs/apk/release/app-release.apk rom_type_b.zip
./solar-rom/scripts/build-rom.sh y2 --apk app/build/outputs/apk/release/app-release.apk rom_y2.zip
```

Requires `curl`, `unzip`, `zip`, `openssl`, `sudo`, and loop-mount support for ext4 (`e2fsprogs`).

Each ROM includes, on the **system** partition:

| Path | Purpose |
|------|---------|
| `/system/app/com.solar.launcher.apk` | Solar launcher (platform-signed) |
| `/system/media/bootanimation.zip` | Solar boot animation |
| `/system/bin/bootanimation` | Boot animation player binary |
| `/system/lib/libconscrypt_jni.so` | Conscrypt JNI for TLS 1.2+ (Reach, podcasts, themes via OkHttp) |
| `/system/etc/security/cacerts/*.0` | Modern CA roots (Let's Encrypt, etc.) for **MediaPlayer** HTTPS and all apps |
| `/system/etc/solar/disable-rockbox-for-solar.sh` | One-shot `pm disable org.rockbox` on first boot (marker wiped on flash) |
| `/system/etc/init.d/99SolarInit.sh` | Boot: SD folders; default Solar IME + accessibility fallback; switch scripts + keymap/codec sync |
| `/system/bin/app_process` | Xposed-modified zygote (Dalvik framework) |
| `/system/bin/app_process.orig` | Stock zygote backup |
| `/system/framework/XposedBridge.jar` | Xposed bridge (persistent on /system) |
| `/system/xposed.prop` | Xposed version label |
| `/system/app/XposedInstaller.apk` | Xposed module manager |
| `/system/etc/init.d/99XposedInit.sh` | Boot: seed runtime jar into installer data dir |

**Xposed** is installed on **all three ROM variants** (Y1 type A/B API 17, Y2 API 19) via `install-xposed-system.sh`. Lab devices without reflash: `solar-rom/scripts/install-xposed-adb.sh` or full overlay `apply-y1-rom-patches-adb.sh` / `apply-y2-rom-patches-adb.sh`. See [`solar-rom/patches/xposed/README.md`](patches/xposed/README.md).

**Y2 only** — Y1 permissive `su` is baked into `/system` during `build-rom.sh y2` (see `solar-rom/vendor/y1-su/` and `install-y1-su-system.sh`). Same setuid binary as the Y1 rockbox base — no Superuser.apk grant dialog. Y1 rockbox bases already ship this root; Y2 ATA stock base does not. **`org.rockbox.apk` + `librockbox.so`** are not vendored in git — `fetch-rockbox-y1-y2-assets.sh` downloads them from the rockbox-y1 type-A base at build time (cached under `~/.cache/solar-rom-build/rockbox-y1-y2/`). **AVRCP Bluetooth metadata patches** (`apply-avrcp-patches.sh`) run on **Y1 type A/B only** — the Y2 MT6582 base ships a different `MtkBt.odex` / `mtkbt` layout without `libextavrcp_jni.so`.

The ROM zip root also ships **`boot.img`** and **`logo.bin`** from `solar-rom/system/`.

These match what `./scripts/clean_install_system.sh` applies on a rooted device. Shared staging: `scripts/stage-y1-system-prep.sh` → `apply-y1-system-prep.sh` (ROM) or `push-y1-system-prep.sh` (adb). `SolarApplication` loads Conscrypt at boot; system cacerts are still required for stock HTTPS stacks (podcast streaming via MediaPlayer).

**First Solar system IME release:** ship all three ROM zips with a ROM-only OTA catalog row (`scripts/rom-only-entries.json` → `romOnly="true"` in `updates.xml`) so users on pre-IME builds must flash ROM; subsequent releases may ship APK + ROM normally. See [`.cursor/rules/layered-fallback.mdc`](../.cursor/rules/layered-fallback.mdc).

## Device install (rooted, without full ROM flash)

```bash
./scripts/build.sh
./scripts/clean_install_system.sh    # full: remove old launchers + APK + TLS prep
# or quick update:
./scripts/install.sh --system
./scripts/install_modern_cacerts.sh  # cacerts only
```

Override release repo for ROM downloads: `SOLAR_GITHUB_REPO=thatwitchgirl/solar` (default).

## CI

`.github/workflows/build-release.yml` runs on pushes to **`main`** and **`nightly`**: signs the release APK, builds Y1 ROM zips (`rom.zip`, `rom_type_b.zip`) and **Y2 ATA** (`rom_y2.zip`, desparsed ext4 + Y1 permissive root), then publishes a GitHub release on both branches.

Required secrets: `SOLAR_PLATFORM_KEY_PK8_B64`, `SOLAR_PLATFORM_KEY_PEM_B64` (base64-encoded AOSP test platform key material).

**Y2 root:** vendored Y1 permissive `su` under `solar-rom/vendor/y1-su/` (~74 KiB). Commit for CI reproducibility. Regenerate:

```bash
./solar-rom/scripts/extract-y1-su-vendor.sh
```

After flashing `rom_y2.zip`, verify root: `adb shell su -c id` (expect `uid=0`, no permission dialog).

## Y2 roadmap

| Phase | Status | Work |
|-------|--------|------|
| **1 — Y1 permissive system root** | Done | Y1 rockbox `su` baked into loop-mounted `system.img` via `install-y1-su-system.sh` (no Superuser.apk) |
| **2 — APK input parity** | Done | Y2 Java keeps DPAD compensation as safety net; external-app input handoff enabled on both devices |
| **3 — Y2 keylayout unification** | Done | `Y2-Rockbox.kl` (wheel/side match Y1; volume on scancodes 114/115) installed in `build-rom.sh y2`; `mtk-kpd.kl` patched for wheel + side keys |

**Intentional Y1/Y2 app divergence:** Y2 quick menu hides Volume and Lock/sleep chips (hardware volume + sleep/lock buttons). Everything else (Rockbox switch, power/reboot/shutdown, dual storage) is shared.

**Non-goals for Y2:** AVRCP native patches (different MT6582 `MtkBt.odex`).
