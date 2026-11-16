package com.radiomod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiomod.block.RadioDisplayBlock;
import com.radiomod.blockentity.RadioDisplayBlockEntity;
import com.radiomod.blockentity.RadioDisplayBlockEntity.DisplayData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

/** Renders at most one face per three adjoining panels, including panels inside Sable plots. */
public class RadioDisplayRenderer implements BlockEntityRenderer<RadioDisplayBlockEntity> {
    private static final int WHITE = 0xFFE8F4FF, DIM = 0xFF8CA9BB, CYAN = 0xFF73D7F4;
    private static final Map<Integer, ResourceLocation> LOGOS = new LinkedHashMap<>();

    public RadioDisplayRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(RadioDisplayBlockEntity panel, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        Level level = panel.getLevel();
        if (level == null) return;
        Direction facing = panel.getBlockState().getValue(RadioDisplayBlock.FACING);
        Direction right = facing.getCounterClockWise();
        BlockPos start = panel.getBlockPos();
        for (int i = 0; i < 24; i++) {
            BlockPos previous = start.relative(right.getOpposite());
            if (!matching(level, previous, facing)) break;
            start = previous;
        }
        int offset = Math.abs(panel.getBlockPos().getX() - start.getX())
                + Math.abs(panel.getBlockPos().getZ() - start.getZ());
        if (offset % 3 != 0) return;
        int width = 1;
        while (width < 3 && matching(level, panel.getBlockPos().relative(right, width), facing)) width++;

        RadioDisplayBlockEntity source = panel;
        for (int i = 0; i < width; i++) {
            if (level.getBlockEntity(panel.getBlockPos().relative(right, i)) instanceof RadioDisplayBlockEntity other
                    && other.radioPos() != null) { source = other; break; }
        }
        DisplayData data = source.data();
        int flags = source.options();
        Font font = Minecraft.getInstance().font;
        light = LightTexture.FULL_BRIGHT;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // Minecraft's horizontal yaw puts east at 270°, while this front-facing
        // quad needs +90° to point toward east. Match the block model rotations.
        int frontYaw = switch (facing) {
            case NORTH -> 180;
            case EAST -> 90;
            case SOUTH -> 0;
            case WEST -> 270;
            default -> 0;
        };
        pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(frontYaw)));
        // Keep the screen outside the solid full-block face, with room for text.
        pose.translate(-0.5, 0.5, 0.54);
        pose.scale(0.01f, -0.01f, 0.01f);
        background(pose, buffers, width * 100, light);
        pose.translate(0, 0, 0.4);
        long clock = System.currentTimeMillis();
        if (source.radioPos() == null) {
            text(font, buffers, pose, "LINK RADIO", 8, 45, DIM, light);
        } else if (width == 1) {
            if (source.singleMode() == 0 && (flags & RadioDisplayBlockEntity.LOGO) != 0 && data.logo().length == 256)
                logo(data.logo(), pose, buffers, 18, 7, 64, light);
            else if (source.singleMode() == 2) {
                if ((flags & RadioDisplayBlockEntity.SIGNAL) != 0) {
                    text(font, buffers, pose, bars(data.bars()), 8, 30, CYAN, light);
                    text(font, buffers, pose, String.format(java.util.Locale.ROOT, "%.1f dBµV", data.signal()),
                            8, 50, WHITE, light);
                }
            } else {
                if ((flags & RadioDisplayBlockEntity.NAME) != 0)
                    text(font, buffers, pose, slide(font, data.name(), 86, clock), 7, 29, WHITE, light);
                if ((flags & RadioDisplayBlockEntity.PTY) != 0)
                    text(font, buffers, pose, slide(font, data.pty(), 86, clock), 7, 55, DIM, light);
            }
        } else if (width == 2) {
            if ((flags & RadioDisplayBlockEntity.NAME) != 0)
                text(font, buffers, pose, slide(font, data.name(), 162, clock), 8, 15, CYAN, light);
            String info = tags(data, flags);
            if ((flags & (RadioDisplayBlockEntity.TEXT | RadioDisplayBlockEntity.TAGS)) != 0)
                text(font, buffers, pose, slide(font, info, 184, clock), 8, 43, WHITE, light);
            if ((flags & RadioDisplayBlockEntity.PTY) != 0)
                text(font, buffers, pose, slide(font, data.pty(), 115, clock), 8, 74, DIM, light);
            if ((flags & RadioDisplayBlockEntity.SIGNAL) != 0)
                text(font, buffers, pose, bars(data.bars()), 132, 74, CYAN, light);
        } else {
            int left = 8;
            if ((flags & RadioDisplayBlockEntity.LOGO) != 0 && data.logo().length == 256) {
                logo(data.logo(), pose, buffers, 8, 13, 70, light);
                left = 88;
            }
            if ((flags & RadioDisplayBlockEntity.NAME) != 0)
                text(font, buffers, pose, slide(font, data.name(), 286 - left, clock), left, 13, CYAN, light);
            if ((flags & RadioDisplayBlockEntity.TEXT) != 0)
                text(font, buffers, pose, slide(font, data.text(), 286 - left, clock), left, 36, WHITE, light);
            int lowerTextWidth = (flags & RadioDisplayBlockEntity.SIGNAL) != 0 ? 208 - left : 286 - left;
            if ((flags & RadioDisplayBlockEntity.TAGS) != 0 && !data.title().isBlank()) {
                text(font, buffers, pose, slide(font, data.title(), lowerTextWidth, clock), left, 55, WHITE, light);
                text(font, buffers, pose, slide(font, data.artist(), lowerTextWidth, clock), left, 70, DIM, light);
            }
            if ((flags & RadioDisplayBlockEntity.PTY) != 0)
                text(font, buffers, pose, slide(font, data.pty(), lowerTextWidth, clock), left, 84, DIM, light);
            if ((flags & RadioDisplayBlockEntity.SIGNAL) != 0) {
                text(font, buffers, pose, bars(data.bars()), 216, 72, CYAN, light);
                text(font, buffers, pose, String.format(java.util.Locale.ROOT, "%.1f", data.signal()),
                        230, 84, WHITE, light);
            }
        }
        pose.popPose();
    }

    private static boolean matching(Level level, BlockPos pos, Direction facing) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof RadioDisplayBlock
                && level.getBlockState(pos).getValue(RadioDisplayBlock.FACING) == facing;
    }

    private static String tags(DisplayData data, int flags) {
        if ((flags & RadioDisplayBlockEntity.TAGS) != 0 && !data.title().isBlank())
            return data.title() + (data.artist().isBlank() ? "" : " — " + data.artist());
        return (flags & RadioDisplayBlockEntity.TEXT) != 0 ? data.text() : "";
    }

    private static String bars(int count) {
        return "▂▄▆▇█".substring(0, Math.max(0, Math.min(5, count)));
    }

    private static String slide(Font font, String value, int maxWidth, long clock) {
        if (value == null || value.isBlank()) return "—";
        if (font.width(value) <= maxWidth) return value;
        String loop = value + "     " + value;
        int start = (int) ((clock / 180) % (value.length() + 5));
        return font.plainSubstrByWidth(loop.substring(Math.min(start, loop.length())), maxWidth);
    }

    private static void text(Font font, MultiBufferSource buffers, PoseStack pose,
                             String value, int x, int y, int color, int light) {
        font.drawInBatch(value, x, y, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, light);
    }

    private static void logo(int[] pixels, PoseStack pose, MultiBufferSource buffers,
                             int x, int y, int size, int light) {
        int key = Arrays.hashCode(pixels);
        ResourceLocation location = LOGOS.get(key);
        if (location == null) {
            NativeImage image = new NativeImage(16, 16, false);
            for (int py = 0; py < 16; py++) for (int px = 0; px < 16; px++) {
                int argb = pixels[py * 16 + px];
                image.setPixelRGBA(px, py, (argb & 0xFF00FF00)
                        | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16));
            }
            location = Minecraft.getInstance().getTextureManager().register("radio_panel_logo", new DynamicTexture(image));
            if (LOGOS.size() >= 32) {
                ResourceLocation old = LOGOS.values().iterator().next();
                LOGOS.remove(LOGOS.keySet().iterator().next());
                Minecraft.getInstance().getTextureManager().release(old);
            }
            LOGOS.put(key, location);
        }
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(location));
        var matrix = pose.last().pose();
        vertex.addVertex(matrix, x, y + size, 0).setColor(-1).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, x + size, y + size, 0).setColor(-1).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, x + size, y, 0).setColor(-1).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, x, y, 0).setColor(-1).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }

    private static void background(PoseStack pose, MultiBufferSource buffers, int width, int light) {
        ResourceLocation texture = ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(texture));
        var matrix = pose.last().pose();
        vertex.addVertex(matrix, 4, 96, 0).setColor(0xFF18212E).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, width - 4, 96, 0).setColor(0xFF18212E).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, width - 4, 4, 0).setColor(0xFF18212E).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        vertex.addVertex(matrix, 4, 4, 0).setColor(0xFF18212E).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }
}
