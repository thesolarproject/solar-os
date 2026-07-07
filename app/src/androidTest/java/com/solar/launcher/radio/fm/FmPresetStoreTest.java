package com.solar.launcher.radio.fm;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** FmPresetStore sort_order + reorder — mirrors play queue order. 2026-07-06 */
@RunWith(AndroidJUnit4.class)
public class FmPresetStoreTest {

  private Context ctx;

  @Before
  public void setUp() {
    ctx = ApplicationProvider.getApplicationContext();
    FmPresetStore.resetForTest();
  }

  @After
  public void tearDown() {
    FmPresetStore.resetForTest();
  }

  @Test
  public void reorder_preservesCountAndOrder() {
    FmPresetStore store = FmPresetStore.getInstance(ctx);
    store.upsert(101100, "101.1");
    store.upsert(102300, "102.3");
    store.upsert(98700, "98.7");
    store.reorder(0, 2);
    List<FmPresetStore.Preset> list = store.listAll();
    if (list.size() != 3) throw new AssertionError("size");
    if (list.get(0).freqKhz != 102300) throw new AssertionError("first after move");
    if (list.get(2).freqKhz != 101100) throw new AssertionError("last after move");
  }

  @Test
  public void replaceAll_assignsScanOrder() {
    FmPresetStore store = FmPresetStore.getInstance(ctx);
    List<FmPresetStore.Preset> scan = new ArrayList<FmPresetStore.Preset>();
    scan.add(new FmPresetStore.Preset(0, 88100, "88.1"));
    scan.add(new FmPresetStore.Preset(0, 95500, "95.5"));
    store.replaceAll(scan);
    List<FmPresetStore.Preset> list = store.listAll();
    if (list.get(0).freqKhz != 88100) throw new AssertionError("scan order 0");
    if (list.get(1).freqKhz != 95500) throw new AssertionError("scan order 1");
  }

  @Test
  public void toQueueItems_matchesPresetOrder() {
    FmPresetStore store = FmPresetStore.getInstance(ctx);
    store.upsert(101100, "A");
    store.upsert(102300, "B");
    if (store.toQueueItems().size() != 2) throw new AssertionError("queue items");
  }
}
