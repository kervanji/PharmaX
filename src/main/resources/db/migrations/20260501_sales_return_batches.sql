ALTER TABLE return_items ADD COLUMN batch_id INTEGER;
ALTER TABLE return_items ADD COLUMN batch_number_snapshot TEXT;
ALTER TABLE return_items ADD COLUMN expiration_date_snapshot TEXT;
ALTER TABLE return_items ADD COLUMN sale_item_batch_id INTEGER;

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
    FOREIGN KEY (batch_id) REFERENCES product_batches(id)
);

CREATE INDEX IF NOT EXISTS idx_return_items_batch ON return_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_return_items_sale_item_batch ON return_items(sale_item_batch_id);
CREATE INDEX IF NOT EXISTS idx_return_item_batches_return_item ON return_item_batches(return_item_id);
CREATE INDEX IF NOT EXISTS idx_return_item_batches_sale_item_batch ON return_item_batches(sale_item_batch_id);
CREATE INDEX IF NOT EXISTS idx_return_item_batches_batch ON return_item_batches(batch_id);
