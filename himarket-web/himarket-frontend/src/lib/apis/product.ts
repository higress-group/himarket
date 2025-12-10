/**
 * 模型相关接口
 */

import request, { type RespI } from "../request";
import type { IAgentConfig, IAPIConfig, IInputSchema, IMCPConfig, IModelConfig, IProductIcon } from "./typing";

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
  enabled: boolean;
  feature?: {
    modelFeature: {
      webSearch: boolean;
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
}) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>('/products', {
    params: {
      name: params.name,
      type: params.type,
      categoryIds: params.categoryIds,
      page: params.page || 0,
      size: params.size || 100,
    },
  });
}


export function getProduct(params: { id: string }) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>('/products/' + params.id)
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