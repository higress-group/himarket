-- V6__Add_chat_tool_calls.sql
-- Add tool_calls column to chat table
-- Description: Support for storing tool call information in chat records

START TRANSACTION;

-- ========================================
-- Add tool_calls column to chat table (safe)
-- ========================================
SET @dbname = DATABASE();
SET @tablename = 'chat';
SET @columnname = 'tool_calls';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT ''Column tool_calls already exists in chat'' AS result;',
  'ALTER TABLE `chat` ADD COLUMN `tool_calls` json DEFAULT NULL COMMENT ''Tool call and result pairs'' AFTER `chat_usage`;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

COMMIT;
