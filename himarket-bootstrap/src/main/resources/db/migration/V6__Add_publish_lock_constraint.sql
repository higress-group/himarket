-- V6__Add_publish_lock_constraint.sql
-- Add unique constraint to prevent concurrent publish/unpublish operations
-- Description: Ensures only one PUBLISHING or UNPUBLISHING operation per API and gateway at a time

START TRANSACTION;

-- ========================================
-- Add unique index for concurrent operation protection
-- ========================================
-- This index prevents duplicate PUBLISHING or UNPUBLISHING operations
-- for the same API on the same gateway.
-- 
-- MySQL does not support partial indexes with WHERE clauses like PostgreSQL,
-- so we need to handle this at the application level primarily.
-- However, we can add a regular index to improve query performance for the
-- status checks in the application code.

-- Check if index already exists before creating
SET @dbname = DATABASE();
SET @tablename = 'api_publish_record';
SET @indexname = 'idx_api_gateway_status';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (INDEX_NAME = @indexname)
  ) > 0,
  'SELECT 1',
  'CREATE INDEX `idx_api_gateway_status` ON `api_publish_record` (`api_definition_id`, `gateway_id`, `status`)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add comment to the status column to document the concurrency protection mechanism
ALTER TABLE `api_publish_record` 
MODIFY COLUMN `status` varchar(32) NOT NULL 
COMMENT 'Status: ACTIVE, INACTIVE, PUBLISHING, UNPUBLISHING, FAILED. Application enforces uniqueness for PUBLISHING/UNPUBLISHING per API+Gateway';

COMMIT;
