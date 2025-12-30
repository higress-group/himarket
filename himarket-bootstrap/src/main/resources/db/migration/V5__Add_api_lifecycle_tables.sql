-- V4__Add_api_lifecycle_tables.sql
-- Add API lifecycle management tables
-- Description: Support for API Definition, Endpoint, Publish Record and History

START TRANSACTION;

-- ========================================
-- API Definition table
-- ========================================
CREATE TABLE IF NOT EXISTS `api_definition` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `api_definition_id` varchar(64) NOT NULL COMMENT 'API Definition unique identifier',
    `name` varchar(255) NOT NULL COMMENT 'API name',
    `description` text COMMENT 'API description',
    `type` varchar(32) NOT NULL COMMENT 'API type: MCP_SERVER, REST_API, AGENT_API, MODEL_API',
    `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'Status: DRAFT, PUBLISHING, PUBLISHED, DEPRECATED, ARCHIVED',
    `version` varchar(32) COMMENT 'API version',
    `properties` json COMMENT 'API Properties (formerly extensions from PublishConfig)',
    `metadata` json COMMENT 'Metadata (tags, documentation, etc.)',
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_definition_id` (`api_definition_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Definition table';

-- ========================================
-- API Endpoint table
-- ========================================
CREATE TABLE IF NOT EXISTS `api_endpoint` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `endpoint_id` varchar(64) NOT NULL COMMENT 'Endpoint unique identifier',
    `api_definition_id` varchar(64) NOT NULL COMMENT 'Associated API Definition ID',
    `type` varchar(32) NOT NULL COMMENT 'Endpoint type: MCP_TOOL, REST_ROUTE, AGENT, MODEL',
    `name` varchar(255) NOT NULL COMMENT 'Endpoint name',
    `description` text COMMENT 'Endpoint description',
    `sort_order` int DEFAULT 0 COMMENT 'Display order',
    `config` json NOT NULL COMMENT 'Type-specific configuration',
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_endpoint_id` (`endpoint_id`),
    KEY `idx_api_definition_id` (`api_definition_id`),
    KEY `idx_type` (`type`),
    CONSTRAINT `fk_endpoint_api_definition` FOREIGN KEY (`api_definition_id`) REFERENCES `api_definition` (`api_definition_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Endpoint table';

-- ========================================
-- API Publish Record table
-- ========================================
CREATE TABLE IF NOT EXISTS `api_publish_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `record_id` varchar(64) NOT NULL COMMENT 'Record unique identifier',
    `api_definition_id` varchar(64) NOT NULL COMMENT 'Associated API Definition ID',
    `gateway_id` varchar(64) NOT NULL COMMENT 'Gateway ID',
    `gateway_name` varchar(255) COMMENT 'Gateway name',
    `gateway_type` varchar(32) NOT NULL COMMENT 'Gateway type',
    `version` varchar(32) COMMENT 'Published API version',
    `status` varchar(32) NOT NULL COMMENT 'Status: ACTIVE, INACTIVE, FAILED',
    `action` varchar(32) COMMENT 'Action: PUBLISH, UNPUBLISH, UPDATE',
    `publish_config` json COMMENT 'Publish configuration including serviceConfig, extensions, etc.',
    `gateway_resource_id` varchar(255) COMMENT 'Gateway-side resource ID',
    `access_endpoint` varchar(512) COMMENT 'Access endpoint URL',
    `error_message` text COMMENT 'Error message if failed',
    `publish_note` text COMMENT 'Publish note',
    `operator` varchar(64) COMMENT 'Operator user ID',
    `snapshot` json COMMENT 'API Definition snapshot',
    `published_at` datetime(3) COMMENT 'Published timestamp',
    `last_sync_at` datetime(3) COMMENT 'Last synchronization timestamp',
    `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_id` (`record_id`),
    KEY `idx_api_definition_id` (`api_definition_id`),
    KEY `idx_gateway_id` (`gateway_id`),
    CONSTRAINT `fk_publish_record_api_definition` FOREIGN KEY (`api_definition_id`) REFERENCES `api_definition` (`api_definition_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Publish Record table';



-- ========================================
-- Add api_definition_ids column to product_ref table
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product_ref';
SET @columnname = 'api_definition_ids';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product_ref` ADD COLUMN `api_definition_ids` json COMMENT ''Associated API Definition ID list (for MANAGED APIs)'''
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
