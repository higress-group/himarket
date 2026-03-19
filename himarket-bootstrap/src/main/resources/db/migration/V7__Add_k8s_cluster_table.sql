-- V6__Add_k8s_cluster_table.sql
-- Add k8s_cluster table for persistent K8s cluster configuration storage
-- Description: Support for K8s cluster management with kubeconfig persistence

-- ========================================
-- K8sCluster table
-- ========================================
CREATE TABLE IF NOT EXISTS `k8s_cluster` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `config_id` varchar(64) NOT NULL COMMENT '配置唯一标识（UUID）',
    `cluster_name` varchar(128) NOT NULL COMMENT '集群名称',
    `server_url` varchar(512) NOT NULL COMMENT 'K8s API Server 地址',
    `kubeconfig` text NOT NULL COMMENT 'kubeconfig 内容（YAML 格式）',
    `description` varchar(512) DEFAULT NULL COMMENT '集群描述',
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_id` (`config_id`),
    KEY `idx_cluster_name` (`cluster_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='K8s 集群配置表';
