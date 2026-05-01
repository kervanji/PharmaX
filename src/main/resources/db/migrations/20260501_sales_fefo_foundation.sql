ALTER TABLE sale_items ADD COLUMN batch_id INTEGER;
ALTER TABLE sale_items ADD COLUMN batch_number_snapshot TEXT;
ALTER TABLE sale_items ADD COLUMN expiration_date_snapshot TEXT;
ALTER TABLE sale_items ADD COLUMN unit_cost_snapshot REAL;

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
);

ALTER TABLE inventory_movements ADD COLUMN quantity_before REAL;
ALTER TABLE inventory_movements ADD COLUMN quantity_after REAL;

DROP INDEX IF EXISTS ux_inventory_movements_item_ref;

CREATE INDEX IF NOT EXISTS idx_sale_items_batch ON sale_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_sale_item_batches_sale_item ON sale_item_batches(sale_item_id);
CREATE INDEX IF NOT EXISTS idx_sale_item_batches_batch ON sale_item_batches(batch_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_movements_item_batch_ref
ON inventory_movements(movement_type, reference_type, reference_id, reference_item_id, batch_id);
