CREATE TABLE IF NOT EXISTS `airegistry_instance` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `airegistry_id` varchar(64) NOT NULL,
    `name` varchar(64) NOT NULL,
    `admin_id` varchar(64) NOT NULL,
    `region_id` varchar(64) NOT NULL,
    `endpoint` varchar(256) DEFAULT NULL,
    `namespace_id` varchar(128) NOT NULL,
    `access_key_id` varchar(128) NOT NULL,
    `access_key_secret` varchar(512) NOT NULL,
    `security_token` varchar(1024) DEFAULT NULL,
    `description` varchar(512) DEFAULT NULL,
    `is_default` tinyint(1) NOT NULL DEFAULT 0,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_airegistry_id` (`airegistry_id`),
    KEY `idx_airegistry_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
