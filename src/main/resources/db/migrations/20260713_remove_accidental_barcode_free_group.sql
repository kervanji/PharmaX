DELETE FROM quick_sale_items
WHERE group_id IN (
    SELECT id
    FROM quick_sale_groups
    WHERE name = 'أدوية بدون باركود'
);

DELETE FROM quick_sale_groups
WHERE name = 'أدوية بدون باركود';
