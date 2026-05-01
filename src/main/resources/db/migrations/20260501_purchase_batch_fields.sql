ALTER TABLE voucher_items ADD COLUMN batch_id INTEGER;
ALTER TABLE voucher_items ADD COLUMN batch_number TEXT;
ALTER TABLE voucher_items ADD COLUMN expiration_date TEXT;

ALTER TABLE inventory_movements ADD COLUMN reference_item_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_voucher_items_batch_id ON voucher_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_voucher_items_batch_number ON voucher_items(batch_number);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference_item ON inventory_movements(reference_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_movements_item_ref
ON inventory_movements(movement_type, reference_type, reference_id, reference_item_id);
