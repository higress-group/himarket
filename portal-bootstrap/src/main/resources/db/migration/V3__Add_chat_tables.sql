-- V3__Add_chat_tables.sql
-- Add chat, chat_session, chat_attachment tables and product feature column
-- Description: Support for AI chat functionality with session management, attachments, and product feature configuration

START TRANSACTION;

-- ========================================
-- ChatSession table
-- ========================================
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` varchar(64) NOT NULL,
    `user_id` varchar(64) NOT NULL,
    `name` varchar(255) DEFAULT NULL,
    `products` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Chat table
-- ========================================
CREATE TABLE IF NOT EXISTS `chat` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `chat_id` varchar(64) NOT NULL,
    `session_id` varchar(64) NOT NULL,
    `user_id` varchar(64) NOT NULL,
    `conversation_id` varchar(64) NOT NULL,
    `status` varchar(32) DEFAULT 'INIT',
    `product_id` varchar(64) DEFAULT NULL,
    `question_id` varchar(64) DEFAULT NULL,
    `question` text DEFAULT NULL,
    `attachments` json DEFAULT NULL,
    `answer_id` varchar(64) DEFAULT NULL,
    `answer` longtext DEFAULT NULL,
    `sequence` int DEFAULT 0,
    `chat_usage` json DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_id` (`chat_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- ChatAttachment table
-- ========================================
CREATE TABLE IF NOT EXISTS `chat_attachment` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `attachment_id` varchar(64) NOT NULL,
    `user_id` varchar(64) NOT NULL,
    `name` varchar(255) DEFAULT NULL,
    `type` varchar(32) NOT NULL,
    `mime_type` varchar(64) DEFAULT NULL,
    `size` bigint DEFAULT 0,
    `data` mediumblob DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attachment_id` (`attachment_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- Add feature column to product table (safe)
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'product';
SET @columnname = 'feature';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `product` ADD COLUMN `feature` json DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- Add is_primary field to consumer table
-- ========================================
-- This field is used to mark the primary consumer for a developer
-- NULL = not explicitly set (for backward compatibility with old data)
-- TRUE = explicitly marked as primary
-- Only one consumer per developer can be marked as primary
SET @dbname = DATABASE();
SET @tablename = 'consumer';
SET @columnname = 'is_primary';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE `consumer` ADD COLUMN `is_primary` TINYINT(1) DEFAULT NULL'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

COMMIT;

