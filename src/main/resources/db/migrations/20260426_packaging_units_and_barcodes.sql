-- PharmaX packaging units and barcode labels migration.
-- The application also applies these changes through DatabaseManager for existing SQLite files.

ALTER TABLE products ADD COLUMN base_unit TEXT;

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
);

CREATE INDEX IF NOT EXISTS idx_product_units_product ON product_units(product_id);
CREATE INDEX IF NOT EXISTS idx_product_units_barcode ON product_units(barcode);

ALTER TABLE sale_items ADD COLUMN sold_unit TEXT;
ALTER TABLE sale_items ADD COLUMN conversion_factor REAL DEFAULT 1;
ALTER TABLE sale_items ADD COLUMN base_quantity REAL DEFAULT 0;

UPDATE products
SET base_unit = COALESCE(NULLIF(base_unit, ''), NULLIF(unit_of_measure, ''), 'قطعة')
WHERE base_unit IS NULL OR TRIM(base_unit) = '';

UPDATE sale_items
SET conversion_factor = 1
WHERE conversion_factor IS NULL OR conversion_factor <= 0;

UPDATE sale_items
SET base_quantity = COALESCE(quantity, 0) * COALESCE(conversion_factor, 1)
WHERE base_quantity IS NULL OR base_quantity <= 0;

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
);
