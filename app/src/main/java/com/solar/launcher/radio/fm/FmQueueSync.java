package com.solar.launcher.radio.fm;

import com.solar.launcher.PlaybackCoordinator;
import com.solar.launcher.PlayQueue;
import com.solar.launcher.radio.FmBandPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * FM play queue mirrors FmPresetStore order — shuffle/repeat never apply. 2026-07-06
 * Layman: saved stations list IS the station playlist for Now Playing and global Queue.
 */
public final class FmQueueSync {

  private FmQueueSync() {}

  /** Rebuild RADIO queue from presets; highlight playFreqKhz (or index 0). */
  public static void syncQueueFromPresets(
      PlaybackCoordinator playback, FmPresetStore store, int playFreqKhz) {
    if (playback == null || store == null) return;
    List<FmPresetStore.Preset> presets = store.listAll();
    if (presets.isEmpty()) return;
    List<PlayQueue.QueueItem> items = new ArrayList<PlayQueue.QueueItem>(presets.size());
    int index = 0;
    boolean matched = false;
    for (int i = 0; i < presets.size(); i++) {
      FmPresetStore.Preset p = presets.get(i);
      String label = p.label;
      if (label == null || label.isEmpty()) {
        label = FmBandPlan.khzToFraction(p.freqKhz, FmBandPlan.fromRegionCode("US"));
      }
      items.add(PlayQueue.QueueItem.fmStation(p.freqKhz, label));
      if (p.freqKhz == playFreqKhz) {
        index = i;
        matched = true;
      }
    }
    if (!matched) index = 0;
    playback.syncFmQueue(items, index);
  }

  /** After queue viewer reorder — persist preset order to match play queue. */
  public static void syncPresetsFromQueue(PlaybackCoordinator playback, FmPresetStore store) {
    if (playback == null || store == null || !playback.isFmActive()) return;
    List<PlayQueue.QueueItem> items = playback.unifiedQueue().items();
    List<FmPresetStore.Preset> next = new ArrayList<FmPresetStore.Preset>();
    for (PlayQueue.QueueItem item : items) {
      if (item == null || item.kind != PlayQueue.ItemKind.FM_STATION) continue;
      FmPresetStore.Preset p = store.findByFreq(item.fmFreqKhz);
      if (p != null) {
        next.add(new FmPresetStore.Preset(p.id, p.freqKhz, item.fmLabel != null ? item.fmLabel : p.label, 0));
      } else {
        next.add(new FmPresetStore.Preset(0, item.fmFreqKhz, item.fmLabel != null ? item.fmLabel : "", 0));
      }
    }
    if (!next.isEmpty()) store.replaceAll(next);
  }
}
