package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity.DabStationDefinition;

/** Resolves a DAB station's static text or its dynamic URL metadata. */
public final class DabDlsResolver {
    private DabDlsResolver() { }

    public static String text(DabStationDefinition station) {
        return station.dlsUrlMode()
                ? DynamicRadioTextResolver.text(station.dlsUrl())
                : station.dlsText();
    }
}
