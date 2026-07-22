package com.pharmax.database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:pharmax.db";
    private static final List<String> MANAGED_MIGRATIONS = List.of(
            "db/migrations/20260501_batch_inventory_foundation.sql",
            "db/migrations/20260501_opening_batches.sql",
            "db/migrations/20260501_purchase_batch_fields.sql",
            "db/migrations/20260501_sales_fefo_foundation.sql",
            "db/migrations/20260501_sales_return_batches.sql",
            "db/migrations/20260502_purchase_returns.sql",
            "db/migrations/20260502_cashbox_foundation.sql",
            "db/migrations/20260502_permissions_audit.sql",
            "db/migrations/20260503_batch_production_date.sql",
            "db/migrations/20260504_supplier_invoice_number.sql",
            "db/migrations/20260504_quick_sale_products.sql",
            "db/migrations/20260701_cashbox_enhancements.sql",
            "db/migrations/20260713_quick_sale_drawer.sql",
            "db/migrations/20260713_unlimited_stock.sql",
            "db/migrations/20260713_remove_accidental_barcode_free_group.sql",
            "db/migrations/20260714_unlimited_stock_sale_movements.sql");
    private static SessionFactory sessionFactory;

    public static void initialize() {
        try {
            // Initialize SQLite database
            initializeSQLite();

            // Configure Hibernate
            configureHibernate();

            logger.info("Database initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private static void initializeSQLite() throws SQLException {
        File dbFile = new File("pharmax.db");

        try (Connection conn = DriverManager.getConnection(DB_URL);
                Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA busy_timeout = 5000");
            stmt.execute("PRAGMA journal_mode = WAL");

            // Create tables if they don't exist
            createTables(stmt);
            ensureSchemaMigrationsTable(stmt);

            // Apply lightweight migrations for existing databases
            applyMigrations(stmt);

            // Apply tracked SQL migrations for additive schema/data upgrades
            applyManagedMigrations(conn);

            // Ensure database integrity and repair corrupted indexes without deleting data
            runIntegrityCheckAndRepair(conn);

            logger.info("SQLite database initialized: {}", dbFile.getAbsolutePath());
        }
    }

    /**
     * Runs PRAGMA integrity_check and attempts non-destructive repair if corruption
     * is detected.
     * We only reindex and vacuum; no data is dropped.
     */
    private static void runIntegrityCheckAndRepair(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                if (rs.next()) {
                    String result = rs.getString(1);
                    if (result != null && !"ok".equalsIgnoreCase(result.trim())) {
                        logger.warn("SQLite integrity check reported issues: {}. Attempting REINDEX and VACUUM.",
                                result);
                        stmt.execute("REINDEX;");
                        stmt.execute("VACUUM;");
                        logger.info("SQLite REINDEX and VACUUM completed after integrity issues.");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to run SQLite integrity check/repair", e);
        }
    }

    private static void applyMigrations(Statement stmt) {
        // Add paid_amount to sales table if missing
        try {
            stmt.execute("ALTER TABLE sales ADD COLUMN paid_amount REAL DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
        }

        boolean customerAccountTypeAdded = false;
        try {
            stmt.execute("ALTER TABLE customers ADD COLUMN account_type TEXT DEFAULT 'CUSTOMER'");
            customerAccountTypeAdded = true;
        } catch (SQLException ignored) {
        }

        if (customerAccountTypeAdded) {
            try {
                stmt.execute("""
                        UPDATE customers
                        SET account_type = CASE
                            WHEN id IN (SELECT DISTINCT customer_id FROM vouchers WHERE voucher_type = 'PURCHASE' AND customer_id IS NOT NULL)
                             AND id IN (SELECT DISTINCT customer_id FROM sales WHERE customer_id IS NOT NULL)
                            THEN 'BOTH'
                            WHEN id IN (SELECT DISTINCT customer_id FROM vouchers WHERE voucher_type = 'PURCHASE' AND customer_id IS NOT NULL)
                            THEN 'SUPPLIER'
                            ELSE COALESCE(account_type, 'CUSTOMER')
                        END
                        WHERE account_type IS NULL OR account_type = 'CUSTOMER'
                        """);
            } catch (SQLException ignored) {
            }
        }

        // Add USD price columns to products table
        try {
            stmt.execute("ALTER TABLE products ADD COLUMN cost_price_usd REAL");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE products ADD COLUMN unit_price_usd REAL");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE products ADD COLUMN wholesale_price_usd REAL");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE products ADD COLUMN special_price_usd REAL");
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("ALTER TABLE vouchers ADD COLUMN supplier_invoice_number TEXT");
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("ALTER TABLE products ADD COLUMN base_unit TEXT");
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("ALTER TABLE sale_items ADD COLUMN sold_unit TEXT");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE sale_items ADD COLUMN conversion_factor REAL DEFAULT 1");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE sale_items ADD COLUMN base_quantity REAL DEFAULT 0");
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("ALTER TABLE cashbox_ledger ADD COLUMN related_created_by TEXT");
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("""
                    UPDATE products
                    SET base_unit = COALESCE(NULLIF(base_unit, ''), NULLIF(unit_of_measure, ''), 'قطعة')
                    WHERE base_unit IS NULL OR TRIM(base_unit) = ''
                    """);
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("""
                    UPDATE sale_items
                    SET conversion_factor = 1
                    WHERE conversion_factor IS NULL OR conversion_factor <= 0
                    """);
        } catch (SQLException ignored) {
        }
        try {
            stmt.execute("""
                    UPDATE sale_items
                    SET base_quantity = COALESCE(quantity, 0) * COALESCE(conversion_factor, 1)
                    WHERE base_quantity IS NULL OR base_quantity <= 0
                    """);
        } catch (SQLException ignored) {
        }

        try {
            stmt.execute("""
                    INSERT OR IGNORE INTO product_units (
                        product_id,
                        unit_name,
                        barcode,
                        conversion_factor,
                        sale_price,
                        sale_price_usd,
                        is_default,
                        is_active,
                        created_at,
                        updated_at
                    )
                    SELECT
                        p.id,
                        COALESCE(NULLIF(p.base_unit, ''), NULLIF(p.unit_of_measure, ''), 'قطعة'),
                        NULLIF(p.barcode, ''),
                        1,
                        p.unit_price,
                        p.unit_price_usd,
                        1,
                        1,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    FROM products p
                    WHERE NOT EXISTS (
                        SELECT 1 FROM product_units u WHERE u.product_id = p.id
                    )
                    """);
        } catch (SQLException ignored) {
        }
    }

    private static void ensureSchemaMigrationsTable(Statement stmt) throws SQLException {
        stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    migration_name TEXT PRIMARY KEY,
                    applied_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void applyManagedMigrations(Connection conn) {
        for (String migrationResource : MANAGED_MIGRATIONS) {
            try {
                if (isMigrationApplied(conn, migrationResource)) {
                    continue;
                }
                applySqlMigration(conn, migrationResource);
                markMigrationApplied(conn, migrationResource);
                logger.info("Applied managed migration: {}", migrationResource);
            } catch (Exception e) {
                logger.error("Failed to apply managed migration: {}", migrationResource, e);
                throw new RuntimeException("Failed to apply managed migration: " + migrationResource, e);
            }
        }
    }

    private static boolean isMigrationApplied(Connection conn, String migrationName) throws SQLException {
        try (var ps = conn.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE migration_name = ?")) {
            ps.setString(1, migrationName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void markMigrationApplied(Connection conn, String migrationName) throws SQLException {
        try (var ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO schema_migrations (migration_name) VALUES (?)")) {
            ps.setString(1, migrationName);
            ps.executeUpdate();
        }
    }

    private static void applySqlMigration(Connection conn, String migrationResource) throws IOException, SQLException {
        String sql = readResource(migrationResource);
        List<String> statements = splitSqlStatements(sql);

        try (Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    stmt.execute(trimmed);
                } catch (SQLException e) {
                    if (isIgnorableMigrationError(e)) {
                        logger.debug("Ignoring idempotent migration error for {}: {}", migrationResource,
                                e.getMessage());
                        continue;
                    }
                    throw e;
                }
            }
        }
    }

    private static String readResource(String migrationResource) throws IOException {
        ClassLoader classLoader = DatabaseManager.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(migrationResource)) {
            if (inputStream == null) {
                throw new IOException("Migration resource not found: " + migrationResource);
            }

            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            return builder.toString();
        }
    }

    private static List<String> splitSqlStatements(String sql) {
        StringBuilder current = new StringBuilder();

        for (String line : sql.split("\n")) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
        }

        List<String> parsed = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        boolean inSingleQuote = false;

        for (int i = 0; i < current.length(); i++) {
            char ch = current.charAt(i);
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
            }

            if (ch == ';' && !inSingleQuote) {
                parsed.add(statement.toString());
                statement.setLength(0);
            } else {
                statement.append(ch);
            }
        }

        if (statement.length() > 0) {
            parsed.add(statement.toString());
        }

        return parsed;
    }

    private static boolean isIgnorableMigrationError(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("duplicate column name")
                || normalized.contains("already exists")
                || normalized.contains("duplicate key");
    }

    private static void createTables(Statement stmt) throws SQLException {
        // Customers table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        customer_code TEXT UNIQUE NOT NULL,
                        name TEXT NOT NULL,
                        phone_number TEXT,
                        address TEXT,
                        account_type TEXT DEFAULT 'CUSTOMER',
                        email TEXT,
                        tax_id TEXT,
                        credit_limit REAL,
                        current_balance REAL DEFAULT 0,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

        // Products table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_code TEXT UNIQUE NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        category TEXT,
                        unit_price REAL,
                        cost_price REAL,
                        quantity_in_stock INTEGER DEFAULT 0,
                        minimum_stock INTEGER DEFAULT 0,
                        maximum_stock INTEGER,
                        unit_of_measure TEXT,
                        base_unit TEXT,
                        barcode TEXT,
                        is_active BOOLEAN DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

        // Sales table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sales (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sale_code TEXT UNIQUE NOT NULL,
                        customer_id INTEGER NOT NULL,
                        sale_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        total_amount REAL NOT NULL,
                        discount_amount REAL DEFAULT 0,
                        tax_amount REAL DEFAULT 0,
                        final_amount REAL NOT NULL,
                        paid_amount REAL DEFAULT 0,
                        payment_method TEXT,
                        payment_status TEXT DEFAULT 'PENDING',
                        notes TEXT,
                        created_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (customer_id) REFERENCES customers(id)
                    )
                """);

        // Sale items table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sale_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sale_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        total_price REAL NOT NULL,
                        discount_percentage REAL DEFAULT 0,
                        discount_amount REAL DEFAULT 0,
                        price_type TEXT,
                        sold_unit TEXT,
                        conversion_factor REAL DEFAULT 1,
                        base_quantity REAL DEFAULT 0,
                        batch_id INTEGER,
                        batch_number_snapshot TEXT,
                        expiration_date_snapshot TEXT,
                        unit_cost_snapshot REAL,
                        FOREIGN KEY (sale_id) REFERENCES sales(id),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(id)
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sale_item_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sale_item_id INTEGER NOT NULL,
                        batch_id INTEGER NOT NULL,
                        quantity_sold REAL NOT NULL,
                        batch_number_snapshot TEXT,
                        expiration_date_snapshot TEXT,
                        unit_cost_snapshot REAL,
                        quantity_before REAL,
                        quantity_after REAL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (sale_item_id) REFERENCES sale_items(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(id)
                    )
                """);

        // Product units / packaging table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_units (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_id INTEGER NOT NULL,
                        unit_name TEXT NOT NULL,
                        barcode TEXT UNIQUE,
                        conversion_factor REAL NOT NULL DEFAULT 1,
                        sale_price REAL,
                        sale_price_usd REAL,
                        is_default BOOLEAN DEFAULT 0,
                        is_active BOOLEAN DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(product_id, unit_name),
                        FOREIGN KEY (product_id) REFERENCES products(id)
                    )
                """);

        // Receipts table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        receipt_number TEXT UNIQUE NOT NULL,
                        sale_id INTEGER NOT NULL,
                        receipt_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        template TEXT DEFAULT 'DEFAULT',
                        file_path TEXT,
                        is_printed BOOLEAN DEFAULT 0,
                        printed_at DATETIME,
                        printed_by TEXT,
                        notes TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (sale_id) REFERENCES sales(id)
                    )
                """);

        // Categories table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT UNIQUE NOT NULL,
                        description TEXT,
                        parent_id INTEGER,
                        is_active BOOLEAN DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (parent_id) REFERENCES categories(id)
                    )
                """);

        // Sale Returns table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sale_returns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        return_code TEXT UNIQUE NOT NULL,
                        sale_id INTEGER NOT NULL,
                        customer_id INTEGER NOT NULL,
                        return_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        total_return_amount REAL NOT NULL,
                        return_reason TEXT,
                        return_status TEXT DEFAULT 'PENDING',
                        notes TEXT,
                        processed_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (sale_id) REFERENCES sales(id),
                        FOREIGN KEY (customer_id) REFERENCES customers(id)
                    )
                """);

        // Return Items table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS return_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        return_id INTEGER NOT NULL,
                        product_id INTEGER NOT NULL,
                        original_sale_item_id INTEGER,
                        quantity INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        total_price REAL NOT NULL,
                        return_reason TEXT,
                        condition_status TEXT DEFAULT 'GOOD',
                        batch_id INTEGER,
                        batch_number_snapshot TEXT,
                        expiration_date_snapshot TEXT,
                        sale_item_batch_id INTEGER,
                        FOREIGN KEY (return_id) REFERENCES sale_returns(id),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (original_sale_item_id) REFERENCES sale_items(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(batch_id),
                        FOREIGN KEY (sale_item_batch_id) REFERENCES sale_item_batches(id)
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS return_item_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        return_item_id INTEGER NOT NULL,
                        sale_item_batch_id INTEGER,
                        batch_id INTEGER NOT NULL,
                        quantity_returned REAL NOT NULL DEFAULT 0,
                        batch_number_snapshot TEXT,
                        expiration_date_snapshot TEXT,
                        quantity_before REAL,
                        quantity_after REAL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (return_item_id) REFERENCES return_items(id),
                        FOREIGN KEY (sale_item_batch_id) REFERENCES sale_item_batches(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(batch_id)
                    )
                """);

        // Customer Payments table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customer_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        payment_code TEXT UNIQUE NOT NULL,
                        customer_id INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        payment_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        payment_method TEXT,
                        notes TEXT,
                        processed_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (customer_id) REFERENCES customers(id)
                    )
                """);

        // Users table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE NOT NULL,
                        display_name TEXT NOT NULL,
                        pin_hash TEXT NOT NULL,
                        role TEXT NOT NULL DEFAULT 'SELLER',
                        is_active BOOLEAN DEFAULT 1,
                        failed_attempts INTEGER DEFAULT 0,
                        locked_until DATETIME,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        last_login_at DATETIME,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    )
                """);

        // Vouchers table (سندات القبض والدفع)
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS vouchers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        voucher_number TEXT UNIQUE NOT NULL,
                        supplier_invoice_number TEXT,
                        voucher_type TEXT NOT NULL,
                        voucher_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        currency TEXT NOT NULL DEFAULT 'دينار',
                        exchange_rate REAL DEFAULT 1,
                        customer_id INTEGER,
                        cash_account TEXT,
                        amount REAL NOT NULL,
                        discount_percentage REAL DEFAULT 0,
                        discount_amount REAL DEFAULT 0,
                        net_amount REAL NOT NULL,
                        amount_in_words TEXT,
                        description TEXT,
                        payment_method TEXT,
                        reference_number TEXT,
                        is_installment BOOLEAN DEFAULT 0,
                        total_installments INTEGER,
                        installment_number INTEGER,
                        parent_voucher_id INTEGER,
                        notes TEXT,
                        created_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        is_cancelled BOOLEAN DEFAULT 0,
                        cancelled_at DATETIME,
                        cancelled_by TEXT,
                        cancel_reason TEXT,
                        FOREIGN KEY (customer_id) REFERENCES customers(id),
                        FOREIGN KEY (parent_voucher_id) REFERENCES vouchers(id)
                    )
                """);

        // Voucher Items table (عناصر السند - للمشتريات)
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS voucher_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        voucher_id INTEGER NOT NULL,
                        product_id INTEGER,
                        product_name TEXT,
                        quantity REAL DEFAULT 1,
                        unit_price REAL,
                        total_price REAL,
                        unit_of_measure TEXT,
                        batch_id INTEGER,
                        batch_number TEXT,
                        expiration_date TEXT,
                        notes TEXT,
                        add_to_inventory BOOLEAN DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(id)
                    )
                """);

        // Product batches table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS product_batches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_id INTEGER NOT NULL,
                        batch_number TEXT NOT NULL,
                        expiry_date DATE,
                        production_date DATE,
                        quantity REAL NOT NULL DEFAULT 0,
                        original_quantity REAL NOT NULL DEFAULT 0,
                        unit_cost REAL,
                        currency TEXT DEFAULT 'دينار',
                        supplier_customer_id INTEGER,
                        is_opening_batch BOOLEAN DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(product_id, batch_number),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (supplier_customer_id) REFERENCES customers(id)
                    )
                """);

        // Inventory movements table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_id INTEGER NOT NULL,
                        batch_id INTEGER,
                        movement_type TEXT NOT NULL,
                        reference_type TEXT,
                        reference_id INTEGER,
                        reference_item_id INTEGER,
                        quantity_delta REAL NOT NULL,
                        quantity_before REAL,
                        quantity_after REAL,
                        unit_cost_snapshot REAL,
                        note TEXT,
                        actor TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(id)
                    )
                """);

        // Purchase returns table
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS purchase_returns (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        customer_id INTEGER,
                        source_voucher_id INTEGER,
                        return_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        currency TEXT DEFAULT 'دينار',
                        total_amount REAL NOT NULL DEFAULT 0,
                        notes TEXT,
                        created_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (customer_id) REFERENCES customers(id),
                        FOREIGN KEY (source_voucher_id) REFERENCES vouchers(id)
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS purchase_return_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        purchase_return_id INTEGER NOT NULL,
                        source_voucher_item_id INTEGER,
                        product_id INTEGER,
                        batch_id INTEGER,
                        batch_number_snapshot TEXT,
                        expiration_date_snapshot TEXT,
                        quantity REAL NOT NULL DEFAULT 0,
                        unit_cost REAL,
                        line_total REAL,
                        reason TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (purchase_return_id) REFERENCES purchase_returns(id),
                        FOREIGN KEY (source_voucher_item_id) REFERENCES voucher_items(id),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (batch_id) REFERENCES product_batches(id)
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS cashbox_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        transaction_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        entry_type TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        amount REAL NOT NULL DEFAULT 0,
                        currency TEXT DEFAULT 'دينار',
                        source_type TEXT,
                        source_id INTEGER,
                        source_item_id INTEGER DEFAULT 0,
                        customer_id INTEGER,
                        supplier_id INTEGER,
                        account_id INTEGER,
                        payment_method TEXT,
                        description TEXT,
                        created_by TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        voided BOOLEAN DEFAULT 0,
                        void_reason TEXT,
                        FOREIGN KEY (customer_id) REFERENCES customers(id),
                        FOREIGN KEY (supplier_id) REFERENCES customers(id),
                        FOREIGN KEY (account_id) REFERENCES customers(id)
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS daily_closings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        closing_date DATE NOT NULL UNIQUE,
                        opening_cash REAL DEFAULT 0,
                        total_cash_in REAL DEFAULT 0,
                        total_cash_out REAL DEFAULT 0,
                        expected_cash REAL DEFAULT 0,
                        actual_cash REAL DEFAULT 0,
                        difference_amount REAL DEFAULT 0,
                        closed_by TEXT,
                        closed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        notes TEXT,
                        status TEXT DEFAULT 'CLOSED'
                    )
                """);

        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        action_type TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        entity_id INTEGER,
                        user_id INTEGER,
                        username_snapshot TEXT,
                        role_snapshot TEXT,
                        details TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    )
                """);

        // Installments table (الأقساط)
        stmt.execute("""
                    CREATE TABLE IF NOT EXISTS installments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        parent_voucher_id INTEGER NOT NULL,
                        installment_number INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        due_date DATE NOT NULL,
                        is_paid BOOLEAN DEFAULT 0,
                        paid_amount REAL DEFAULT 0,
                        paid_date DATE,
                        payment_voucher_id INTEGER,
                        notes TEXT,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (parent_voucher_id) REFERENCES vouchers(id),
                        FOREIGN KEY (payment_voucher_id) REFERENCES vouchers(id)
                    )
                """);

        // Create indexes for better performance
        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_customers_code ON customers(customer_code)",
            "CREATE INDEX IF NOT EXISTS idx_products_code ON products(product_code)",
            "CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)",
            "CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)",
            "CREATE INDEX IF NOT EXISTS idx_product_units_product ON product_units(product_id)",
            "CREATE INDEX IF NOT EXISTS idx_product_units_barcode ON product_units(barcode)",
            "CREATE INDEX IF NOT EXISTS idx_sales_code ON sales(sale_code)",
            "CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_sale_items_batch ON sale_items(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_sale_item_batches_sale_item ON sale_item_batches(sale_item_id)",
            "CREATE INDEX IF NOT EXISTS idx_sale_item_batches_batch ON sale_item_batches(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_return_items_batch ON return_items(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_return_items_sale_item_batch ON return_items(sale_item_batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_return_item_batches_return_item ON return_item_batches(return_item_id)",
            "CREATE INDEX IF NOT EXISTS idx_return_item_batches_sale_item_batch ON return_item_batches(sale_item_batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_return_item_batches_batch ON return_item_batches(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_receipts_number ON receipts(receipt_number)",
            "CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name)",
            "CREATE INDEX IF NOT EXISTS idx_returns_code ON sale_returns(return_code)",
            "CREATE INDEX IF NOT EXISTS idx_returns_sale ON sale_returns(sale_id)",
            "CREATE INDEX IF NOT EXISTS idx_returns_customer ON sale_returns(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_payments_code ON customer_payments(payment_code)",
            "CREATE INDEX IF NOT EXISTS idx_payments_customer ON customer_payments(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)",
            "CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)",
            "CREATE INDEX IF NOT EXISTS idx_vouchers_number ON vouchers(voucher_number)",
            "CREATE INDEX IF NOT EXISTS idx_vouchers_supplier_invoice ON vouchers(supplier_invoice_number)",
            "CREATE INDEX IF NOT EXISTS idx_vouchers_type ON vouchers(voucher_type)",
            "CREATE INDEX IF NOT EXISTS idx_vouchers_customer ON vouchers(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_vouchers_date ON vouchers(voucher_date)",
            "CREATE INDEX IF NOT EXISTS idx_voucher_items_voucher ON voucher_items(voucher_id)",
            "CREATE INDEX IF NOT EXISTS idx_voucher_items_batch_id ON voucher_items(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_voucher_items_batch_number ON voucher_items(batch_number)",
            "CREATE INDEX IF NOT EXISTS idx_product_batches_product ON product_batches(product_id)",
            "CREATE INDEX IF NOT EXISTS idx_product_batches_batch_number ON product_batches(batch_number)",
            "CREATE INDEX IF NOT EXISTS idx_product_batches_expiry ON product_batches(expiry_date)",
            "CREATE INDEX IF NOT EXISTS idx_product_batches_supplier ON product_batches(supplier_customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_product ON inventory_movements(product_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_batch ON inventory_movements(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_type ON inventory_movements(movement_type)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference ON inventory_movements(reference_type, reference_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference_item ON inventory_movements(reference_item_id)",
            "CREATE INDEX IF NOT EXISTS idx_inventory_movements_created_at ON inventory_movements(created_at)",
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_movements_item_batch_ref ON inventory_movements(movement_type, reference_type, reference_id, reference_item_id, batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_returns_customer ON purchase_returns(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_returns_voucher ON purchase_returns(source_voucher_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_returns_date ON purchase_returns(return_date)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_return_items_return ON purchase_return_items(purchase_return_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_return_items_source_item ON purchase_return_items(source_voucher_item_id)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_return_items_batch ON purchase_return_items(batch_id)",
            "CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_date ON cashbox_ledger(transaction_date)",
            "CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_entry_type ON cashbox_ledger(entry_type)",
            "CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_source ON cashbox_ledger(source_type, source_id)",
            "CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_customer ON cashbox_ledger(customer_id)",
            "CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_supplier ON cashbox_ledger(supplier_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_cashbox_ledger_source_entry ON cashbox_ledger(entry_type, source_type, source_id, source_item_id)",
            "CREATE INDEX IF NOT EXISTS idx_daily_closings_date ON daily_closings(closing_date)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_action_type ON audit_log(action_type)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at)",
            "CREATE INDEX IF NOT EXISTS idx_installments_voucher ON installments(parent_voucher_id)",
            "CREATE INDEX IF NOT EXISTS idx_installments_due_date ON installments(due_date)"
        };

        for (String indexSql : indexes) {
            try {
                stmt.execute(indexSql);
            } catch (SQLException e) {
                // Ignore missing column errors, migrations will add them and create the index later
                logger.debug("Skipped index creation (may need migration first): {}", e.getMessage());
            }
        }

        logger.info("Database tables created successfully");
    }

    private static void configureHibernate() {
        try {
            Configuration configuration = new Configuration();

            // Hibernate properties for SQLite
            configuration.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC");
            configuration.setProperty("hibernate.connection.url", DB_URL);
            configuration.setProperty("hibernate.dialect", "com.pharmax.database.SQLiteDialect");
            configuration.setProperty("hibernate.hbm2ddl.auto", "update");
            configuration.setProperty("hibernate.show_sql", "false");
            configuration.setProperty("hibernate.format_sql", "true");
            configuration.setProperty("hibernate.connection.pool_size", "10");
            configuration.setProperty("hibernate.current_session_context_class", "thread");

            // Add entity classes
            configuration.addAnnotatedClass(com.pharmax.model.Customer.class);
            configuration.addAnnotatedClass(com.pharmax.model.Product.class);
            configuration.addAnnotatedClass(com.pharmax.model.ProductUnit.class);
            configuration.addAnnotatedClass(com.pharmax.model.Sale.class);
            configuration.addAnnotatedClass(com.pharmax.model.SaleItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.SaleItemBatch.class);
            configuration.addAnnotatedClass(com.pharmax.model.Receipt.class);
            configuration.addAnnotatedClass(com.pharmax.model.Category.class);
            configuration.addAnnotatedClass(com.pharmax.model.SaleReturn.class);
            configuration.addAnnotatedClass(com.pharmax.model.ReturnItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.ReturnItemBatch.class);
            configuration.addAnnotatedClass(com.pharmax.model.CustomerPayment.class);
            configuration.addAnnotatedClass(com.pharmax.model.User.class);
            configuration.addAnnotatedClass(com.pharmax.model.Voucher.class);
            configuration.addAnnotatedClass(com.pharmax.model.VoucherItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.Installment.class);
            configuration.addAnnotatedClass(com.pharmax.model.ProductBatch.class);
            configuration.addAnnotatedClass(com.pharmax.model.InventoryMovement.class);
            configuration.addAnnotatedClass(com.pharmax.model.PurchaseReturn.class);
            configuration.addAnnotatedClass(com.pharmax.model.PurchaseReturnItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.CashboxLedger.class);
            configuration.addAnnotatedClass(com.pharmax.model.CashboxManualOpening.class);
            configuration.addAnnotatedClass(com.pharmax.model.DailyClosing.class);
            configuration.addAnnotatedClass(com.pharmax.model.AuditLog.class);
            configuration.addAnnotatedClass(com.pharmax.model.QuickSaleGroup.class);
            configuration.addAnnotatedClass(com.pharmax.model.QuickSaleItem.class);

            sessionFactory = configuration.buildSessionFactory();
            logger.info("Hibernate configured successfully");

        } catch (Exception e) {
            logger.error("Failed to configure Hibernate", e);
            throw new RuntimeException("Hibernate configuration failed", e);
        }
    }

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            initialize();
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
            logger.info("Database connection closed");
        }
    }
}
