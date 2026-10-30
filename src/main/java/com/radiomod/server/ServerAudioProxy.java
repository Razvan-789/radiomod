package com.radiomod.server;

import com.radiomod.Radio;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ServerAudioProxy {
    private static final int BASE_PORT = 28080;
    private static final int MAX_PORT_SCAN = 30;
    private static final String PATH_PREFIX = "/fm/";
    private static final String DAB_PREFIX = "/dab/";
    private static final int PROXY_OUTPUT_SAMPLE_RATE_HZ = 44_100;
    private static final long MAX_UPLOADED_DURATION_SECONDS = 2 * 60 * 60;
    private static final long MAX_OWNER_STORAGE_BYTES = 512L * 1024 * 1024;
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Pattern AUDIO_STREAM_PATTERN = Pattern.compile("(?m)^\\s*Stream #\\d+:\\d+[^\\n]*: Audio:");
    private static final int[] MP3_BITRATES_KBPS = {
            32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    };
    private static volatile HttpServer server;
    private static volatile int boundPort = -1;
    private static volatile String ffmpegExecutable = "";
    private static volatile Path uploadedAudioDirectory;
    private static final ConcurrentHashMap<Path, Double> UPLOADED_DURATIONS = new ConcurrentHashMap<>();

    private ServerAudioProxy() {
    }

    public static synchronized void start(MinecraftServer minecraftServer) {
        if (server != null) {
            return;
        }
        ffmpegExecutable = resolveFfmpegExecutable();
        uploadedAudioDirectory = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("radio_uploads");
        if (ffmpegExecutable.isBlank()) {
            Radio.LOGGER.warn("Server audio proxy could not find a usable ffmpeg executable; proxy requests may fail.");
        } else {
            Radio.LOGGER.info("Server audio proxy using ffmpeg executable: {}", ffmpegExecutable);
        }
        IOException last = null;
        for (int offset = 0; offset <= MAX_PORT_SCAN; offset++) {
            int candidatePort = BASE_PORT + offset;
            try {
                HttpServer created = HttpServer.create(new InetSocketAddress(candidatePort), 0);
                created.createContext(PATH_PREFIX, new FmProxyHandler());
                created.createContext(DAB_PREFIX, new FmProxyHandler());
                created.setExecutor(Executors.newCachedThreadPool(task -> {
                    Thread thread = new Thread(task, "radio-audio-proxy");
                    thread.setDaemon(true);
                    return thread;
                }));
                created.start();
                server = created;
                boundPort = candidatePort;
                Radio.LOGGER.info("Started server audio proxy on port {}", boundPort);
                return;
            } catch (IOException ex) {
                last = ex;
            }
        }
        if (last != null) {
            Radio.LOGGER.error("Failed to start server audio proxy.", last);
        }
    }

    public static synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
        boundPort = -1;
        ffmpegExecutable = "";
        uploadedAudioDirectory = null;
        UPLOADED_DURATIONS.clear();
        Radio.LOGGER.info("Stopped server audio proxy.");
    }

    public static String buildProxyToken(String sourceStreamUrl) {
        return buildProxyToken(sourceStreamUrl, "fm");
    }

    public static String buildDabProxyToken(String sourceStreamUrl, boolean dabPlus, int bitrateKbps) {
        return buildProxyToken(sourceStreamUrl, "dab/mp3/" + dabMp3BitrateKbps(dabPlus, bitrateKbps));
    }

    public static String buildOpusEquivalentMp3ProxyToken(String sourceStreamUrl, int opusBitrateKbps) {
        return buildDabProxyToken(sourceStreamUrl, true, opusBitrateKbps);
    }

    /** DAB+ bitrates describe Opus quality; MP3 needs roughly twice the rate. */
    public static int dabMp3BitrateKbps(boolean dabPlus, int bitrateKbps) {
        int target = Math.max(8, Math.min(512, bitrateKbps)) * (dabPlus ? 2 : 1);
        for (int valid : MP3_BITRATES_KBPS) {
            if (valid >= target) {
                return valid;
            }
        }
        return MP3_BITRATES_KBPS[MP3_BITRATES_KBPS.length - 1];
    }

    private static String buildProxyToken(String sourceStreamUrl, String format) {
        if (sourceStreamUrl == null || sourceStreamUrl.isBlank()) {
            return "";
        }
        if (sourceStreamUrl.startsWith("radio-file://") && (boundPort <= 0 || ffmpegExecutable.isBlank())) {
            return "";
        }
        if (boundPort <= 0 || ffmpegExecutable.isBlank()) {
            return sourceStreamUrl;
        }
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sourceStreamUrl.getBytes(StandardCharsets.UTF_8));
        // Uploaded files are relayed through the Minecraft connection so listeners do not
        // need direct access to the server's separate audio-proxy TCP port.
        String scheme = sourceStreamUrl.startsWith("radio-file://") ? "radio-relay://" : "radio-proxy://";
        return scheme + boundPort + "/" + format + "/" + encoded;
    }

    /** Resolve only an existing uploaded file to this server's local transcoding endpoint. */
    public static String localUploadedRelayUrl(String token) {
        int port = boundPort;
        if (port <= 0 || ffmpegExecutable.isBlank() || token == null) return "";
        String prefix = "radio-relay://" + port + "/";
        if (!token.startsWith(prefix)) return "";
        String path = token.substring(prefix.length());
        String encoded;
        if (path.startsWith("fm/")) {
            encoded = path.substring(3);
        } else if (path.startsWith("dab/mp3/")) {
            int slash = path.indexOf('/', 8);
            if (slash < 0) return "";
            try {
                int bitrate = Integer.parseInt(path.substring(8, slash));
                if (java.util.Arrays.binarySearch(MP3_BITRATES_KBPS, bitrate) < 0) return "";
            } catch (NumberFormatException ex) {
                return "";
            }
            encoded = path.substring(slash + 1);
        } else {
            return "";
        }
        if (encoded.isBlank() || encoded.contains("/")) return "";
        try {
            String source = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            if (resolveUploadedAudio(source) == null) return "";
        } catch (IllegalArgumentException ex) {
            return "";
        }
        return "http://127.0.0.1:" + port + "/" + path;
    }

    public static boolean canPlayUploadedAudio() {
        return boundPort > 0 && !ffmpegExecutable.isBlank() && uploadedAudioDirectory != null;
    }

    public static String restartUploadedAudioClock(String sourceUrl) {
        if (sourceUrl == null || !sourceUrl.startsWith("radio-file://")) {
            return sourceUrl;
        }
        int query = sourceUrl.indexOf('?');
        String base = query >= 0 ? sourceUrl.substring(0, query) : sourceUrl;
        return base + "?start=" + System.currentTimeMillis();
    }

    public static Path createTemporaryUpload(UUID owner) throws IOException {
        Path root = uploadedAudioDirectory;
        if (root == null) {
            throw new IOException("Audio proxy is not running");
        }
        Path ownerDirectory = root.resolve(owner.toString());
        Files.createDirectories(ownerDirectory);
        return Files.createTempFile(ownerDirectory, "upload-", ".tmp");
    }

    public static String finishUploadedAudio(UUID owner, Path temporary, long expectedBytes) throws IOException {
        return finishUploadedAudio(owner, temporary, expectedBytes, "");
    }

    public static String finishUploadedAudio(UUID owner, Path temporary, long expectedBytes, String name) throws IOException {
        Path root = uploadedAudioDirectory;
        if (root == null) {
            throw new IOException("Audio proxy is not running");
        }
        Path ownerDirectory = root.resolve(owner.toString());
        if (!temporary.getParent().equals(ownerDirectory) || Files.size(temporary) != expectedBytes) {
            throw new IOException("Incomplete audio upload");
        }
        double durationSeconds = validateAudioDuration(temporary);
        UUID fileId = contentId(temporary);
        Path file = ownerDirectory.resolve(fileId + ".audio");
        synchronized (ServerAudioProxy.class) {
            if (uploadedAudioDirectory != root) {
                throw new IOException("Audio proxy stopped during upload");
            }
            if (Files.isRegularFile(file)) {
                UPLOADED_DURATIONS.put(file, durationSeconds);
                saveAudioName(ownerDirectory, fileId, name, false);
                return "radio-file://" + owner + "/" + fileId;
            }
            long storedBytes;
            try (var files = Files.list(ownerDirectory)) {
                storedBytes = files.filter(path -> path.getFileName().toString().endsWith(".audio"))
                        .filter(Files::isRegularFile).mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException ignored) {
                        return 0L;
                    }
                }).sum();
            }
            if (storedBytes + expectedBytes > MAX_OWNER_STORAGE_BYTES) {
                throw new IOException("Your uploaded audio storage is full (512 MiB)");
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            UPLOADED_DURATIONS.put(file, durationSeconds);
            saveAudioName(ownerDirectory, fileId, name, false);
        }
        return "radio-file://" + owner + "/" + fileId;
    }

    public record StoredAudio(UUID id, String name, long bytes) {}

    public static synchronized List<StoredAudio> listUploadedAudio(UUID owner) throws IOException {
        Path directory = ownerDirectory(owner);
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        Properties names = readAudioNames(directory);
        List<StoredAudio> result = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String filename = file.getFileName().toString();
                if (!filename.matches("[0-9a-fA-F-]{36}\\.audio")) {
                    continue;
                }
                try {
                    UUID id = UUID.fromString(filename.substring(0, 36));
                    String name = names.getProperty(id.toString(), "Audio " + id.toString().substring(0, 8));
                    result.add(new StoredAudio(id, name, Files.size(file)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        result.sort(Comparator.comparing(StoredAudio::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.id().toString()));
        return result;
    }

    public static synchronized boolean ownsUploadedAudio(UUID owner, UUID id) {
        Path directory = ownerDirectory(owner);
        return directory != null && Files.isRegularFile(directory.resolve(id + ".audio"));
    }

    public static synchronized void renameUploadedAudio(UUID owner, UUID id, String name) throws IOException {
        if (!ownsUploadedAudio(owner, id)) {
            throw new IOException("Audio file was not found");
        }
        saveAudioName(ownerDirectory(owner), id, name, true);
    }

    public static synchronized void deleteUploadedAudio(UUID owner, UUID id) throws IOException {
        if (!ownsUploadedAudio(owner, id)) {
            throw new IOException("Audio file was not found");
        }
        Path directory = ownerDirectory(owner);
        Path file = directory.resolve(id + ".audio");
        Files.delete(file);
        UPLOADED_DURATIONS.remove(file);
        Properties names = readAudioNames(directory);
        names.remove(id.toString());
        writeAudioNames(directory, names);
    }

    private static Path ownerDirectory(UUID owner) {
        Path root = uploadedAudioDirectory;
        return root == null ? null : root.resolve(owner.toString());
    }

    private static Properties readAudioNames(Path directory) throws IOException {
        Properties names = new Properties();
        Path file = directory.resolve("names.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                names.load(input);
            }
        }
        return names;
    }

    private static void saveAudioName(Path directory, UUID id, String name, boolean replace) throws IOException {
        String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (clean.isEmpty()) {
            clean = "Audio " + id.toString().substring(0, 8);
        }
        clean = clean.substring(0, Math.min(80, clean.length()));
        Properties names = readAudioNames(directory);
        if (replace || !names.containsKey(id.toString())) {
            names.setProperty(id.toString(), clean);
            writeAudioNames(directory, names);
        }
    }

    private static void writeAudioNames(Path directory, Properties names) throws IOException {
        Path temporary = Files.createTempFile(directory, "names-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                names.store(output, "Radio+ uploaded audio names");
            }
            Files.move(temporary, directory.resolve("names.properties"), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static double validateAudioDuration(Path file) throws IOException {
        String executable = ffmpegExecutable;
        if (executable.isBlank()) {
            throw new IOException("FFmpeg is unavailable");
        }
        Path log = Files.createTempFile(file.getParent(), "probe-", ".txt");
        try {
            Process process = new ProcessBuilder(executable, "-hide_banner", "-i", file.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("Audio duration check timed out");
                }
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Audio duration check interrupted", ex);
            }
            String output = Files.readString(log);
            Matcher duration = DURATION_PATTERN.matcher(output);
            if (!duration.find() || !AUDIO_STREAM_PATTERN.matcher(output).find()) {
                throw new IOException("Could not read an audio track and duration from this file");
            }
            double seconds;
            try {
                seconds = Long.parseLong(duration.group(1)) * 3600.0
                        + Long.parseLong(duration.group(2)) * 60.0
                        + Double.parseDouble(duration.group(3));
            } catch (NumberFormatException ex) {
                throw new IOException("Audio duration is invalid", ex);
            }
            if (!Double.isFinite(seconds) || seconds <= 0) {
                throw new IOException("Audio duration is invalid");
            }
            if (seconds > MAX_UPLOADED_DURATION_SECONDS) {
                throw new IOException("Audio file is longer than 2 hours");
            }
            return seconds;
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static double uploadedDuration(Path file) throws IOException {
        Double cached = UPLOADED_DURATIONS.get(file);
        if (cached != null) {
            return cached;
        }
        double duration = validateAudioDuration(file);
        UPLOADED_DURATIONS.put(file, duration);
        return duration;
    }

    private static double playbackOffsetSeconds(String sourceUrl, Path file) throws IOException {
        long start = Files.getLastModifiedTime(file).toMillis();
        String query = URI.create(sourceUrl).getRawQuery();
        if (query != null && query.startsWith("start=")) {
            try {
                long parsed = Long.parseLong(query.substring(6));
                if (parsed > 0 && parsed <= System.currentTimeMillis()) {
                    start = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(0.0, (System.currentTimeMillis() - start) / 1000.0) % uploadedDuration(file);
    }

    private static UUID contentId(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            ByteBuffer bits = ByteBuffer.wrap(hash);
            return new UUID(bits.getLong(), bits.getLong());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable", ex);
        }
    }

    private static Path resolveUploadedAudio(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            if (!"radio-file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            UUID owner = UUID.fromString(uri.getHost());
            String path = uri.getPath();
            if (path == null || !path.matches("/[0-9a-fA-F-]{36}")) {
                return null;
            }
            UUID fileId = UUID.fromString(path.substring(1));
            Path root = uploadedAudioDirectory;
            if (root == null) {
                return null;
            }
            Path file = root.resolve(owner.toString()).resolve(fileId + ".audio");
            return Files.isRegularFile(file) ? file : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String resolveFfmpegExecutable() {
        List<String> candidates = new ArrayList<>();
        String configured = System.getProperty("radio.ffmpeg.path", "").trim();
        if (!configured.isBlank()) {
            candidates.add(configured);
        }
        String envConfigured = System.getenv("RADIO_FFMPEG_PATH");
        if (envConfigured != null && !envConfigured.isBlank()) {
            candidates.add(envConfigured.trim());
        }
        candidates.add("ffmpeg");
        candidates.add("ffmpeg.exe");

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null && !userProfile.isBlank()) {
                candidates.add(userProfile + "\\scoop\\shims\\ffmpeg.exe");
            }
            candidates.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe");
            candidates.add("C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe");
        } else if (os.contains("mac")) {
            candidates.add("/opt/homebrew/bin/ffmpeg");
            candidates.add("/usr/local/bin/ffmpeg");
            candidates.add("/usr/bin/ffmpeg");
        } else {
            candidates.add("/usr/bin/ffmpeg");
            candidates.add("/usr/local/bin/ffmpeg");
            candidates.add("/snap/bin/ffmpeg");
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null && !pathEnv.isBlank()) {
            String[] entries = pathEnv.split(File.pathSeparator);
            for (String entry : entries) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                String suffix = os.contains("win") ? "ffmpeg.exe" : "ffmpeg";
                candidates.add(Path.of(entry, suffix).toString());
            }
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (looksLikePath(candidate) && !Files.exists(Path.of(candidate))) {
                continue;
            }
            if (canExecute(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static boolean looksLikePath(String value) {
        return value.contains("/") || value.contains("\\");
    }

    private static boolean canExecute(String executable) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-version")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static final class FmProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null) {
                send(exchange, 404, "Not found");
                return;
            }
            boolean dabMp3 = path.startsWith("/dab/mp3/");
            String prefix = dabMp3 ? "/dab/mp3/" : PATH_PREFIX;
            if (!path.startsWith(prefix) || path.length() <= prefix.length() || ffmpegExecutable.isBlank()) {
                send(exchange, 404, "Not found");
                return;
            }
            int bitrateKbps = 128;
            String encoded = path.substring(prefix.length());
            if (dabMp3) {
                int separator = encoded.indexOf('/');
                if (separator <= 0 || separator == encoded.length() - 1) {
                    send(exchange, 400, "Invalid DAB bitrate");
                    return;
                }
                try {
                    bitrateKbps = Integer.parseInt(encoded.substring(0, separator));
                } catch (NumberFormatException ex) {
                    send(exchange, 400, "Invalid DAB bitrate");
                    return;
                }
                if (java.util.Arrays.binarySearch(MP3_BITRATES_KBPS, bitrateKbps) < 0) {
                    send(exchange, 400, "Invalid DAB bitrate");
                    return;
                }
                encoded = encoded.substring(separator + 1);
            }
            String sourceUrl = "";
            Path localFile = null;
            double seekSeconds = 0;
            try {
                sourceUrl = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
                String scheme = URI.create(sourceUrl).getScheme();
                if ("radio-file".equalsIgnoreCase(scheme)) {
                    localFile = resolveUploadedAudio(sourceUrl);
                    if (localFile != null) {
                        seekSeconds = playbackOffsetSeconds(sourceUrl, localFile);
                    }
                }
                if (localFile == null && (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")))) {
                    send(exchange, 400, "Invalid stream URL");
                    return;
                }
            } catch (IllegalArgumentException ex) {
                send(exchange, 400, "Invalid stream");
                return;
            } catch (IOException ex) {
                Radio.LOGGER.warn("Could not read uploaded audio duration for {}", sourceUrl, ex);
                send(exchange, 422, "Invalid uploaded audio");
                return;
            }
            Process process;
            try {
                List<String> command = new ArrayList<>();
                command.add(ffmpegExecutable);
                command.add("-hide_banner");
                command.add("-loglevel");
                command.add("error");
                if (localFile != null) {
                    command.add("-stream_loop");
                    command.add("-1");
                    command.add("-ss");
                    command.add(String.format(Locale.ROOT, "%.3f", seekSeconds));
                }
                command.add("-i");
                command.add(localFile == null ? sourceUrl : localFile.toString());
                command.add("-vn");
                if (localFile != null) {
                    // Looping after an input seek resets FFmpeg's timestamps. Rebuild a
                    // continuous timeline and pace the output at normal playback speed.
                    command.add("-af");
                    command.add("asetpts=N/SR/TB,arealtime");
                }
                command.add("-c:a");
                command.add("libmp3lame");
                command.add("-f");
                command.add("mp3");
                command.add("-b:a");
                command.add(bitrateKbps + "k");
                command.add("-ac");
                command.add("2");
                command.add("-ar");
                command.add(Integer.toString(PROXY_OUTPUT_SAMPLE_RATE_HZ));
                command.add("pipe:1");
                process = new ProcessBuilder(command)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException ex) {
                Radio.LOGGER.warn("Audio proxy failed to spawn ffmpeg for {}: {}", sourceUrl, ex.getMessage());
                send(exchange, 503, "ffmpeg unavailable");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, 0);
            try (InputStream in = process.getInputStream(); OutputStream out = exchange.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException ignored) {
            } finally {
                if (process.isAlive()) {
                    process.destroy();
                }
            }
        }

        private static void send(HttpExchange exchange, int status, String message) throws IOException {
            byte[] body = message.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
