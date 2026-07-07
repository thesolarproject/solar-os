package com.solar.launcher;

import org.junit.Test;

/** GPU rendering + disable HW overlays policy — Y1/Y2 JB/KK only. */
public class GraphicsPerformancePolicyTest {

    @Test
    public void apply_noOpOnNullContext() {
        GraphicsPerformancePolicy.applySync(null);
        GraphicsPerformancePolicy.ensureAsync(null);
    }

    @Test
    public void settingKeysAreStable() {
        if (!"force_gpu_rendering".equals(GraphicsPerformancePolicy.KEY_FORCE_GPU)) {
            throw new AssertionError("force_gpu key drift");
        }
        if (!"disable_overlays".equals(GraphicsPerformancePolicy.KEY_DISABLE_OVERLAYS)) {
            throw new AssertionError("disable_overlays key drift");
        }
    }
}
