CREATE TABLE IF NOT EXISTS cashbox_manual_opening (
    opening_date DATE PRIMARY KEY,
    opening_cash REAL NOT NULL DEFAULT 0,
    set_by TEXT,
    set_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cashbox_manual_opening_date ON cashbox_manual_opening(opening_date);
