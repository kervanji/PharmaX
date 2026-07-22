package com.pharmax.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickSaleMigrationTest {
    @TempDir Path tempDir;

    @Test
    void migratesLegacyProductsAndKeepsImagesInDatabase() throws Exception {
        Path db = tempDir.resolve("quick-sale.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
                statement.execute("CREATE TABLE product_units (id INTEGER PRIMARY KEY, product_id INTEGER)");
                executeResource(statement, "/db/migrations/20260504_quick_sale_products.sql");
                statement.execute("INSERT INTO products (id, name, is_quick_sale) VALUES (1, 'A', 1), (2, 'B', 1), (3, 'C', 0)");
                executeResource(statement, "/db/migrations/20260713_quick_sale_drawer.sql");
            }

            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM quick_sale_groups"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM quick_sale_items"));

            byte[] expected = new byte[] {1, 2, 3, 4, 5};
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE quick_sale_items SET image_data = ?, image_mime_type = 'image/png' WHERE product_id = 1")) {
                statement.setBytes(1, expected);
                statement.executeUpdate();
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT image_data FROM quick_sale_items WHERE product_id = 1")) {
                result.next();
                assertArrayEquals(expected, result.getBytes(1));
            }

            try (Statement statement = connection.createStatement()) {
                executeResource(statement, "/db/migrations/20260713_quick_sale_drawer.sql");
            }
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM quick_sale_groups"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM quick_sale_items"));

            try (Statement statement = connection.createStatement()) {
                executeResource(statement, "/db/migrations/20260713_unlimited_stock.sql");
            }
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM quick_sale_groups"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM products WHERE COALESCE(is_unlimited_stock, 0) = 1"));

            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO quick_sale_groups (name, icon_key, sort_order, is_active) "
                        + "VALUES ('أدوية بدون باركود', '∞', 99, 1)");
                statement.execute("INSERT INTO quick_sale_items (group_id, product_id, sort_order) "
                        + "SELECT id, 3, 0 FROM quick_sale_groups WHERE name = 'أدوية بدون باركود'");
                executeResource(statement, "/db/migrations/20260713_remove_accidental_barcode_free_group.sql");
            }
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM quick_sale_groups"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM quick_sale_items"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM quick_sale_groups WHERE name = 'أدوية بدون باركود'"));

        }
    }

    private void executeResource(Statement statement, String resource) throws Exception {
        try (var stream = getClass().getResourceAsStream(resource)) {
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String command : sql.split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
