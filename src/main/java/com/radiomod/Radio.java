package com.radiomod;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.radiomod.block.AntennaBlock;
import net.minecraft.world.InteractionHand;
import com.radiomod.block.LoopAntennaBlock;
import com.radiomod.block.DrmTransmitterBlock;
import com.radiomod.block.DrmRadioBlock;
import com.radiomod.block.DirectionalAntennaBlock;
import com.radiomod.block.YagiAntennaBlock;
import com.radiomod.block.WebserverBlock;
import com.radiomod.block.AdsbReceiverBlock;
import com.radiomod.block.AdsbServerBlock;
import com.radiomod.block.VorTransmitterBlock;
import com.radiomod.block.DmeModuleBlock;
import com.radiomod.block.DabServerBlock;
import com.radiomod.block.DabTransmitterBlock;
import com.radiomod.block.TransmitterBlock;
import com.radiomod.block.Dx3ReceiverBlock;
import com.radiomod.block.TechnisatReceiverBlock;
import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.LoopAntennaBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.DirectionalAntennaBlockEntity;
import com.radiomod.blockentity.YagiAntennaBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import com.radiomod.blockentity.AdsbReceiverBlockEntity;
import com.radiomod.blockentity.AdsbServerBlockEntity;
import com.radiomod.blockentity.VorTransmitterBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity.DabStationDefinition;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.TechnisatReceiverBlockEntity;
import com.radiomod.menu.DabServerMenu;
import com.radiomod.menu.DrmTransmitterMenu;
import com.radiomod.menu.DrmRadioMenu;
import com.radiomod.menu.DabTransmitterMenu;
import com.radiomod.item.DigitalRadioItem;
import com.radiomod.item.Dx3RadioItem;
import com.radiomod.item.TechnisatRadioItem;
import com.radiomod.item.LinkingToolItem;
import com.radiomod.item.AdsbTransponderItem;
import com.radiomod.item.NavigatorItem;
import com.radiomod.item.RadioItem;
import com.radiomod.item.SignalMeterItem;
import com.radiomod.item.SpectrumAnalyserItem;
import com.radiomod.item.MeteorForecasterItem;
import com.radiomod.item.PortableDrmRadioItem;
import com.radiomod.menu.PortableDrmMenu;
import com.radiomod.network.SyncPortableDrmPayload;
import com.radiomod.network.RequestAudioRelayPayload;
import com.radiomod.network.StopAudioRelayPayload;
import com.radiomod.network.AudioRelayChunkPayload;
import com.radiomod.signal.PortableDrmState;
import com.radiomod.menu.SignalMeterMenu;
import com.radiomod.menu.SpectrumAnalyserMenu;
import com.radiomod.network.SyncSignalMeterPayload;
import com.radiomod.network.SyncSpectrumPayload;
import com.radiomod.signal.SignalMeterState;
import com.radiomod.signal.SpectrumAnalyserState;
import com.radiomod.menu.RadioMenu;
import com.radiomod.menu.Dx3Menu;
import com.radiomod.menu.TransmitterMenu;
import com.radiomod.network.SetDabEnsembleNamePayload;
import com.radiomod.network.SetDabServerConfigPayload;
import com.radiomod.network.SetDrmTextPayload;
import com.radiomod.network.SetFmRdsConfigPayload;
import com.radiomod.network.UploadAudioChunkPayload;
import com.radiomod.network.AudioLibraryActionPayload;
import com.radiomod.network.SyncAudioLibraryPayload;
import com.radiomod.server.AudioLibraryManager;
import com.radiomod.block.RecorderBlock;
import com.radiomod.menu.RecorderMenu;
import com.radiomod.menu.DirectionalAntennaMenu;
import com.radiomod.menu.AdsbRadarMenu;
import com.radiomod.menu.AdsbTransponderMenu;
import com.radiomod.menu.NavigatorMenu;
import com.radiomod.menu.VorTransmitterMenu;
import com.radiomod.network.SetAdsbTransponderPayload;
import com.radiomod.network.SyncAdsbRadarPayload;
import com.radiomod.network.SyncAdsbTabletPayload;
import com.radiomod.network.SyncNavigatorPayload;
import com.radiomod.network.NavigatorActionPayload;
import com.radiomod.network.SyncVorPayload;
import com.radiomod.network.SyncNavigatorHudPayload;
import com.radiomod.network.SetVorPayload;
import com.radiomod.network.SyncFmReceptionPayload;
import com.radiomod.network.SyncDx3CatalogPayload;
import com.radiomod.network.SyncDx3PlayerPayload;
import com.radiomod.network.SyncDx3LogoPayload;
import com.radiomod.network.SelectDx3StationPayload;
import com.radiomod.network.RequestDx3CatalogPayload;
import com.radiomod.network.SyncDx3PlaybackPayload;
import com.radiomod.network.SyncDx3ReceiverStatePayload;
import com.radiomod.network.SyncDx3ReceiverAudioPayload;
import com.radiomod.network.TechnisatActionPayload;
import com.radiomod.network.SyncTechnisatStatusPayload;
import com.radiomod.network.SyncTechnisatReceiverHudPayload;
import com.radiomod.network.SetDx3LinkModePayload;
import com.radiomod.network.SyncDx3TuningPayload;
import com.radiomod.network.SyncDabPlaybackPayload;
import com.radiomod.signal.Dx3Catalog;
import com.radiomod.signal.DabPlaybackState;
import com.radiomod.signal.DabSfnReception;
import com.radiomod.signal.Dx3PlaybackState;
import com.radiomod.signal.Dx3PlayerState;
import com.radiomod.signal.FmReceptionState;
import com.radiomod.signal.DabChannel;
import com.radiomod.signal.DabVersion;
import com.radiomod.signal.RdsFrameData;
import com.radiomod.signal.RdsTextDecoding;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import com.radiomod.signal.MeteorScatter;
import com.radiomod.signal.Dx3ReceiverNetwork;
import com.radiomod.signal.Dx3ReceiverCatalog;
import com.radiomod.signal.DrmReception;
import com.radiomod.signal.VorReception;
import com.radiomod.server.ServerAudioProxy;
import com.radiomod.server.AudioRelayManager;
import com.radiomod.item.RegistryItem;
import com.radiomod.item.WebTabletItem;
import com.radiomod.menu.RegistryMenu;
import com.radiomod.menu.WebTabletMenu;
import com.radiomod.network.RegistryActionPayload;
import com.radiomod.network.SyncRegistryPayload;
import com.radiomod.network.RegistryFilterPayload;
import com.radiomod.network.WebTabletActionPayload;
import com.radiomod.network.SyncWebTabletPayload;
import com.radiomod.server.AudioUploadManager;
import com.radiomod.server.TransmitterPresets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.world.level.block.entity.BlockEntityType;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Radio.MODID)
public class Radio {
    private static final Pattern AF_TOKEN_PATTERN = Pattern.compile("\\d+(?:[\\.,]\\d+)?");
    private static final int FM_BAND_MIN_KHZ = SignalMath.FM_MIN_FREQUENCY_KHZ;
    private static final int FM_BAND_MAX_KHZ = SignalMath.FM_MAX_FREQUENCY_KHZ;
    private static final double AF_SWITCH_MARGIN_DBUV = 1.5;
    private static final double MIN_DISPLAY_SIGNAL_DBUV = -5.0;
    private static final double RDS_DECODE_MIN_SIGNAL_DBUV = -10.0;
    private static final double RDS_DECODE_MIN_SNR_DB = -5.0;
    private static final int RDS_DECODE_MAX_BER_PERCENT = 94;
    private static final double DAB_RETUNE_SWITCH_MARGIN_DBUV = 1.0;

