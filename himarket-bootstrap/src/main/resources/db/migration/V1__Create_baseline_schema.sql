-- ========================================
-- Himarket Portal Database Baseline Schema
-- Version: 1.0
-- Description: Baseline database schema matching JPA entities exactly
-- ========================================

START TRANSACTION;

-- Administrator table
CREATE TABLE IF NOT EXISTS `administrator` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` varchar(64) NOT NULL,
    `username` varchar(64) NOT NULL,
    `password_hash` varchar(255) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_id` (`admin_id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Consumer table
CREATE TABLE IF NOT EXISTS `consumer` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `consumer_id` varchar(64) NOT NULL,
    `name` varchar(64) NOT NULL,
    `description` varchar(256) DEFAULT NULL,
    `portal_id` varchar(64) NOT NULL,
    `developer_id` varchar(64) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consumer_id` (`consumer_id`),
    UNIQUE KEY `uk_name_portal_developer` (`name`, `portal_id`, `developer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Consumer credential table
CREATE TABLE IF NOT EXISTS `consumer_credential` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `consumer_id` varchar(64) NOT NULL,
    `apikey_config` json DEFAULT NULL,
    `hmac_config` json DEFAULT NULL,
    `jwt_config` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_consumer_id` (`consumer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Consumer reference table
CREATE TABLE IF NOT EXISTS `consumer_ref` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `consumer_id` varchar(64) NOT NULL,
    `gateway_type` varchar(32) NOT NULL,
    `gw_consumer_id` varchar(64) NOT NULL,
    `gateway_config` json NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Developer table  
CREATE TABLE IF NOT EXISTS `developer` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `developer_id` varchar(64) NOT NULL,
    `username` varchar(64) DEFAULT NULL,
    `password_hash` varchar(255) DEFAULT NULL,
    `email` varchar(128) DEFAULT NULL,
    `portal_id` varchar(64) NOT NULL,
    `avatar_url` varchar(256) DEFAULT NULL,
    `status` varchar(16) NOT NULL,
    `auth_type` varchar(16) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_developer_id` (`developer_id`),
    UNIQUE KEY `uk_portal_username` (`portal_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Developer external identity table
CREATE TABLE IF NOT EXISTS `developer_external_identity` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `developer_id` varchar(64) NOT NULL,
    `provider` varchar(32) NOT NULL,
    `subject` varchar(128) NOT NULL,
    `display_name` varchar(128) DEFAULT NULL,
    `auth_type` varchar(32) DEFAULT NULL,
    `raw_info_json` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_provider_subject` (`provider`, `subject`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Gateway table
CREATE TABLE IF NOT EXISTS `gateway` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `gateway_name` varchar(64) NOT NULL,
    `gateway_type` varchar(32) NOT NULL,
    `gateway_id` varchar(64) NOT NULL,
    `admin_id` varchar(64) NOT NULL,
    `apig_config` json DEFAULT NULL,
    `adp_ai_gateway_config` json DEFAULT NULL,
    `higress_config` json DEFAULT NULL,
    `apsara_gateway_config` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gateway_id` (`gateway_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Nacos instance table
CREATE TABLE IF NOT EXISTS `nacos_instance` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `nacos_name` varchar(64) NOT NULL,
    `nacos_id` varchar(64) NOT NULL,
    `admin_id` varchar(64) NOT NULL,
    `server_url` varchar(256) NOT NULL,
    `username` varchar(64) DEFAULT NULL,
    `password` varchar(128) DEFAULT NULL,
    `access_key` varchar(128) DEFAULT NULL,
    `secret_key` varchar(256) DEFAULT NULL,
    `description` varchar(512) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_nacos_id` (`nacos_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Portal table
CREATE TABLE IF NOT EXISTS `portal` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `portal_id` varchar(64) NOT NULL,
    `name` varchar(64) NOT NULL,
    `description` varchar(256) DEFAULT NULL,
    `admin_id` varchar(64) DEFAULT NULL,
    `portal_setting_config` json DEFAULT NULL,
    `portal_ui_config` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_portal_id` (`portal_id`),
    UNIQUE KEY `uk_name_admin_id` (`name`, `admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Portal domain table
CREATE TABLE IF NOT EXISTS `portal_domain` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `portal_id` varchar(64) NOT NULL,
    `domain` varchar(128) NOT NULL,
    `type` varchar(32) NOT NULL,
    `protocol` varchar(32) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_domain` (`domain`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product table
CREATE TABLE IF NOT EXISTS `product` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `product_id` varchar(64) NOT NULL,
    `admin_id` varchar(64) DEFAULT NULL,
    `name` varchar(64) NOT NULL,
    `type` varchar(64) DEFAULT NULL,
    `description` varchar(256) DEFAULT NULL,
    `enable_consumer_auth` tinyint(1) DEFAULT NULL,
    `document` longtext DEFAULT NULL,
    `icon` json DEFAULT NULL,
    `status` varchar(64) DEFAULT NULL,
    `auto_approve` tinyint(1) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product category table
CREATE TABLE IF NOT EXISTS `product_category` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` varchar(64) DEFAULT NULL,
    `category_id` varchar(64) NOT NULL,
    `name` varchar(64) NOT NULL,
    `description` varchar(256) DEFAULT NULL,
    `icon` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product category relation table
CREATE TABLE IF NOT EXISTS `product_category_relation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `product_id` varchar(64) NOT NULL,
    `category_id` varchar(64) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_category` (`product_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product publication table
CREATE TABLE IF NOT EXISTS `publication` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `portal_id` varchar(64) NOT NULL,
    `product_id` varchar(64) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product reference table
CREATE TABLE IF NOT EXISTS `product_ref` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `product_id` varchar(64) NOT NULL,
    `gateway_id` varchar(64) DEFAULT NULL,
    `apig_ref_config` json DEFAULT NULL,
    `adp_ai_gateway_ref_config` json DEFAULT NULL,
    `apsara_gateway_ref_config` json DEFAULT NULL,
    `higress_ref_config` json DEFAULT NULL,
    `nacos_id` varchar(64) DEFAULT NULL,
    `nacos_ref_config` json DEFAULT NULL,
    `source_type` varchar(32) DEFAULT NULL,
    `api_config` json DEFAULT NULL,
    `mcp_config` json DEFAULT NULL,
    `agent_config` json DEFAULT NULL,
    `model_config` json DEFAULT NULL,
    `enabled` tinyint(1) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product subscription table
CREATE TABLE IF NOT EXISTS `product_subscription` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `product_id` varchar(64) NOT NULL,
    `consumer_id` varchar(64) NOT NULL,
    `developer_id` varchar(64) DEFAULT NULL,
    `portal_id` varchar(64) DEFAULT NULL,
    `status` varchar(32) NOT NULL,
    `consumer_auth_config` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_consumer` (`product_id`, `consumer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
