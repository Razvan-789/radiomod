package com.radiomod.signal;

import com.radiomod.blockentity.DabServerBlockEntity.DabStationDefinition;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads dynamic DLS endpoints off the server thread and keeps their latest text. */
public final class DabDlsResolver {
    private static final long REFRESH_MS = 5_000L;
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "radio-dab-dls");
        thread.setDaemon(true);
        return thread;
    });

    private DabDlsResolver() {
    }

    public static String text(DabStationDefinition station) {
        if (!station.dlsUrlMode()) {
            return station.dlsText();
        }
        String url = station.dlsUrl().trim();
        if (url.isEmpty()) {
            return "";
        }
        Entry entry = CACHE.computeIfAbsent(url, ignored -> new Entry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            if (!entry.loading && now - entry.lastRequested >= REFRESH_MS) {
                entry.loading = true;
                entry.lastRequested = now;
                WORKER.execute(() -> {
                    String value = fetch(url);
                    synchronized (entry) {
                        entry.text = value;
                        entry.loading = false;
                    }
                });
            }
            return entry.text;
        }
    }

    private static String fetch(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return "";
            }
            URLConnection connection = uri.toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readNBytes(1024), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class Entry {
        private String text = "";
        private long lastRequested;
        private boolean loading;
    }
}
