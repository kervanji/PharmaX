package com.pharmax.database;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerMigrationTest {

    @Test
    void applyMigrationsIsIdempotentAndBackfillsPackagingColumns() throws Exception {
        Path dbFile = Files.createTempFile("pharmax-migration-", ".db");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            invokePrivate("createTables", statement);

            statement.executeUpdate("""
                    INSERT INTO products (
                        id, product_code, name, unit_price, quantity_in_stock, minimum_stock,
                        unit_of_measure, base_unit, barcode, is_active
                    ) VALUES (
                        1, 'P-001', 'Test Product', 2500, 24, 2,
                        'piece', NULL, 'BC-001', 1
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO sales (
                        id, sale_code, customer_id, total_amount, discount_amount, tax_amount, final_amount
                    ) VALUES (
                        1, 'S-001', 1, 5000, 0, 0, 5000
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO sale_items (
                        id, sale_id, product_id, quantity, unit_price, total_price,
                        discount_percentage, discount_amount, price_type, sold_unit, conversion_factor, base_quantity
                    ) VALUES (
                        1, 1, 1, 2, 2500, 5000,
                        0, 0, 'RETAIL', NULL, NULL, NULL
                    )
                    """);

            invokePrivate("applyMigrations", statement);
            invokePrivate("applyMigrations", statement);

            assertTrue(hasColumn(statement, "products", "base_unit"));
            assertTrue(hasColumn(statement, "sale_items", "conversion_factor"));
            assertTrue(hasColumn(statement, "sale_items", "base_quantity"));

            try (ResultSet rs = statement.executeQuery("SELECT base_unit FROM products WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("piece", rs.getString("base_unit"));
            }

            try (ResultSet rs = statement.executeQuery("""
                    SELECT conversion_factor, base_quantity
                    FROM sale_items
                    WHERE id = 1
                    """)) {
                assertTrue(rs.next());
                assertEquals(1.0, rs.getDouble("conversion_factor"));
                assertEquals(2.0, rs.getDouble("base_quantity"));
            }

            try (ResultSet rs = statement.executeQuery("""
                    SELECT COUNT(*) AS unit_count
                    FROM product_units
                    WHERE product_id = 1
                    """)) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("unit_count"));
            }

            try (ResultSet rs = statement.executeQuery("""
                    SELECT unit_name, barcode, conversion_factor, is_default
                    FROM product_units
                    WHERE product_id = 1
                    """)) {
                assertTrue(rs.next());
                assertEquals("piece", rs.getString("unit_name"));
                assertEquals("BC-001", rs.getString("barcode"));
                assertEquals(1.0, rs.getDouble("conversion_factor"));
                assertEquals(1, rs.getInt("is_default"));
                assertFalse(rs.next());
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    private void invokePrivate(String methodName, Statement statement) throws Exception {
        Method method = DatabaseManager.class.getDeclaredMethod(methodName, Statement.class);
        method.setAccessible(true);
        method.invoke(null, statement);
    }

    private boolean hasColumn(Statement statement, String tableName, String columnName) throws Exception {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
