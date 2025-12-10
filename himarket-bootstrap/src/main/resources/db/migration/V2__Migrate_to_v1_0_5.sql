-- V2__Migrate_to_v1_0_5.sql
-- Complete database schema migration for existing tables
-- Organized by table for better maintainability

START TRANSACTION;

-- ========================================
-- ConsumerCredential table modifications
-- ========================================

-- Convert config fields to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'consumer_credential' 
     AND COLUMN_NAME = 'apikey_config') != 'json',
    'ALTER TABLE `consumer_credential` MODIFY COLUMN `apikey_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'consumer_credential' 
     AND COLUMN_NAME = 'hmac_config') != 'json',
    'ALTER TABLE `consumer_credential` MODIFY COLUMN `hmac_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'consumer_credential' 
     AND COLUMN_NAME = 'jwt_config') != 'json',
    'ALTER TABLE `consumer_credential` MODIFY COLUMN `jwt_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Set consumer_id field length to 64
SET @sql = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'consumer_credential' 
     AND COLUMN_NAME = 'consumer_id') != 64,
    'ALTER TABLE `consumer_credential` MODIFY COLUMN `consumer_id` VARCHAR(64) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- ConsumerRef table modifications
-- ========================================

-- Convert gateway_config to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'consumer_ref' 
     AND COLUMN_NAME = 'gateway_config') != 'json',
    'ALTER TABLE `consumer_ref` MODIFY COLUMN `gateway_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- Developer table modifications
-- ========================================

-- Make username nullable
SET @sql = IF(
    (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'developer' 
     AND COLUMN_NAME = 'username') = 'NO',
    'ALTER TABLE `developer` MODIFY COLUMN `username` VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Make password_hash nullable
SET @sql = IF(
    (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'developer' 
     AND COLUMN_NAME = 'password_hash') = 'NO',
    'ALTER TABLE `developer` MODIFY COLUMN `password_hash` TEXT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Make email nullable
SET @sql = IF(
    (SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'developer' 
     AND COLUMN_NAME = 'email') = 'NO',
    'ALTER TABLE `developer` MODIFY COLUMN `email` VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ========================================
-- DeveloperExternalIdentity table modifications
-- ========================================

-- Convert raw_info_json to JSON type and make nullable
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'developer_external_identity' 
     AND COLUMN_NAME = 'raw_info_json') != 'json',
    'ALTER TABLE `developer_external_identity` MODIFY COLUMN `raw_info_json` JSON NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Convert auth_type column type from INT to VARCHAR(32)
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'developer_external_identity'
     AND COLUMN_NAME = 'auth_type') = 'int',
    'ALTER TABLE `developer_external_identity` MODIFY COLUMN `auth_type` VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Convert authType from enum index to string value
-- Only convert specific enum index values, leave others unchanged
UPDATE `developer_external_identity` 
SET `auth_type` = CASE 
    WHEN `auth_type` = '0' THEN 'LOCAL'
    WHEN `auth_type` = '1' THEN 'BUILTIN'
    WHEN `auth_type` = '2' THEN 'EXTERNAL'
    WHEN `auth_type` = '3' THEN 'OIDC'
    WHEN `auth_type` = '4' THEN 'OAUTH2'
    ELSE `auth_type`  -- Keep original value for any other cases
END
WHERE `auth_type` IN ('0', '1', '2', '3', '4');

-- Ensure column is VARCHAR(32) (may already be correct)
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'developer_external_identity' 
     AND COLUMN_NAME = 'auth_type') != 'varchar',
    'ALTER TABLE `developer_external_identity` MODIFY COLUMN `auth_type` VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- Gateway table modifications
-- ========================================

-- Convert config fields to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'gateway' 
     AND COLUMN_NAME = 'apig_config') != 'json',
    'ALTER TABLE `gateway` MODIFY COLUMN `apig_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'gateway' 
     AND COLUMN_NAME = 'adp_ai_gateway_config') != 'json',
    'ALTER TABLE `gateway` MODIFY COLUMN `adp_ai_gateway_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'gateway' 
     AND COLUMN_NAME = 'higress_config') != 'json',
    'ALTER TABLE `gateway` MODIFY COLUMN `higress_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Rename name field to gateway_type if exists
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'gateway' 
     AND COLUMN_NAME = 'name') > 0,
    'ALTER TABLE `gateway` CHANGE COLUMN `name` `gateway_type` VARCHAR(32) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- Portal table modifications
-- ========================================

-- Convert config fields to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'portal' 
     AND COLUMN_NAME = 'portal_setting_config') != 'json',
    'ALTER TABLE `portal` MODIFY COLUMN `portal_setting_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'portal' 
     AND COLUMN_NAME = 'portal_ui_config') != 'json',
    'ALTER TABLE `portal` MODIFY COLUMN `portal_ui_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- Product table modifications
-- ========================================

-- Convert document to LONGTEXT type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product' 
     AND COLUMN_NAME = 'document') != 'longtext',
    'ALTER TABLE `product` MODIFY COLUMN `document` LONGTEXT',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Convert icon to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product' 
     AND COLUMN_NAME = 'icon') != 'json',
    'ALTER TABLE `product` MODIFY COLUMN `icon` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add auto_approve field if not exists
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product' 
     AND COLUMN_NAME = 'auto_approve') = 0,
    'ALTER TABLE `product` ADD COLUMN `auto_approve` TINYINT(1) DEFAULT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- ProductRef table modifications
-- ========================================

-- Convert config fields to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'apig_ref_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `apig_ref_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'adp_ai_gateway_ref_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `adp_ai_gateway_ref_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'higress_ref_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `higress_ref_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'nacos_ref_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `nacos_ref_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'api_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `api_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'mcp_config') != 'json',
    'ALTER TABLE `product_ref` MODIFY COLUMN `mcp_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add agent_config field if not exists
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'agent_config') = 0,
    'ALTER TABLE `product_ref` ADD COLUMN `agent_config` JSON DEFAULT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add model_config field if not exists
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_ref' 
     AND COLUMN_NAME = 'model_config') = 0,
    'ALTER TABLE `product_ref` ADD COLUMN `model_config` JSON DEFAULT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- ProductSubscription table modifications
-- ========================================

-- Convert consumer_auth_config to JSON type
SET @sql = IF(
    (SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'product_subscription' 
     AND COLUMN_NAME = 'consumer_auth_config') != 'json',
    'ALTER TABLE `product_subscription` MODIFY COLUMN `consumer_auth_config` JSON',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

COMMIT;
