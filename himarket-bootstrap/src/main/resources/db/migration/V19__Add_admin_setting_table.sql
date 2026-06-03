CREATE TABLE IF NOT EXISTS `admin_setting` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` varchar(64) NOT NULL,
    `setting_key` varchar(128) NOT NULL,
    `setting_value` varchar(1024) NOT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_setting_key` (`admin_id`, `setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
