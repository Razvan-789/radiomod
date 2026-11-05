package com.radiomod.signal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared, nonblocking source for plain text, TuneIn now-playing JSON, and SSE titles. */
public final class DynamicRadioTextResolver {
    private static final long REFRESH_MS = 10_000L;
    private static final int MAX_RESPONSE_CHARS = 8_192;
    private static final Pattern TUNEIN_STATION_ID = Pattern.compile("(?i)(?:^|[^a-z0-9])s(\\d{1,10})(?:$|[^a-z0-9])");
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "radio-dynamic-text");
        thread.setDaemon(true);
        return thread;
    });

    private DynamicRadioTextResolver() { }

    public static String text(String sourceUrl) {
        String url = normalizeUrl(sourceUrl);
        if (url.isEmpty()) return "";
        Entry entry = CACHE.computeIfAbsent(url, ignored -> new Entry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            if (!entry.loading && now - entry.lastRequested >= REFRESH_MS) {
                entry.loading = true;
                entry.lastRequested = now;
                WORKERS.execute(() -> {
                    String fetched = fetch(url);
                    synchronized (entry) {
                        if (fetched != null) entry.text = fetched;
                        entry.loading = false;
                    }
                });
            }
            return entry.text;
        }
    }

    static String normalizeUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return "";
        try {
            URI uri = URI.create(sourceUrl.trim());
            String scheme = uri.getScheme(), host = uri.getHost();
            if (scheme == null || host == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                return "";
            String lowerHost = host.toLowerCase(java.util.Locale.ROOT);
            if (lowerHost.equals("tunein.com") || lowerHost.equals("www.tunein.com")) {
                Matcher id = TUNEIN_STATION_ID.matcher(uri.getPath() == null ? "" : uri.getPath());
                if (id.find()) return "https://feed.tunein.com/profiles/s" + id.group(1) + "/nowPlaying";
            }
            return uri.toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(3_000);
            connection.setReadTimeout(3_000);
            connection.setRequestProperty("Accept", "application/json, text/plain, text/event-stream");
            connection.setRequestProperty("User-Agent", "RadioMod");
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while (body.length() < MAX_RESPONSE_CHARS && (line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String title = parseEventData(line.substring(5).trim());
                        if (title != null) return title;
                    }
                    if (body.length() > 0) body.append('\n');
                    body.append(line, 0, Math.min(line.length(), MAX_RESPONSE_CHARS - body.length()));
                }
                return parseResponse(body.toString());
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String parseResponse(String body) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.startsWith("{")) {
            try {
                JsonObject data = JsonParser.parseString(trimmed).getAsJsonObject();
                JsonObject header = data.has("Header") && data.get("Header").isJsonObject()
                        ? data.getAsJsonObject("Header") : null;
                if (header != null && header.has("Subtitle") && header.get("Subtitle").isJsonPrimitive())
                    return clean(header.get("Subtitle").getAsString());
                JsonObject secondary = data.has("Secondary") && data.get("Secondary").isJsonObject()
                        ? data.getAsJsonObject("Secondary") : null;
                if (secondary != null && secondary.has("Title") && secondary.get("Title").isJsonPrimitive())
                    return clean(secondary.get("Title").getAsString());
                return "";
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        for (String line : trimmed.split("\\R")) {
            if (line.startsWith("data:")) {
                String title = parseEventData(line.substring(5).trim());
                if (title != null) return title;
            }
        }
        if (trimmed.startsWith("data:") || trimmed.startsWith("event:") || trimmed.startsWith("id:"))
            return null;
        return clean(trimmed);
    }

    private static String parseEventData(String data) {
        if (data.isBlank()) return null;
        try {
            JsonElement element = JsonParser.parseString(data);
            if (!element.isJsonObject()) return null;
            JsonObject event = element.getAsJsonObject();
            return event.has("title") && event.get("title").isJsonPrimitive()
                    ? clean(event.get("title").getAsString()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        return value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("\\s+", " ").trim();
    }

    private static final class Entry {
        private String text = "";
        private long lastRequested;
        private boolean loading;
    }
}
