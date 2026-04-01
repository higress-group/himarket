# Sandbox 实例表设计

## 表名：`sandbox_instance`

用于存储沙箱运行环境实例的配置信息，支持 AgentRuntime 和自建 Sandbox 两种类型。

## 字段说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `id` | bigint, 自增 | 是 | AUTO_INCREMENT | 物理主键 |
| `sandbox_id` | varchar(64), 唯一 | 是 | - | 业务主键，格式 `sandbox-xxxx` |
| `admin_id` | varchar(64) | 是 | - | 创建人，关联管理员 ID |
| `sandbox_name` | varchar(64) | 是 | - | 实例名称，同一管理员下唯一 |
| `sandbox_type` | varchar(32) | 是 | - | 实例类型：`AGENT_RUNTIME` / `SELF_HOSTED` |
| `cluster_attribute` | json | 否 | NULL | 集群属性 JSON，包含 clusterId、clusterName 等 |
| `api_server` | varchar(256), 唯一 | 否 | NULL | K8s Master URL，从 KubeConfig 中解析提取 |
| `kube_config` | text | 否 | NULL | 完整的 KubeConfig 文本（加密存储） |
| `description` | varchar(512) | 否 | NULL | 实例描述 |
| `extra_config` | json | 否 | NULL | 扩展配置 JSON，包含 resourceSpec、image、capabilities 等 |
| `status` | varchar(32) | 是 | `'RUNNING'` | 实例状态：`RUNNING` / `STOPPED` / `ERROR` |
| `status_message` | varchar(512) | 否 | NULL | 状态描述，健康检查失败时记录错误信息 |
| `last_checked_at` | datetime(3) | 否 | NULL | 上次健康检查时间 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| `updated_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间 |

## 索引

| 索引名 | 类型 | 字段 | 说明 |
|--------|------|------|------|
| `PRIMARY` | 主键 | `id` | 物理主键 |
| `uk_sandbox_id` | 唯一索引 | `sandbox_id` | 业务主键唯一 |
| `uk_admin_sandbox_name` | 唯一索引 | `admin_id`, `sandbox_name` | 同一管理员下实例名称唯一 |
| `uk_api_server` | 唯一索引 | `api_server` | 同一 K8s 集群只能注册一个实例 |
| `idx_admin_id` | 普通索引 | `admin_id` | 按管理员查询 |
| `idx_sandbox_type` | 普通索引 | `sandbox_type` | 按实例类型查询 |

## 建表 SQL

```sql
CREATE TABLE IF NOT EXISTS `sandbox_instance` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `sandbox_id` varchar(64) NOT NULL,
    `admin_id` varchar(64) NOT NULL,
    `sandbox_name` varchar(64) NOT NULL,
    `sandbox_type` varchar(32) NOT NULL COMMENT 'AGENT_RUNTIME / SELF_HOSTED',
    `cluster_attribute` json DEFAULT NULL COMMENT '集群属性JSON: clusterId, clusterName, slbType, vpcId, mappingIP, mappingPort等',
    `api_server` varchar(256) DEFAULT NULL,
    `kube_config` text DEFAULT NULL,
    `description` varchar(512) DEFAULT NULL,
    `extra_config` json DEFAULT NULL COMMENT '扩展配置: resourceSpec, image, capabilities',
    `status` varchar(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / STOPPED / ERROR',
    `status_message` varchar(512) DEFAULT NULL,
    `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `last_checked_at` datetime(3) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sandbox_id` (`sandbox_id`),
    UNIQUE KEY `uk_admin_sandbox_name` (`admin_id`, `sandbox_name`),
    UNIQUE KEY `uk_api_server` (`api_server`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_sandbox_type` (`sandbox_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## extra_config JSON 结构（AGENT_RUNTIME 类型）

```json
{
  "resourceSpec": {
    "cpuRequest": "1",
    "cpuLimit": "2",
    "memoryRequest": "2Gi",
    "memoryLimit": "4Gi",
    "ephemeralStorage": "20Gi"
  },
  "image": "registry.example.com/agent-runtime:latest",
  "capabilities": ["MCP_HOSTING", "AGENT_HOSTING", "CODING"]
}
```

### capabilities 枚举
- `MCP_HOSTING` — MCP Server 托管
- `AGENT_HOSTING` — Agent 托管
- `CODING` — Coding 开发环境
