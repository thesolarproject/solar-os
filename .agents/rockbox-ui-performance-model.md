# Rockbox UI performance model → Solar

2026-07-18 — Shared mental model for agents. Source tree: `~/Documents/Y2 Rockbox Workspace/rockbox-y2`.

## Rockbox loop (selection machine)

```
wheel / REPEAT → button_queue_try_post (drop if busy)
             → gui_synclist index += step (± accel modifier ≤64)
             → if queue_depth < 6: list_draw(visible rows only)
             → yield()
```

Key files: `apps/gui/list.c` (`FRAMEDROP_TRIGGER 6`, `gui_synclist_do_button`), `apps/gui/bitmap/list.c` (`list_draw`), `firmware/drivers/button.c` (soft repeat 300ms → 160→50ms), `button_queue_try_post` (refuse enqueue when UI behind).

Y1/Y2 Android port shares this list path; kinetic touch is compiled out on `PLATFORM_ANDROID`.

## Why it feels fast

1. Integer selection + callbacks — never N row objects
2. Drop input when busy — no afterscroll
3. Frame-drop paint — selection always advances
4. Accel by stride, not more paints
5. `yield()` every step
6. Defer voice/art (~HZ/5)
7. Partial viewport flush
8. No wrap during REPEAT

## Solar mapping

| Rockbox | Solar |
|---------|--------|
| Coalesce / one apply per frame | `ListWheelCoalescer` (lists) + `menuWheelCoalescer` (home/settings/browser, 2026-07-19) |
| Soft accel | `WheelPhysics` |
| Instant ensure-visible | `FocusScrollHelper` (duration 0) |
| Ghost-stop | `MicScrollBoost` + LiveGate |
| Frame-drop paint | `ScrollIdleGate` — skip preview/bind while spinning; settings preview deferred 2026-07-19 |
| RAM catalog | `customLibrary` + `LibraryRamCache` (indexes) |
| Large-lib paging | `LibrarySegmentCache` + `LibraryMemoryBudget` |
| Defer heavy open | `UiBusy.REASON_LIBRARY_LOAD` + post-frame bind |
| Queue input during busy | `pendingAnimWheelSignedSteps` across ScreenTransition (2026-07-19) |

## Contract for Solar changes

- Logical selection tracks the dial; paint may lag one frame under backlog.
- While scrolling: no art decode, no preview refresh, no DB (`ScrollIdleGate`).
- Library browse serves from RAM after hydrate (`LibraryRamCache`); SQLite is durable store, not the hot path.
- OK into heavy menus: placeholder + `UiBusy.REASON_LIBRARY_LOAD` first frame; fill after Choreographer.
- No menu wrap while `WheelPhysics.suppressWrapAround()` (Rockbox REPEAT).
- Home/settings: never sync dual-pane preview from `onFocusChange` — idle-gate only (2026-07-19).

Status 2026-07-19: Phase A menu perf — home/settings coalesce + deferred preview + StateListDrawable focus chrome + anim wheel queue; Phase C start — MEDIA_BUTTON register 2s throttle + handoff FG cache 1200ms.

2026-07-19 Solar-only product note: do not spend cycles on Rockbox/JJ install or launcher-matrix coexistence. Selection-machine speed is the goal; Rockbox list.c is the reference for *feel*, not a dependency to ship.
