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
);

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
);

CREATE INDEX IF NOT EXISTS idx_purchase_returns_customer ON purchase_returns(customer_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_voucher ON purchase_returns(source_voucher_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_date ON purchase_returns(return_date);
CREATE INDEX IF NOT EXISTS idx_purchase_return_items_return ON purchase_return_items(purchase_return_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_items_source_item ON purchase_return_items(source_voucher_item_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_items_batch ON purchase_return_items(batch_id);
