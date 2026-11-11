package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity.DabStationDefinition;

/** Resolves a DAB station's static text or its dynamic URL metadata. */
public final class DabDlsResolver {
    private DabDlsResolver() { }

    public static String text(DabStationDefinition station) {
        if (station.dlsUrlMode() && !station.dlsUrl().isBlank()) {
            return "icy:stream".equals(station.dlsUrl())
                    ? DynamicRadioTextResolver.icyText(station.streamUrl())
                    : DynamicRadioTextResolver.text(station.dlsUrl());
        }
        return station.dlsText().isBlank()
                ? DynamicRadioTextResolver.icyText(station.streamUrl()) : station.dlsText();
    }
}
