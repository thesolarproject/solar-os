# Y1 hardware keymaps (Solar 1.0 input)

Solar listens to **Android keycodes** (`KeyEvent`), not kernel scancodes. The system file `/system/usr/keylayout/mtk-kpd.kl` maps physical controls to keycodes; **`Y1KeyMap`** maps keycodes to UI roles (wheel, skip, play/pause).

## Physical controls → keycodes

| Control | Scancode | Stock mtk-kpd (`mtk-kpd.kl`) | Rockbox mtk-kpd (`mtk-kpd-rockbox.kl`) |
|---------|----------|------------------------------|----------------------------------------|
| Wheel CCW | 103 | DPAD_LEFT (21) | DPAD_UP (19) |
| Wheel CW | 108 | DPAD_RIGHT (22) | DPAD_DOWN (20) |
| Prev | 105 | MEDIA_PREVIOUS (88) | DPAD_LEFT (21) |
| Next | 106 | MEDIA_NEXT (87) | DPAD_RIGHT (22) |
| Center | 232 | DPAD_CENTER | same |
| Play/pause | 164 | MEDIA_PLAY_PAUSE | same |
| Top / menu | 229 etc. | MENU | same |

## Solar app semantics (both modes feel identical)

| Role | Stock keycodes | Rockbox keycodes |
|------|----------------|------------------|
| Wheel up / down | 21 / 22 | 19 / 20 |
| Media prev / next | 88 / 87 (+ 165/163) | 21 / 22 (treated as skip in player) |
| BT remotes | MEDIA_* via Koensayr | same |

## Auto-detect (`Y1KeyMap.detectMtkLayout`)

Reads live `/system/usr/keylayout/mtk-kpd.kl` lines for keys **103** and **105**:

| Condition | Layout | Rockbox mode |
|-----------|--------|--------------|
| `105` → `MEDIA_PREVIOUS` | Stock Y1 | off |
| `103` → `MEDIA_PREVIOUS` | Sideload swap | on (wheel 88/87, skip 21/22) |
| `103` LEFT + `105` UP | Rockbox ROM variant | on |
| `103` UP alone (canonical base) | Rockbox classic | on |
| Ambiguous + enabled `org.rockbox` | fallback | on |

Settings → Debug → **Rockbox button mapping** can override auto-detect; preview shows layout label (e.g. `On · Rockbox classic · auto`).

## ROM vs sideload

| Install path | mtk-kpd | org.rockbox |
|--------------|---------|-------------|
| **Solar ROM** (`build-rom.sh`) | `mtk-kpd-rockbox.kl` via `apply-rockbox-keylayout.sh` | kept from Rockbox-Y1 base |
| **adb sideload** (`clean_install_system.sh`) | stock `mtk-kpd.kl` via `apply-stock-keylayout.sh` pattern | not removed if present |

Push canonical Rockbox layout to a test device without reflashing:

```bash
./scripts/push-rockbox-keylayout-adb.sh
```

## AVRCP / Bluetooth remotes

Car stereos send **MEDIA_*** keycodes through normal Activity dispatch (`dispatchKeyEvent` → `onKeyDown`). Koensayr (`Y1Bridge.apk` + patched BT stack) exposes metadata via the legacy `y1-track-info` path.

## Verify on device

```bash
./scripts/verify-y1-input.sh
./scripts/verify-y1-input.sh --getevent   # optional 5s capture
adb shell grep -E '^key (103|105|106|108)' /system/usr/keylayout/mtk-kpd.kl
```

## Related scripts

| Script | Purpose |
|--------|---------|
| `solar-rom/scripts/apply-rockbox-keylayout.sh` | Rockbox mtk-kpd on ROM mount |
| `solar-rom/scripts/apply-stock-keylayout.sh` | Stock mtk-kpd (adb sideload) |
| `scripts/push-rockbox-keylayout-adb.sh` | Push Rockbox mtk-kpd via `su` |
| `scripts/push-koensayr-adb.sh` | Install Koensayr stack via `su` |
| `scripts/stage-koensayr-prep.sh` | Build patched blobs for push / APK assets |
| `scripts/verify-y1-input.sh` | Dump layout + org.rockbox + expected table |
