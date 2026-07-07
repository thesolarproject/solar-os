package com.solar.launcher.radio.fm;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2026-07-06 — One-shot import of JJ Launcher comma-separated MHz presets into SQLite.
 * Layman: copies saved stations from JJ if Solar has none yet.
 */
public final class FmJjPresetImport {

  /** JJ prefs key — com.themoon.y1 SharedPreferences default name. */
  public static final String JJ_PREFS_NAME = "com.themoon.y1_preferences";
  public static final String JJ_STATIONS_KEY = "radio_stations";

  private FmJjPresetImport() {}

  /** Parse JJ MHz list e.g. "87.5,101.1" → kHz integers. */
  public static List<Integer> parseMhzCsv(String csv) {
    List<Integer> out = new ArrayList<Integer>();
    if (csv == null || csv.trim().isEmpty()) return out;
    for (String part : csv.split(",")) {
      try {
        float mhz = Float.parseFloat(part.trim());
        int khz = Math.round(mhz * 1000f);
        if (khz > 0) out.add(khz);
      } catch (NumberFormatException ignored) {}
    }
    return out;
  }

  /**
   * Import when Solar preset table is empty and JJ string exists.
   * @return count imported
   */
  public static int importIfEmpty(Context ctx) {
    FmPresetStore store = FmPresetStore.getInstance(ctx);
    if (!store.listAll().isEmpty()) return 0;
    SharedPreferences jj =
        ctx.getApplicationContext().getSharedPreferences(JJ_PREFS_NAME, Context.MODE_PRIVATE);
    String csv = jj.getString(JJ_STATIONS_KEY, "");
    if (csv == null || csv.isEmpty()) {
      jj = ctx.getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
      csv = jj.getString(JJ_STATIONS_KEY, "");
    }
    List<Integer> freqs = parseMhzCsv(csv);
    if (freqs.isEmpty()) return 0;
    for (int khz : freqs) {
      String label = String.format(Locale.US, "%.1f", khz / 1000f);
      store.upsert(khz, label);
    }
    return freqs.size();
  }
}
