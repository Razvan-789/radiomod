package com.radiomod.signal;

public enum DabErrorCorrection {
    EEP_1A("EEP-1A", 4.0, 3.0, 12),
    EEP_2A("EEP-2A", 5.0, 6.0, 8),
    EEP_3A("EEP-3A", 6.0, 9.0, 6),
    EEP_4A("EEP-4A", 7.0, 12.0, 4);

    private final String label;
    private final double thresholdSnrDb;
    private final double minimumSignalDbuv;
    private final int capacityUnitsPer8Kbps;

    DabErrorCorrection(String label, double thresholdSnrDb, double minimumSignalDbuv, int capacityUnitsPer8Kbps) {
        this.label = label;
        this.thresholdSnrDb = thresholdSnrDb;
        this.minimumSignalDbuv = minimumSignalDbuv;
        this.capacityUnitsPer8Kbps = capacityUnitsPer8Kbps;
    }

    public String getLabel() {
        return label;
    }

    public double getThresholdSnrDb() {
        return thresholdSnrDb;
    }

    public double getMinimumSignalDbuv() {
        return minimumSignalDbuv;
    }

    public int decodeState(double signalDbuv, double snrDb, double snrBias) {
        if (signalDbuv < minimumSignalDbuv || snrDb < thresholdSnrDb + snrBias + 1.0) {
            return 0;
        }
        return snrDb >= thresholdSnrDb + snrBias + 4.0 ? 2 : 1;
    }

    public int getMuxCapacityKbps() {
        return (864 / capacityUnitsPer8Kbps) * 8;
    }

    public DabErrorCorrection next() {
        DabErrorCorrection[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
