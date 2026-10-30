package com.radiomod.client.screen;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;

/** Small asynchronous preview for DRM service artwork. */
public final class DrmImageCache {
    private static final Map<String, int[]> CACHE = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, int[]> entry) { return size() > 16; }
    };
    private DrmImageCache() { }
    public static void draw(GuiGraphics graphics, String url, int x, int y, int size) {
        drawPixels(graphics, url, x, y, size, size, 48, 48);
    }
    private static void drawPixels(GuiGraphics graphics, String url, int x, int y,
                                   int width, int height, int pixelWidth, int pixelHeight) {
        graphics.fill(x, y, x + width, y + height, 0xFF304754);
        if (url == null || url.isBlank()) return;
        String key = pixelWidth + "x" + pixelHeight + ":" + url;
        int[] pixels;
        synchronized (CACHE) {
            pixels = CACHE.get(key);
            if (pixels == null) {
                CACHE.put(key, new int[0]);
                Thread worker = new Thread(() -> fetch(url, key, pixelWidth, pixelHeight), "radio-drm-image");
                worker.setDaemon(true); worker.start();
                return;
            }
        }
        if (pixels.length != pixelWidth * pixelHeight) return;
        for (int py = 0; py < pixelHeight; py++) for (int px = 0; px < pixelWidth; px++) {
            int color = pixels[py * pixelWidth + px];
            if ((color >>> 24) != 0) {
                int x0 = x + px * width / pixelWidth, x1 = x + (px + 1) * width / pixelWidth;
                int y0 = y + py * height / pixelHeight, y1 = y + (py + 1) * height / pixelHeight;
                if (x1 > x0 && y1 > y0) graphics.fill(x0, y0, x1, y1, color);
            }
        }
    }
    private static void fetch(String url, String key, int width, int height) {
        int[] result = new int[0];
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return;
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getContentLengthLong() <= 2_000_000) {
                try (InputStream source = connection.getInputStream()) {
                    byte[] bytes = source.readNBytes(2_000_001);
                    if (bytes.length > 2_000_000) return;
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (image != null && image.getWidth() <= 4096 && image.getHeight() <= 4096) {
                        Image scaled = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = resized.createGraphics(); g.drawImage(scaled, 0, 0, null); g.dispose();
                        result = resized.getRGB(0, 0, width, height, null, 0, width);
                    }
                }
            }
            connection.disconnect();
        } catch (Exception ignored) { }
        finally { synchronized (CACHE) { CACHE.put(key, result); } }
    }
}
