-- Add production_date column to product_batches for tracking manufacturing date
ALTER TABLE product_batches ADD COLUMN production_date DATE;
