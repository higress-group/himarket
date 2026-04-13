import type { ProductIcon } from '@/types/api-product'

// ==================== 创建方式 ====================

/** MCP 创建方式 */
export type CreationMode = 'manual' | 'gateway' | 'nacos' | 'vendor'

// ==================== 额外参数 ====================

/** MCP 额外参数定义（对应后端 extraParams JSON 中的单个条目） */
export interface ExtraParam {
  key: string
  name: string
  position: 'env' | 'args' | 'header'
  required: boolean
  description: string
  example: string
}

// ==================== 协议类型 ====================

/** MCP 协议类型 */
export type ProtocolType = 'sse' | 'http' | 'stdio'

// ==================== 资源规格 ====================

/** 沙箱资源规格预设 */
export type ResourceSpecPreset = 'small' | 'medium' | 'large' | 'custom'

/** 沙箱资源规格配置 */
export interface ResourceSpec {
  preset: ResourceSpecPreset
  cpu?: string
  memory?: string
}

// ==================== 统一表单数据 ====================

/**
 * 创建向导的统一表单数据结构。
 *
 * 覆盖以下场景的所有字段：
 * - Product 基础信息（对应后端 CreateProductParam）
 * - MCP 元信息（对应后端 SaveMcpMetaParam 通用字段）
 * - MCP 配置（manual 模式，协议/连接/额外参数）
 * - 服务介绍
 * - 网关导入（对应 SaveMcpMetaParam.GatewayImport）
 * - Nacos 导入（对应 SaveMcpMetaParam.NacosImport）
 * - 沙箱部署（对应 SaveMcpMetaParam.SandboxDeploy）
 */
export interface McpCreationFormData {
  // ---- Product 基础信息（CreateProductParam） ----
  /** 产品名称（必填） */
  name: string
  /** 产品描述（必填） */
  description: string
  /** 自动审批订阅 */
  autoApprove?: boolean
  /** 产品图标 */
  icon?: ProductIcon
  /** 产品分类 */
  categories?: string[]

  // ---- MCP 元信息（SaveMcpMetaParam 通用字段） ----
  /** MCP 英文名称（必填，^[a-z][a-z0-9-]*$，最长 63 字符） */
  mcpName: string
  /** MCP 展示名称 */
  displayName?: string
  /** 自定义标签 */
  tags?: string[]
  /** 仓库地址（仅 manual 模式） */
  repoUrl?: string

  // ---- MCP 配置（manual 模式） ----
  /** 协议类型 */
  protocolType?: ProtocolType
  /** 连接配置 JSON */
  connectionConfig?: string
  /** 是否需要沙箱托管 */
  sandboxRequired?: boolean
  /** 额外参数定义 */
  extraParams?: ExtraParam[]

  // ---- 服务介绍 ----
  /** Markdown 格式的服务文档 */
  serviceIntro?: string

  // ---- 网关导入（SaveMcpMetaParam.GatewayImport） ----
  /** 网关 ID */
  gatewayId?: string
  /** 网关关联配置 JSON */
  gatewayRefConfig?: object

  // ---- Nacos 导入（SaveMcpMetaParam.NacosImport） ----
  /** Nacos 实例 ID */
  nacosId?: string
  /** Nacos 关联配置 JSON */
  nacosRefConfig?: object

  // ---- 沙箱部署（SaveMcpMetaParam.SandboxDeploy） ----
  /** 沙箱实例 ID */
  sandboxId?: string
  /** 部署目标 Namespace */
  namespace?: string
  /** 传输协议：sse / http */
  transportType?: string
  /** 鉴权方式：none / bearer */
  authType?: string
  /** 资源规格配置 */
  resourceSpec?: ResourceSpec
  /** 额外参数实际值（如 {"API_KEY":"sk-xxx"}） */
  paramValues?: Record<string, string>
}

// ==================== 组件 Props ====================

/** McpCreationSelector 组件 Props */
export interface McpCreationSelectorProps {
  visible: boolean
  onCancel: () => void
  onSelect: (mode: CreationMode) => void
}

/** McpStepWizard 组件 Props */
export interface McpStepWizardProps {
  visible: boolean
  mode: Exclude<CreationMode, 'vendor'>
  onCancel: () => void
  onSuccess: () => void
}

/** 步骤组件通用 Props */
export interface StepProps {
  mode: Exclude<CreationMode, 'vendor'>
}
