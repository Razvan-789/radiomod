package com.radiomod.item;

import com.radiomod.blockentity.AntennaBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.blockentity.Dx3ReceiverBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.DrmRadioBlockEntity;
import com.radiomod.blockentity.LoopAntennaBlockEntity;
import com.radiomod.blockentity.YagiAntennaBlockEntity;
import com.radiomod.blockentity.WebserverBlockEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LinkingToolItem extends Item {
    private static final Map<UUID, BlockPos> SELECTED_ANTENNA_POSITIONS = new HashMap<>();
    private static final Map<UUID, GlobalPos> SELECTED_DAB_SERVERS = new HashMap<>();

    public LinkingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (blockEntity instanceof DabServerBlockEntity server) {
            if (server.getEnsembleCount() == 0) {
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.no_mux")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            SELECTED_DAB_SERVERS.put(player.getUUID(), GlobalPos.of(context.getLevel().dimension(),
                    context.getClickedPos().immutable()));
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.radio.linking_tool.server_selected",
                    context.getClickedPos().toShortString()).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.CONSUME;
        }
        if (blockEntity instanceof AntennaBlockEntity) {
            SELECTED_ANTENNA_POSITIONS.put(player.getUUID(), context.getClickedPos().immutable());
            SELECTED_DAB_SERVERS.remove(player.getUUID());
            player.displayClientMessage(
                    Component.translatable("message.radio.linking_tool.antenna_selected", context.getClickedPos().toShortString())
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof Dx3ReceiverBlockEntity receiver) {
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null) {
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.select_antenna_first")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (!(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)) {
                SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.antenna_missing")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (antenna instanceof LoopAntennaBlockEntity) {
                player.displayClientMessage(Component.literal("Loop antennas are for DRM radios only").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            BlockPos oldAntennaPos = receiver.getAntennaPos();
            if (oldAntennaPos != null && context.getLevel().getBlockEntity(oldAntennaPos) instanceof AntennaBlockEntity oldAntenna)
                oldAntenna.setLinkedTransmitterPos(null);
            BlockPos oldEndpoint = antenna.getLinkedTransmitterPos();
            if (oldEndpoint != null) clearAntennaFromAnyTransmitter(context, oldEndpoint);
            receiver.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(receiver.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.radio.linking_tool.linked",
                    antennaPos.toShortString(), receiver.getBlockPos().toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof WebserverBlockEntity webserver) {
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null || !(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)
                    || antenna instanceof LoopAntennaBlockEntity) {
                player.displayClientMessage(Component.literal("Select a regular, directional, or Yagi antenna first")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (webserver.antennaPos() != null
                    && context.getLevel().getBlockEntity(webserver.antennaPos()) instanceof AntennaBlockEntity old)
                old.setLinkedTransmitterPos(null);
            if (antenna.getLinkedTransmitterPos() != null)
                clearAntennaFromAnyTransmitter(context, antenna.getLinkedTransmitterPos());
            webserver.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(webserver.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.radio.linking_tool.linked",
                    antennaPos.toShortString(), webserver.getBlockPos().toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof TransmitterBlockEntity transmitter) {
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null) {
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.select_antenna_first").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }

            if (!(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)) {
                SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.antenna_missing").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (antenna instanceof LoopAntennaBlockEntity || antenna instanceof YagiAntennaBlockEntity) {
                player.displayClientMessage(Component.literal("This antenna is receive only").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }

            BlockPos oldAntennaPos = transmitter.getAntennaPos();
            if (oldAntennaPos != null && context.getLevel().getBlockEntity(oldAntennaPos) instanceof AntennaBlockEntity oldAntenna) {
                oldAntenna.setLinkedTransmitterPos(null);
            }

            BlockPos oldTransmitterPos = antenna.getLinkedTransmitterPos();
            if (oldTransmitterPos != null) {
                clearAntennaFromAnyTransmitter(context, oldTransmitterPos);
            }

            transmitter.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(transmitter.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());

            player.displayClientMessage(
                    Component.translatable("message.radio.linking_tool.linked",
                                    antennaPos.toShortString(), transmitter.getBlockPos().toShortString())
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof DabTransmitterBlockEntity transmitter) {
            GlobalPos selectedServer = SELECTED_DAB_SERVERS.get(player.getUUID());
            if (selectedServer != null) {
                if (!selectedServer.dimension().equals(context.getLevel().dimension())
                        || !(context.getLevel().getBlockEntity(selectedServer.pos()) instanceof DabServerBlockEntity server)
                        || server.getEnsembleCount() == 0) {
                    SELECTED_DAB_SERVERS.remove(player.getUUID());
                    player.displayClientMessage(Component.translatable("message.radio.linking_tool.server_missing")
                            .withStyle(ChatFormatting.RED), true);
                    return InteractionResult.CONSUME;
                }
                transmitter.linkToServer(selectedServer.pos());
                SELECTED_DAB_SERVERS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.mux_linked",
                        transmitter.getLinkedMuxName()).withStyle(ChatFormatting.GREEN), true);
                return InteractionResult.CONSUME;
            }
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null) {
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.select_antenna_first").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }

            if (!(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)) {
                SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
                player.displayClientMessage(Component.translatable("message.radio.linking_tool.antenna_missing").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (antenna instanceof LoopAntennaBlockEntity || antenna instanceof YagiAntennaBlockEntity) {
                player.displayClientMessage(Component.literal("This antenna is receive only").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }

            BlockPos oldAntennaPos = transmitter.getAntennaPos();
            if (oldAntennaPos != null && context.getLevel().getBlockEntity(oldAntennaPos) instanceof AntennaBlockEntity oldAntenna) {
                oldAntenna.setLinkedTransmitterPos(null);
            }

            BlockPos oldTransmitterPos = antenna.getLinkedTransmitterPos();
            if (oldTransmitterPos != null) {
                clearAntennaFromAnyTransmitter(context, oldTransmitterPos);
            }

            transmitter.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(transmitter.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());

            player.displayClientMessage(
                    Component.translatable("message.radio.linking_tool.linked",
                                    antennaPos.toShortString(), transmitter.getBlockPos().toShortString())
                            .withStyle(ChatFormatting.GREEN),
                    true
            );
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof DrmTransmitterBlockEntity transmitter) {
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null || !(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)
                    || antenna instanceof LoopAntennaBlockEntity || antenna instanceof YagiAntennaBlockEntity) {
                player.displayClientMessage(Component.literal("Select a normal or directional antenna first")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (transmitter.getAntennaPos() != null
                    && context.getLevel().getBlockEntity(transmitter.getAntennaPos()) instanceof AntennaBlockEntity old)
                old.setLinkedTransmitterPos(null);
            if (antenna.getLinkedTransmitterPos() != null)
                clearAntennaFromAnyTransmitter(context, antenna.getLinkedTransmitterPos());
            transmitter.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(transmitter.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.radio.linking_tool.linked",
                    antennaPos.toShortString(), transmitter.getBlockPos().toShortString()).withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }

        if (blockEntity instanceof DrmRadioBlockEntity receiver) {
            BlockPos antennaPos = SELECTED_ANTENNA_POSITIONS.get(player.getUUID());
            if (antennaPos == null || !(context.getLevel().getBlockEntity(antennaPos) instanceof AntennaBlockEntity antenna)
                    || !(antenna instanceof LoopAntennaBlockEntity || antenna instanceof YagiAntennaBlockEntity)) {
                player.displayClientMessage(Component.literal("Select a loop or Yagi antenna first").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            if (receiver.getAntennaPos() != null
                    && context.getLevel().getBlockEntity(receiver.getAntennaPos()) instanceof AntennaBlockEntity old)
                old.setLinkedTransmitterPos(null);
            if (antenna.getLinkedTransmitterPos() != null)
                clearAntennaFromAnyTransmitter(context, antenna.getLinkedTransmitterPos());
            receiver.setAntennaPos(antennaPos);
            antenna.setLinkedTransmitterPos(receiver.getBlockPos());
            SELECTED_ANTENNA_POSITIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.radio.linking_tool.linked",
                    antennaPos.toShortString(), receiver.getBlockPos().toShortString()).withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static void clearAntennaFromAnyTransmitter(UseOnContext context, BlockPos transmitterPos) {
        BlockEntity be = context.getLevel().getBlockEntity(transmitterPos);
        if (be instanceof TransmitterBlockEntity fmTransmitter) {
            fmTransmitter.setAntennaPos(null);
        } else if (be instanceof DabTransmitterBlockEntity dabTransmitter) {
            dabTransmitter.setAntennaPos(null);
        } else if (be instanceof Dx3ReceiverBlockEntity receiver) {
            receiver.setAntennaPos(null);
        } else if (be instanceof DrmTransmitterBlockEntity transmitter) {
            transmitter.setAntennaPos(null);
        } else if (be instanceof DrmRadioBlockEntity receiver) {
            receiver.setAntennaPos(null);
        } else if (be instanceof WebserverBlockEntity webserver) {
            webserver.setAntennaPos(null);
        }
    }
}
