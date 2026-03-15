CREATE TABLE IF NOT EXISTS `coding_session` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` varchar(64) NOT NULL,
    `cli_session_id` varchar(128) NOT NULL,
    `user_id` varchar(64) NOT NULL,
    `title` varchar(255) DEFAULT NULL,
    `provider_key` varchar(64) DEFAULT NULL,
    `cwd` varchar(512) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
