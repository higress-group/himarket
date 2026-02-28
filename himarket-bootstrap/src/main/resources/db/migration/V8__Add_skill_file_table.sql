ALTER TABLE product MODIFY COLUMN description VARCHAR(1000) DEFAULT NULL;

CREATE TABLE skill_file (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(64) NOT NULL,
    path        VARCHAR(512) NOT NULL,
    encoding    VARCHAR(16) NOT NULL DEFAULT 'text',
    content     MEDIUMTEXT NOT NULL,
    size        INT DEFAULT 0,
    created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_product_path (product_id, path(191))
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
