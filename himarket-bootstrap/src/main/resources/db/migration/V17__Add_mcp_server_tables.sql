-- ========================================
-- MCP Server Meta (冷数据 — 元信息)
-- ========================================
CREATE TABLE IF NOT EXISTS `mcp_server_meta` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `mcp_server_id` varchar(64) NOT NULL,
    `product_id` varchar(64) NOT NULL,
    `mcp_name` varchar(128) NOT NULL,
    `repo_url` varchar(512) DEFAULT NULL,
    `source_type` varchar(32) DEFAULT NULL COMMENT 'npm / docker / git / config',
    `origin` varchar(32) NOT NULL DEFAULT 'ADMIN' COMMENT 'ADMIN / GATEWAY / USER / THIRD_PARTY',
    `tags` json DEFAULT NULL,
    `protocol_type` varchar(32) NOT NULL COMMENT 'stdio / sse / http',
    `connection_config` json NOT NULL,
    `extra_params` json DEFAULT NULL,
    `tools_config` json DEFAULT NULL,
    `created_by` varchar(64) DEFAULT NULL,
    `sandbox_required` tinyint(1) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mcp_server_id` (`mcp_server_id`),
    UNIQUE KEY `uk_product_mcp_name` (`product_id`, `mcp_name`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_mcp_name` (`mcp_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ========================================
-- MCP Server Endpoint (热数据 — 运行时连接)
-- ========================================
CREATE TABLE IF NOT EXISTS `mcp_server_endpoint` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `endpoint_id` varchar(64) NOT NULL,
    `mcp_server_id` varchar(64) NOT NULL,
    `mcp_name` varchar(128) NOT NULL,
    `endpoint_url` varchar(512) NOT NULL,
    `hosting_type` varchar(32) NOT NULL COMMENT 'NACOS / GATEWAY / SANDBOX',
    `protocol` varchar(32) NOT NULL COMMENT 'stdio / sse / http',
    `user_id` varchar(64) NOT NULL DEFAULT '*' COMMENT '* = all users',
    `hosting_instance_id` varchar(64) DEFAULT NULL,
    `hosting_identifier` varchar(128) DEFAULT NULL,
    `subscribe_params` json DEFAULT NULL COMMENT '用户订阅时提交的参数 JSON',
    `status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE',
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_endpoint_id` (`endpoint_id`),
    KEY `idx_mcp_server_id` (`mcp_server_id`),
    KEY `idx_user_hosting` (`user_id`, `hosting_type`),
    UNIQUE KEY `uk_server_user_hosting` (`mcp_server_id`, `user_id`, `hosting_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
