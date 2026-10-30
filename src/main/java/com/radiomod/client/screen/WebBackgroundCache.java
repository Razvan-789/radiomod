package com.radiomod.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Async image download with a full-resolution GPU texture for tablet backgrounds. */
final class WebBackgroundCache {
    private static final int WIDTH = 1024, HEIGHT = 616;
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(8, .75f, true);
    private WebBackgroundCache() { }

    static void draw(GuiGraphics graphics, String url, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF304754);
        if (url == null || url.isBlank()) return;
        Entry entry = CACHE.get(url);
        if (entry == null) {
            entry = new Entry(CompletableFuture.supplyAsync(() -> fetch(url)));
            CACHE.put(url, entry);
            if (CACHE.size() > 4) {
                String eldest = CACHE.keySet().iterator().next();
                Entry removed = CACHE.remove(eldest);
                if (removed.texture != null) Minecraft.getInstance().getTextureManager().release(removed.texture);
            }
        }
        if (entry.texture == null && entry.pixels.isDone()) {
            int[] pixels = entry.pixels.getNow(null);
            if (pixels != null) {
                NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
                for (int py = 0; py < HEIGHT; py++) for (int px = 0; px < WIDTH; px++) {
                    int argb = pixels[py * WIDTH + px];
                    int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >>> 16) & 0xFF);
                    image.setPixelRGBA(px, py, abgr);
                }
                entry.texture = Minecraft.getInstance().getTextureManager().register("radio_web_background",
                        new DynamicTexture(image));
            }
        }
        if (entry.texture != null)
            graphics.blit(entry.texture, x, y, 0f, 0f, width, height, WIDTH, HEIGHT);
    }

    private static int[] fetch(String url) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getContentLengthLong() > 5_000_000) return null;
            BufferedImage source;
            try (InputStream stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(5_000_001);
                if (bytes.length > 5_000_000) return null;
                source = ImageIO.read(new ByteArrayInputStream(bytes));
            }
            if (source == null || source.getWidth() > 4096 || source.getHeight() > 4096) return null;
            BufferedImage target = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.max(WIDTH / (double) source.getWidth(), HEIGHT / (double) source.getHeight());
                int w = (int) Math.ceil(source.getWidth() * scale), h = (int) Math.ceil(source.getHeight() * scale);
                graphics.drawImage(source, (WIDTH - w) / 2, (HEIGHT - h) / 2, w, h, null);
            } finally { graphics.dispose(); }
            return target.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static final class Entry {
        private final CompletableFuture<int[]> pixels;
        private ResourceLocation texture;
        private Entry(CompletableFuture<int[]> pixels) { this.pixels = pixels; }
    }
}
