# A5 hybrid touch navigation (2026-07-14)

Two input modes in one APK — finger and buttons stay friendly.

## Lists / settings / Reach / context modals

- **Scrollable screens** (content taller/wider than the viewport): **scroll**, **peck** (first tap focuses), **tap** again to activate. No drag-to-move-focus (would fight flings).
- **Short / non-scrollable screens**: finger **slide** moves the highlight (`A5FocusConfirm.followFinger`); **second tap** on the lit row activates.
- Gate: `A5FocusConfirm.enabled()` (`isA5` / touchscreen); followFinger only when `isShortNonScrollSurface`.
- In-app: `createListButton`, home/settings shells, adapters, `ThemedContextMenu`.
- Companion: `ChipContextMenu` + TextView fallback in `GlobalContextOverlayService`.

## Flow Cover Flow (exception)

- Touch: finger-follow + **inertia fling** + snap (`FlowEngine` free-scroll APIs).
- Tap side cover → center; tap center → `FlowScreenHost.handleCenterOk`.
- **Flipped tracklist (back face):**
  - Vertical drag → scroll tracks (`scrollBackBy`, same as wheel)
  - Horizontal flick → dismiss + neighbor cover (`flipToFrontThenScroll`)
  - Tap a track → play immediately (Cover Flow directness)
- Keys / wheel: unchanged stepped `scrollBy` / `SCROLL_MS` ease; wheel past list end still uses `flipToFrontThenScroll`.
- Mutex: wheel cancels free/fling via `cancelFreeScrollForKeyInput`.

## Wheel keyboard / Solar IME (2026-07-14)

- **Touch:** `SolarWheelKeyboardUi.attachTouchSlots` — first tap focuses a letter slot; second tap (or tap current) types it (`A5FocusConfirm`). Horizontal drag / fling on the strip steps the charset (Flow-like).
- **In-app:** `MainActivity.attachInAppKeyboardTouch` after `openKeyboard`.
- **System IME (A5):** real `onCreateInputView` shell (no Xposed); `attachTouchSlotsForIme` commits via `InputConnection`.
- **A5 hardware while tray open** (`A5KeyboardKeys`):
  - Vol Up → space; Vol Down → delete
  - Side Back short → Enter; hold Back → charset (Aa / #)
  - Face mid → select (CENTER); face L/R → wheel
  - Edge flick Back → cancel (hardware Back does not dismiss)
- Y1/Y2 keyboard bindings unchanged (track prev/next, Play-long charset, Back cancel).

## Out of scope here

- Do not wrap Flow with `A5FocusConfirm`.
- Y1 keylayout / wheel scancodes unchanged.
- Pagination “Show more” footers may stay one-tap.
