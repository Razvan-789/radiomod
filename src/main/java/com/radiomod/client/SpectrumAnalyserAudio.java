package com.radiomod.client;

import com.radiomod.client.screen.SpectrumAnalyserScreen;
import com.radiomod.client.screen.WebTabletScreen;
import com.radiomod.network.SyncSpectrumPayload;
import com.radiomod.signal.SpectrumAnalyserState;
import net.minecraft.client.Minecraft;

/** Owns analyser playback while its screen is open and while FM remains tuned in inventory. */
public final class SpectrumAnalyserAudio {
    private SpectrumAnalyserAudio() { }

    public static boolean ownsBackgroundAudio(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen instanceof WebTabletScreen) return false;
        SyncSpectrumPayload sample = SpectrumAnalyserState.freshClient();
        return sample != null && sample.band() == 0
                && SpectrumAnalyserState.hasAnalyser(minecraft.player);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.screen instanceof SpectrumAnalyserScreen
                || minecraft.screen instanceof WebTabletScreen) return;
        if (!ownsBackgroundAudio(minecraft)) {
            WebTabletAudio.get().stop();
            return;
        }
        playFm(minecraft, SpectrumAnalyserState.freshClient());
    }

    public static void playScreen(Minecraft minecraft, SyncSpectrumPayload sample) {
        if (sample == null) {
            WebTabletAudio.get().stop();
        } else if (sample.band() == 0) {
            playFm(minecraft, sample);
        } else if (sample.band() == 1 || sample.band() == 2) {
            if (sample.signalPresent() && !sample.audioUrl().isBlank())
                WebTabletAudio.get().play(minecraft, true, true, sample.tunedSignalTenths() / 10.0,
                        sample.snrTenths() / 10.0, sample.audioUrl(), sample.sourceUrl());
            else if (!sample.signalPresent())
                WebTabletAudio.get().play(minecraft, false, false, 0, 0, "", "");
            else WebTabletAudio.get().stop();
        } else {
            WebTabletAudio.get().stop();
        }
    }

    private static void playFm(Minecraft minecraft, SyncSpectrumPayload sample) {
        if (sample == null) return;
        boolean playable = sample.signalPresent() && !sample.audioUrl().isBlank();
        double strength = sample.tunedSignalTenths() / 10.0;
        double narrow = Math.clamp((180.0 - sample.bandwidthKhz()) / 130.0, 0, 1);
        double weak = Math.clamp((30.0 - strength) / 35.0, 0, 1);
        double wide = Math.clamp((sample.bandwidthKhz() - 200.0) / 200.0, 0, 1) * weak;
        double passbandNoise = Math.clamp(0.015 + narrow * 0.46 + wide * 0.45, 0, 0.75);
        WebTabletAudio.get().play(minecraft, false, playable, strength, sample.snrTenths() / 10.0,
                sample.audioUrl(), sample.sourceUrl(), passbandNoise,
                1 - narrow * 0.25 - wide * 0.15);
    }
}
