-- V4__Optimize_resource_definition.sql
-- Optimize resource definition for REST API design
-- Description: Add unique identifiers (subscription_id, publication_id) for better REST API compliance

START TRANSACTION;

-- ========================================
-- Add subscription_id column (nullable)
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product_subscription';
SET @columnname = 'subscription_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product_subscription` ADD COLUMN `subscription_id` varchar(64) DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- Initialize subscription_id for existing records
-- Format: subscription-{24 hex characters} to match ObjectId format
-- Use SHA2-256 hash with UUID for random-looking IDs (prevents exposing sequence)
-- SHA2 produces 64 hex chars, we take first 24 to match ObjectId format
-- UUID() ensures each row gets a unique random value
-- ========================================
UPDATE `product_subscription`
SET `subscription_id` = CONCAT(
    'subscription-',
    LOWER(SUBSTRING(SHA2(CONCAT(id, '_', UUID()), 256), 1, 24))
)
WHERE `subscription_id` IS NULL;

-- Note: The UNIQUE INDEX creation below will automatically verify:
-- 1. No duplicate subscription_id exists (will fail if duplicates found)
-- 2. Data integrity is maintained (transaction will rollback on failure)

-- ========================================
-- Add unique index on subscription_id
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product_subscription';
SET @indexname = 'uk_subscription_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (INDEX_NAME = @indexname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product_subscription` ADD UNIQUE KEY `uk_subscription_id` (`subscription_id`)'
));
PREPARE addIndexIfNotExists FROM @preparedStatement;
EXECUTE addIndexIfNotExists;
DEALLOCATE PREPARE addIndexIfNotExists;

-- ========================================
-- Add publication_id column to publication table
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'publication';
SET @columnname = 'publication_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `publication` ADD COLUMN `publication_id` varchar(64) DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- Initialize publication_id for existing records
-- Format: publication-{24 hex characters} to match ObjectId format
-- Use SHA2-256 hash with UUID for random-looking IDs (prevents exposing sequence)
-- SHA2 produces 64 hex chars, we take first 24 to match ObjectId format
-- UUID() ensures each row gets a unique random value
-- ========================================
UPDATE `publication`
SET `publication_id` = CONCAT(
    'publication-',
    LOWER(SUBSTRING(SHA2(CONCAT(id, '_', UUID()), 256), 1, 24))
)
WHERE `publication_id` IS NULL;

-- Note: The UNIQUE INDEX creation below will automatically verify:
-- 1. No duplicate publication_id exists (will fail if duplicates found)
-- 2. Data integrity is maintained (transaction will rollback on failure)

-- ========================================
-- Add unique index on publication_id
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'publication';
SET @indexname = 'uk_publication_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (INDEX_NAME = @indexname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `publication` ADD UNIQUE KEY `uk_publication_id` (`publication_id`)'
));
PREPARE addIndexIfNotExists FROM @preparedStatement;
EXECUTE addIndexIfNotExists;
DEALLOCATE PREPARE addIndexIfNotExists;

COMMIT;

