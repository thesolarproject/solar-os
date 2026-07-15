# Solar-only launcher policy (2026-07-10)

Solar is the sole user-facing HOME experience. Alternate launcher switching UI and
boot-time handoff scripts are disabled.

## Context menu architecture

| Foreground | Menu host | Input |
|------------|-----------|-------|
| `com.solar.launcher` | In-app `ThemedContextMenu` | `MainActivity` key dispatch |
| Third-party / SystemUI | `com.solar.launcher.globalcontext` companion overlay | Xposed hold → `showPowerOverlay` |

Y2 POWER-hold while Solar is foreground routes to `MainActivity` via
`SolarOverlayClient.showInAppPowerMenu()` (not the companion WM shell).

## Global overlay power list

Restart + Shutdown only under the quick chip bar — no launcher rows.

## Stale gate heal

`StaleOverlayGate` clears `active`/`ui` props when `shell_visible=0` for >500ms
(ghost gate from the retired solar_home_* companion IPC path).

## Intentional ROM artifacts

Rockbox APK and switch scripts may remain on `/system` for adb recovery; they are
not exposed in Solar UI and are not seeded to `/data/data` on boot.
