# A5 keycode map (KeyCodeDisp + p2_ata stock 2026-07-14)

Canonical Solar remap: `app/src/main/java/com/solar/launcher/A5InputKeys.java`.  
Stock tree: `/home/deck/Downloads/p2_ata_20230718/editor/system/usr/keylayout/`  
Solar assets: `app/src/main/assets/y1/A5-mtk.kl`, `A5.kl`  
Push: `scripts/push-a5-keymap.sh`

## Soft roles (after kl + app remap)

| Physical | Scancode (mtk-kpd) | Stock KeyEvent | Solar |
|----------|--------------------|----------------|-------|
| Front left | 103 | `DPAD_UP` (19) | Menus: list up; **NP: prev track / hold rewind** |
| Front right | 108 | `DPAD_DOWN` (20) | Menus: list down; **NP: next track / hold FF** |
| Front middle | 158 | `BACK` (4) | OK (`DPAD_CENTER`); hold → context / queue-move |
| Side Vol Down | 114 | `VOLUME_DOWN` (25) | Solar volume HUD (not skip); **keyboard open → delete** |
| Side Vol Up | 115 | `VOLUME_UP` (24) | Solar volume HUD (not skip); **keyboard open → space** |
| Side Power | 116 | stock `POWER`; **A5-mtk → `MEDIA_STOP` (86)** | Back; hold → context; **keyboard open → short Enter / hold charset** |

## Keyboard / Soft keyboard (`A5KeyboardKeys`)

While the in-app tray or Solar IME is open: Vol = space/delete; side Back short = Enter; hold Back = charset; face mid = type (CENTER). Edge flick cancels (hardware Back does not dismiss). A5 IME uses a real `InputMethod` input view (no Xposed). See `a5-hybrid-touch-nav.md`.

## What was wrong on device

Lab units had `Generic.kl` / `A5.kl` remapping **114/115 → DPAD**, so side volume and face L/R all felt like scroll (KeyCodeDisp 19/20). Stock `mtk-kpd.kl` already separates VOLUME vs DPAD — restore it via `push-a5-keymap.sh`.

Power stays `MEDIA_STOP` in Solar A5-mtk (not stock `POWER`) so Solar can treat it as Back without phone sleep / GlobalActions.

## Touch (A5EdgeGestures)

| Gesture | Action |
|---------|--------|
| Horizontal from left/right edge | Back / dismiss context |
| Swipe up from bottom edge | Home (`STATE_MENU`) |
| Hold finger still ~420ms (not on Now Playing art scrub) | Open in-app context modal |

```bash
adb shell setprop persist.solar.device_family a5
./scripts/push-a5-keymap.sh   # reboots
# Inspect after boot:
adb shell "su -c 'grep -E \"^key (103|108|114|115|116|158)\" /system/usr/keylayout/mtk-kpd.kl'"
```
