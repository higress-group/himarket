-- V14__Add_summary_tables.sql
-- Add product_summary and product_like tables to support product recommendation
-- Description: support product recommendation sorting by usage, likes and subscriptions

START TRANSACTION;

-- ========================================
-- product_summary table
-- ========================================
CREATE TABLE IF NOT EXISTS `product_summary` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `product_id` varchar(64) NOT NULL,
    `name` varchar(64) NOT NULL,
    `type` varchar(64) DEFAULT NULL,
    `description` varchar(1000) DEFAULT NULL,
    `icon` json DEFAULT NULL,
    `usage_count` bigint NOT NULL,
    `likes_count` bigint NOT NULL,
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


-- Delete duplicate data, keep the record with the smallest id
DELETE ps1 FROM product_summary ps1
INNER JOIN product_summary ps2
  ON ps1.product_id = ps2.product_id
  AND ps1.id > ps2.id;

-- Add unique constraint
ALTER TABLE `product_summary` ADD UNIQUE KEY `uk_product_id` (`product_id`);

-- Add description column
ALTER TABLE product_summary MODIFY COLUMN description VARCHAR(1000) DEFAULT NULL;

COMMIT;

