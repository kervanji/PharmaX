package com.pharmax.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages saving and loading the dashboard tile order per user.
 * Layout is stored as a simple text file (one tile ID per line).
 * Hidden tiles are prefixed with '#'.
 */
public class DashboardLayoutService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardLayoutService.class);
    private static final String LAYOUT_DIR = "dashboard_layouts";
    private static final String HIDDEN_PREFIX = "#";      // hidden from everyone (current user)
    private static final String SELLER_HIDDEN_PREFIX = "!"; // hidden from sellers only
    private static final String BOTH_HIDDEN_PREFIX = "*";   // hidden from both

    /**
     * Save the tile order for a specific user.
     * @param username the username
     * @param tileIds ordered list of tile IDs
     */
    public static void saveTileOrder(String username, List<String> tileIds) {
        try {
            Path dir = getLayoutDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitize(username) + ".layout");
            String content = String.join("\n", tileIds);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            logger.info("Saved dashboard layout for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to save dashboard layout for user: {}", username, e);
        }
    }

    /**
     * Save the tile order with hidden state for a specific user.
     * Hidden tile IDs are prefixed with '#', '!', or '*'.
     * @param username the username
     * @param tileIds ordered list of tile IDs (without prefix)
     * @param hiddenIds set of tile IDs hidden from everyone
     * @param sellerHiddenIds set of tile IDs hidden from sellers only
     */
    public static void saveTileLayout(String username, List<String> tileIds, Set<String> hiddenIds, Set<String> sellerHiddenIds) {
        try {
            Path dir = getLayoutDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(sanitize(username) + ".layout");
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
            String content = String.join("\n", lines);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            logger.info("Saved dashboard layout for user: {} ({} tiles, {} hidden, {} seller-hidden)",
                    username,
                    tileIds.size(),
                    hiddenIds != null ? hiddenIds.size() : 0,
                    sellerHiddenIds != null ? sellerHiddenIds.size() : 0);
        } catch (Exception e) {
            logger.error("Failed to save dashboard layout for user: {}", username, e);
        }
    }

    /**
     * Load the tile order for a specific user.
     * Strips prefixes so the returned list contains clean tile IDs.
     * @param username the username
     * @return ordered list of tile IDs, or empty list if none saved
     */
    public static List<String> loadTileOrder(String username) {
        try {
            Path file = getLayoutDir().resolve(sanitize(username) + ".layout");
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                List<String> order = Arrays.stream(content.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> {
                            if (s.startsWith(BOTH_HIDDEN_PREFIX)) return s.substring(BOTH_HIDDEN_PREFIX.length());
                            if (s.startsWith(HIDDEN_PREFIX)) return s.substring(HIDDEN_PREFIX.length());
                            if (s.startsWith(SELLER_HIDDEN_PREFIX)) return s.substring(SELLER_HIDDEN_PREFIX.length());
                            return s;
                        })
                        .collect(Collectors.toList());
                if (!order.isEmpty()) {
                    logger.info("Loaded dashboard layout for user: {} ({} tiles)", username, order.size());
                    return order;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load dashboard layout for user: {}", username, e);
        }
        return Collections.emptyList();
    }

    /**
     * Load the set of hidden tile IDs for a specific user (hidden from everyone/self).
     * @param username the username
     * @return set of hidden tile IDs
     */
    public static Set<String> loadHiddenTiles(String username) {
        Set<String> hidden = new HashSet<>();
        try {
            Path file = getLayoutDir().resolve(sanitize(username) + ".layout");
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Arrays.stream(content.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(s -> {
                            if (s.startsWith(HIDDEN_PREFIX)) {
                                hidden.add(s.substring(HIDDEN_PREFIX.length()));
                            } else if (s.startsWith(BOTH_HIDDEN_PREFIX)) {
                                hidden.add(s.substring(BOTH_HIDDEN_PREFIX.length()));
                            }
                        });
            }
        } catch (Exception e) {
            logger.error("Failed to load hidden tiles for user: {}", username, e);
        }
        return hidden;
    }

    /**
     * Load the set of seller-hidden tile IDs for a specific user.
     * @param username the username
     * @return set of seller-hidden tile IDs
     */
    public static Set<String> loadSellerHiddenTiles(String username) {
        Set<String> hidden = new HashSet<>();
        try {
            Path file = getLayoutDir().resolve(sanitize(username) + ".layout");
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Arrays.stream(content.split("\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(s -> {
                            if (s.startsWith(SELLER_HIDDEN_PREFIX)) {
                                hidden.add(s.substring(SELLER_HIDDEN_PREFIX.length()));
                            } else if (s.startsWith(BOTH_HIDDEN_PREFIX)) {
                                hidden.add(s.substring(BOTH_HIDDEN_PREFIX.length()));
                            }
                        });
            }
        } catch (Exception e) {
            logger.error("Failed to load seller-hidden tiles for user: {}", username, e);
        }
        return hidden;
    }

    /**
     * Delete saved layout for a user (reset to default).
     */
    public static void resetLayout(String username) {
        try {
            Path file = getLayoutDir().resolve(sanitize(username) + ".layout");
            Files.deleteIfExists(file);
            logger.info("Reset dashboard layout for user: {}", username);
        } catch (Exception e) {
            logger.error("Failed to reset dashboard layout for user: {}", username, e);
        }
    }

    private static Path getLayoutDir() {
        return Paths.get(System.getProperty("user.dir"), LAYOUT_DIR);
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
