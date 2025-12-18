-- V5__Support_sofa_higress.sql
-- Add sofa higress related tables and columns
-- Description: Add sofa higress related tables and columns

START TRANSACTION;

-- ========================================
-- Add api_definition_ids column to product_ref table
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'gateway';
SET @columnname = 'sofa_higress_config';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `gateway` ADD COLUMN `sofa_higress_config` json DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

COMMIT;
