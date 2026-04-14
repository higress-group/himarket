/**
 * 模型相关接口
 */

import request, { type RespI } from "../request";
import type { IAgentConfig, IAPIConfig, IInputSchema, IMCPConfig, IModelConfig, IProductIcon, ISkillConfig, IWorkerConfig } from "./typing";

export interface IProductDetail {
  productId: string;
  name: string;
  description: string;
  status: string;
  enableConsumerAuth: boolean;
  type: string;
  document: string | null;
  icon?: IProductIcon;
  categories: {
    categoryId: string;
    name: string;
    description: string;
    icon: {
      type: string,
      value: string
    },
    createAt: string;
    updatedAt: string;
  }[];
  autoApprove: boolean | null;
  createAt: string;
  updatedAt: string;
  apiConfig: IAPIConfig,
  agentConfig: IAgentConfig;
  mcpConfig: IMCPConfig;
  modelConfig?: IModelConfig;
  skillConfig?: ISkillConfig;
  workerConfig?: IWorkerConfig;
  developerId?: string | null;
  developerUsername?: string | null;
  enabled: boolean;
  feature?: {
    modelFeature: {
      model: string;
      webSearch: boolean;
      enableMultiModal: boolean;
    }
  }
}

interface GetProductsResp {
  content: IProductDetail[];
  number: number
  size: number
  totalElements: number
}
// 获取模型列表
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  name?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  ["modelFilter.category"]?: "Image" | "TEXT";
}) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>('/products', {
    params: {
      name: params.name,
      type: params.type,
      categoryIds: params.categoryIds,
      page: params.page || 0,
      size: params.size || 100,
      sortBy: params.sortBy,
      ["modelFilter.category"]: params["modelFilter.category"],
    },
  });
}


export function getProduct(params: { id: string }) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>('/products/' + params.id)
}

// MCP 元信息类型
export interface IMcpMeta {
  mcpServerId: string;
  productId: string;
  displayName: string;
  mcpName: string;
  description: string;
  repoUrl: string;
  sourceType: string;
  origin: string;
  tags: string;
  icon: string;
  protocolType: string;
  connectionConfig: string;
  extraParams: string;
  serviceIntro: string;
  visibility: string;
  publishStatus: string;
  toolsConfig: string;
  sandboxRequired: boolean;
  createdBy: string;
  createAt: string;
  /** 沙箱托管后的 endpoint URL（热数据，userId=* 公共端点） */
  endpointUrl?: string;
  /** endpoint 协议（热数据） */
  endpointProtocol?: string;
  /** endpoint 状态 */
  endpointStatus?: string;
  /** endpoint 的 subscribeParams */
  subscribeParams?: string;
  /** endpoint 的托管类型（SANDBOX / GATEWAY / NACOS / DIRECT） */
  endpointHostingType?: string;
  /** 后端统一解析的连接配置 JSON（标准 mcpServers 格式，热数据优先冷数据 fallback） */
  resolvedConfig?: string;
}

// 获取产品关联的 MCP 元信息
export function getProductMcpMeta(productId: string) {
  return request.get<RespI<IMcpMeta[]>, RespI<IMcpMeta[]>>(`/products/${productId}/mcp-meta`);
}

// 获取产品关联的 MCP 公开信息（匿名可访问，脱敏）
export function getProductMcpMetaPublic(productId: string) {
  return request.get<RespI<IMcpMeta[]>, RespI<IMcpMeta[]>>(`/products/${productId}/mcp-meta/public`);
}

// 批量获取多个产品的 MCP 元信息（一次请求替代 N 次）
export function getProductMcpMetaBatch(productIds: string[]) {
  return request.get<RespI<IMcpMeta[]>, RespI<IMcpMeta[]>>('/mcp-servers/meta/batch', {
    params: { productIds: productIds.join(',') },
  });
}

// 批量获取多个产品的 MCP 公开信息（匿名可访问，脱敏）
export function getProductMcpMetaBatchPublic(productIds: string[]) {
  return request.get<RespI<IMcpMeta[]>, RespI<IMcpMeta[]>>('/mcp-servers/meta/batch/public', {
    params: { productIds: productIds.join(',') },
  });
}


// MCP 工具列表相关类型
export interface IMcpTool {
  name: string;
  description: string;
  inputSchema: {
    type: string;
    properties: IInputSchema;
    required?: string[];
  };
}

export interface IMcpToolsListResp {
  nextCursor: string;
  tools: IMcpTool[];
}

