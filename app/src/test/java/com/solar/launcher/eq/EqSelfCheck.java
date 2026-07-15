package com.solar.launcher.eq;

/**
 * 2026-07-15 — JVM self-check for EQ model / filter graph / Rockbox+FixedBand parsers.
 * Layman: quick prove-out that preset math works without a device.
 */
public final class EqSelfCheck {

    private EqSelfCheck() {}

    public static void main(String[] args) {
        run();
        System.out.println("EqSelfCheck OK");
    }

    public static void run() {
        EqBandModel m = new EqBandModel();
        if (m.needsSoftwareEq()) throw new AssertionError("flat should not need EQ");
        m.setGainDb(3, 6f);
        if (!m.needsSoftwareEq()) throw new AssertionError("boost needs EQ");
        String af = EqFilterGraph.buildAf(m);
        if (af == null || af.indexOf("equalizer=f=250") < 0) throw new AssertionError("af band: " + af);

        String rb = ""
                + "eq enabled: on\n"
                + "eq precut: 50\n"
                + "eq low shelf filter: 31, 7, 20\n"
                + "eq peak filter 1: 62, 10, 10\n"
                + "eq peak filter 2: 125, 10, 0\n"
                + "eq peak filter 3: 250, 10, -10\n"
                + "eq peak filter 4: 500, 10, 0\n"
                + "eq peak filter 5: 1000, 10, 30\n"
                + "eq peak filter 6: 2000, 10, 0\n"
                + "eq peak filter 7: 4000, 10, 0\n"
                + "eq peak filter 8: 8000, 10, 0\n"
                + "eq high shelf filter: 16000, 7, -20\n";
        EqBandModel fromRb = EqPresetImporter.parseRockboxCfg(rb);
        if (Math.abs(fromRb.getPreampDb() + 5f) > 0.2f) {
            throw new AssertionError("precut → preamp: " + fromRb.getPreampDb());
        }
        if (Math.abs(fromRb.getGainDb(5) - 3f) > 0.5f) {
            throw new AssertionError("1k gain: " + fromRb.getGainDb(5));
        }

        String fixed = ""
                + "Preamp: -6.0 dB\n"
                + "Filter 1: ON PK Fc 31.0 Hz Gain 2.0 dB Q 1.41\n"
                + "Filter 2: ON PK Fc 62.0 Hz Gain 1.0 dB Q 1.41\n"
                + "Filter 3: ON PK Fc 125.0 Hz Gain 0.0 dB Q 1.41\n"
                + "Filter 4: ON PK Fc 250.0 Hz Gain -1.0 dB Q 1.41\n"
                + "Filter 5: ON PK Fc 500.0 Hz Gain 0.0 dB Q 1.41\n"
                + "Filter 6: ON PK Fc 1000.0 Hz Gain 4.0 dB Q 1.41\n"
                + "Filter 7: ON PK Fc 2000.0 Hz Gain 0.0 dB Q 1.41\n"
                + "Filter 8: ON PK Fc 4000.0 Hz Gain 0.0 dB Q 1.41\n"
                + "Filter 9: ON PK Fc 8000.0 Hz Gain 0.0 dB Q 1.41\n"
                + "Filter 10: ON PK Fc 16000.0 Hz Gain -2.0 dB Q 1.41\n";
        EqBandModel fromFixed = EqPresetImporter.parseFixedBandEq(fixed);
        if (Math.abs(fromFixed.getPreampDb() + 6f) > 0.2f) throw new AssertionError("fixed preamp");
        if (Math.abs(fromFixed.getGainDb(5) - 4f) > 0.2f) throw new AssertionError("fixed 1k");

        String roundTrip = EqPresetImporter.toRockboxCfg(fromFixed);
        EqBandModel again = EqPresetImporter.parseRockboxCfg(roundTrip);
        if (Math.abs(again.getGainDb(5) - 4f) > 0.6f) throw new AssertionError("round-trip 1k");
    }
}
