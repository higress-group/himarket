-- V5__Fix_chat_attachment.sql
-- Add created_at and updated_at columns to chat_attachment table
-- Description: Add timestamp tracking for chat attachments

START TRANSACTION;

-- ========================================
-- Add created_at column to chat_attachment (safe)
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'chat_attachment';
SET @columnname = 'created_at';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT ''Column created_at already exists in chat_attachment'' AS result;',
  'ALTER TABLE `chat_attachment` ADD COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT ''创建时间'' AFTER `data`;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- ========================================
-- Add updated_at column to chat_attachment (safe)
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'chat_attachment';
SET @columnname = 'updated_at';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT ''Column updated_at already exists in chat_attachment'' AS result;',
  'ALTER TABLE `chat_attachment` ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT ''更新时间'' AFTER `created_at`;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

COMMIT;