// 获取 MCP 服务的工具列表
export function getMcpTools(params: { productId: string }) {
  // TODO: 临时使用 mock 数据，待后端接口实现后替换
  // console.log('getMcpTools called with productId:', params.productId);

  // return Promise.resolve({
  //   code: 'SUCCESS',
  //   message: null,
  //   data: {
  //     nextCursor: '',
  //     tools: [
  //       {
  //         name: 'getCurTime',
  //         description: '获取当前最新日期时间。注意：模型不知道当前时间，需要通过此日期工具查询最新日期',
  //         inputSchema: {
  //           type: 'object',
  //           properties: {},
  //           required: []
  //         }
  //       },
  //       {
  //         name: 'fundReturnWithFrame',
  //         description: '基金收益归因解读API 支持用户输入基金代码、基金简称、基金全称，输出基金近一个月的涨跌情况以及对应关联的板块分析。',
  //         inputSchema: {
  //           type: 'object',
  //           properties: {
  //             scene: {
  //               default: '',
  //               description: '基金全称，例如华夏成长证券投资基金。',
  //               type: 'string'
  //             }
  //           },
  //           required: []
  //         }
  //       },
  //       {
  //         name: 'fundscore',
  //         description: '基金分数API 基于基金分类，根据不同类型的定位和特点，选取了盈利能力、风险控制能力、选股能力、择时能力、业绩稳定性、收益风险比、大类资产配置能力、基金经理能力等多个维度对基金在过去一年表现情况进行综合评价，评价打分范围：0-100。支持用户输入基金代码、基金简称、基金全称，查询基金综合分数。',
  //         inputSchema: {
  //           type: 'object',
  //           properties: {
  //             fundObject: {
  //               default: '',
  //               description: '公募基金实体标识，支持输入基金代码、基金简称、基金全称，仅支持输入一个。',
  //               type: 'string'
  //             }
  //           },
  //           required: ['fundObject']
  //         }
  //       },
  //       {
  //         name: 'fundscore1',
  //         description: '基金分数API 基于基金分类，根据不同类型的定位和特点，选取了盈利能力、风险控制能力、选股能力、择时能力、业绩稳定性、收益风险比、大类资产配置能力、基金经理能力等多个维度对基金在过去一年表现情况进行综合评价，评价打分范围：0-100。支持用户输入基金代码、基金简称、基金全称，查询基金综合分数。',
  //         inputSchema: {
  //           type: 'object',
  //           properties: {
  //             fundObject: {
  //               default: '',
  //               description: '公募基金实体标识，支持输入基金代码、基金简称、基金全称，仅支持输入一个。',
  //               type: 'string'
  //             }
  //           },
  //           required: ['fundObject']
  //         }
  //       },
  //       {
  //         name: 'fundscore2',
  //         description: '基金分数API 基于基金分类，根据不同类型的定位和特点，选取了盈利能力、风险控制能力、选股能力、择时能力、业绩稳定性、收益风险比、大类资产配置能力、基金经理能力等多个维度对基金在过去一年表现情况进行综合评价，评价打分范围：0-100。支持用户输入基金代码、基金简称、基金全称，查询基金综合分数。',
  //         inputSchema: {
  //           type: 'object',
  //           properties: {
  //             fundObject: {
  //               default: '',
  //               description: '公募基金实体标识，支持输入基金代码、基金简称、基金全称，仅支持输入一个。',
  //               type: 'string'
  //             }
  //           },
  //           required: ['fundObject']
  //         }
  //       }
  //     ]
  //   }
  // } as any);

  // 真实接口调用（暂时注释）
  return request.get<RespI<IMcpToolsListResp>, RespI<IMcpToolsListResp>>(
    `/products/${params.productId}/tools`
  );
}

// ==================== 沙箱相关 ====================

export interface ISandboxInstance {
  sandboxId: string;
  sandboxName: string;
  sandboxType: string;
  apiServer: string;
  namespace: string;
  description: string;
  status: string;
  createAt: string;
}

interface GetSandboxesResp {
  content: ISandboxInstance[];
  number: number;
  size: number;
  totalElements: number;
}

// 沙箱简要信息（Portal 端，只有 id 和名称）
export interface ISandboxSimple {
  sandboxId: string;
  sandboxName: string;
}

// 获取可用沙箱列表（Admin 端，按 adminId 过滤）
export function getSandboxes(params?: { sandboxType?: string; page?: number; size?: number }) {
  return request.get<RespI<GetSandboxesResp>, RespI<GetSandboxesResp>>('/sandboxes', { params });
}

// ==================== 我的 MCP（热数据） ====================

export interface IMyEndpoint {
  endpointId: string;
  mcpServerId: string;
  endpointUrl: string;
  hostingType: string;
  protocol: string;
  hostingInstanceId: string;
  subscribeParams: string;
  status: string;
  endpointCreatedAt: string;
  // meta 展示字段
  productId: string;
  displayName: string;
  mcpName: string;
  description: string;
  icon: string;
  tags: string;
  protocolType: string;
  origin: string;
  toolsConfig: string;
}

// 获取当前用户的 MCP endpoint 列表
export function getMyEndpoints() {
  return request.get<RespI<IMyEndpoint[]>, RespI<IMyEndpoint[]>>('/mcp-servers/my-endpoints');
}

// ==================== 用户注册 MCP ====================

export interface IRegisterMcpParam {
  mcpName: string;
  displayName: string;
  description?: string;
  repoUrl?: string;
  tags?: string;
  icon?: string;
  protocolType: string;
  connectionConfig: string;
  extraParams?: string;
  serviceIntro?: string;
  sandboxRequired?: boolean;
  origin?: string;
}

export function registerMcp(data: IRegisterMcpParam) {
  return request.post<RespI<IMcpMeta>, RespI<IMcpMeta>>('/mcp-servers/register', data);
}
