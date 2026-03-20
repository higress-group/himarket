-- V3__Add_chat_tables.sql
-- Add chat, chat_session, chat_attachment tables and product feature column
-- Description: Support for AI chat functionality with session management, attachments, and product feature configuration

START TRANSACTION;

-- ========================================
-- product_summary table
-- ========================================
CREATE TABLE IF NOT EXISTS `product_summary` (
                                                 `id` bigint NOT NULL AUTO_INCREMENT,
                                                 `product_id` varchar(64) NOT NULL,
    `admin_id` varchar(64) DEFAULT NULL,
    `name` varchar(64) NOT NULL,
    `type` varchar(64) DEFAULT NULL,
    `description` varchar(1000) DEFAULT NULL,
    `icon` json DEFAULT NULL,
    `usage_count` int NOT NULL,
    `likes_count` int NOT NULL,
    `subscription_count` int NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- product_like table
-- ========================================
CREATE TABLE IF NOT EXISTS `product_like` (
                                              `id` bigint NOT NULL AUTO_INCREMENT,
                                              `developer_id` varchar(64) DEFAULT NULL,
    `like_id` varchar(64) DEFAULT NULL,
    `portal_id` varchar(64) DEFAULT NULL,
    `product_id` varchar(64) NOT NULL,
    `status` varchar(64) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_consumer` (`product_id`, `developer_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


COMMIT;

