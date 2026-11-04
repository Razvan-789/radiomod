package com.radiomod.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiomod.Radio;
import com.radiomod.blockentity.DabServerBlockEntity;
import com.radiomod.blockentity.DabTransmitterBlockEntity;
import com.radiomod.blockentity.DrmTransmitterBlockEntity;
import com.radiomod.blockentity.TransmitterBlockEntity;
import com.radiomod.signal.RegistryCountries;
import com.radiomod.signal.SignalMath;
import com.radiomod.signal.SignalNetwork;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/** Optional, server-only Squaremap layers and web assets. Never loaded without Squaremap. */
public final class SquaremapIntegration {
    private static final Key SITES = Key.of("radio_sites");
    private static final int[] FM_THRESHOLDS = {5, 25, 50, 75};
    // Increase this when the contour sampling formula or stored geometry changes.
    private static final int COVERAGE_CACHE_VERSION = 1;
    private static final Color[] FM_COLORS = {new Color(30, 160, 226), new Color(51, 205, 108),
            new Color(255, 204, 44), new Color(244, 83, 62)};
    private static final String[] ICONS = {"tx-lo.png", "tx-lo-sel.png", "tx-med.png",
            "tx-med-sel.png", "tx-hi.png", "tx-hi-sel.png"};
    private static final int[] ICON_SIZES = {19, 23, 27};
    private static final Map<String, Layers> LAYERS = new HashMap<>();
    private static final Map<String, Long> PUBLISHED_REVISIONS = new HashMap<>();
    private static final Map<String, CachedCoverage> COVERAGE_CACHE = new HashMap<>();
    private static Path coverageCachePath;
    private static long pendingSinceTick;
    private static long retryAfterTick;
    private static boolean warned;

    private SquaremapIntegration() { }

