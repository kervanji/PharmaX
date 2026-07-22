-- Unlimited-stock sales are real sales activity even though they do not change
-- a physical batch balance. Backfill their missing report movements safely.
INSERT INTO inventory_movements (
    product_id,
    batch_id,
    movement_type,
    reference_type,
    reference_id,
    reference_item_id,
    quantity_delta,
    quantity_before,
    quantity_after,
    unit_cost_snapshot,
    note,
    actor,
    created_at
)
SELECT
    si.product_id,
    NULL,
    'sale',
    'sale',
    si.sale_id,
    si.id,
    -COALESCE(NULLIF(si.base_quantity, 0), si.quantity * COALESCE(NULLIF(si.conversion_factor, 0), 1)),
    NULL,
    NULL,
    COALESCE(si.unit_cost_snapshot, p.cost_price),
    'Sale ' || s.sale_code || ' - ' || p.name || ' - unlimited stock',
    s.created_by,
    s.sale_date
FROM sale_items si
JOIN sales s ON s.id = si.sale_id
JOIN products p ON p.id = si.product_id
WHERE COALESCE(p.is_unlimited_stock, 0) = 1
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_movements m
      WHERE m.movement_type = 'sale'
        AND m.reference_type = 'sale'
        AND m.reference_id = si.sale_id
        AND m.reference_item_id = si.id
  );
