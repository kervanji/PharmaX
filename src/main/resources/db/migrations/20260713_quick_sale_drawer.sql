CREATE TABLE IF NOT EXISTS quick_sale_groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    icon_key TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quick_sale_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    product_unit_id INTEGER,
    image_data BLOB,
    image_mime_type TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    accent_color TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES quick_sale_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (product_unit_id) REFERENCES product_units(id) ON DELETE SET NULL,
    UNIQUE (group_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_quick_sale_items_group ON quick_sale_items(group_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_quick_sale_items_product ON quick_sale_items(product_id);

INSERT INTO quick_sale_groups (name, icon_key, sort_order, is_active)
SELECT 'الافتراضية', '★', 0, 1
WHERE NOT EXISTS (SELECT 1 FROM quick_sale_groups);

INSERT OR IGNORE INTO quick_sale_items (group_id, product_id, sort_order)
SELECT
    (SELECT id FROM quick_sale_groups ORDER BY sort_order, id LIMIT 1),
    p.id,
    ROW_NUMBER() OVER (ORDER BY p.name, p.id) - 1
FROM products p
WHERE COALESCE(p.is_quick_sale, 0) = 1;
