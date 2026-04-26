package com.pharmax.database;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_URL = "jdbc:sqlite:pharmax.db";
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

            // Apply lightweight migrations for existing databases
            applyMigrations(stmt);

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
        // Add project_location to sales table if missing
        try {
            stmt.execute("ALTER TABLE sales ADD COLUMN project_location TEXT");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
        }

        // Add paid_amount to sales table if missing
        try {
            stmt.execute("ALTER TABLE sales ADD COLUMN paid_amount REAL DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
        }

        // Add project_name to vouchers table if missing
        try {
            stmt.execute("ALTER TABLE vouchers ADD COLUMN project_name TEXT");
        } catch (SQLException ignored) {
            // Column already exists or table missing; ignore
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
                        project_location TEXT,
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
                        project_location TEXT,
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
                        FOREIGN KEY (sale_id) REFERENCES sales(id),
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
                        FOREIGN KEY (return_id) REFERENCES sale_returns(id),
                        FOREIGN KEY (product_id) REFERENCES products(id),
                        FOREIGN KEY (original_sale_item_id) REFERENCES sale_items(id)
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
                        notes TEXT,
                        add_to_inventory BOOLEAN DEFAULT 1,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
                        FOREIGN KEY (product_id) REFERENCES products(id)
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
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_customers_code ON customers(customer_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_code ON products(product_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_code ON sales(sale_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_receipts_number ON receipts(receipt_number)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_returns_code ON sale_returns(return_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_returns_sale ON sale_returns(sale_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_returns_customer ON sale_returns(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_code ON customer_payments(payment_code)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_payments_customer ON customer_payments(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_vouchers_number ON vouchers(voucher_number)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_vouchers_type ON vouchers(voucher_type)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_vouchers_customer ON vouchers(customer_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_vouchers_date ON vouchers(voucher_date)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_voucher_items_voucher ON voucher_items(voucher_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_installments_voucher ON installments(parent_voucher_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_installments_due_date ON installments(due_date)");

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
            configuration.addAnnotatedClass(com.pharmax.model.Sale.class);
            configuration.addAnnotatedClass(com.pharmax.model.SaleItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.Receipt.class);
            configuration.addAnnotatedClass(com.pharmax.model.Category.class);
            configuration.addAnnotatedClass(com.pharmax.model.SaleReturn.class);
            configuration.addAnnotatedClass(com.pharmax.model.ReturnItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.CustomerPayment.class);
            configuration.addAnnotatedClass(com.pharmax.model.User.class);
            configuration.addAnnotatedClass(com.pharmax.model.Voucher.class);
            configuration.addAnnotatedClass(com.pharmax.model.VoucherItem.class);
            configuration.addAnnotatedClass(com.pharmax.model.Installment.class);

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
            logger.info("Database connection closed");
        }
    }
}