    // Define mod id in a common place for everything to reference
    public static final String MODID = "radio";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "radio" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "radio" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "radio" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<Block> TRANSMITTER_BLOCK = BLOCKS.register("transmitter",
            () -> new TransmitterBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> TRANSMITTER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("transmitter", TRANSMITTER_BLOCK);

    public static final DeferredBlock<Block> DAB_TRANSMITTER_BLOCK = BLOCKS.register("dab_transmitter",
            () -> new DabTransmitterBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DAB_TRANSMITTER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("dab_transmitter", DAB_TRANSMITTER_BLOCK);
    public static final DeferredBlock<Block> DRM_TRANSMITTER_BLOCK = BLOCKS.register("drm_transmitter",
            () -> new DrmTransmitterBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DRM_TRANSMITTER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("drm_transmitter", DRM_TRANSMITTER_BLOCK);
    public static final DeferredBlock<Block> DRM_RADIO_BLOCK = BLOCKS.register("drm_radio",
            () -> new DrmRadioBlock(BlockBehaviour.Properties.of().strength(2.5F)));
    public static final DeferredItem<BlockItem> DRM_RADIO_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("drm_radio", DRM_RADIO_BLOCK);
    public static final DeferredBlock<Block> DAB_SERVER_BLOCK = BLOCKS.register("dab_server",
            () -> new DabServerBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DAB_SERVER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("dab_server", DAB_SERVER_BLOCK);
    public static final DeferredBlock<Block> DX3_RECEIVER_BLOCK = BLOCKS.register("dx3_receiver",
            () -> new Dx3ReceiverBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DX3_RECEIVER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("dx3_receiver", DX3_RECEIVER_BLOCK);
    public static final DeferredBlock<Block> TECHNISAT_RECEIVER_BLOCK = BLOCKS.register("technisat_receiver",
            () -> new TechnisatReceiverBlock(BlockBehaviour.Properties.of().strength(2.5F)));
    public static final DeferredItem<BlockItem> TECHNISAT_RECEIVER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("technisat_receiver", TECHNISAT_RECEIVER_BLOCK);
    public static final DeferredBlock<Block> RECORDER_BLOCK = BLOCKS.register("recorder",
            () -> new RecorderBlock(BlockBehaviour.Properties.of().strength(2.5F)));
    public static final DeferredItem<BlockItem> RECORDER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("recorder", RECORDER_BLOCK);

    public static final DeferredBlock<Block> ANTENNA_BLOCK = BLOCKS.register("antenna",
            () -> new AntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion()));
    public static final DeferredItem<BlockItem> ANTENNA_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("antenna", ANTENNA_BLOCK);
    public static final DeferredBlock<Block> LOOP_ANTENNA_BLOCK = BLOCKS.register("loop_antenna",
            () -> new LoopAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion()));
    public static final DeferredItem<BlockItem> LOOP_ANTENNA_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("loop_antenna", LOOP_ANTENNA_BLOCK);
    public static final DeferredBlock<Block> DIRECTIONAL_ANTENNA_BLOCK = BLOCKS.register("directional_antenna",
            () -> new DirectionalAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion()));
    public static final DeferredItem<BlockItem> DIRECTIONAL_ANTENNA_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("directional_antenna", DIRECTIONAL_ANTENNA_BLOCK);
    public static final DeferredBlock<Block> YAGI_3_BLOCK = BLOCKS.register("yagi_3",
            () -> new YagiAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion(), 3));
    public static final DeferredBlock<Block> YAGI_5_BLOCK = BLOCKS.register("yagi_5",
            () -> new YagiAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion(), 5));
    public static final DeferredBlock<Block> YAGI_7_BLOCK = BLOCKS.register("yagi_7",
            () -> new YagiAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion(), 7));
    public static final DeferredBlock<Block> YAGI_11_BLOCK = BLOCKS.register("yagi_11",
            () -> new YagiAntennaBlock(BlockBehaviour.Properties.of().strength(1.5F).noOcclusion(), 11));
    public static final DeferredItem<BlockItem> YAGI_3_ITEM = ITEMS.registerSimpleBlockItem("yagi_3", YAGI_3_BLOCK);
    public static final DeferredItem<BlockItem> YAGI_5_ITEM = ITEMS.registerSimpleBlockItem("yagi_5", YAGI_5_BLOCK);
    public static final DeferredItem<BlockItem> YAGI_7_ITEM = ITEMS.registerSimpleBlockItem("yagi_7", YAGI_7_BLOCK);
    public static final DeferredItem<BlockItem> YAGI_11_ITEM = ITEMS.registerSimpleBlockItem("yagi_11", YAGI_11_BLOCK);
    public static final DeferredBlock<Block> WEBSERVER_BLOCK = BLOCKS.register("webserver",
            () -> new WebserverBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> WEBSERVER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("webserver", WEBSERVER_BLOCK);
    public static final DeferredBlock<Block> ADSB_RECEIVER_BLOCK = BLOCKS.register("adsb_receiver",
            () -> new AdsbReceiverBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ADSB_RECEIVER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("adsb_receiver", ADSB_RECEIVER_BLOCK);
    public static final DeferredBlock<Block> ADSB_SERVER_BLOCK = BLOCKS.register("adsb_server",
            () -> new AdsbServerBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ADSB_SERVER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("adsb_server", ADSB_SERVER_BLOCK);
    public static final DeferredBlock<Block> VOR_TRANSMITTER_BLOCK = BLOCKS.register("vor_transmitter",
            () -> new VorTransmitterBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> VOR_TRANSMITTER_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("vor_transmitter", VOR_TRANSMITTER_BLOCK);
    public static final DeferredBlock<Block> DME_MODULE_BLOCK = BLOCKS.register("dme_module",
            () -> new DmeModuleBlock(BlockBehaviour.Properties.of().strength(2.5F).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DME_MODULE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("dme_module", DME_MODULE_BLOCK);

    public static final DeferredItem<Item> RADIO_ITEM = ITEMS.register("radio", () -> new RadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DIGITAL_RADIO_ITEM = ITEMS.register("digital_radio", () -> new DigitalRadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLACK_DIGITAL_RADIO_ITEM = ITEMS.register("black_digital_radio", () -> new DigitalRadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DX3_RADIO_ITEM = ITEMS.register("dx3_radio", () -> new Dx3RadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TECHNISAT_RADIO_ITEM = ITEMS.register("technisat_radio",
            () -> new TechnisatRadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> PORTABLE_DRM_RADIO_ITEM = ITEMS.register("portable_drm_radio",
            () -> new PortableDrmRadioItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SIGNAL_METER_ITEM = ITEMS.register("signal_meter", () -> new SignalMeterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SPECTRUM_ANALYSER_ITEM = ITEMS.register("spectrum_analyser", () -> new SpectrumAnalyserItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> REGISTRY_ITEM = ITEMS.register("registry", () -> new RegistryItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WEB_TABLET_ITEM = ITEMS.register("web_tablet",
            () -> new WebTabletItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ADSB_TRANSPONDER_ITEM = ITEMS.register("adsb_transponder",
            () -> new AdsbTransponderItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> NAVIGATOR_ITEM = ITEMS.register("navigator",
            () -> new NavigatorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> METEOR_FORECASTER_ITEM = ITEMS.register("meteor_forecaster", () -> new MeteorForecasterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> LINKING_TOOL_ITEM = ITEMS.register("linking_tool", () -> new LinkingToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmitterBlockEntity>> TRANSMITTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("transmitter", () -> BlockEntityType.Builder.of(TransmitterBlockEntity::new, TRANSMITTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DabTransmitterBlockEntity>> DAB_TRANSMITTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("dab_transmitter", () -> BlockEntityType.Builder.of(DabTransmitterBlockEntity::new, DAB_TRANSMITTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrmTransmitterBlockEntity>> DRM_TRANSMITTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("drm_transmitter", () -> BlockEntityType.Builder.of(DrmTransmitterBlockEntity::new, DRM_TRANSMITTER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrmRadioBlockEntity>> DRM_RADIO_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("drm_radio", () -> BlockEntityType.Builder.of(DrmRadioBlockEntity::new, DRM_RADIO_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DabServerBlockEntity>> DAB_SERVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("dab_server", () -> BlockEntityType.Builder.of(DabServerBlockEntity::new, DAB_SERVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Dx3ReceiverBlockEntity>> DX3_RECEIVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("dx3_receiver", () -> BlockEntityType.Builder.of(Dx3ReceiverBlockEntity::new, DX3_RECEIVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TechnisatReceiverBlockEntity>> TECHNISAT_RECEIVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("technisat_receiver", () -> BlockEntityType.Builder.of(
                    TechnisatReceiverBlockEntity::new, TECHNISAT_RECEIVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AntennaBlockEntity>> ANTENNA_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("antenna", () -> BlockEntityType.Builder.of(AntennaBlockEntity::new, ANTENNA_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoopAntennaBlockEntity>> LOOP_ANTENNA_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("loop_antenna", () -> BlockEntityType.Builder.of(LoopAntennaBlockEntity::new, LOOP_ANTENNA_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DirectionalAntennaBlockEntity>> DIRECTIONAL_ANTENNA_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("directional_antenna", () -> BlockEntityType.Builder.of(
                    DirectionalAntennaBlockEntity::new, DIRECTIONAL_ANTENNA_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<YagiAntennaBlockEntity>> YAGI_ANTENNA_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("yagi_antenna", () -> BlockEntityType.Builder.of(
                    YagiAntennaBlockEntity::new, YAGI_3_BLOCK.get(), YAGI_5_BLOCK.get(),
                    YAGI_7_BLOCK.get(), YAGI_11_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WebserverBlockEntity>> WEBSERVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("webserver", () -> BlockEntityType.Builder.of(
                    WebserverBlockEntity::new, WEBSERVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdsbReceiverBlockEntity>> ADSB_RECEIVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("adsb_receiver", () -> BlockEntityType.Builder.of(
                    AdsbReceiverBlockEntity::new, ADSB_RECEIVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdsbServerBlockEntity>> ADSB_SERVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("adsb_server", () -> BlockEntityType.Builder.of(
                    AdsbServerBlockEntity::new, ADSB_SERVER_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VorTransmitterBlockEntity>> VOR_TRANSMITTER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("vor_transmitter", () -> BlockEntityType.Builder.of(
                    VorTransmitterBlockEntity::new, VOR_TRANSMITTER_BLOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<RadioMenu>> RADIO_MENU =
            MENUS.register("radio", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new RadioMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<RadioMenu>> DIGITAL_RADIO_MENU =
            MENUS.register("digital_radio", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new RadioMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<Dx3Menu>> DX3_MENU =
            MENUS.register("dx3_radio", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new Dx3Menu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<Dx3Menu>> DX3_RECEIVER_MENU =
            MENUS.register("dx3_receiver", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) ->
                    new Dx3Menu(containerId, inventory, byteBuf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<Dx3Menu>> TECHNISAT_MENU =
            MENUS.register("technisat_radio", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new Dx3Menu(id, inventory, inventory.player, true)));
    public static final DeferredHolder<MenuType<?>, MenuType<Dx3Menu>> TECHNISAT_RECEIVER_MENU =
            MENUS.register("technisat_receiver", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new Dx3Menu(id, inventory, buffer.readBlockPos(), true)));
    public static final DeferredHolder<MenuType<?>, MenuType<SignalMeterMenu>> SIGNAL_METER_MENU =
            MENUS.register("signal_meter", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new SignalMeterMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<SpectrumAnalyserMenu>> SPECTRUM_ANALYSER_MENU =
            MENUS.register("spectrum_analyser", () -> IMenuTypeExtension.create((id, inventory, buffer) -> new SpectrumAnalyserMenu(id, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<RegistryMenu>> REGISTRY_MENU =
            MENUS.register("registry", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new RegistryMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<WebTabletMenu>> WEB_TABLET_MENU =
            MENUS.register("web_tablet", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new WebTabletMenu(id, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<WebTabletMenu>> WEBSERVER_MENU =
            MENUS.register("webserver", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new WebTabletMenu(id, inventory, buffer.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<AdsbRadarMenu>> ADSB_RADAR_MENU =
            MENUS.register("adsb_receiver", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new AdsbRadarMenu(id, inventory, buffer.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<AdsbTransponderMenu>> ADSB_TRANSPONDER_MENU =
            MENUS.register("adsb_transponder", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new AdsbTransponderMenu(id, inventory,
                            buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                            buffer.readUtf(24), buffer.readUtf(32))));
    public static final DeferredHolder<MenuType<?>, MenuType<NavigatorMenu>> NAVIGATOR_MENU =
            MENUS.register("navigator", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new NavigatorMenu(id, inventory, buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)));
    public static final DeferredHolder<MenuType<?>, MenuType<VorTransmitterMenu>> VOR_TRANSMITTER_MENU =
            MENUS.register("vor_transmitter", () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new VorTransmitterMenu(id, inventory, buffer.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<TransmitterMenu>> TRANSMITTER_MENU =
            MENUS.register("transmitter", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new TransmitterMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<DabTransmitterMenu>> DAB_TRANSMITTER_MENU =
            MENUS.register("dab_transmitter", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new DabTransmitterMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<DrmTransmitterMenu>> DRM_TRANSMITTER_MENU =
            MENUS.register("drm_transmitter", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new DrmTransmitterMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<DrmRadioMenu>> DRM_RADIO_MENU =
            MENUS.register("drm_radio", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new DrmRadioMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<PortableDrmMenu>> PORTABLE_DRM_MENU =
            MENUS.register("portable_drm_radio", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new PortableDrmMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<DabServerMenu>> DAB_SERVER_MENU =
            MENUS.register("dab_server", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) -> new DabServerMenu(containerId, inventory)));
    public static final DeferredHolder<MenuType<?>, MenuType<RecorderMenu>> RECORDER_MENU =
            MENUS.register("recorder", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) ->
                    new RecorderMenu(containerId, inventory, byteBuf.readBlockPos())));
    public static final DeferredHolder<MenuType<?>, MenuType<DirectionalAntennaMenu>> DIRECTIONAL_ANTENNA_MENU =
            MENUS.register("directional_antenna", () -> IMenuTypeExtension.create((containerId, inventory, byteBuf) ->
                    new DirectionalAntennaMenu(containerId, inventory, byteBuf.readBlockPos())));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADIO_TAB = CREATIVE_MODE_TABS.register("radio_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.radio")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> RADIO_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(RADIO_ITEM.get());
                output.accept(DIGITAL_RADIO_ITEM.get());
                output.accept(BLACK_DIGITAL_RADIO_ITEM.get());
                output.accept(DX3_RADIO_ITEM.get());
                output.accept(TECHNISAT_RADIO_ITEM.get());
                output.accept(PORTABLE_DRM_RADIO_ITEM.get());
                output.accept(SIGNAL_METER_ITEM.get());
                output.accept(SPECTRUM_ANALYSER_ITEM.get());
                output.accept(REGISTRY_ITEM.get());
                output.accept(WEB_TABLET_ITEM.get());
                output.accept(ADSB_TRANSPONDER_ITEM.get());
                output.accept(NAVIGATOR_ITEM.get());
                output.accept(METEOR_FORECASTER_ITEM.get());
                output.accept(LINKING_TOOL_ITEM.get());
                output.accept(TRANSMITTER_BLOCK_ITEM.get());
                output.accept(DAB_TRANSMITTER_BLOCK_ITEM.get());
                output.accept(DRM_TRANSMITTER_BLOCK_ITEM.get());
                output.accept(DRM_RADIO_BLOCK_ITEM.get());
                output.accept(DAB_SERVER_BLOCK_ITEM.get());
                output.accept(RECORDER_BLOCK_ITEM.get());
                output.accept(DX3_RECEIVER_BLOCK_ITEM.get());
                output.accept(TECHNISAT_RECEIVER_BLOCK_ITEM.get());
                output.accept(ANTENNA_BLOCK_ITEM.get());
                output.accept(LOOP_ANTENNA_BLOCK_ITEM.get());
                output.accept(DIRECTIONAL_ANTENNA_BLOCK_ITEM.get());
                output.accept(YAGI_3_ITEM.get());
                output.accept(YAGI_5_ITEM.get());
                output.accept(YAGI_7_ITEM.get());
                output.accept(YAGI_11_ITEM.get());
                output.accept(WEBSERVER_BLOCK_ITEM.get());
                output.accept(ADSB_RECEIVER_BLOCK_ITEM.get());
                output.accept(ADSB_SERVER_BLOCK_ITEM.get());
                output.accept(VOR_TRANSMITTER_BLOCK_ITEM.get());
                output.accept(DME_MODULE_BLOCK_ITEM.get());
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Radio(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Radio) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(com.radiomod.signal.AdsbReception::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(com.radiomod.signal.AdsbReception::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(TransmitterPresets::onPlayerClone);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerPayloadHandlers);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncNavigatorHudPayload.TYPE, SyncNavigatorHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.radiomod.signal.NavigatorHudState.set(payload)));
        registrar.playToClient(SyncNavigatorPayload.TYPE, SyncNavigatorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof NavigatorMenu menu
                            && menu.containerId == payload.containerId()) menu.setSnapshot(payload);
                }));
        registrar.playToServer(NavigatorActionPayload.TYPE, NavigatorActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof NavigatorMenu menu
                            && menu.containerId == payload.containerId())
                        menu.setFrequency(player, payload.channel(), payload.frequencyKhz());
                }));
        registrar.playToClient(SyncVorPayload.TYPE, SyncVorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof VorTransmitterMenu menu
                            && menu.containerId == payload.containerId()) menu.setSnapshot(payload);
                }));
        registrar.playToServer(SetVorPayload.TYPE, SetVorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof VorTransmitterMenu menu
                            && menu.containerId == payload.containerId())
                        menu.configure(player, payload.frequencyKhz(), payload.identifier());
                }));
        registrar.playToServer(SetAdsbTransponderPayload.TYPE, SetAdsbTransponderPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof AdsbTransponderMenu menu
                            && menu.containerId == payload.containerId())
                        menu.configure(player, payload.identifier(), payload.operator());
                }));
        registrar.playToClient(SyncAdsbRadarPayload.TYPE, SyncAdsbRadarPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof AdsbRadarMenu menu
                            && menu.containerId == payload.containerId()) menu.setSnapshot(payload);
                }));
        registrar.playToClient(SyncAdsbTabletPayload.TYPE, SyncAdsbTabletPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof WebTabletMenu menu
                            && menu.containerId == payload.containerId()) menu.setAdsbSnapshot(payload);
                }));
        registrar.playToServer(WebTabletActionPayload.TYPE, WebTabletActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof WebTabletMenu menu
                            && menu.containerId == payload.containerId()) menu.action(player, payload);
                }));
        registrar.playToClient(SyncWebTabletPayload.TYPE, SyncWebTabletPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof WebTabletMenu menu
                            && menu.containerId == payload.containerId()) menu.setSnapshot(payload);
                }));
        registrar.playToServer(RegistryActionPayload.TYPE, RegistryActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof RegistryMenu menu)
                        menu.action(player, payload);
                }));
        registrar.playToServer(RegistryFilterPayload.TYPE, RegistryFilterPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof RegistryMenu menu)
                        menu.filters(player, payload);
                }));
        registrar.playToClient(SyncRegistryPayload.TYPE, SyncRegistryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof RegistryMenu menu
                            && menu.containerId == payload.containerId()) {
                        menu.setSnapshot(payload);
                        com.radiomod.client.screen.RegistryScreen.receive(payload);
                    }
                }));
        registrar.playToServer(RequestAudioRelayPayload.TYPE, RequestAudioRelayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) AudioRelayManager.start(player, payload);
                }));
        registrar.playToServer(StopAudioRelayPayload.TYPE, StopAudioRelayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) AudioRelayManager.stop(player, payload.sessionId());
                }));
        registrar.playToClient(AudioRelayChunkPayload.TYPE, AudioRelayChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.radiomod.client.AudioRelayClient.accept(payload)));
        registrar.playToClient(SyncPortableDrmPayload.TYPE, SyncPortableDrmPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    PortableDrmState.update(payload);
                    PortableDrmRadioItem.setFrequency(context.player(), payload.frequencyKhz());
                    PortableDrmRadioItem.setMuted(context.player(), payload.muted());
                    PortableDrmRadioItem.setPinned(context.player(), payload.pinned());
                }));
        registrar.playToServer(
                SetDabEnsembleNamePayload.TYPE,
                SetDabEnsembleNamePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof DabTransmitterMenu menu
                            && context.player().containerMenu.containerId == payload.containerId()) {
                        menu.setEnsembleNameFromClient(payload.ensembleName());
                    }
                })
        );
        registrar.playToServer(
                SetFmRdsConfigPayload.TYPE,
                SetFmRdsConfigPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof TransmitterMenu menu
                            && context.player().containerMenu.containerId == payload.containerId()) {
                        menu.applyRdsConfigAction(payload.action(), payload.value());
                    }
                })
        );
        registrar.playToServer(
                SetDabServerConfigPayload.TYPE,
                SetDabServerConfigPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof DabServerMenu menu
                            && context.player().containerMenu.containerId == payload.containerId()) {
                        menu.applyAction(payload.action(), payload.value());
                    }
                })
        );
        registrar.playToServer(SetDrmTextPayload.TYPE, SetDrmTextPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof DrmTransmitterMenu menu
                            && menu.containerId == payload.containerId()
                            && menu.stillValid(context.player())) {
                        menu.setText(payload.field(), payload.value());
                    }
                }));
        registrar.playToServer(
                UploadAudioChunkPayload.TYPE,
                UploadAudioChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AudioUploadManager.accept(player, payload);
                    }
                })
        );
        registrar.playToServer(AudioLibraryActionPayload.TYPE, AudioLibraryActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AudioLibraryManager.handle(player, payload);
                    }
                }));
        registrar.playToClient(SyncAudioLibraryPayload.TYPE, SyncAudioLibraryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.radiomod.client.screen.AudioLibraryScreen.receive(payload)));
        registrar.playToServer(
                SelectDx3StationPayload.TYPE,
                SelectDx3StationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.selectStation(payload);
                    }
                })
        );
        registrar.playToServer(TechnisatActionPayload.TYPE, TechnisatActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu && menu.isTechnisat()
                            && menu.containerId == payload.containerId()) menu.technisatAction(payload.action());
                }));
        registrar.playToClient(SyncTechnisatStatusPayload.TYPE, SyncTechnisatStatusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu && menu.isTechnisat()
                            && menu.containerId == payload.containerId()) {
                        menu.setTechnisatStatus(payload);
                        if (!menu.isReceiver()) {
                            Player player = context.player();
                            RadioItem.setTechnisatSelected(player, true);
                            RadioItem.setDx3Selected(player, true);
                            RadioItem.setMode(player, payload.mode());
                            if (payload.drm()) TechnisatRadioItem.setDrmFrequency(player, payload.frequencyKhz());
                            else RadioItem.setFrequencyKhz(player, payload.frequencyKhz());
                            RadioItem.setDabChannelIndex(player, payload.channelIndex());
                            RadioItem.setMuted(player, payload.muted());
                        }
                    }
                }));
        registrar.playToClient(SyncTechnisatReceiverHudPayload.TYPE,
                SyncTechnisatReceiverHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.radiomod.signal.TechnisatPinnedState.update(payload)));
        registrar.playToServer(
                SetDx3LinkModePayload.TYPE,
                SetDx3LinkModePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.setLinkMode(payload.mode());
                    }
                })
        );
        registrar.playToServer(
                RequestDx3CatalogPayload.TYPE,
                RequestDx3CatalogPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.requestCatalog();
                    }
                })
        );
        registrar.playToClient(
                SyncDx3CatalogPayload.TYPE,
                SyncDx3CatalogPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.setCatalog(payload.stations());
                    }
                })
        );
        registrar.playToClient(
                SyncDx3PlayerPayload.TYPE,
                SyncDx3PlayerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player().containerMenu instanceof Dx3Menu menu) || !menu.isReceiver())
                        Dx3PlayerState.update(payload);
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.setPlayerInfo(payload);
                    }
                })
        );
        registrar.playToClient(
                SyncDx3LogoPayload.TYPE,
                SyncDx3LogoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player().containerMenu instanceof Dx3Menu menu) || !menu.isReceiver())
                        Dx3PlayerState.updateLogo(payload.pixels());
                    if (context.player().containerMenu instanceof Dx3Menu menu
                            && menu.containerId == payload.containerId()) {
                        menu.setPlayerLogo(payload.pixels());
                    }
                })
        );
        registrar.playToClient(
                SyncDx3PlaybackPayload.TYPE,
                SyncDx3PlaybackPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> Dx3PlaybackState.update(
                        payload.channelIndex(), payload.sid(), payload.mux(), payload.streamUrl(), payload.sourceUrl(),
                        payload.signalTimes10(), payload.snrTimes10()))
        );
        registrar.playToClient(
                SyncDx3TuningPayload.TYPE,
                SyncDx3TuningPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Player player = context.player();
                    if (RadioItem.isDx3Selected(player)) {
                        RadioItem.setMode(player, payload.mode());
                        RadioItem.setFrequencyKhz(player, payload.frequencyKhz());
                        RadioItem.setDabChannelIndex(player, payload.channelIndex());
                        RadioItem.setDabStation(player, payload.mux(), payload.sid());
                        RadioItem.setDx3FmPi(player, payload.pi());
                        RadioItem.setDx3LinkId(player, payload.linkId());
                        RadioItem.setDx3LinkMode(player, payload.linkMode());
                    }
                })
        );
        registrar.playToClient(
                SyncDabPlaybackPayload.TYPE,
                SyncDabPlaybackPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> DabPlaybackState.update(
                        payload.channelIndex(), payload.streamUrl(), payload.sourceUrl(),
                        payload.signalTimes10(), payload.snrTimes10()))
        );
        registrar.playToClient(
                SyncFmReceptionPayload.TYPE,
                SyncFmReceptionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        FmReceptionState.update(
                                payload.active(),
                                payload.signalDbuvTimes10(),
                                payload.snrDbTimes10(),
                                payload.rdsBerPercent(),
                                payload.streamUrl(),
                                payload.ps()))
        );
        registrar.playToClient(SyncDx3ReceiverStatePayload.TYPE, SyncDx3ReceiverStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof Dx3Menu menu && menu.isReceiver()
                            && menu.containerId == payload.containerId()) menu.setReceiverState(payload);
                }));
        registrar.playToClient(SyncDx3ReceiverAudioPayload.TYPE, SyncDx3ReceiverAudioPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.radiomod.client.PlacedReceiverAudioManager.get().update(payload)));
        registrar.playToClient(SyncSignalMeterPayload.TYPE, SyncSignalMeterPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SignalMeterState.updateClient(payload)));
        registrar.playToClient(SyncSpectrumPayload.TYPE, SyncSpectrumPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SpectrumAnalyserState.updateClient(payload)));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(TRANSMITTER_BLOCK_ITEM);
            event.accept(DAB_TRANSMITTER_BLOCK_ITEM);
            event.accept(DAB_SERVER_BLOCK_ITEM);
            event.accept(DX3_RECEIVER_BLOCK_ITEM);
            event.accept(ANTENNA_BLOCK_ITEM);
            event.accept(YAGI_3_ITEM);
            event.accept(YAGI_5_ITEM);
            event.accept(YAGI_7_ITEM);
            event.accept(YAGI_11_ITEM);
            event.accept(WEBSERVER_BLOCK_ITEM);
            event.accept(ADSB_RECEIVER_BLOCK_ITEM);
            event.accept(ADSB_SERVER_BLOCK_ITEM);
        }

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RADIO_ITEM);
            event.accept(DIGITAL_RADIO_ITEM);
            event.accept(BLACK_DIGITAL_RADIO_ITEM);
            event.accept(DX3_RADIO_ITEM);
            event.accept(SIGNAL_METER_ITEM);
            event.accept(SPECTRUM_ANALYSER_ITEM);
            event.accept(LINKING_TOOL_ITEM);
            event.accept(ADSB_TRANSPONDER_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting radio systems.");
        ServerAudioProxy.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AudioRelayManager.stopAll();
        AudioUploadManager.clear();
        ServerAudioProxy.stop();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("meteorscatter").executes(context -> {
            String status = MeteorScatter.status(context.getSource().getLevel());
            context.getSource().sendSuccess(() -> Component.literal(status), false);
            return 1;
        }).then(Commands.literal("rate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("meteors_per_hour", IntegerArgumentType.integer(0, 1200))
                        .executes(context -> {
                            int rate = IntegerArgumentType.getInteger(context, "meteors_per_hour");
                            if (rate != 0 && rate < MeteorScatter.MIN_RATE) {
                                context.getSource().sendFailure(Component.literal("Use 0 for random or 20–1200 meteors per real hour."));
                                return 0;
                            }
                            MeteorScatter.setRate(context.getSource().getLevel(), rate);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Meteor rate set to " + (rate == 0 ? "random" : rate + " per real hour")
                                            + " in this dimension."), true);
                            return 1;
                        }))));
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (RadioItem.isTechnisatSelected(player) && !TechnisatRadioItem.hasInInventory(player))
            RadioItem.setTechnisatSelected(player, false);
        if ((player.tickCount % 5) != 0) {
            return;
        }
        AudioUploadManager.prune(serverPlayer);
        if (player.tickCount % 10 == 0) {
            ItemStack navigator = NavigatorItem.pinnedInInventory(serverPlayer);
            if (!navigator.isEmpty()) {
                int first = NavigatorItem.frequency(navigator, 1);
                int second = NavigatorItem.frequency(navigator, 2);
                PacketDistributor.sendToPlayer(serverPlayer, SyncNavigatorHudPayload.sample(first, second,
                        VorReception.receive(serverPlayer.serverLevel(), serverPlayer.position(), first),
                        VorReception.receive(serverPlayer.serverLevel(), serverPlayer.position(), second)));
            }
        }

        if (player.tickCount % 20 == 0) {
            for (BlockPos pos : Dx3ReceiverNetwork.get(player.level())) {
                if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > 32.0 * 32.0) continue;
                if (player.level().getBlockEntity(pos) instanceof Dx3ReceiverBlockEntity receiver) {
                    if (!(receiver instanceof TechnisatReceiverBlockEntity)) Dx3ReceiverCatalog.autoLink(receiver);
                    if (receiver instanceof TechnisatReceiverBlockEntity technisat
                            && technisat.getRadioMode() == RadioItem.MODE_DRM) {
                        DrmReception.Sample sample = DrmReception.sampleReceiver(technisat);
                        String source = sample.decoded() && !receiver.isMuted()
                                ? sample.transmitter().getStreamUrl() : "";
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3ReceiverAudioPayload(pos,
                                ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(source,
                                        sample.decoded() ? sample.transmitter().getOpusBitrateKbps() : 32),
                                source, false));
                        String label = sample.decoded() ? sample.transmitter().getServiceLabel() : "";
                        String message = sample.decoded() ? sample.transmitter().getTextMessage() : "";
                        int bars = sample.decoded() ? Math.max(0, Math.min(5,
                                (int) ((sample.signalDbuv() - 5) / 12) + 1)) : 0;
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncTechnisatReceiverHudPayload(pos,
                                RadioItem.MODE_DRM, technisat.getDrmFrequencyKhz(), sample.decoded(),
                                Dx3Catalog.limit(label, 96), Dx3Catalog.limit(message, 256),
                                bars, false, false, ""));
                    } else {
                        Dx3ReceiverCatalog.Sample sample = Dx3ReceiverCatalog.sample(receiver);
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3ReceiverAudioPayload(pos,
                                receiver.isMuted() ? "" : sample.url(),
                                receiver.isMuted() ? "" : sample.sourceUrl(), receiver.isDab()));
                        if (receiver instanceof TechnisatReceiverBlockEntity technisat) {
                            SyncDx3PlayerPayload info = sample.info();
                            int frequency = technisat.getRadioMode() == RadioItem.MODE_DAB
                                    ? DabChannel.byIndex(technisat.getChannelIndex()).frequencyKhz()
                                    : technisat.getFrequencyKhz();
                            PacketDistributor.sendToPlayer(serverPlayer, new SyncTechnisatReceiverHudPayload(pos,
                                    technisat.getRadioMode(), frequency, info.active(), info.ps(), info.rt(),
                                    info.signalBars(), info.rds(), info.ct(), info.time()));
                        }
                    }
                } else if (player.level().getBlockEntity(pos) instanceof DrmRadioBlockEntity receiver) {
                    DrmReception.Sample sample = DrmReception.sample(receiver);
                    String source = sample.decoded() && !receiver.isMuted() ? sample.transmitter().getStreamUrl() : "";
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3ReceiverAudioPayload(pos,
                            ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(source,
                                    sample.decoded() ? sample.transmitter().getOpusBitrateKbps() : 32), source, false));
                }
            }
        }

        if (SignalMeterState.hasMeter(player)
                && (player.containerMenu instanceof SignalMeterMenu || SignalMeterState.settings(player).pinned)) {
            PacketDistributor.sendToPlayer(serverPlayer, SignalMeterState.sample(player));
        }
        if (SpectrumAnalyserState.hasAnalyser(player)
                && (player.containerMenu instanceof SpectrumAnalyserMenu
                    || SpectrumAnalyserState.settings(player).band == 0 && player.tickCount % 10 == 0))
            PacketDistributor.sendToPlayer(serverPlayer, SpectrumAnalyserState.sample(player));

        if (PortableDrmRadioItem.isSelected(player) && PortableDrmRadioItem.hasInInventory(player)) {
            if (player.tickCount % 20 == 0) {
                int frequency = PortableDrmRadioItem.frequency(player);
                DrmReception.Sample drm = DrmReception.samplePortable(player, frequency);
                DrmTransmitterBlockEntity tx = drm.transmitter();
                boolean active = tx != null;
                String source = active && !PortableDrmRadioItem.muted(player) ? tx.getStreamUrl() : "";
                PacketDistributor.sendToPlayer(serverPlayer, new SyncPortableDrmPayload(frequency, active,
                        PortableDrmRadioItem.muted(player), PortableDrmRadioItem.pinned(player),
                        active ? tx.getShortLabel() : "", active ? tx.getServiceLabel() : "",
                        active ? tx.getTextMessage() : "", active ? tx.getImageUrl() : "",
                        active ? tx.getMode() : 0, active ? tx.getBandwidthKhz() : 0,
                        active ? tx.getOpusBitrateKbps() : 0, active ? tx.getMp3BitrateKbps() : 0,
                        active ? (int) Math.round(drm.signalDbuv() * 10) : 0,
                        active ? (int) Math.round(drm.snrDb() * 10) : 0,
                        active ? tx.getPowerW() : 0,
                        ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(source,
                                active ? tx.getOpusBitrateKbps() : 32), source));
            }
            return;
        }

        if (!hasRadioInInventory(player)) {
            RdsTextDecoding.clearPlayer(player);
            RadioItem.clearDecodedAfState(player);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncFmReceptionPayload(false, 0, 0, 100, "", "--------"));
            return;
        }

        boolean dx3Active = hasDx3InInventory(player) && RadioItem.isDx3Selected(player);
        if (!dx3Active || RadioItem.getMode(player) == RadioItem.MODE_DRM) {
            RdsTextDecoding.clearPlayer(player);
        }
        if (dx3Active && RadioItem.isTechnisatSelected(player)
                && RadioItem.getMode(player) == RadioItem.MODE_DRM && player.tickCount % 20 == 0) {
            int frequency = TechnisatRadioItem.drmFrequency(player);
            DrmReception.Sample drm = DrmReception.samplePortable(player, frequency);
            var tx = drm.transmitter();
            boolean active = tx != null;
            String source = active && !RadioItem.isMuted(player) ? tx.getStreamUrl() : "";
            PacketDistributor.sendToPlayer(serverPlayer, new SyncPortableDrmPayload(frequency, active,
                    RadioItem.isMuted(player), RadioItem.isDx3Pinned(player),
                    active ? tx.getShortLabel() : "", active ? tx.getServiceLabel() : "",
                    active ? tx.getTextMessage() : "", active ? tx.getImageUrl() : "",
                    active ? tx.getMode() : 0, active ? tx.getBandwidthKhz() : 0,
                    active ? tx.getOpusBitrateKbps() : 0, active ? tx.getMp3BitrateKbps() : 0,
                    active ? (int) Math.round(drm.signalDbuv() * 10) : 0,
                    active ? (int) Math.round(drm.snrDb() * 10) : 0,
                    active ? tx.getPowerW() : 0,
                    ServerAudioProxy.buildOpusEquivalentMp3ProxyToken(source,
                            active ? tx.getOpusBitrateKbps() : 32), source));
        }
        if (dx3Active) {
            if (!RadioItem.isTechnisatSelected(player)) Dx3Catalog.autoLink(player);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3TuningPayload(
                    RadioItem.getMode(player), RadioItem.getFrequencyKhz(player),
                    RadioItem.getDabChannelIndex(player), RadioItem.getDabStationSid(player),
                    RadioItem.getDabStationMux(player), RadioItem.getDx3FmPi(player),
                    RadioItem.getDx3LinkId(player), RadioItem.getDx3LinkMode(player)));
        }
        boolean dx3Dab = dx3Active
                && RadioItem.getMode(player) == RadioItem.MODE_DAB
                && !RadioItem.getDabStationSid(player).isBlank();
        if (dx3Dab) {
            Dx3Catalog.DabPlayback playback = Dx3Catalog.findDabPlayback(player);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3PlaybackPayload(
                    RadioItem.getDabChannelIndex(player), RadioItem.getDabStationSid(player),
                    RadioItem.getDabStationMux(player), playback.streamUrl(), playback.sourceUrl(),
                    (int) Math.round(playback.signalDbuv() * 10.0),
                    (int) Math.round(playback.snrDb() * 10.0)));
        }
        if (dx3Active && RadioItem.getMode(player) != RadioItem.MODE_DRM && player.tickCount % 20 == 0) {
            Dx3Catalog.PlayerInfo info = Dx3Catalog.playerInfo(player);
            PacketDistributor.sendToPlayer(serverPlayer, new SyncDx3PlayerPayload(0, info.active(),
                    info.ps(), info.longPs(), info.rt(), info.pty(), info.bars(), false, "",
                    info.bitrateKbps(), info.codec(), info.rds()));
        }

        FmSyncSample sample = findBestFmForPlayer(player.level(), player.position(), player);
        if (!dx3Dab && !RadioItem.isTechnisatSelected(player)) {
            autoRetuneDabForPlayer(player);
        }
        if (RadioItem.getMode(player) == RadioItem.MODE_DAB && !dx3Dab
                && !RadioItem.isTechnisatSelected(player)) {
            int channelIndex = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
            DabSample dab = findPlayableDabSample(player,
                    DabChannel.byIndex(channelIndex).frequencyKhz());
            boolean available = dab != null;
            String sourceUrl = available ? dab.streamUrl() : "";
            PacketDistributor.sendToPlayer(serverPlayer, new SyncDabPlaybackPayload(channelIndex,
                    available ? ServerAudioProxy.buildDabProxyToken(sourceUrl,
                            dab.dabVersion() == DabVersion.DAB_PLUS, dab.bitrateKbps()) : "",
                    sourceUrl, available ? (int) Math.round(dab.signalDbuv() * 10.0) : 0,
                    available ? (int) Math.round(dab.snrDb() * 10.0) : 0));
        }
        if (sample == null) {
            PacketDistributor.sendToPlayer(serverPlayer, new SyncFmReceptionPayload(false, 0, 0, 100, "", "--------"));
            return;
        }

        PacketDistributor.sendToPlayer(
                serverPlayer,
                new SyncFmReceptionPayload(
                        !sample.streamUrl().isBlank(),
                        (int) Math.round(displaySignalDbuv(sample.signalDbuv()) * 10.0),
                        (int) Math.round(displaySnrDb(sample.signalDbuv(), sample.snrDb()) * 10.0),
                        sample.rdsBerPercent(),
                        ServerAudioProxy.buildProxyToken(sample.streamUrl()),
                        sample.ps()
                )
        );
    }

    private static boolean hasRadioInInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).is(RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.items.get(i).is(TECHNISAT_RADIO_ITEM.get())) return true;
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            if (inventory.offhand.get(i).is(RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(BLACK_DIGITAL_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(DX3_RADIO_ITEM.get())) {
                return true;
            }
            if (inventory.offhand.get(i).is(TECHNISAT_RADIO_ITEM.get())) return true;
        }
        return false;
    }

    private static boolean hasDx3InInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.is(DX3_RADIO_ITEM.get()) || stack.is(TECHNISAT_RADIO_ITEM.get())) {
                return true;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (stack.is(DX3_RADIO_ITEM.get()) || stack.is(TECHNISAT_RADIO_ITEM.get())) {
                return true;
            }
        }
        return false;
    }

    private static FmSyncSample findBestFmForPlayer(Level level, Vec3 radioPosition, Player player) {
        int tunedFrequencyKhz = SignalMath.clampFmFrequencyKhz(RadioItem.getFrequencyKhz(player));
        boolean underwater = player.isUnderWater();
        FmStationSample tunedSample = findStrongestFmSample(level, radioPosition, tunedFrequencyKhz, underwater);
        if (tunedSample == null) {
            RadioItem.clearDecodedAfState(player);
            return null;
        }

        maybeUpdateDecodedAfState(player, tunedFrequencyKhz, tunedSample);
        FmStationSample bestSample = tunedSample;
        int resolvedFrequencyKhz = tunedFrequencyKhz;
        if (RadioItem.isAfEnabled(player) && RadioItem.hasDecodedAfStateForFrequency(player, tunedFrequencyKhz)) {
            int afFrequencyKhz = selectAlternativeFrequency(
                    level,
                    radioPosition,
                    normalizePiCode(RadioItem.getDecodedAfPi(player)),
                    parseAlternativeFrequenciesKhz(RadioItem.getDecodedAfList(player)),
                    tunedSample.signalDbuv(),
                    tunedFrequencyKhz,
                    underwater
            );
            if (afFrequencyKhz != tunedFrequencyKhz) {
                FmStationSample afSample = findStrongestFmSample(level, radioPosition, afFrequencyKhz, underwater);
                if (afSample != null) {
                    bestSample = afSample;
                    resolvedFrequencyKhz = afFrequencyKhz;
                }
            }
        }

        if (resolvedFrequencyKhz != tunedFrequencyKhz) {
            RadioItem.clearDecodedAfState(player);
            RadioItem.setFrequencyKhz(player, resolvedFrequencyKhz);
        }

        int berPercent = 100;
        String ps = "--------";
        if (bestSample.transmitter() != null && bestSample.transmitter().isRdsEnabled()) {
            berPercent = calculateRdsBerPercent(bestSample.signalDbuv(), bestSample.snrDb(), true);
            ps = resolveDynamicPs(bestSample.transmitter(), berPercent, level.getGameTime(), level.dayTime());
        }
        return new FmSyncSample(bestSample.signalDbuv(), bestSample.snrDb(), bestSample.streamUrl().trim(), berPercent, ps);
    }

    private static int selectAlternativeFrequency(
            Level level,
            Vec3 radioPosition,
            String decodedPiCode,
            Set<Integer> afFrequencies,
            double currentSignalDbuv,
            int tunedFrequencyKhz,
            boolean underwater
    ) {
        if (decodedPiCode.isEmpty() || afFrequencies.isEmpty()) {
            return tunedFrequencyKhz;
        }

        int bestFrequencyKhz = tunedFrequencyKhz;
        double bestSignalDbuv = currentSignalDbuv;
        for (int afFrequencyKhz : afFrequencies) {
            if (afFrequencyKhz == tunedFrequencyKhz) {
                continue;
            }
            FmStationSample candidate = findStrongestFmSample(level, radioPosition, afFrequencyKhz, underwater);
            if (candidate == null || candidate.transmitter() == null) {
                continue;
            }
            if (candidate.transmitter().getFrequencyKhz() != afFrequencyKhz) {
                continue;
            }
            if (!decodedPiCode.equals(normalizePiCode(candidate.transmitter().getRdsPi()))) {
                continue;
            }
            if (candidate.signalDbuv() > bestSignalDbuv + AF_SWITCH_MARGIN_DBUV) {
                bestSignalDbuv = candidate.signalDbuv();
                bestFrequencyKhz = afFrequencyKhz;
            }
        }
        return bestFrequencyKhz;
    }

    private static FmStationSample findStrongestFmSample(Level level, Vec3 radioPosition,
                                                         int tunedFrequencyKhz, boolean underwater) {
        int frequencyKhz = SignalMath.clampFmFrequencyKhz(tunedFrequencyKhz);
        double noiseFloorDbuv = SignalMath.calculateNoiseFloorDbuv(radioPosition);
        int overheadBlockCount = SignalMath.countOverheadBlocks(level, radioPosition);
        FmStationSample bestWithStream = null;
        FmStationSample bestAny = null;

        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            if (!(level.getBlockEntity(pos) instanceof TransmitterBlockEntity transmitter)) {
                continue;
            }
            String streamUrl = transmitter.getStreamUrl();
            boolean hasStream = streamUrl != null && !streamUrl.trim().isBlank();
            double upperBoundDbuv = SignalMath.calculateSignalUpperBoundDbuv(
                    transmitter.getPowerW(),
                    transmitter.getFrequencyKhz(),
                    frequencyKhz,
                    transmitter.hasAntennaConnected(),
                    transmitter.isDirectionalEnabled(),
                    transmitter.getDirectionDegrees(),
                    Vec3.atCenterOf(pos),
                    radioPosition,
                    overheadBlockCount
            );
            upperBoundDbuv += transmitter.antennaGainToward(radioPosition);
            upperBoundDbuv = MeteorScatter.signal(level, transmitter, radioPosition,
                    frequencyKhz, upperBoundDbuv, underwater);
            double bestAnySignal = bestAny == null ? Double.NEGATIVE_INFINITY : bestAny.signalDbuv();
            double bestWithStreamSignal = bestWithStream == null ? Double.NEGATIVE_INFINITY : bestWithStream.signalDbuv();
            if (!hasStream && upperBoundDbuv <= bestAnySignal) {
                continue;
            }
            if (hasStream && upperBoundDbuv <= bestAnySignal && upperBoundDbuv <= bestWithStreamSignal) {
                continue;
            }
            double signalDbuv = SignalMath.calculateSignalDbuv(
                    transmitter.getPowerW(),
                    transmitter.getFrequencyKhz(),
                    frequencyKhz,
                    transmitter.hasAntennaConnected(),
                    transmitter.isDirectionalEnabled(),
                    transmitter.getDirectionDegrees(),
                    Vec3.atCenterOf(pos),
                    radioPosition,
                    overheadBlockCount,
                    underwater
            );
            signalDbuv += transmitter.antennaGainToward(radioPosition);
            signalDbuv = MeteorScatter.signal(level, transmitter, radioPosition, frequencyKhz, signalDbuv, underwater);
            double snrDb = SignalMath.calculateSnrDb(signalDbuv, noiseFloorDbuv);
            FmStationSample sample = new FmStationSample(signalDbuv, snrDb, streamUrl == null ? "" : streamUrl.trim(), transmitter);

            if (bestAny == null || signalDbuv > bestAny.signalDbuv()) {
                bestAny = sample;
            }
            if (!sample.streamUrl().isBlank() && (bestWithStream == null || signalDbuv > bestWithStream.signalDbuv())) {
                bestWithStream = sample;
            }
        }

        return bestWithStream != null ? bestWithStream : bestAny;
    }

    private static Set<Integer> parseAlternativeFrequenciesKhz(String afField) {
        if (afField == null || afField.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<Integer> parsed = new LinkedHashSet<>();
        Matcher matcher = AF_TOKEN_PATTERN.matcher(afField);
        while (matcher.find()) {
            Integer khz = parseAlternativeFrequencyToken(matcher.group());
            if (khz != null) {
                parsed.add(khz);
            }
        }
        return parsed;
    }

    private static Integer parseAlternativeFrequencyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim();
        try {
            if (normalized.indexOf('.') >= 0 || normalized.indexOf(',') >= 0) {
                double mhz = Double.parseDouble(normalized.replace(',', '.'));
                int khz = (int) Math.round(mhz * 1000.0);
                return isFmBandFrequency(khz) ? khz : null;
            }

            int raw = Integer.parseInt(normalized);
            int khz;
            if (raw >= FM_BAND_MIN_KHZ && raw <= FM_BAND_MAX_KHZ) {
                khz = raw;
            } else if (raw >= 650 && raw <= 1080) {
                khz = raw * 100;
            } else if (raw >= 65 && raw <= 108) {
                khz = raw * 1000;
            } else {
                return null;
            }
            return isFmBandFrequency(khz) ? khz : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isFmBandFrequency(int frequencyKhz) {
        return frequencyKhz >= FM_BAND_MIN_KHZ && frequencyKhz <= FM_BAND_MAX_KHZ;
    }

    private static String normalizePiCode(String piCode) {
        if (piCode == null) {
            return "";
        }
        return piCode.trim().toUpperCase(Locale.ROOT);
    }

    private static void maybeUpdateDecodedAfState(Player player, int tunedFrequencyKhz, FmStationSample tunedSample) {
        TransmitterBlockEntity transmitter = tunedSample.transmitter();
        if (transmitter == null || !transmitter.isRdsEnabled()) {
            return;
        }
        int berPercent = calculateRdsBerPercent(tunedSample.signalDbuv(), tunedSample.snrDb(), true);
        boolean canDecodeRds = tunedSample.signalDbuv() > RDS_DECODE_MIN_SIGNAL_DBUV
                && tunedSample.snrDb() > RDS_DECODE_MIN_SNR_DB
                && berPercent <= RDS_DECODE_MAX_BER_PERCENT;
        if (!canDecodeRds) {
            return;
        }
        String piCode = normalizePiCode(transmitter.getRdsPi());
        String afList = transmitter.getRdsAf();
        if (piCode.isEmpty() || parseAlternativeFrequenciesKhz(afList).isEmpty()) {
            return;
        }
        RadioItem.setDecodedAfState(player, tunedFrequencyKhz, piCode, afList);
    }

    private static int calculateRdsBerPercent(double signalDbuv, double snrDb, boolean rdsEnabled) {
        if (!rdsEnabled) {
            return 100;
        }
        double quality = (signalDbuv * 0.45) + (snrDb * 0.55);
        double referenceQuality = 9.11;
        double ber = 99.0 * Math.exp(-0.191 * (quality - referenceQuality));
        return (int) Math.round(Math.max(0.0, Math.min(99.0, ber)));
    }

    private static double displaySignalDbuv(double signalDbuv) {
        return Math.max(signalDbuv, MIN_DISPLAY_SIGNAL_DBUV);
    }

    private static double displaySnrDb(double signalDbuv, double snrDb) {
        return snrDb;
    }

    private static String resolveDynamicPs(TransmitterBlockEntity transmitter, int berPercent, long gameTime, long dayTime) {
        if (!transmitter.isRdsEnabled()) {
            return "--------";
        }
        String ctString = Long.toString(dayTime % 24_000L);
        RdsFrameData frame = transmitter.getCurrentRdsFrame(berPercent, gameTime, ctString);
        if (frame == null || frame.ps().isBlank()) {
            return "--------";
        }
        return frame.ps();
    }

    private static void autoRetuneDabForPlayer(Player player) {
        int tunedIndex = DabChannel.clampIndex(RadioItem.getDabChannelIndex(player));
        DabChannel tunedChannel = DabChannel.byIndex(tunedIndex);
        DabSample tuned = findStrongestDabSample(player, tunedChannel.frequencyKhz());
        if (tuned == null) {
            return;
        }
        if (tuned.mscState() > 0) {
            return;
        }

        String currentMuxName = tuned.muxName();
        String currentSid = tuned.primarySid();
        DabSample best = tuned;
        int bestChannelIndex = tunedIndex;
        int overheadBlockCount = SignalMath.countOverheadBlocks(player.level(), player.position());
        for (DabSfnReception.Group group : DabSfnReception.scan(player, overheadBlockCount, 0)) {
            DabServerBlockEntity.DabEnsembleDefinition linked = group.ensemble();
            boolean sameMux = !currentMuxName.isBlank() && linked.name().equals(currentMuxName);
            boolean sameSid = false;
            if (currentSid != null && !currentSid.isBlank()) {
                for (DabServerBlockEntity.DabStationDefinition station : linked.stations()) {
                    if (station.sid().equalsIgnoreCase(currentSid)) {
                        sameSid = true;
                        break;
                    }
                }
            }
            if (!sameMux && !sameSid) {
                continue;
            }

            if (group.signalDbuv() > best.signalDbuv() + DAB_RETUNE_SWITCH_MARGIN_DBUV) {
                best = dabSample(group);
                bestChannelIndex = DabChannel.nearestIndexForFrequencyKhz(group.frequencyKhz());
            }
        }

        if (bestChannelIndex != tunedIndex) {
            RadioItem.setDabChannelIndex(player, bestChannelIndex);
        }
    }

    private static DabSample findStrongestDabSample(Player player, int channelFrequencyKhz) {
        int overheadBlockCount = SignalMath.countOverheadBlocks(player.level(), player.position());
        DabSample best = null;
        for (DabSfnReception.Group group : DabSfnReception.scan(
                player, overheadBlockCount, channelFrequencyKhz)) {
            DabSample candidate = dabSample(group);
            if (best == null || candidate.signalDbuv() > best.signalDbuv()) {
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static DabSample findPlayableDabSample(Player player, int channelFrequencyKhz) {
        int overheadBlockCount = SignalMath.countOverheadBlocks(player.level(), player.position());
        DabSample best = null;
        for (DabSfnReception.Group group : DabSfnReception.scan(
                player, overheadBlockCount, channelFrequencyKhz)) {
            DabSample candidate = dabSample(group);
            if (candidate.mscState() > 0 && !candidate.streamUrl().isBlank()
                    && (best == null || candidate.signalDbuv() > best.signalDbuv())) {
                best = candidate;
            }
        }
        return best;
    }

    private static DabSample dabSample(DabSfnReception.Group group) {
        DabTransmitterBlockEntity transmitter = group.transmitter();
        DabServerBlockEntity.DabEnsembleDefinition linked = group.ensemble();
        DabStationDefinition station = preferredStation(linked);
        String streamUrl = station == null ? "" : station.streamUrl();
        return new DabSample(group.signalDbuv(), group.snrDb(), linked.name(),
                station == null ? "" : station.sid(), station == null ? "" : station.label(), streamUrl,
                station == null ? 0 : station.bitrateKbps(), transmitter.getDabVersion(),
                calculateDabState(group.signalDbuv(), group.snrDb(), transmitter.getErrorCorrection(), 0.8), transmitter);
    }

    @Nullable
    private static DabStationDefinition preferredStation(DabServerBlockEntity.DabEnsembleDefinition ensemble) {
        if (ensemble.stations().isEmpty()) {
            return null;
        }
        for (DabStationDefinition station : ensemble.stations()) {
            if (!station.streamUrl().isBlank()) {
                return station;
            }
        }
        return ensemble.stations().get(0);
    }

    private static int calculateDabState(double signalDbuv, double snrDb,
                                         com.radiomod.signal.DabErrorCorrection ec, double bias) {
        return ec.decodeState(signalDbuv, snrDb, bias);
    }

    private record FmStationSample(double signalDbuv, double snrDb, String streamUrl, TransmitterBlockEntity transmitter) {
    }

    private record FmSyncSample(double signalDbuv, double snrDb, String streamUrl, int rdsBerPercent, String ps) {
    }

    private record DabSample(
            double signalDbuv,
            double snrDb,
            String muxName,
            String primarySid,
            String stationLabel,
            String streamUrl,
            int bitrateKbps,
            DabVersion dabVersion,
            int mscState,
            DabTransmitterBlockEntity transmitter
    ) {
    }
}
