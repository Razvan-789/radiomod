package com.radiomod.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.radiomod.client.SpectrumAnalyserAudio;
import com.radiomod.menu.SpectrumAnalyserMenu;
import com.radiomod.network.SyncSpectrumPayload;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.PtyNames;
import com.radiomod.signal.SpectrumAnalyserState;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** All FFT-like drawing, motion, noise and waterfall history live on the client. */
public class SpectrumAnalyserScreen extends AbstractContainerScreen<SpectrumAnalyserMenu> {
    private record Marker(int from, int to, String label) { }
    private static final Marker[] MARKERS = {
            new Marker(140, 400, "LW BROADCAST"), new Marker(500, 1_600, "MW BROADCAST"),
            new Marker(2_300, 2_495, "120m SW"), new Marker(3_200, 3_400, "90m SW"),
            new Marker(3_900, 4_000, "75m SW"), new Marker(4_750, 5_060, "60m SW"),
            new Marker(5_900, 6_200, "49m SW"), new Marker(7_200, 7_450, "41m SW"),
            new Marker(9_400, 9_900, "31m SW"), new Marker(11_600, 12_100, "25m SW"),
            new Marker(13_570, 13_870, "22m SW"), new Marker(15_100, 15_800, "19m SW"),
            new Marker(17_480, 17_900, "16m SW"), new Marker(18_900, 19_020, "15m SW"),
            new Marker(21_450, 21_850, "13m SW"), new Marker(25_670, 26_100, "11m SW"),
            new Marker(65_000, 74_000, "OIRT FM"), new Marker(74_000, 86_000, "JAPAN FM"),
            new Marker(86_000, 108_000, "FM BROADCAST"),
            new Marker(108_000, 118_000, "VOR / ILS"),
            new Marker(174_000, 240_000, "DAB BROADCAST"),
            new Marker(1_089_500, 1_090_500, "ADS-B 1090")
    };
    private static final int BINS = 160, ROWS = 68;
    private static final int SPECTRUM_WIDTH = 640, SPECTRUM_HEIGHT = 192;
    private static final int[] SPECTRUM_GRADIENT = new int[SPECTRUM_HEIGHT];
    static {
        for (int y = 0; y < SPECTRUM_HEIGHT; y++) {
            double t = 1.0 - y / (double) (SPECTRUM_HEIGHT - 1);
            // Fixed vertical blue-to-white palette, as on an SDR spectrum display.
            SPECTRUM_GRADIENT[y] = t < .55
                    ? blend(0xFF071B4A, 0xFF2878B9, t / .55)
                    : blend(0xFF2878B9, 0xFFEAF6FF, (t - .55) / .45);
        }
    }
    private final Random random = new Random();
    private final double[] noise = new double[BINS];
    private final double[] levels = new double[BINS];
    private final double[][] waterfall = new double[ROWS][BINS];
    private final double[][] waterfallEcho = new double[ROWS][BINS];
    private final double[][] echoPositions = new double[64][4];
    private final boolean[] waterfallValid = new boolean[ROWS];
    private static Integer savedUpperLimit, savedLowerLimit;
    private EditBox frequencyInput, upperInput, lowerInput;
    private String rangeError = "";
    private int waterfallHead;
    private int serviceScroll;
    private long lastFrame;
    private long nextAdsbBurstAt, adsbBurstUntil;
    private int lastBand = -1, lastCenter, lastSpan;
    private boolean compact;
    private DynamicTexture spectrumTexture, waterfallTexture;
    private ResourceLocation spectrumId, waterfallId;

    public SpectrumAnalyserScreen(SpectrumAnalyserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 700; imageHeight = 430;
    }

