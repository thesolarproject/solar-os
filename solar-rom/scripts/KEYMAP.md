# Y1 hardware keymaps (Solar 1.0 input)

Solar listens to **Android keycodes** (`KeyEvent`), with **kernel scancodes** (103/108 wheel, 105/106 skip) as a safety net when keylayout files disagree.

## Physical controls → keycodes

| Control | Scancode | Stock (`mtk-kpd.kl` / `Stock.kl`) | Rockbox ROM (`mtk-kpd-rockbox.kl` + `Generic-rockbox.kl`) |
|---------|----------|-----------------------------------|-----------------------------------------------------------|
| Wheel CCW | 103 | DPAD_LEFT (21) | DPAD_UP (19) |
| Wheel CW | 108 | DPAD_RIGHT (22) | DPAD_DOWN (20) |
| Prev | 105 | MEDIA_PREVIOUS (88) | DPAD_LEFT (21) |
| Next | 106 | MEDIA_NEXT (87) | DPAD_RIGHT (22) |
| Center | 232 | DPAD_CENTER | same |
| Play/pause | 164 | MEDIA_PLAY_PAUSE | same |
| Top | — | BACK (4) | BACK (4) |

## Solar app semantics (both modes feel identical in UI)

| Role | Stock keycodes | Rockbox keycodes |
|------|----------------|------------------|
| Wheel up / down | 21 / 22 | 19 / 20 |
| Media prev / next | 88 / 87 (+ 165/163) | 21 / 22 |
| BT remotes | MEDIA_* via Koensayr | same |

## Why three keylayout files matter

Y1 InputReader loads **both** `mtk-kpd.kl` and `Generic.kl` (identical to `Stock.kl` on Solar ROM). Patching only `mtk-kpd.kl` leaves the wheel at stock 21/22 — menus appear dead because Solar expects 19/20.

**Solar ROM install** ([`apply-rockbox-keylayout.sh`](apply-rockbox-keylayout.sh)) copies:

| Repo file | Device path |
|-----------|-------------|
| [`Generic-rockbox.kl`](Generic-rockbox.kl) | `/system/usr/keylayout/Generic.kl` and `Stock.kl` |
| [`mtk-kpd-rockbox.kl`](mtk-kpd-rockbox.kl) | `/system/usr/keylayout/mtk-kpd.kl` |

All patching happens at **ROM build time** in [`build-rom.sh`](build-rom.sh) — no boot-time or app-side `.kl` writes. Koensayr (`AVRCP.kl`, BT stack) is separate and also ROM-build-only.

## Auto-detect (`Y1KeyMap`)

Reads **`mtk-kpd.kl` and `Generic.kl`** lines 103/105, plus runtime hints from first hardware keys (keyboard/dpad source only — not BT):

| Condition | Layout | Rockbox mode |
|-----------|--------|--------------|
| `105` → `MEDIA_PREVIOUS` on either file | Stock Y1 | off |
| `103` → `MEDIA_PREVIOUS` | Sideload swap | on |
| `103` UP + `105` LEFT (mtk-kpd) | Rockbox ROM variant | on |
| `103` UP + `105` LEFT | Rockbox classic | on |
| Wheel scancode 103 → keycode UP | Runtime Rockbox | on |
| Wheel scancode 103 → keycode LEFT | Runtime Stock | off |

Settings → Debug → **Rockbox button mapping** overrides auto-detect when set manually.

## ROM vs sideload

| Install path | Keylayout |
|--------------|-----------|
| **Solar ROM** | `apply-rockbox-keylayout.sh` → `Generic-rockbox.kl` + `mtk-kpd-rockbox.kl` |
| **adb sideload** | `apply-stock-keylayout.sh` pattern in `clean_install_system.sh` |

Push Rockbox layout without reflashing: `./scripts/push-rockbox-keylayout-adb.sh`

## Verify on device

```bash
./scripts/verify-y1-input.sh
```

Expected **KeyCodeDisp** on Solar Rockbox ROM:

| Control | Keycode |
|---------|---------|
| Wheel CCW | DPAD_UP (19) |
| Wheel CW | DPAD_DOWN (20) |
| Prev button | DPAD_LEFT (21) |
| Next button | DPAD_RIGHT (22) |
| Bottom | MEDIA_PLAY_PAUSE (85) |
| Top | BACK (4) |

## Related scripts

| Script | Purpose |
|--------|---------|
| `apply-rockbox-keylayout.sh` | Install Generic-rockbox + mtk-kpd-rockbox on ROM mount |
| `apply-stock-keylayout.sh` | Stock keylayout (adb sideload) |
| `scripts/push-rockbox-keylayout-adb.sh` | Push both `.kl` files via `su` |
| `scripts/verify-y1-input.sh` | Dump layout, Generic/mtk-kpd consistency, KeyCodeDisp table |
