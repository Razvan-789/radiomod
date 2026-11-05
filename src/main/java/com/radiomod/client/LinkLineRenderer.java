package com.radiomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiomod.Radio;
import com.radiomod.network.SyncLinkLinesPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Renders server-confirmed connections only while the linking tool is held. */
public final class LinkLineRenderer {
    private static List<SyncLinkLinesPayload.Link> links = List.of();
    private static String dimension = "";
    private static long receivedAt;

    private LinkLineRenderer() { }

    public static void receive(SyncLinkLinesPayload snapshot) {
        links = snapshot.links();
        dimension = snapshot.dimension();
        receivedAt = System.currentTimeMillis();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || links.isEmpty()) return;
        if (!minecraft.player.getMainHandItem().is(Radio.LINKING_TOOL_ITEM.get())
                && !minecraft.player.getOffhandItem().is(Radio.LINKING_TOOL_ITEM.get())) return;
        if (!minecraft.level.dimension().location().toString().equals(dimension)
                || System.currentTimeMillis() - receivedAt > 1_500) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lines());
        for (SyncLinkLinesPayload.Link link : links) {
            if (Vec3.atCenterOf(link.from()).distanceToSqr(camera) > 96 * 96
                    && Vec3.atCenterOf(link.to()).distanceToSqr(camera) > 96 * 96) continue;
            int red = link.kind() == SyncLinkLinesPayload.ANTENNA ? 55 : 255;
            int green = link.kind() == SyncLinkLinesPayload.ADSB_SERVER ? 165 : 220;
            int blue = link.kind() == SyncLinkLinesPayload.ANTENNA ? 255 : 65;
            addLine(vertices, pose, link.from(), link.to(), red, green, blue);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void addLine(VertexConsumer vertices, PoseStack pose, BlockPos from, BlockPos to,
                                int red, int green, int blue) {
        float dx = to.getX() - from.getX();
        float dy = to.getY() - from.getY();
        float dz = to.getZ() - from.getZ();
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) return;
        float nx = dx / length, ny = dy / length, nz = dz / length;
        PoseStack.Pose transform = pose.last();
        vertices.addVertex(transform, from.getX() + .5f, from.getY() + .5f, from.getZ() + .5f)
                .setColor(red, green, blue, 230).setNormal(transform, nx, ny, nz);
        vertices.addVertex(transform, to.getX() + .5f, to.getY() + .5f, to.getZ() + .5f)
                .setColor(red, green, blue, 230).setNormal(transform, nx, ny, nz);
    }
}