    @Override protected void init() {
        imageWidth = Math.min(700, width - 12);
        imageHeight = Math.min(430, height - 12);
        compact = imageWidth < 680;
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(x + imageWidth - 29, y + 8, 20, 18).build());
        int controlY = y + imageHeight - 30;
        int step = compact ? (imageWidth - 16) / 6 : 0;
        addRenderableWidget(Button.builder(Component.literal("TUNE -"), b -> click(1))
                .bounds(x + (compact ? 8 : 10), controlY, compact ? step - 3 : 57, 20).build());
        addRenderableWidget(Button.builder(Component.literal("TUNE +"), b -> click(2))
                .bounds(x + (compact ? 8 + step : 71), controlY, compact ? step - 3 : 57, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SPAN -"), b -> click(3))
                .bounds(x + (compact ? 8 + step * 2 : 132), controlY, compact ? step - 3 : 57, 20).build());
        addRenderableWidget(Button.builder(Component.literal("SPAN +"), b -> click(4))
                .bounds(x + (compact ? 8 + step * 3 : 193), controlY, compact ? step - 3 : 57, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BW -"), b -> click(5))
                .bounds(x + (compact ? 8 + step * 4 : 254), controlY, compact ? step - 3 : 47, 20).build());
        addRenderableWidget(Button.builder(Component.literal("BW +"), b -> click(6))
                .bounds(x + (compact ? 8 + step * 5 : 305), controlY, compact ? step - 3 : 47, 20).build());
        int inputY = compact ? controlY - 23 : controlY;
        frequencyInput = new EditBox(font, x + imageWidth - 157, inputY + 2, 93, 16,
                Component.literal("Center MHz"));
        frequencyInput.setHint(Component.literal("MHz"));
        frequencyInput.setMaxLength(8);
        frequencyInput.setFilter(s -> s.matches("[0-9.]*"));
        addRenderableWidget(frequencyInput);
        addRenderableWidget(Button.builder(Component.literal("SET"), b -> setFrequency())
                .bounds(x + imageWidth - 60, inputY, 48, 20).build());
        int rangeY = compact ? controlY - 47 : controlY - 25;
        int upperX = compact ? x + 28 : x + 91;
        int lowerX = compact ? x + 106 : x + 253;
        upperInput = new EditBox(font, upperX, rangeY + 2, compact ? 46 : 58, 16,
                Component.literal("Upper signal limit in dBuV"));
        lowerInput = new EditBox(font, lowerX, rangeY + 2, compact ? 46 : 58, 16,
                Component.literal("Lower signal limit in dBuV"));
        for (EditBox input : new EditBox[]{upperInput, lowerInput}) {
            input.setHint(Component.literal("Auto"));
            input.setMaxLength(4);
            input.setFilter(value -> value.matches("-?[0-9]*"));
            addRenderableWidget(input);
        }
        if (savedUpperLimit != null) upperInput.setValue(savedUpperLimit.toString());
        if (savedLowerLimit != null) lowerInput.setValue(savedLowerLimit.toString());
        addRenderableWidget(Button.builder(Component.literal("APPLY"), b -> applyRange())
                .bounds(compact ? x + 160 : x + 322, rangeY, compact ? 51 : 55, 20).build());
        addRenderableWidget(Button.builder(Component.literal("AUTO"), b -> resetRange())
                .bounds(compact ? x + 215 : x + 381, rangeY, compact ? 47 : 55, 20).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void setFrequency() {
        try {
            int khz = (int) Math.round(Double.parseDouble(frequencyInput.getValue()) * 1000);
            if (SpectrumAnalyserState.bandFor(khz) >= 0) click(1_000_000 + khz);
        } catch (NumberFormatException ignored) { }
    }

    private void applyRange() {
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        if (p == null) { rangeError = "Waiting for RF sample"; return; }
        try {
            Integer upper = upperInput.getValue().isBlank() ? null : Integer.parseInt(upperInput.getValue());
            Integer lower = lowerInput.getValue().isBlank() ? null : Integer.parseInt(lowerInput.getValue());
            if ((upper != null && (upper < -120 || upper > 150))
                    || (lower != null && (lower < -120 || lower > 150))) {
                rangeError = "Use -120 to 150 dBuV";
                return;
            }
            double high = upper == null ? autoHigh(p) : upper;
            double low = lower == null ? autoLow(p) : lower;
            if (high - low < 10) { rangeError = "Upper must exceed lower by 10 dB"; return; }
            savedUpperLimit = upper;
            savedLowerLimit = lower;
            rangeError = "";
            uploadTextures(p);
        } catch (NumberFormatException ignored) {
            rangeError = "Enter whole dBuV values";
        }
    }

    private void resetRange() {
        savedUpperLimit = null;
        savedLowerLimit = null;
        upperInput.setValue("");
        lowerInput.setValue("");
        rangeError = "";
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        if (p != null) uploadTextures(p);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean textFocused = frequencyInput.isFocused() || upperInput.isFocused() || lowerInput.isFocused();
        if (textFocused && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        if (textFocused && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (frequencyInput.isFocused()) setFrequency();
            else applyRange();
            return true;
        }
        if (!textFocused && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN)) {
            click(1);
            return true;
        }
        if (!textFocused && (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP)) {
            click(2);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override protected void containerTick() {
        super.containerTick();
        SpectrumAnalyserAudio.playScreen(minecraft, SpectrumAnalyserState.freshClient());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        int sx = leftPos + 52, sy = topPos + 42, sw = imageWidth - 70;
        int spectrumH = Math.max(55, (imageHeight - (compact ? 125 : 100)) * 52 / 100);
        if (button == 0 && p != null && p.band() == 1 && !p.services().isEmpty()) {
            int listTop = topPos + 42 + spectrumH + 27;
            int listLeft = leftPos + imageWidth - 192;
            int row = (int) (mouseY - listTop - 22) / 13;
            if (mouseX >= listLeft && mouseX < leftPos + imageWidth - 20
                    && mouseY >= listTop + 22 && row >= 0
                    && row < Math.min(8, p.services().size() - serviceScroll)) {
                click(2_000_000 + row + serviceScroll);
                return true;
            }
        }
        if (button == 0 && p != null && mouseX >= sx && mouseX < sx + sw
                && mouseY >= sy && mouseY < sy + spectrumH) {
            double fraction = (mouseX - sx) / sw;
            int khz = (int)Math.round(p.centerKhz() + (fraction - 0.5) * p.spanKhz());
            if (p.band() == 1) {
                khz = DabChannel.byIndex(DabChannel.nearestIndexForFrequencyKhz(khz)).frequencyKhz();
            } else if (p.band() == 0) {
                khz = Math.round(khz / 100f) * 100;
            } else if (p.band() == 3) {
                khz = Math.round(khz / 50f) * 50;
            }
            if (SpectrumAnalyserState.bandFor(khz) >= 0) click(1_000_000 + khz);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        if (p != null && p.band() == 1 && mouseX >= leftPos + imageWidth - 196
                && mouseX < leftPos + imageWidth - 14 && mouseY >= topPos + 42 + Math.max(55,
                (imageHeight - (compact ? 125 : 100)) * 52 / 100) + 27) {
            serviceScroll = Math.clamp(serviceScroll + (scrollY < 0 ? 1 : -1), 0,
                    Math.max(0, p.services().size() - 8));
            return true;
        }
        if (scrollY != 0 && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight) {
            click(scrollY > 0 ? 2 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void advance(SyncSpectrumPayload p) {
        long now = System.currentTimeMillis();
        if (lastBand != p.band() || lastCenter != p.centerKhz() || lastSpan != p.spanKhz()) {
            lastBand = p.band(); lastCenter = p.centerKhz(); lastSpan = p.spanKhz();
            java.util.Arrays.fill(waterfallValid, false);
            for (int i = 0; i < BINS; i++) noise[i] = 0;
            lastFrame = 0;
            nextAdsbBurstAt = 0;
            adsbBurstUntil = 0;
        }
        if (now - lastFrame < 90) return;
        lastFrame = now;
        if (p.band() == 4 && p.adsbContacts() > 0) {
            if (nextAdsbBurstAt == 0) nextAdsbBurstAt = now + adsbIntervalMs(p.adsbContacts());
            if (now >= nextAdsbBurstAt) {
                adsbBurstUntil = now + 105;
                nextAdsbBurstAt = now + adsbIntervalMs(p.adsbContacts());
            }
        } else {
            nextAdsbBurstAt = 0;
            adsbBurstUntil = 0;
        }
        double floor = p.noiseTenths() / 10.0;
        double start = p.centerKhz() - p.spanKhz() / 2.0;
        double binWidth = p.spanKhz() / (double) BINS;
        double[] row = waterfall[waterfallHead];
        double[] echoRow = waterfallEcho[waterfallHead];
        int overhead = Math.min(24, p.overheadBlocks());
        int echoCount = overhead == 0 ? 0 : 2 + Math.min(2, overhead / 8);
        if (echoCount > 0) {
            for (int carrierIndex = 0; carrierIndex < p.carriers().size(); carrierIndex++) {
                SyncSpectrumPayload.Carrier carrier = p.carriers().get(carrierIndex);
                if (carrier.kind() == 0) continue;
                double halfWidth = carrier.widthKhz() / 2.0;
                double speed = 0.000065 + (carrier.frequencyKhz() % 7) * 0.000006;
                for (int echo = 0; echo < echoCount; echo++) {
                    double phase = (now * speed + carrier.frequencyKhz() * 0.000371 + echo * 0.37) % 1.0;
                    echoPositions[carrierIndex][echo] = carrier.frequencyKhz() - halfWidth
                            + phase * carrier.widthKhz();
                }
            }
        }
        for (int i = 0; i < BINS; i++) {
            double frequency = start + (i + 0.5) * p.spanKhz() / BINS;
            noise[i] = Math.clamp(noise[i] * 0.75 + random.nextGaussian() * 0.55, -2, 2);
            double level = floor + noise[i];
            boolean carrierPeak = false;
            double echoStrength = 0;
            for (int carrierIndex = 0; carrierIndex < p.carriers().size(); carrierIndex++) {
                SyncSpectrumPayload.Carrier c = p.carriers().get(carrierIndex);
                double delta = Math.abs(frequency - c.frequencyKhz());
                // A carrier between two display samples must still reach its measured peak.
                double sampledDelta = Math.max(0, delta - binWidth * 0.5);
                double half = Math.max(1, c.widthKhz() / 2.0);
                double strength = c.signalTenths() / 10.0;
                double shape;
                if (c.kind() == 0) {
                    // FM side lobes move, but the center remains at the measured strength.
                    double motion = Math.sin(sampledDelta / half * 10 + now * 0.0018
                            + random.nextGaussian() * 0.25);
                    double sideMovement = 0.18 * motion * Math.min(1, sampledDelta / half);
                    shape = Math.exp(-Math.pow(sampledDelta / (half * 0.72), 2)) * (1 + sideMovement);
                    shape += 0.14 * Math.exp(-Math.pow((sampledDelta - half * 1.4) / (half * 0.3), 2));
                } else if (c.kind() == 3) {
                    // Narrow continuous VOR carrier with shallow modulation sidebands.
                    shape = 0.9 * Math.exp(-Math.pow(sampledDelta / 7.0, 2))
                            + 0.22 * Math.exp(-Math.pow((sampledDelta - 12) / 6.0, 2));
                } else if (c.kind() == 4) {
                    // A packet appears briefly across the occupied channel; arrival time is
                    // random and scales with how many transponders the receiver can hear.
                    shape = now < adsbBurstUntil && sampledDelta <= half
                            ? 0.72 + 0.25 * Math.pow(Math.cos(sampledDelta * 0.045), 2) : 0.0;
                } else {
                    // DAB and DRM have flat occupied bandwidth with steep skirts.
                    shape = sampledDelta <= half * 0.92 ? 1.0
                            : Math.exp(-Math.pow((sampledDelta - half * 0.92) / (half * 0.07), 2));
                }
                shape = Math.clamp(shape, 0, 1);
                carrierPeak |= shape >= 0.95;
                double multipath = 0;
                if (echoCount > 0 && (c.kind() == 1 || c.kind() == 2) && strength > floor + 3 && sampledDelta <= half) {
                    for (int echo = 0; echo < echoCount; echo++) {
                        boolean hole = (echo & 1) == 0;
                        double spikeWidth = Math.max(binWidth * (hole ? 1.3 : 0.9),
                                c.widthKhz() * (hole ? 0.055 : 0.035));
                        double distance = Math.abs(frequency - echoPositions[carrierIndex][echo]);
                        double ridge = Math.max(0, 1 - distance / spikeWidth);
                        double effect = (hole ? -(4.0 + overhead * 0.25)
                                : 2.5 + overhead * 0.16) * ridge * ridge;
                        if (Math.abs(effect) > Math.abs(multipath)) multipath = effect;
                    }
                }
                double candidate = floor + (strength - floor) * shape
                        + Math.clamp(random.nextGaussian() * (c.kind() == 0 ? 0.9 : 0.6), -2, 2)
                        + multipath;
                level = Math.max(level, candidate);
                if (Math.abs(multipath) > Math.abs(echoStrength)) echoStrength = multipath;
            }
            // Brief fading notches break up the skirts without hiding measured peaks.
            if (!carrierPeak && random.nextDouble() < 0.025) level -= random.nextDouble() * 2;
            levels[i] = level;
            row[i] = level + random.nextGaussian() * 0.8;
            echoRow[i] = echoStrength;
        }
        waterfallValid[waterfallHead] = true;
        waterfallHead = (waterfallHead + 1) % ROWS;
        uploadTextures(p);
    }

    private long adsbIntervalMs(int contacts) {
        double packetsPerSecond = Math.min(8.0, Math.max(1, contacts) * 0.7);
        return Math.max(120, (long) (-Math.log(Math.max(1e-6, random.nextDouble()))
                * 1_000.0 / packetsPerSecond));
    }

    private static int blend(int a, int b, double t) {
        int r = (int)(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = (int)(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int blue = (int)((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | blue;
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 255) << 16) | ((argb >>> 16) & 255);
    }

    private void ensureTextures() {
        if (spectrumTexture != null) return;
        spectrumTexture = new DynamicTexture(new NativeImage(SPECTRUM_WIDTH, SPECTRUM_HEIGHT, false));
        waterfallTexture = new DynamicTexture(new NativeImage(BINS, ROWS, false));
        spectrumId = minecraft.getTextureManager().register("radio_spectrum", spectrumTexture);
        waterfallId = minecraft.getTextureManager().register("radio_waterfall", waterfallTexture);
    }

    private static double autoHigh(SyncSpectrumPayload p) {
        double strongest = p.noiseTenths() / 10.0;
        for (SyncSpectrumPayload.Carrier c : p.carriers())
            strongest = Math.max(strongest, c.signalTenths() / 10.0);
        return Math.max(p.noiseTenths() / 10.0 + 55, strongest + 8);
    }

    private static double autoLow(SyncSpectrumPayload p) {
        // Leave room below the noise trace so it remains visible on quiet frequencies.
        return p.noiseTenths() / 10.0 - 20;
    }

    private double plotHigh(SyncSpectrumPayload p) {
        if (savedUpperLimit != null) return savedUpperLimit;
        double low = savedLowerLimit == null ? autoLow(p) : savedLowerLimit;
        return Math.max(autoHigh(p), low + 10);
    }

    private double plotLow(SyncSpectrumPayload p) {
        if (savedLowerLimit != null) return savedLowerLimit;
        double high = savedUpperLimit == null ? autoHigh(p) : savedUpperLimit;
        return Math.min(autoLow(p), high - 10);
    }

    private void uploadTextures(SyncSpectrumPayload p) {
        ensureTextures();
        NativeImage spectrum = spectrumTexture.getPixels();
        NativeImage fall = waterfallTexture.getPixels();
        if (spectrum == null || fall == null) return;
        double low = plotLow(p), high = plotHigh(p);
        int previous = SPECTRUM_HEIGHT - 1;
        for (int x = 0; x < SPECTRUM_WIDTH; x++) {
            double position = x * (BINS - 1.0) / (SPECTRUM_WIDTH - 1);
            int index = Math.min(BINS - 2, (int)position);
            double level = levels[index] + (levels[index + 1] - levels[index]) * (position - index);
            int line = (int)Math.round((high - level) / (high - low) * (SPECTRUM_HEIGHT - 1));
            line = Math.clamp(line, 0, SPECTRUM_HEIGHT - 1);
            if (x == 0) previous = line;
            int strokeTop = Math.min(previous, line), strokeBottom = Math.max(previous, line);
            for (int y = 0; y < SPECTRUM_HEIGHT; y++) {
                int color = y >= strokeTop && y <= strokeBottom ? 0xFFF2F9FF
                        : y > line ? SPECTRUM_GRADIENT[y] : 0xFF020408;
                spectrum.setPixelRGBA(x, y, abgr(color));
            }
            previous = line;
        }
        for (int y = 0; y < ROWS; y++) {
            int rowIndex = Math.floorMod(waterfallHead - 1 - y, ROWS);
            double[] row = waterfall[rowIndex];
            for (int x = 0; x < BINS; x++) {
                int color = waterfallValid[rowIndex] ? heat((row[x] - low) / (high - low)) : 0xFF020914;
                if (waterfallValid[rowIndex]) {
                    double echo = waterfallEcho[rowIndex][x];
                    if (echo > 0) color = blend(color, 0xFFFFF4C9, Math.min(0.7, echo / 7.0));
                    else if (echo < 0) color = blend(color, 0xFF010715, Math.min(0.8, -echo / 11.0));
                }
                fall.setPixelRGBA(x, y, abgr(color));
            }
        }
        spectrumTexture.upload();
        waterfallTexture.upload();
    }

    private static int heat(double raw) {
        double t = Math.clamp(raw, 0, 1);
        int r, g, b;
        if (t < .35) { double v = t / .35; r = (int)(2 + v * 10); g = (int)(9 + v * 88); b = (int)(25 + v * 215); }
        else if (t < .58) { double v = (t - .35) / .23; r = (int)(12 + v * 230); g = (int)(97 + v * 158); b = (int)(240 + v * 15); }
        else if (t < .8) { double v = (t - .58) / .22; r = 255; g = (int)(255 - v * 135); b = (int)(255 - v * 245); }
        else { double v = (t - .8) / .2; r = (int)(255 - v * 30); g = (int)(120 - v * 95); b = (int)(10 + v * 10); }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, right = x + imageWidth;
        g.fill(x, y, right, y + imageHeight, 0xFF35373D);
        g.fill(x + 4, y + 4, right - 4, y + imageHeight - 4, 0xFF44464C);
        g.fill(x + 8, y + 32, right - 8, y + imageHeight - 38, 0xFF05070C);
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        int sx = x + 52, sw = imageWidth - 70;
        int sy = y + 42, spectrumH = Math.max(55, (imageHeight - (compact ? 125 : 100)) * 52 / 100);
        int wy = sy + spectrumH + 27, wh = Math.max(15, y + imageHeight - (compact ? 90 : 66) - wy);
        if (p == null) return;
        advance(p);
        if (spectrumId != null)
            g.blit(spectrumId, sx, sy, sw, spectrumH, 0f, 0f,
                    SPECTRUM_WIDTH, SPECTRUM_HEIGHT, SPECTRUM_WIDTH, SPECTRUM_HEIGHT);
        for (int n = 0; n <= 4; n++) {
            int gy = sy + n * spectrumH / 4;
            g.fill(sx, gy, sx + sw, gy + 1, 0x664C566A);
        }
        for (int n = 0; n <= 8; n++) {
            int gx = sx + n * sw / 8;
            g.fill(gx, sy, gx + 1, sy + spectrumH, 0x664C566A);
        }
        int selectLeft = sx + (int)((0.5 - p.bandwidthKhz() / (2.0 * p.spanKhz())) * sw);
        int selectRight = sx + (int)((0.5 + p.bandwidthKhz() / (2.0 * p.spanKhz())) * sw);
        g.fill(sx + sw / 2, sy, sx + sw / 2 + 1, sy + spectrumH, 0xB9FFD343);
        for (int dash = sy; dash < sy + spectrumH; dash += 7) {
            g.fill(selectLeft, dash, selectLeft + 1, Math.min(dash + 4, sy + spectrumH), 0xB9FFD343);
            g.fill(selectRight, dash, selectRight + 1, Math.min(dash + 4, sy + spectrumH), 0xB9FFD343);
        }
        if (waterfallId != null) g.blit(waterfallId, sx, wy, sw, wh, 0f, 0f, BINS, ROWS, BINS, ROWS);
        g.fill(sx + sw / 2, wy, sx + sw / 2 + 1, wy + wh, 0x88FFD343);
        double start = p.centerKhz() - p.spanKhz() / 2.0;
        int markerY = sy + spectrumH - 9;
        for (Marker marker : MARKERS) {
            if (marker.to <= start || marker.from >= start + p.spanKhz()) continue;
            int from = sx + (int) Math.round((Math.max(start, marker.from) - start) * sw / p.spanKhz());
            int to = sx + (int) Math.round((Math.min(start + p.spanKhz(), marker.to) - start) * sw / p.spanKhz());
            g.fill(from, markerY, Math.max(from + 1, to), markerY + 2, 0xFFFF2828);
            int labelWidth = font.width(marker.label);
            if (to - from >= labelWidth + 14) {
                int tabLeft = (from + to - labelWidth - 12) / 2;
                g.fill(tabLeft, markerY - 7, tabLeft + labelWidth + 12, markerY + 7, 0xFFFF2828);
                g.drawString(font, marker.label, tabLeft + 6, markerY - 5, 0xFFFFFFFF, false);
            }
        }
        if (p.band() == 1 && !p.services().isEmpty()) {
            int lx = x + imageWidth - 192;
            g.fill(lx - 4, wy + 2, right - 14, Math.min(wy + wh, wy + 27 + Math.min(8, p.services().size()) * 13), 0xEB071321);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, compact ? "SPECTRUM" : "SPECTRUM ANALYSER", 12, 12, 0xFFF4F5F7, false);
        int rangeY = imageHeight - (compact ? 77 : 55);
        g.drawString(font, compact ? "HI" : "UPPER dBuV", compact ? 8 : 12, rangeY + 6,
                0xFFB8C9DA, false);
        g.drawString(font, compact ? "LO" : "LOWER dBuV", compact ? 82 : 172, rangeY + 6,
                0xFFB8C9DA, false);
        if (!rangeError.isEmpty()) g.drawString(font, rangeError, compact ? 8 : imageWidth - 245,
                imageHeight - (compact ? 55 : 44), 0xFFFF8C83, false);
        else if (!compact) g.drawString(font, "ARROWS: TUNE", imageWidth - 125,
                imageHeight - 44, 0xFFA5A8AC, false);
        SyncSpectrumPayload p = SpectrumAnalyserState.freshClient();
        if (p == null) {
            g.drawString(font, "Waiting for RF readings...", 54, 51, 0xFFB8C3D4, false);
            return;
        }
        String band = switch (p.band()) {
            case 0 -> "FM"; case 1 -> "DAB"; case 2 -> "DRM"; case 3 -> "VOR"; default -> "ADS-B";
        };
        if (compact) {
            g.drawString(font, String.format(Locale.ROOT, "%s %.3f MHz  S%d  BW%d",
                    band, p.centerKhz() / 1000.0, p.spanKhz(), p.bandwidthKhz()),
                    12, 28, 0xFFDAE6FF, false);
        } else {
            g.drawString(font, String.format(Locale.ROOT, "%s  %.3f MHz   SPAN %.3f MHz   BW %d kHz",
                    band, p.centerKhz() / 1000.0, p.spanKhz() / 1000.0, p.bandwidthKhz()),
                    152, 13, 0xFFDAE6FF, false);
            g.drawString(font, String.format(Locale.ROOT, "RF %.1f dBuV  NOISE %.1f dBuV",
                    p.capturedTenths() / 10.0, p.noiseTenths() / 10.0),
                    54, 31, 0xFFFFDD79, false);
            g.drawString(font, !p.audioUrl().isBlank() ? "AUDIO"
                    : p.signalPresent() ? "SIGNAL" : "STATIC",
                    imageWidth - 100, 31, 0xFFB7D7EE, false);
        }
        if (p.band() == 0) renderRds(g, p.rds());
        int spectrumH = Math.max(55, (imageHeight - (compact ? 125 : 100)) * 52 / 100);
        for (int n = 0; n <= 4; n++)
            g.drawString(font, Integer.toString((int)Math.round(plotHigh(p)
                            - n * (plotHigh(p) - plotLow(p)) / 4)),
                    11, 38 + n * spectrumH / 4, 0xFF9FA8B8, false);
        int axisY = 44 + spectrumH;
        for (int n = 0; n <= 4; n++) {
            double freq = (p.centerKhz() + (n - 2) * p.spanKhz() / 4.0) / 1000.0;
            int px = 52 + n * (imageWidth - 70) / 4;
            g.drawString(font, String.format(Locale.ROOT, "%.3f", freq), px - (n == 4 ? 32 : 0), axisY,
                    0xFFAFB7C7, false);
        }
        int waterfallY = 42 + spectrumH + 27;
        if (!p.serviceInfo().isBlank())
            g.drawString(font, font.plainSubstrByWidth(p.serviceInfo(),
                            p.band() == 1 ? imageWidth - 265 : imageWidth - 80),
                    56, waterfallY + 5, 0xFFE6F4FF, true);
        if (p.band() == 1 && !p.services().isEmpty()) {
            int lx = imageWidth - 192;
            g.drawString(font, "SERVICES (click to listen)", lx, waterfallY + 7, 0xFF95CBEF, false);
            serviceScroll = Math.clamp(serviceScroll, 0, Math.max(0, p.services().size() - 8));
            for (int i = 0; i < Math.min(8, p.services().size() - serviceScroll); i++) {
                SyncSpectrumPayload.Service service = p.services().get(i + serviceScroll);
                int color = service.selected() ? 0xFFFFDD79 : 0xFFE2F0FF;
                g.drawString(font, font.plainSubstrByWidth(service.label() + "  " + service.detail(), 168),
                        lx, waterfallY + 22 + i * 13, color, false);
            }
        }
    }

    private void renderRds(GuiGraphics g, SyncSpectrumPayload.RdsReading rds) {
        int left = 59, right = imageWidth - 17;
        g.fill(left - 4, 46, right, 91, 0xC9081420);
        int textWidth = right - left - 7;
        if (!rds.present()) {
            g.drawString(font, "RDS unavailable", left, 53, 0xFF8FA6B8, false);
            return;
        }
        if (!rds.decoded()) {
            g.drawString(font, "Searching for RDS groups", left, 53, 0xFFAAC3D0, false);
            g.drawString(font, "BER " + rds.berPercent() + "%", left, 67, 0xFF88A7BE, false);
            return;
        }
        String station = "RDS  PI " + rds.pi() + "   PS " + (rds.ps().isBlank() ? "________" : rds.ps())
                + "   PTY " + PtyNames.name(rds.pty());
        g.drawString(font, font.plainSubstrByWidth(station, textWidth), left, 52, 0xFFE7F8FF, false);
        String details = "BER " + rds.berPercent() + "%   GROUP " + rds.group() + (rds.tp() ? "   TP" : "");
        g.drawString(font, details, left, 65, 0xFF8ED8F2, false);
        String rt = rds.rt().isBlank() ? "RT decoding..." : "RT " + rds.rt();
        g.drawString(font, font.plainSubstrByWidth(rt, textWidth), left, 78, 0xFFD2E4F3, false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override public void removed() {
        super.removed();
        if (spectrumId != null) minecraft.getTextureManager().release(spectrumId);
        if (waterfallId != null) minecraft.getTextureManager().release(waterfallId);
        spectrumId = null;
        waterfallId = null;
        spectrumTexture = null;
        waterfallTexture = null;
    }
}
