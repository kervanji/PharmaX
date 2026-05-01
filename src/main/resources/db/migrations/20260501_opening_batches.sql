INSERT INTO product_batches (
    product_id,
    batch_number,
    expiry_date,
    quantity,
    original_quantity,
    unit_cost,
    currency,
    supplier_customer_id,
    is_opening_batch,
    status,
    created_at,
    updated_at
)
SELECT
    p.id,
    'OPENING-' || p.id,
    NULL,
    COALESCE(p.quantity_in_stock, 0),
    COALESCE(p.quantity_in_stock, 0),
    COALESCE(p.cost_price, p.cost_price_usd),
    CASE
        WHEN COALESCE(p.cost_price, 0) <= 0 AND COALESCE(p.cost_price_usd, 0) > 0 THEN 'دولار'
        ELSE 'دينار'
    END,
    NULL,
    1,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM products p
WHERE COALESCE(p.quantity_in_stock, 0) > 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_batches b
      WHERE b.product_id = p.id
        AND b.is_opening_batch = 1
  );

INSERT INTO inventory_movements (
    product_id,
    batch_id,
    movement_type,
    reference_type,
    reference_id,
    quantity_delta,
    unit_cost_snapshot,
    note,
    actor,
    created_at
)
SELECT
    p.id,
    b.id,
    'migration_opening_balance',
    'product_opening',
    p.id,
    COALESCE(p.quantity_in_stock, 0),
    COALESCE(p.cost_price, p.cost_price_usd),
    'Opening batch generated from existing product summary stock',
    'system',
    CURRENT_TIMESTAMP
FROM products p
JOIN product_batches b
    ON b.product_id = p.id
   AND b.batch_number = 'OPENING-' || p.id
WHERE COALESCE(p.quantity_in_stock, 0) > 0
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_movements m
      WHERE m.product_id = p.id
        AND m.batch_id = b.id
        AND m.movement_type = 'migration_opening_balance'
        AND m.reference_type = 'product_opening'
        AND m.reference_id = p.id
  );
