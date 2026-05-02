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
);

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
);

CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_date ON cashbox_ledger(transaction_date);
CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_entry_type ON cashbox_ledger(entry_type);
CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_source ON cashbox_ledger(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_customer ON cashbox_ledger(customer_id);
CREATE INDEX IF NOT EXISTS idx_cashbox_ledger_supplier ON cashbox_ledger(supplier_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cashbox_ledger_source_entry
ON cashbox_ledger(entry_type, source_type, source_id, source_item_id);

CREATE INDEX IF NOT EXISTS idx_daily_closings_date ON daily_closings(closing_date);
