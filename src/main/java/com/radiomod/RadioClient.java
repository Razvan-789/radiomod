package com.radiomod;

import com.radiomod.client.screen.DabTransmitterScreen;
import com.radiomod.client.screen.DrmTransmitterScreen;
import com.radiomod.client.screen.DrmRadioScreen;
import com.radiomod.client.screen.PortableDrmScreen;
import com.radiomod.client.screen.DrmImageCache;
import com.radiomod.client.screen.DabServerScreen;
import com.radiomod.client.screen.RecorderScreen;
import com.radiomod.client.screen.DirectionalAntennaScreen;
import com.radiomod.client.screen.RadioScreen;
import com.radiomod.client.screen.Dx3Screen;
import com.radiomod.client.screen.TechnisatScreen;
import com.radiomod.client.screen.TransmitterScreen;
import com.radiomod.client.screen.SignalMeterScreen;
import com.radiomod.client.screen.RegistryScreen;
import com.radiomod.client.screen.WebTabletScreen;
import com.radiomod.client.screen.AdsbRadarScreen;
import com.radiomod.client.screen.AdsbTransponderScreen;
import com.radiomod.client.screen.NavigatorScreen;
import com.radiomod.client.screen.VorTransmitterScreen;
import com.radiomod.menu.RegistryMenu;
import com.radiomod.menu.WebTabletMenu;
import com.radiomod.menu.AdsbRadarMenu;
import com.radiomod.menu.AdsbTransponderMenu;
import com.radiomod.menu.NavigatorMenu;
import com.radiomod.menu.VorTransmitterMenu;
import com.radiomod.client.FmAudioManager;
import com.radiomod.client.PlacedReceiverAudioManager;
import com.radiomod.item.RadioItem;
import com.radiomod.item.TechnisatRadioItem;
import com.radiomod.item.PortableDrmRadioItem;
import com.radiomod.item.NavigatorItem;
import com.radiomod.signal.PortableDrmState;
import com.radiomod.signal.TechnisatPinnedState;
import com.radiomod.network.SyncPortableDrmPayload;
import com.radiomod.network.SyncNavigatorHudPayload;
import com.radiomod.signal.NavigatorHudState;
import com.radiomod.network.SyncTechnisatReceiverHudPayload;
import com.radiomod.signal.FmReceptionState;
import com.radiomod.signal.Dx3PlayerState;
import com.radiomod.network.SyncDx3PlayerPayload;
import java.util.Locale;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import com.radiomod.menu.RadioMenu;
import com.radiomod.menu.Dx3Menu;
import com.radiomod.menu.SignalMeterMenu;
import com.radiomod.signal.SignalMeterState;
import com.radiomod.network.SyncSignalMeterPayload;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Radio.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = Radio.MODID, value = Dist.CLIENT)
public class RadioClient {
    private static Object pinnedLevel;
    public RadioClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.<RadioMenu, RadioScreen>register(
                Radio.RADIO_MENU.get(),
                (RadioMenu menu, Inventory inventory, Component title) -> new RadioScreen(menu, inventory, title)
        );
        event.<RadioMenu, RadioScreen>register(
                Radio.DIGITAL_RADIO_MENU.get(),
                (RadioMenu menu, Inventory inventory, Component title) -> new RadioScreen(menu, inventory, title, true)
        );
        event.<Dx3Menu, Dx3Screen>register(Radio.DX3_MENU.get(), Dx3Screen::new);
        event.<Dx3Menu, Dx3Screen>register(Radio.DX3_RECEIVER_MENU.get(), Dx3Screen::new);
        event.<Dx3Menu, TechnisatScreen>register(Radio.TECHNISAT_MENU.get(), TechnisatScreen::new);
        event.<Dx3Menu, TechnisatScreen>register(Radio.TECHNISAT_RECEIVER_MENU.get(), TechnisatScreen::new);
        event.<SignalMeterMenu, SignalMeterScreen>register(Radio.SIGNAL_METER_MENU.get(), SignalMeterScreen::new);
        event.<RegistryMenu, RegistryScreen>register(Radio.REGISTRY_MENU.get(), RegistryScreen::new);
        event.<WebTabletMenu, WebTabletScreen>register(Radio.WEB_TABLET_MENU.get(), WebTabletScreen::new);
        event.<WebTabletMenu, WebTabletScreen>register(Radio.WEBSERVER_MENU.get(), WebTabletScreen::new);
        event.<AdsbRadarMenu, AdsbRadarScreen>register(Radio.ADSB_RADAR_MENU.get(), AdsbRadarScreen::new);
        event.<AdsbTransponderMenu, AdsbTransponderScreen>register(Radio.ADSB_TRANSPONDER_MENU.get(), AdsbTransponderScreen::new);
        event.<NavigatorMenu, NavigatorScreen>register(Radio.NAVIGATOR_MENU.get(), NavigatorScreen::new);
        event.<VorTransmitterMenu, VorTransmitterScreen>register(Radio.VOR_TRANSMITTER_MENU.get(), VorTransmitterScreen::new);
        event.register(Radio.TRANSMITTER_MENU.get(), TransmitterScreen::new);
        event.register(Radio.DAB_TRANSMITTER_MENU.get(), DabTransmitterScreen::new);
        event.register(Radio.DRM_TRANSMITTER_MENU.get(), DrmTransmitterScreen::new);
        event.register(Radio.DRM_RADIO_MENU.get(), DrmRadioScreen::new);
        event.register(Radio.PORTABLE_DRM_MENU.get(), PortableDrmScreen::new);
        event.register(Radio.DAB_SERVER_MENU.get(), DabServerScreen::new);
        event.register(Radio.RECORDER_MENU.get(), RecorderScreen::new);
        event.register(Radio.DIRECTIONAL_ANTENNA_MENU.get(), DirectionalAntennaScreen::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pinnedLevel != minecraft.level) {
            TechnisatPinnedState.clear();
            NavigatorHudState.clear();
            pinnedLevel = minecraft.level;
        }
        if (minecraft.isPaused()) {
            return;
        }
        FmAudioManager.get().tick(minecraft);
        PlacedReceiverAudioManager.get().tick(minecraft);
    }

    @SubscribeEvent
    static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        int nextY = 8;
        if (player != null) {
            nextY = renderMeterHud(event.getGuiGraphics(), minecraft, player, nextY);
            nextY = renderNavigatorHud(event.getGuiGraphics(), minecraft, player, nextY);
            if (TechnisatPinnedState.hasPinned())
                nextY = renderTechnisatReceiverHud(event.getGuiGraphics(), minecraft, nextY);
        }
        if (player != null && PortableDrmRadioItem.isSelected(player)
                && PortableDrmRadioItem.hasInInventory(player)) {
            if (PortableDrmRadioItem.pinned(player))
                renderPortableDrmHud(event.getGuiGraphics(), minecraft, player, false, nextY);
            return;
        }
        if (player == null || !hasRadioInInventory(player)) {
            return;
        }

        if (RadioItem.isDx3Pinned(player) && RadioItem.isDx3Selected(player)) {
            if (RadioItem.isTechnisatSelected(player) && RadioItem.getMode(player) == RadioItem.MODE_DRM)
                renderPortableDrmHud(event.getGuiGraphics(), minecraft, player, true, nextY);
            else renderDx3Hud(event.getGuiGraphics(), minecraft, player, nextY);
            return;
        }
        if (!RadioItem.isHudPinned(player)) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        FmReceptionState.Snapshot snapshot = FmReceptionState.getFresh();
        int frequencyKhz = RadioItem.getFrequencyKhz(player);
        String frequencyText = String.format(Locale.ROOT, "%.2f MHz", frequencyKhz / 1000.0);
        String psText = snapshot != null && !snapshot.ps().isBlank() ? snapshot.ps() : "--------";
        String signalText = snapshot != null
                ? String.format(Locale.ROOT, "%.1f dBuV", snapshot.signalDbuv())
                : "--.- dBuV";
        String snrText = snapshot != null
                ? String.format(Locale.ROOT, "%.1f dB", snapshot.snrDb())
                : "--.- dB";
        int berPercent = snapshot != null ? snapshot.rdsBerPercent() : 100;
        String berText = "BER " + berPercent + "%";

        String line1 = "FM " + frequencyText + "  PS " + psText;
        String line2 = "SIG " + signalText + "  SNR " + snrText + "  ";
        int padding = 5;
        int lineHeight = 10;
        int line2Width = minecraft.font.width(line2) + minecraft.font.width(berText);
        int textWidth = Math.max(minecraft.font.width(line1), line2Width);
        int width = textWidth + (padding * 2);
        int height = (lineHeight * 2) + (padding * 2) + 2;
        int x = guiGraphics.guiWidth() - width - 8;
        int y = nextY;

        guiGraphics.fill(x, y, x + width, y + height, 0xC0101018);
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF0CA8E2);
        guiGraphics.drawString(minecraft.font, line1, x + padding, y + padding, 0xFFE6ECF2, false);
        guiGraphics.drawString(minecraft.font, line2, x + padding, y + padding + lineHeight + 1, 0xFFFFFF33, false);
        guiGraphics.drawString(
                minecraft.font,
                berText,
                x + padding + minecraft.font.width(line2),
                y + padding + lineHeight + 1,
                berColor(berPercent),
                false
        );
    }

    private static int renderNavigatorHud(GuiGraphics g, Minecraft minecraft, Player player, int y) {
        var navigator = NavigatorItem.pinnedInInventory(player);
        if (navigator.isEmpty()) return y;
        SyncNavigatorHudPayload state = NavigatorHudState.fresh();
        int first = NavigatorItem.frequency(navigator, 1), second = NavigatorItem.frequency(navigator, 2);
        if (state != null && (state.frequency1Khz() != first || state.frequency2Khz() != second)) state = null;
        int width = Math.min(272, g.guiWidth() - 16), x = g.guiWidth() - width - 8;
        g.fill(x, y, x + width, y + 57, 0xE4162936);
        g.fill(x, y, x + width, y + 2, 0xFF74DCF6);
        g.drawString(minecraft.font, "NAVIGATOR · VOR", x + 6, y + 5, 0xFF74DCF6, false);
        drawNavigatorHudLine(g, minecraft, x + 6, y + 20, width - 12, 1, first, state == null ? null : state.vor1());
        drawNavigatorHudLine(g, minecraft, x + 6, y + 36, width - 12, 2, second, state == null ? null : state.vor2());
        return y + 57 + 6;
    }
    private static void drawNavigatorHudLine(GuiGraphics g, Minecraft minecraft, int x, int y, int width,
                                             int channel, int khz, SyncNavigatorHudPayload.Fix fix) {
        String prefix = String.format(Locale.ROOT, "V%d %.2f  ", channel, khz / 1000.0);
        String detail = fix == null ? "Unavailable"
                : fix.identifier() + " " + String.format(Locale.ROOT, "%03d° %.0f dB", fix.radialDegrees(), fix.snrDb())
                + (fix.dme() ? String.format(Locale.ROOT, "  DME %.0f blocks", fix.dmeBlocks()) : "");
        g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(prefix + detail, width),
                x, y, fix == null ? 0xFFFFAE76 : 0xFFEAF5FF, false);
    }

    private static int renderMeterHud(GuiGraphics g, Minecraft minecraft, Player player, int y) {
        if (!SignalMeterState.hasMeter(player)) return y;
        SyncSignalMeterPayload p = SignalMeterState.freshClient();
        if (p == null || !p.pinned()) return y;
        int width = Math.min(260, g.guiWidth() - 16), x = g.guiWidth() - width - 8;
        int height = p.dab() ? 72 : 62;
        g.fill(x, y, x + width, y + height, 0xE410212A);
        g.fill(x, y, x + width, y + 2, 0xFF51C6C1);
        String tune = p.dab() ? "DAB " + com.radiomod.signal.DabChannel.byIndex(p.channelIndex()).label()
                : String.format(Locale.ROOT, "FM %.2f MHz", p.frequencyKhz() / 1000.0);
        g.drawString(minecraft.font, "METER  " + tune + (p.active() ? "" : "  NO SIGNAL"), x + 7, y + 7, 0xFF82E4DB, false);
        if (!p.active()) return y + height + 6;
        g.drawString(minecraft.font, String.format(Locale.ROOT, "SIG %.1f dBuV   SNR %.1f dB",
                p.signalTenths() / 10.0, p.snrTenths() / 10.0), x + 7, y + 23, 0xFFF1F8F8, false);
        if (p.dab()) {
            g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth("MUX " + p.mux() + "  " + p.correction(), width - 14),
                    x + 7, y + 36, 0xFFD4E8EB, false);
            g.drawString(minecraft.font, "FIC " + meterStatus(p.fic()) + "  MSC " + meterStatus(p.msc())
                    + "  FS " + meterStatus(p.fs()) + "  TII " + p.tii().size(), x + 7, y + 49, 0xFFD4E8EB, false);
        } else {
            g.drawString(minecraft.font, "RDS BER " + p.berPercent() + "%  PI "
                    + (p.pi().isBlank() ? "----" : p.pi()) + "  GRP "
                    + (p.currentGroup().isBlank() ? "--" : p.currentGroup()), x + 7, y + 37, 0xFFD4E8EB, false);
        }
        return y + height + 6;
    }

    private static int renderTechnisatReceiverHud(GuiGraphics g, Minecraft minecraft, int y) {
        SyncTechnisatReceiverHudPayload info = TechnisatPinnedState.fresh();
        int width = Math.min(270, g.guiWidth() - 16), x = g.guiWidth() - width - 8;
        g.fill(x, y, x + width, y + 64, 0xE4081530);
        g.fill(x, y, x + width, y + 2, 0xFF2873D0);
        String band = info == null ? "RADIO" : info.mode() == RadioItem.MODE_DAB ? "DAB+"
                : info.mode() == RadioItem.MODE_DRM ? "DRM" : "FM";
        g.drawString(minecraft.font, "TechniSat  " + band, x + 7, y + 7, 0xFFDCEAFF, false);
        if (info == null || !info.active()) {
            g.drawString(minecraft.font, "Signal unavailable", x + 7, y + 25, 0xFF9FB3D1, false);
            return y + 64 + 6;
        }
        String frequency = info.mode() == RadioItem.MODE_FM
                ? String.format(Locale.ROOT, "%.2f MHz", info.frequencyKhz() / 1000.0)
                : info.mode() == RadioItem.MODE_DRM ? info.frequencyKhz() + " kHz" : "DAB+";
        String title = info.label().isBlank() ? frequency : info.label();
        g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(title, width - 14),
                x + 7, y + 22, 0xFFF0F5FF, false);
        g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(info.text(), width - 14),
                x + 7, y + 37, 0xFFB8C9E4, false);
        g.drawString(minecraft.font, (info.rds() ? "RDS  " : "") + frequency
                + (info.ct() ? "  " + info.time() : ""), x + 7, y + 51, 0xFF83ABDC, false);
        return y + 64 + 6;
    }

    private static void renderPortableDrmHud(GuiGraphics g, Minecraft minecraft, Player player,
                                             boolean technisat, int y) {
        SyncPortableDrmPayload info = PortableDrmState.fresh();
        int frequency = technisat ? TechnisatRadioItem.drmFrequency(player) : PortableDrmRadioItem.frequency(player);
        boolean active = info != null && info.active() && info.frequencyKhz() == frequency;
        int width = Math.min(270, g.guiWidth() - 16), x = g.guiWidth() - width - 8;
        g.fill(x, y, x + width, y + 72, 0xE4122028);
        g.fill(x, y, x + width, y + 2, 0xFF6DDAD1);
        g.drawString(minecraft.font, (technisat ? "TechniSat DRM  " : "DRM  ")
                + com.radiomod.signal.DrmSignal.band(frequency)
                + "  " + frequency + " kHz", x + 7, y + 6, 0xFF6DDAD1, false);
        if (!active) {
            g.drawString(minecraft.font, "DRM Unavailable", x + 7, y + 28, 0xFFFF9B92, false);
            return;
        }
        int textX = info.imageUrl().isBlank() ? 7 : 48;
        if (!info.imageUrl().isBlank()) DrmImageCache.draw(g, info.imageUrl(), x + 7, y + 21, 36);
        String label = info.serviceLabel().isBlank() ? info.shortLabel() : info.serviceLabel();
        g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(label, width - textX - 7),
                x + textX, y + 24, 0xFFEAF4F8, false);
        g.drawString(minecraft.font, "Mode " + "ABCD".charAt(Math.max(0, Math.min(3, info.mode())))
                + "  Opus " + info.opusBitrateKbps() + "k", x + textX, y + 37, 0xFFA3BBC7, false);
        g.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(info.message(), width - textX - 7),
                x + textX, y + 51, 0xFFEAF4F8, false);
    }

    private static String meterStatus(int state) {
        return state == 2 ? "OK" : state == 1 ? "ERR" : "LOST";
    }

    private static void renderDx3Hud(GuiGraphics graphics, Minecraft minecraft, Player player, int y) {
        SyncDx3PlayerPayload info = Dx3PlayerState.getFresh();
        boolean dab = RadioItem.getMode(player) == RadioItem.MODE_DAB;
        boolean active = info != null && info.active();
        String mode = dab ? "DAB" : "FM";
        String name = active
                ? !info.longPs().isBlank() ? info.longPs() : info.ps().isBlank() ? mode : info.ps()
                : dab ? "DAB Unavailable" : "FM Signal Lost";
        String radioText = active ? info.rt() : "";
        int[] logo = dab && active ? Dx3PlayerState.getLogoPixels() : null;
        int width = Math.min(280, graphics.guiWidth() - 16);
        int height = 70;
        int x = graphics.guiWidth() - width - 8;
        graphics.fill(x, y, x + width, y + height, 0xDB142530);
        graphics.fill(x, y, x + width, y + 2, 0xFF70C2DC);
        graphics.drawString(minecraft.font,
                (RadioItem.isTechnisatSelected(player) ? "TechniSat  " : "DX3  ") + mode,
                x + 7, y + 6, 0xFF70C2DC, false);
        int textX = logo == null ? 7 : 46;
        if (logo != null) {
            graphics.fill(x + 6, y + 19, x + 41, y + 54, 0xFF405764);
            for (int row = 0; row < 32; row++) {
                for (int column = 0; column < 32; column++) {
                    int color = logo[row * 32 + column];
                    if ((color >>> 24) != 0) {
                        graphics.fill(x + 8 + column, y + 21 + row,
                                x + 9 + column, y + 22 + row, color);
                    }
                }
            }
        }
        graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(name, width - textX - 7),
                x + textX, y + 20, 0xFFF0F5F8, false);
        graphics.drawString(minecraft.font, dab ? "DLS" : "RT", x + textX, y + 34, 0xFF70C2DC, false);
        if (!radioText.isBlank()) {
            var lines = minecraft.font.split(Component.literal(radioText), width - textX - 7);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(minecraft.font, lines.get(line), x + textX,
                        y + 46 + line * 11, 0xFFA3BBC7, false);
            }
        } else {
            graphics.drawString(minecraft.font, "—", x + textX, y + 46, 0xFF78919D, false);
        }
        for (int bar = 0; bar < 5; bar++) {
            int barHeight = 3 + bar * 2;
            graphics.fill(x + width - 43 + bar * 7, y + 17 - barHeight,
                    x + width - 39 + bar * 7, y + 17,
                    active && bar < info.signalBars() ? 0xFF70C2DC : 0xFF405764);
        }
    }

    private static boolean hasRadioInInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).is(Radio.RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            if (inventory.offhand.get(i).is(Radio.RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(Radio.TECHNISAT_RADIO_ITEM.get())) return true;
        }
        return false;
    }

    private static int berColor(int berPercent) {
        if (berPercent <= 8) {
            return 0xFF8BC34A;
        }
        if (berPercent <= 25) {
            return 0xFFFFEB3B;
        }
        if (berPercent <= 60) {
            return 0xFFFFA726;
        }
        return 0xFFFF5252;
    }
}
