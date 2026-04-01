# MCP 冷热数据分离表设计

## 设计思路

MCP 数据分为两张表：
- **冷数据表 `mcp_server_meta`**：存储 MCP 独有的技术配置（协议、连接、工具等），变更频率低。展示信息（名称、描述、图标、文档等）统一由关联的 `product` 表管理
- **热数据表 `mcp_server_endpoint`**：存储 MCP 的运行时连接信息（endpoint、托管方式），由系统在部署/订阅时写入，查询频率高

## 表一：`mcp_server_meta`（冷数据 — MCP 技术配置）

> 展示字段（display_name、description、icon、service_intro、visibility、publish_status）统一存储在关联的 `product` 表中，不在本表冗余。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `id` | bigint, 自增 | 是 | AUTO_INCREMENT | 物理主键 |
| `mcp_server_id` | varchar(64), 唯一 | 是 | - | 业务主键 |
| `product_id` | varchar(64) | 是 | - | 关联 product 表 |
| `mcp_name` | varchar(128) | 是 | - | MCP 英文名称 |
| `repo_url` | varchar(512) | 否 | NULL | 源码仓库地址 |
| `source_type` | varchar(32) | 否 | NULL | 来源类型：npm / docker / git / config |
| `origin` | varchar(32) | 是 | 'ADMIN' | 来源：ADMIN / GATEWAY / USER / THIRD_PARTY |
| `tags` | json | 否 | NULL | 标签数组 |
| `protocol_type` | varchar(32) | 是 | - | 协议类型：stdio / sse / http |
| `connection_config` | json | 是 | - | 连接配置 JSON（模板） |
| `extra_params` | json | 否 | NULL | 额外参数列表 |
| `tools_config` | json | 否 | NULL | MCP tools 列表 |
| `sandbox_required` | tinyint(1) | 否 | NULL | 是否需要沙箱托管 |
| `created_by` | varchar(64) | 否 | NULL | 创建人 |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| `updated_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间 |

## 表二：`mcp_server_endpoint`（热数据 — MCP 运行时连接）

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `id` | bigint, 自增 | 是 | AUTO_INCREMENT | 物理主键 |
| `endpoint_id` | varchar(64), 唯一 | 是 | - | 业务主键 |
| `mcp_server_id` | varchar(64) | 是 | - | 关联 mcp_server_meta |
| `mcp_name` | varchar(128) | 是 | - | MCP 英文名称（冗余，避免 join） |
| `endpoint_url` | varchar(512) | 是 | - | 连接 endpoint 地址 |
| `hosting_type` | varchar(32) | 是 | - | 托管类型：NACOS / GATEWAY / SANDBOX |
| `protocol` | varchar(32) | 是 | - | 连接协议：stdio / sse / http |
| `user_id` | varchar(64) | 是 | '*' | 用户 ID，* 代表所有用户可见 |
| `hosting_instance_id` | varchar(64) | 否 | NULL | 托管方实例 ID（网关ID / Sandbox ID / Nacos实例ID） |
| `hosting_identifier` | varchar(128) | 否 | NULL | 托管标识符（网关=consumerId，Sandbox=部署ID） |
| `subscribe_params` | json | 否 | NULL | 用户订阅时提交的参数 JSON |
| `status` | varchar(32) | 是 | 'ACTIVE' | 状态：ACTIVE / INACTIVE |
| `created_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) | 创建时间 |
| `updated_at` | datetime(3) | 否 | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间 |

## 索引

### mcp_server_meta
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `uk_mcp_server_id` | 唯一 | `mcp_server_id` |
| `uk_product_mcp_name` | 唯一 | `product_id`, `mcp_name` |
| `idx_product_id` | 普通 | `product_id` |
| `idx_mcp_name` | 普通 | `mcp_name` |

### mcp_server_endpoint
| 索引名 | 类型 | 字段 |
|--------|------|------|
| `uk_endpoint_id` | 唯一 | `endpoint_id` |
| `idx_mcp_server_id` | 普通 | `mcp_server_id` |
| `idx_user_hosting` | 普通 | `user_id`, `hosting_type` |
| `uk_server_user_hosting` | 唯一 | `mcp_server_id`, `user_id`, `hosting_instance_id` |