    public static void tick(MinecraftServer server) {
        if (!ModList.get().isLoaded("squaremap")) return;
        boolean changed = PUBLISHED_REVISIONS.isEmpty();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            if (!PUBLISHED_REVISIONS.containsKey(dimension)
                    || PUBLISHED_REVISIONS.get(dimension) != SignalNetwork.revision(level)) changed = true;
        }
        if (!changed) { pendingSinceTick = 0; return; }
        long tick = server.getTickCount();
        if (pendingSinceTick == 0) pendingSinceTick = tick;
        // Coalesce rapid edits into one rebuild; idle transmitters cause no sampling.
        if (tick - pendingSinceTick < 20 || tick < retryAfterTick) return;
        try {
            Squaremap squaremap = SquaremapProvider.get();
            if (squaremap == null) { retryAfterTick = tick + 400; return; }
            publish(server, squaremap);
            PUBLISHED_REVISIONS.clear();
            for (ServerLevel level : server.getAllLevels()) PUBLISHED_REVISIONS.put(
                    level.dimension().location().toString(), SignalNetwork.revision(level));
            pendingSinceTick = 0;
            retryAfterTick = 0;
            warned = false;
        } catch (IllegalStateException notReady) {
            // Squaremap may register its API after our first server tick.
            retryAfterTick = tick + 400;
        } catch (Exception ex) {
            if (!warned) Radio.LOGGER.warn("Squaremap radio integration is unavailable: {}", ex.toString());
            warned = true;
            retryAfterTick = tick + 400;
        }
    }

    public static void stop() {
        LAYERS.clear();
        PUBLISHED_REVISIONS.clear();
        COVERAGE_CACHE.clear();
        coverageCachePath = null;
        pendingSinceTick = 0;
        retryAfterTick = 0;
        warned = false;
    }

    private static void publish(MinecraftServer server, Squaremap map) throws IOException {
        Path cachePath = server.getWorldPath(LevelResource.ROOT).resolve("radio_squaremap_coverage.json");
        loadCoverageCache(cachePath);
        // Keep entries for chunks that have not loaded yet; they can still be reused later in this startup.
        Map<String, CachedCoverage> currentCoverage = new HashMap<>(COVERAGE_CACHE);
        Path web = map.webDir().toAbsolutePath().normalize();
        Path assets = web.resolve("radio-map");
        Files.createDirectories(assets);
        for (String name : ICONS) copyResource(assets.resolve(name), "/assets/radio/squaremap/" + name);
        copyResource(assets.resolve("radio-map.css"), "/assets/radio/squaremap/radio-map.css");
        copyResource(assets.resolve("radio-map.js"), "/assets/radio/squaremap/radio-map.js");
        inject(web.resolve("index.html"));
        JsonArray worlds = new JsonArray();
        RegistryCountries countries = RegistryCountries.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            MapWorld world = map.getWorldIfEnabled(WorldIdentifier.parse(dimension)).orElse(null);
            if (world == null) continue;
            Layers layers = ensureLayers(world, dimension);
            layers.clear(world);
            List<Tx> transmitters = scan(level, countries);
            List<Site> sites = merge(dimension, transmitters);
            JsonObject jsonWorld = new JsonObject();
            jsonWorld.addProperty("dimension", dimension);
            JsonArray jsonSites = new JsonArray();
            for (Site site : sites) {
                registerIcon(map, site);
                Marker icon = Marker.icon(Point.of(site.center.getX() + .5, site.center.getZ() + .5),
                        iconKey(site, false), ICON_SIZES[iconTier(site.power)])
                        .markerOptions(MarkerOptions.builder().clickTooltip(tooltip(site)).hoverTooltip(
                                site.programmes.size() + " radio service(s)").build());
                layers.sites.addMarker(Key.of("radio_site_" + site.id), icon);
                jsonSites.add(siteJson(site));
            }
            for (Tx tx : transmitters) addCoverage(world, layers, dimension, tx, currentCoverage);
            jsonWorld.add("sites", jsonSites);
            worlds.add(jsonWorld);
        }
        JsonObject root = new JsonObject();
        root.add("worlds", worlds);
        Path tmp = Files.createTempFile(assets, "radio-data-", ".tmp");
        try {
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            try { Files.move(tmp, assets.resolve("data.json"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, assets.resolve("data.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
        if (!currentCoverage.equals(COVERAGE_CACHE)) {
            try { saveCoverageCache(cachePath, currentCoverage); }
            catch (IOException ex) { Radio.LOGGER.warn("Could not save Squaremap coverage cache: {}", ex.toString()); }
            COVERAGE_CACHE.clear();
            COVERAGE_CACHE.putAll(currentCoverage);
        }
    }

    private static Layers ensureLayers(MapWorld world, String dimension) {
        Layers layers = LAYERS.computeIfAbsent(dimension, ignored -> new Layers());
        if (world.layerRegistry().hasEntry(SITES) && world.layerRegistry().get(SITES) != layers.sites)
            world.layerRegistry().unregister(SITES);
        if (!world.layerRegistry().hasEntry(SITES)) world.layerRegistry().register(SITES, layers.sites);
        return layers;
    }

    private static List<Tx> scan(ServerLevel level, RegistryCountries countries) {
        List<Tx> result = new ArrayList<>();
        for (BlockPos pos : SignalNetwork.getTransmitters(level)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransmitterBlockEntity fm) {
                String name = fm.getRdsLongPs().isBlank() ? fm.getRdsPs() : fm.getRdsLongPs();
                if (name.isBlank()) name = "FM station";
                result.add(new Tx(pos, "FM", fm.getFrequencyKhz(), fm.getPowerW(), fm.hasAntennaConnected(),
                        fm.isDirectionalEnabled(), fm.getDirectionDegrees(),
                        List.of(new Programme(name, fm.getRdsPi(), countries.countryFor(fm.getRdsPi()),
                                fm.getFrequencyKhz(), fm.getPowerW(), "FM", coverageLabel(pos, fm.getFrequencyKhz()))), be));
            } else if (be instanceof DabTransmitterBlockEntity dab) {
                DabServerBlockEntity.DabEnsembleDefinition mux = dab.resolveBroadcastMux();
                List<Programme> programmes = new ArrayList<>();
                if (mux != null) for (var station : mux.stations()) programmes.add(new Programme(
                        station.label(), station.sid(), countries.countryFor(station.sid()),
                        dab.getFrequencyKhz(), dab.getPowerW(), dab.getDabVersion().getLabel(),
                        coverageLabel(pos, dab.getFrequencyKhz())));
                result.add(new Tx(pos, "DAB", dab.getFrequencyKhz(), dab.getPowerW(), dab.hasAntennaConnected(),
                        dab.isDirectionalEnabled(), dab.getDirectionDegrees(), programmes, be));
            } else if (be instanceof DrmTransmitterBlockEntity drm) {
                result.add(new Tx(pos, "DRM", drm.getFrequencyKhz(), drm.getPowerW(), drm.hasAntennaConnected(),
                        false, 0, List.of(new Programme(drm.getServiceLabel(), "", "Unknown",
                        drm.getFrequencyKhz(), drm.getPowerW(), "DRM", "")), be));
            }
        }
        result.sort(Comparator.comparing(Tx::pos));
        return result;
    }

    /** Connected components of transmitters no more than 50 blocks apart. */
    private static List<Site> merge(String dimension, List<Tx> transmitters) {
        int n = transmitters.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++)
            if (transmitters.get(i).pos().distSqr(transmitters.get(j).pos()) <= 50 * 50) {
                int a = root(parent, i), b = root(parent, j);
                parent[b] = a;
            }
        Map<Integer, List<Tx>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(root(parent, i), ignored -> new ArrayList<>()).add(transmitters.get(i));
        List<Site> sites = new ArrayList<>();
        for (List<Tx> group : groups.values()) {
            Tx strongest = group.stream().max(Comparator.comparingInt(Tx::power)).orElseThrow();
            List<Programme> programmes = new ArrayList<>();
            for (Tx tx : group) programmes.addAll(tx.programmes());
            programmes.sort(Comparator.comparingInt(Programme::frequencyKhz).thenComparing(Programme::name));
            String id = Integer.toHexString(dimension.hashCode()) + "_"
                    + Long.toUnsignedString(group.stream().mapToLong(tx -> tx.pos().asLong()).min().orElse(0), 16);
            sites.add(new Site(id, strongest.pos(), strongest.power(), programmes));
        }
        sites.sort(Comparator.comparing(Site::id));
        return sites;
    }
    private static int root(int[] parent, int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    private static String coverageLabel(BlockPos pos, int frequencyKhz) {
        return "Radio coverage " + formatFrequency(frequencyKhz) + " MHz @ "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void addCoverage(MapWorld world, Layers layers, String dimension, Tx tx,
                                    Map<String, CachedCoverage> currentCoverage) {
        if (!(tx.entity instanceof TransmitterBlockEntity || tx.entity instanceof DabTransmitterBlockEntity)) return;
        String cacheKey = dimension + "|" + tx.pos.asLong();
        String fingerprint = coverageFingerprint(tx);
        CachedCoverage cached = COVERAGE_CACHE.get(cacheKey);
        if (cached == null || !cached.fingerprint.equals(fingerprint)) {
            List<List<Point>> contours = new ArrayList<>();
            if (tx.entity instanceof TransmitterBlockEntity) {
                for (int threshold : FM_THRESHOLDS) contours.add(contour(tx, threshold));
            } else if (tx.entity instanceof DabTransmitterBlockEntity dab) {
                contours.add(contour(tx, dab.getErrorCorrection().getMinimumSignalDbuv()));
            }
            cached = new CachedCoverage(fingerprint, List.copyOf(contours));
        }
        currentCoverage.put(cacheKey, cached);
        Key key = Key.of("radio_cov_" + Long.toUnsignedString(tx.pos.asLong(), 16));
        SimpleLayerProvider coverage = SimpleLayerProvider.builder(coverageLabel(tx.pos, tx.frequencyKhz))
                .defaultHidden(true).layerPriority(30).zIndex(10).build();
        if (tx.entity instanceof TransmitterBlockEntity) {
            for (int i = 0; i < FM_THRESHOLDS.length; i++) {
                List<Point> points = cached.contours.get(i);
                if (points.isEmpty()) continue;
                Color color = FM_COLORS[i];
                coverage.addMarker(Key.of("radio_fm_" + tx.pos.asLong() + "_" + i),
                        Marker.polygon(points).markerOptions(MarkerOptions.builder().stroke(false)
                                .fill(true).fillColor(color).fillOpacity(.32)
                                .clickTooltip("FM " + formatFrequency(tx.frequencyKhz) + " MHz · ≥" + FM_THRESHOLDS[i] + " dBµV").build()));
            }
        } else if (tx.entity instanceof DabTransmitterBlockEntity dab) {
            List<Point> points = cached.contours.get(0);
            if (!points.isEmpty()) coverage.addMarker(Key.of("radio_dab_" + tx.pos.asLong()),
                    Marker.polygon(points).markerOptions(MarkerOptions.builder().stroke(false)
                            .fill(true).fillColor(new Color(96, 72, 225)).fillOpacity(.42)
                            .clickTooltip("DAB " + formatFrequency(tx.frequencyKhz) + " MHz · "
                                    + dab.getErrorCorrection().getLabel()).build()));
        }
        if (!coverage.registeredMarkers().isEmpty()) {
            world.layerRegistry().register(key, coverage);
            layers.coverage.add(key);
        }
    }

    private static String coverageFingerprint(Tx tx) {
        StringBuilder value = new StringBuilder().append(COVERAGE_CACHE_VERSION).append('|')
                .append(tx.kind).append('|').append(tx.pos.asLong()).append('|')
                .append(tx.frequencyKhz).append('|').append(tx.power).append('|')
                .append(tx.antenna).append('|').append(tx.directional).append('|').append(tx.azimuth);
        BlockPos antennaPos;
        if (tx.entity instanceof TransmitterBlockEntity fm) antennaPos = fm.getAntennaPos();
        else if (tx.entity instanceof DabTransmitterBlockEntity dab) {
            antennaPos = dab.getAntennaPos();
            value.append('|').append(dab.getErrorCorrection().name());
        } else antennaPos = null;
        value.append('|').append(antennaPos == null ? "none" : antennaPos.asLong());
        // A directional antenna's sector gains can change without changing the transmitter's own fields.
        Vec3 source = Vec3.atCenterOf(tx.pos);
        for (int sector = 0; sector < 36; sector++) {
            double angle = 2 * Math.PI * sector / 36;
            Vec3 receiver = new Vec3(source.x + Math.cos(angle) * 8_192, 100,
                    source.z + Math.sin(angle) * 8_192);
            double gain = tx.entity instanceof TransmitterBlockEntity fm ? fm.antennaGainToward(receiver)
                    : ((DabTransmitterBlockEntity) tx.entity).antennaGainToward(receiver);
            value.append('|').append(Double.doubleToLongBits(gain));
        }
        return value.toString();
    }

    private static void loadCoverageCache(Path path) {
        if (path.equals(coverageCachePath)) return;
        coverageCachePath = path;
        COVERAGE_CACHE.clear();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != COVERAGE_CACHE_VERSION) return;
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("entries").entrySet()) {
                JsonObject data = entry.getValue().getAsJsonObject();
                List<List<Point>> contours = new ArrayList<>();
                for (com.google.gson.JsonElement shape : data.getAsJsonArray("contours")) {
                    List<Point> points = new ArrayList<>();
                    for (com.google.gson.JsonElement coordinate : shape.getAsJsonArray()) {
                        JsonArray pair = coordinate.getAsJsonArray();
                        if (pair.size() != 2) throw new IllegalArgumentException("Invalid coverage point");
                        double x = pair.get(0).getAsDouble(), z = pair.get(1).getAsDouble();
                        if (!Double.isFinite(x) || !Double.isFinite(z))
                            throw new IllegalArgumentException("Non-finite coverage point");
                        points.add(Point.of(x, z));
                    }
                    if (!points.isEmpty() && points.size() != 36)
                        throw new IllegalArgumentException("Incomplete coverage contour");
                    contours.add(List.copyOf(points));
                }
                if (contours.size() != 1 && contours.size() != FM_THRESHOLDS.length)
                    throw new IllegalArgumentException("Invalid coverage contour count");
                COVERAGE_CACHE.put(entry.getKey(), new CachedCoverage(data.get("fingerprint").getAsString(),
                        List.copyOf(contours)));
            }
        } catch (IOException | RuntimeException invalid) {
            COVERAGE_CACHE.clear();
            Radio.LOGGER.warn("Ignoring invalid Squaremap coverage cache: {}", invalid.toString());
        }
    }

    private static void saveCoverageCache(Path path, Map<String, CachedCoverage> coverage) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", COVERAGE_CACHE_VERSION);
        JsonObject entries = new JsonObject();
        coverage.keySet().stream().sorted().forEach(key -> {
            CachedCoverage cached = coverage.get(key);
            JsonObject data = new JsonObject();
            data.addProperty("fingerprint", cached.fingerprint);
            JsonArray contours = new JsonArray();
            for (List<Point> shape : cached.contours) {
                JsonArray points = new JsonArray();
                for (Point point : shape) {
                    JsonArray pair = new JsonArray();
                    pair.add(point.x()); pair.add(point.z());
                    points.add(pair);
                }
                contours.add(points);
            }
            data.add("contours", contours);
            entries.add(key, data);
        });
        root.add("entries", entries);
        Path tmp = Files.createTempFile(path.getParent(), "radio-coverage-", ".tmp");
        try {
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    private static List<Point> contour(Tx tx, double threshold) {
        List<Point> points = new ArrayList<>(36);
        Vec3 source = Vec3.atCenterOf(tx.pos);
        for (int sector = 0; sector < 36; sector++) {
            double angle = 2 * Math.PI * sector / 36;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double low = 0, high = 256;
            while (high < 8_192 && strength(tx, source, dx, dz, high) >= threshold) high *= 2;
            if (strength(tx, source, dx, dz, low) < threshold) return List.of();
            for (int step = 0; step < 9; step++) {
                double middle = (low + high) / 2;
                if (strength(tx, source, dx, dz, middle) >= threshold) low = middle;
                else high = middle;
            }
            points.add(Point.of(source.x + dx * low, source.z + dz * low));
        }
        return points;
    }

    private static double strength(Tx tx, Vec3 source, double dx, double dz, double radius) {
        Vec3 receiver = new Vec3(source.x + dx * radius, 100, source.z + dz * radius);
        double signal = tx.entity instanceof TransmitterBlockEntity
                ? SignalMath.calculateSignalDbuv(tx.power, tx.frequencyKhz, tx.frequencyKhz,
                        tx.antenna, tx.directional, tx.azimuth, source, receiver, 0, false)
                : SignalMath.calculateDabSignalDbuv(tx.power, tx.frequencyKhz, tx.frequencyKhz,
                        tx.antenna, tx.directional, tx.azimuth, source, receiver, 0, false);
        if (tx.entity instanceof TransmitterBlockEntity fm) signal += fm.antennaGainToward(receiver);
        else if (tx.entity instanceof DabTransmitterBlockEntity dab) signal += dab.antennaGainToward(receiver);
        return signal;
    }

    private static void registerIcon(Squaremap map, Site site) throws IOException {
        int tier = iconTier(site.power);
        for (boolean selected : new boolean[]{false, true}) {
            Key key = iconKey(site, selected);
            if (map.iconRegistry().hasEntry(key)) continue;
            String name = ICONS[tier * 2 + (selected ? 1 : 0)];
            try (InputStream stream = SquaremapIntegration.class.getResourceAsStream("/assets/radio/squaremap/" + name)) {
                if (stream == null) throw new IOException("Missing " + name);
                BufferedImage image = ImageIO.read(stream);
                map.iconRegistry().register(key, image);
            }
        }
    }
    private static int iconTier(int power) { return power < 500 ? 0 : power < 5_000 ? 1 : 2; }
    private static Key iconKey(Site site, boolean selected) {
        return Key.of("radio_tx_" + (selected ? "sel_" : "") + site.id + "_" + iconTier(site.power));
    }

    private static String tooltip(Site site) {
        StringBuilder html = new StringBuilder("<strong>Radio site</strong> · ")
                .append(site.center.getX()).append(", ").append(site.center.getY()).append(", ")
                .append(site.center.getZ()).append("<br><table><tr><th>Freq</th><th>Station</th><th>PI/SID</th><th>ERP kW</th></tr>");
        for (Programme programme : site.programmes) html.append("<tr><td>").append(formatFrequency(programme.frequencyKhz))
                .append("</td><td>").append(escape(programme.name)).append("</td><td>")
                .append(escape(programme.code)).append("</td><td>")
                .append(String.format(java.util.Locale.ROOT, "%.3f", programme.powerW / 1000.0))
                .append("</td></tr>");
        return html.append("</table>").toString();
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String formatFrequency(int khz) {
        return String.format(java.util.Locale.ROOT, "%.3f", khz / 1000.0);
    }
    private static JsonObject siteJson(Site site) {
        JsonObject json = new JsonObject();
        json.addProperty("id", site.id); json.addProperty("x", site.center.getX());
        json.addProperty("y", site.center.getY()); json.addProperty("z", site.center.getZ());
        json.addProperty("power", site.power); json.addProperty("icon", iconKey(site, false).getKey());
        json.addProperty("selectedIcon", iconKey(site, true).getKey());
        JsonArray programmes = new JsonArray();
        for (Programme p : site.programmes) {
            JsonObject item = new JsonObject();
            item.addProperty("name", p.name); item.addProperty("code", p.code);
            item.addProperty("country", p.country); item.addProperty("frequency", p.frequencyKhz);
            item.addProperty("power", p.powerW); item.addProperty("type", p.type);
            item.addProperty("coverage", p.coverage);
            programmes.add(item);
        }
        json.add("programmes", programmes);
        return json;
    }
    private static void copyResource(Path target, String resource) throws IOException {
        try (InputStream stream = SquaremapIntegration.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing " + resource);
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void inject(Path index) throws IOException {
        if (!Files.isRegularFile(index)) return;
        String html = Files.readString(index, StandardCharsets.UTF_8);
        if (html.contains("radio-map/radio-map.js")) return;
        String injection = "<link rel=\"stylesheet\" href=\"radio-map/radio-map.css\">\n"
                + "<script defer src=\"radio-map/radio-map.js\"></script>\n";
        if (!html.contains("</head>")) return;
        Files.writeString(index, html.replace("</head>", injection + "</head>"), StandardCharsets.UTF_8);
    }

    private record Programme(String name, String code, String country, int frequencyKhz, int powerW,
                             String type, String coverage) { }
    private record Tx(BlockPos pos, String kind, int frequencyKhz, int power, boolean antenna,
                      boolean directional, int azimuth, List<Programme> programmes, BlockEntity entity) { }
    private record Site(String id, BlockPos center, int power, List<Programme> programmes) { }
    private record CachedCoverage(String fingerprint, List<List<Point>> contours) { }
    private static final class Layers {
        final SimpleLayerProvider sites = SimpleLayerProvider.builder("Radio transmitters").layerPriority(20).zIndex(50).build();
        final List<Key> coverage = new ArrayList<>();
        void clear(MapWorld world) {
            sites.clearMarkers();
            for (Key key : coverage) if (world.layerRegistry().hasEntry(key)) world.layerRegistry().unregister(key);
            coverage.clear();
        }
    }
}
