-- V6__Add_api_lifecycle_tables.sql
-- Add API lifecycle management tables
-- Description: Support for API Definition, Endpoint, Publish Record and History

START TRANSACTION;

-- ========================================
-- API Definition table
-- ========================================
CREATE TABLE IF NOT EXISTS `api_definition` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `api_definition_id` varchar(64) NOT NULL,
    `name` varchar(255) NOT NULL,
    `description` text,
    `type` varchar(32) NOT NULL,
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
    `version` varchar(32),
    `policies` json,
    `spec` json,
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_definition_id` (`api_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- API Deployment table
-- ========================================
CREATE TABLE IF NOT EXISTS `api_deployment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `deployment_id` varchar(64) NOT NULL,
    `api_definition_id` varchar(64) NOT NULL,
    `gateway_id` varchar(64) NOT NULL,
    `version` varchar(32),
    `status` varchar(32) NOT NULL,
    `gateway_resource_config` json,
    `error_message` text,
    `description` text,
    `snapshot` json,
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_deployment_id` (`deployment_id`),
    KEY `idx_api_definition_id` (`api_definition_id`),
    KEY `idx_gateway_id` (`gateway_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- ========================================
-- Add api_definition_id column to product_ref table
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product_ref';
SET @columnname = 'api_definition_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product_ref` ADD COLUMN `api_definition_id` varchar(64)'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- Add sofa_higress_config column to gateway table
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

-- ========================================
-- Add sofa_higress_ref_config column to product_ref table
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product_ref';
SET @columnname = 'sofa_higress_ref_config';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product_ref` ADD COLUMN `sofa_higress_ref_config` json DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

COMMIT;
