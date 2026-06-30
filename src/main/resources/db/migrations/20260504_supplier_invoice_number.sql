ALTER TABLE vouchers ADD COLUMN supplier_invoice_number TEXT;
CREATE INDEX IF NOT EXISTS idx_vouchers_supplier_invoice ON vouchers(supplier_invoice_number);
