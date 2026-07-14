# Third-party apps (non-launcher)

When a **non-launcher** app is foreground — Android Settings, FM radio APK, a game, or any `Activity` that is not Solar/Rockbox/JJ — Solar still provides **global overlay**, **keyboard (IME)**, and **wheel handoff** through layered tiers.

Solar’s own UI (`com.solar.launcher`) is excluded from bridge BACK handling; see [Input and overlays](input-and-overlays.md).

---

## HOLD BACK → Solar (third-party apps)

**2026-07-14:** Solar no longer paints a system-wide quick menu over third-party apps.

**Trigger:**

- Y1 / Y2: BACK hold ~420 ms → **launch Solar Home** (`MainActivity`)
- Y2: POWER hold → **stock Android GlobalActions** (Solar does not suppress outside Solar)
- Ultra-long BACK/POWER (~10 s) → rescue → Solar (unchanged)
- Volume keys → Solar volume OSD (unchanged)
- OK hold ~420 ms on focused list row → app context menu where `AppMenuHooks` still applies

**Delivery chain (BACK hold):**

```
SystemServerHooks / root miss-gate → SolarOverlayClient.launchSolarHome
```

**While Solar itself is foreground:** POWER/BACK hold opens in-app `ThemedContextMenu` only.

USB / BT / ANR / volume shells may still use overlay services when those surfaces need them — not the old BACK/POWER quick menu.

---

## Solar IME (typing in stock apps)

When a stock app shows a text field (Wi‑Fi password in overlay, Settings search, etc.):

| Tier | Mechanism |
|------|-----------|
| 1 | `SolarInputMethodService` — `InputConnection` commit |
| 2 | `ImeKeyForwarder` (Xposed PWM) — wheel to tray |
| 3 | `ImeSessionHooks` / `SolarImeAccessibilityService` — paste fallback |
| 4 | Root evdev forward on `sys.solar.ime.xposed_miss` |

**Mutex:** IME cannot arm while `sys.solar.overlay.opening/active=1`.

Overlay Wi‑Fi credential entry keeps the user in the third-party/launcher context — password tier opens IME over the modal, then returns focus to the modal tier.

---

## External input handoff (wheel in stock apps)

When Solar loses window focus to a **handoff-eligible** app:

```java
ExternalInputHandoff.setDpadMode(MODE_ANDROID | MODE_FM | MODE_JJ);
```

Wheel media keys (126/127) and side keys map to **DPAD** inject so lists scroll in Holo Settings and similar UIs.

**Blocked when:**

- Global overlay active/opening
- Solar IME active
- Post-overlay cooldown (~450 ms)

**Y1 Bluetooth:** `Y1BluetoothInput.isBluetoothTransportKey()` — only remap when event is from AVRCP device, never mtk-kpd wheel.

---

## Dialog, ANR, and toast hooks

`AppAnrHooks` / `AppErrorHooks` / `DialogHooks` / `ToastHooks` in context bridge:

| Surface | Hook | Fail-open |
|---------|------|-----------|
| ANR / crash (incl. system_server) | Companion native dialog tier | Stock Holo + `AnrDialogKeyForwarder` after ~2 s paint miss |
| Plain AlertDialog | Scrollable body + buttons | Stock Holo |
| `Toast.show()` | Passive hint HUD (no key gate) | Stock Toast when native_error tier active |
| USB storage prompt | Always companion USB tier (in-place takeover) | Queued behind native-error; else morphs same WM shell |

### System error tier ladder

| Tier | When | Input path |
|------|------|------------|
| 1 | Xposed + companion paints | `OverlayKeyForwarder` → companion rows; result → AMS via `DIALOG_RESULT` |
| 2 | `legacy_shell=1` | Solar `:overlay` paints (rollback) |
| 3 | Overlay start miss / paint timeout (~2 s) | Stock Holo + `AnrDialogKeyForwarder` (wheel → DPAD inject) |
| 4 | Stale props | `StaleOverlayGate.clearIfNeeded()` before any hold |

**Solar HOME ANR:** auto-wait path keeps the sole shell painting even when Solar’s main thread is blocked.

Cross-link: [solar-rom/patches/xposed/README.md](../../solar-rom/patches/xposed/README.md), [Third-party overlay integration](third-party-overlay-integration.md).

---

## App launcher surface

`AppLauncher` + `AppsMenuPolicy` — on Solar ROM the **Apps** menu may show **Settings only**; other launchers hidden but APKs remain installed for handoff and services (`FMRadio.apk`, etc.).

---

## Testing checklist

On Y1 and Y2 hardware:

1. Open **Settings** (stock) — wheel scrolls lists (handoff).
2. Open arbitrary third-party app — BACK long opens quick menu; short BACK reaches app.
3. Quick menu → Wi‑Fi → new network — IME appears; password saves without leaving overlay.
4. Confirm Rockbox/JJ rules unchanged (see input doc launcher table).
5. Force ANR in test app — wheel navigates Wait/Close on overlay or stock Holo fallback.
6. Trigger Toast from third-party app — passive hint bar or stock Toast fail-open.

```bash
./solar-rom/scripts/audit-device-parity.sh
```

---

## Key files

| File | Role |
|------|------|
| `ExternalInputHandoff.java` | Handoff modes + inject |
| `SolarInputMethodService.java` | IME tray |
| `SolarImeRouteArbiter.java` | IME sysprops |
| `AppMenuHooks.java` | Third-party context menus |
| `ActivityOverlayKeyHooks.java` | App-process key fallback |
| `AppsMenuPolicy.java` | Apps menu visibility |

---

## Related

- [Input and overlays](input-and-overlays.md)
- [Third-party overlay integration](third-party-overlay-integration.md)
- [Hardware input reference](hardware-input-reference.md)
- [Global quick menu (user)](../using-solar/global-quick-menu.md)
- [Launchers and home routing](launchers-and-home-routing.md)
- [Troubleshooting](../ecosystem/troubleshooting.md)
