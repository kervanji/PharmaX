package com.pharmax.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages saving and loading the dashboard tile order per user.
 * Layout is stored as a simple text file (one tile ID per line).
 * Hidden tiles are prefixed with '#', '!', or '*'.
 */
public class DashboardLayoutService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardLayoutService.class);
    private static final String LAYOUT_DIR = "dashboard_layouts";
    private static final String DEFAULT_LAYOUT_NAME = "default";
    private static final String SELLER_LAYOUT_NAME = "seller";
    private static final String DEFAULT_RESOURCE = "/dashboard/default.layout";
    private static final String HIDDEN_PREFIX = "#";
    private static final String SELLER_HIDDEN_PREFIX = "!";
    private static final String BOTH_HIDDEN_PREFIX = "*";

    public static void saveTileOrder(String username, List<String> tileIds) {
        saveTileLayout(username, tileIds, Collections.emptySet(), Collections.emptySet());
    }

    public static void saveTileLayout(String username, List<String> tileIds, Set<String> hiddenIds, Set<String> sellerHiddenIds) {
        writeLayoutFile(sanitize(username) + ".layout", tileIds, hiddenIds, sellerHiddenIds);
        logger.info("Saved dashboard layout for user: {}", username);
    }

    public static void saveSellerLayout(List<String> tileIds, Set<String> sellerHiddenIds) {
        writeLayoutFile(SELLER_LAYOUT_NAME + ".layout", tileIds, Collections.emptySet(), sellerHiddenIds);
        logger.info("Saved seller dashboard layout ({} seller-hidden tiles)", sellerHiddenIds != null ? sellerHiddenIds.size() : 0);
    }

    public static void saveDefaultLayout(List<String> tileIds, Set<String> hiddenIds, Set<String> sellerHiddenIds) {
        writeLayoutFile(DEFAULT_LAYOUT_NAME + ".layout", tileIds, hiddenIds, sellerHiddenIds);
        logger.info("Saved default dashboard layout");
    }

    public static List<String> loadTileOrder(String username) {
        List<String> order = readLayoutOrder(getUserLayoutPath(username));
        if (!order.isEmpty()) {
            logger.info("Loaded dashboard layout for user: {} ({} tiles)", username, order.size());
            return order;
        }
        return Collections.emptyList();
    }

    public static List<String> loadDefaultTileOrder() {
        List<String> order = readLayoutOrder(getDefaultLayoutPath());
        if (!order.isEmpty()) {
            return order;
        }
        return readLayoutOrderFromResource(DEFAULT_RESOURCE);
    }

    public static Set<String> loadHiddenTiles(String username) {
        return readHiddenTiles(getUserLayoutPath(username), true, false);
    }

    public static Set<String> loadDefaultHiddenTiles() {
        Set<String> hidden = readHiddenTiles(getDefaultLayoutPath(), true, false);
        if (!hidden.isEmpty()) {
            return hidden;
        }
        return readHiddenTilesFromResource(DEFAULT_RESOURCE, true, false);
    }

    public static Set<String> loadSellerHiddenTiles() {
        Set<String> hidden = readHiddenTiles(getSellerLayoutPath(), false, true);
        if (!hidden.isEmpty()) {
            return hidden;
        }
        hidden = readHiddenTiles(getDefaultLayoutPath(), false, true);
        if (!hidden.isEmpty()) {
            return hidden;
        }
        return readHiddenTilesFromResource(DEFAULT_RESOURCE, false, true);
    }

    public static void resetLayout(String username) {
        try {
            Files.deleteIfExists(getUserLayoutPath(username));
            logger.info("Reset dashboard layout for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to reset dashboard layout for user: {}", username, e);
        }
    }

    public static void ensureDefaultLayoutFiles() {
        try {
            Path dir = getLayoutDir();
            Files.createDirectories(dir);
            Path defaultFile = getDefaultLayoutPath();
            if (!Files.exists(defaultFile)) {
                List<String> lines = readLayoutOrderFromResource(DEFAULT_RESOURCE);
                if (!lines.isEmpty()) {
                    Files.writeString(defaultFile, String.join("\n", lines), StandardCharsets.UTF_8);
                }
            }
            Path sellerFile = getSellerLayoutPath();
            if (!Files.exists(sellerFile)) {
                Files.writeString(sellerFile, "", StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.warn("Failed to ensure default layout files", e);
        }
    }

    private static void writeLayoutFile(String fileName, List<String> tileIds, Set<String> hiddenIds, Set<String> sellerHiddenIds) {
        try {
            Path dir = getLayoutDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            List<String> lines = new ArrayList<>();
            for (String id : tileIds) {
                boolean isHidden = hiddenIds != null && hiddenIds.contains(id);
                boolean isSellerHidden = sellerHiddenIds != null && sellerHiddenIds.contains(id);
                if (isHidden && isSellerHidden) {
                    lines.add(BOTH_HIDDEN_PREFIX + id);
                } else if (isHidden) {
                    lines.add(HIDDEN_PREFIX + id);
                } else if (isSellerHidden) {
                    lines.add(SELLER_HIDDEN_PREFIX + id);
                } else {
                    lines.add(id);
                }
            }
            Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to write layout file {}", fileName, e);
        }
    }

    private static List<String> readLayoutOrder(Path file) {
        try {
            if (file != null && Files.exists(file)) {
                return parseLines(Files.readString(file, StandardCharsets.UTF_8)).stream()
                        .map(ParsedLine::id)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Failed to read layout order from {}", file, e);
        }
        return Collections.emptyList();
    }

    private static Set<String> readHiddenTiles(Path file, boolean includePersonalHidden, boolean includeSellerHidden) {
        Set<String> hidden = new HashSet<>();
        try {
            if (file != null && Files.exists(file)) {
                for (ParsedLine line : parseLines(Files.readString(file, StandardCharsets.UTF_8))) {
                    if (includePersonalHidden && (line.personalHidden() || line.bothHidden())) {
                        hidden.add(line.id());
                    }
                    if (includeSellerHidden && (line.sellerHidden() || line.bothHidden())) {
                        hidden.add(line.id());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to read hidden tiles from {}", file, e);
        }
        return hidden;
    }

    private static List<String> readLayoutOrderFromResource(String resourcePath) {
        try (InputStream inputStream = DashboardLayoutService.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Collections.emptyList();
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return parseLines(content).stream().map(ParsedLine::id).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to read layout resource {}", resourcePath, e);
            return Collections.emptyList();
        }
    }

    private static Set<String> readHiddenTilesFromResource(String resourcePath, boolean includePersonalHidden, boolean includeSellerHidden) {
        Set<String> hidden = new HashSet<>();
        try (InputStream inputStream = DashboardLayoutService.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return hidden;
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            for (ParsedLine line : parseLines(content)) {
                if (includePersonalHidden && (line.personalHidden() || line.bothHidden())) {
                    hidden.add(line.id());
                }
                if (includeSellerHidden && (line.sellerHidden() || line.bothHidden())) {
                    hidden.add(line.id());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to read hidden tiles from resource {}", resourcePath, e);
        }
        return hidden;
    }

    private static List<ParsedLine> parseLines(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }
        List<ParsedLine> lines = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith(BOTH_HIDDEN_PREFIX)) {
                lines.add(new ParsedLine(trimmed.substring(BOTH_HIDDEN_PREFIX.length()), false, false, true));
            } else if (trimmed.startsWith(HIDDEN_PREFIX)) {
                lines.add(new ParsedLine(trimmed.substring(HIDDEN_PREFIX.length()), true, false, false));
            } else if (trimmed.startsWith(SELLER_HIDDEN_PREFIX)) {
                lines.add(new ParsedLine(trimmed.substring(SELLER_HIDDEN_PREFIX.length()), false, true, false));
            } else {
                lines.add(new ParsedLine(trimmed, false, false, false));
            }
        }
        return lines;
    }

    private static Path getLayoutDir() {
        return Paths.get(System.getProperty("user.dir"), LAYOUT_DIR);
    }

    private static Path getUserLayoutPath(String username) {
        return getLayoutDir().resolve(sanitize(username) + ".layout");
    }

    private static Path getDefaultLayoutPath() {
        return getLayoutDir().resolve(DEFAULT_LAYOUT_NAME + ".layout");
    }

    private static Path getSellerLayoutPath() {
        return getLayoutDir().resolve(SELLER_LAYOUT_NAME + ".layout");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record ParsedLine(String id, boolean personalHidden, boolean sellerHidden, boolean bothHidden) {
    }
}
