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
// Mock Model 数据
const MOCK_MODELS: IProductDetail[] = [
  {
    productId: "model-001",
    name: "GPT-4o",
    description: "OpenAI 最强大的多模态模型，支持文本、图像输入输出，具备卓越的推理能力和创造力。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-01-15T08:30:00Z",
    updatedAt: "2024-03-20T10:15:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-002",
    name: "Claude 3.5 Sonnet",
    description: "Anthropic 推出的智能助手，擅长长文本理解、代码生成和复杂任务处理。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-02-10T14:20:00Z",
    updatedAt: "2024-03-18T09:45:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-003",
    name: "通义千问 Max",
    description: "阿里云推出的大语言模型，针对中文场景深度优化，支持多种行业应用。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    icon: { type: "URL", value: "https://img.alicdn.com/imgextra/i4/O1CN01ZJpsux1z0XHbN8yPh_!!6000000006649-55-tps-83-82.svg" },
    categories: [],
    autoApprove: true,
    createAt: "2024-01-20T11:00:00Z",
    updatedAt: "2024-03-22T16:30:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-004",
    name: "文心一言 4.0",
    description: "百度推出的知识增强大语言模型，在中文理解和生成方面表现出色。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-02-05T09:15:00Z",
    updatedAt: "2024-03-19T13:20:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-005",
    name: "Gemini Pro",
    description: "Google DeepMind 打造的多模态 AI 模型，支持文本、图像、音频和视频理解。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-01-25T10:30:00Z",
    updatedAt: "2024-03-21T11:45:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-006",
    name: "Llama 3 70B",
    description: "Meta 开源的大规模语言模型，性能强劲，支持商业用途。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-02-15T13:45:00Z",
    updatedAt: "2024-03-20T08:00:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-007",
    name: "Kimi K1.5",
    description: "月之暗面推出的长文本大模型，支持超长上下文窗口，擅长文档分析。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-03-01T15:20:00Z",
    updatedAt: "2024-03-23T10:30:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
  {
    productId: "model-008",
    name: "DeepSeek V3",
    description: "深度求索推出的开源大模型，在代码生成和数学推理方面表现优异。",
    status: "PUBLISHED",
    enableConsumerAuth: true,
    type: "MODEL_API",
    document: null,
    categories: [],
    autoApprove: true,
    createAt: "2024-02-28T11:10:00Z",
    updatedAt: "2024-03-22T14:15:00Z",
    apiConfig: {} as IAPIConfig,
    agentConfig: {} as IAgentConfig,
    mcpConfig: {} as IMCPConfig,
    enabled: true,
  },
];

// 获取模型列表
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  name?: string;
  page?: number;
  size?: number;
  ["modelFilter.category"]?: "Image" | "TEXT";
}) {
  // TODO: Mock 数据，仅用于前端展示效果测试
  if (params.type === "MODEL_API") {
    return Promise.resolve({
      code: "SUCCESS",
      message: undefined,
      data: {
        content: MOCK_MODELS,
        number: params.page || 0,
        size: params.size || 100,
        totalElements: MOCK_MODELS.length,
      },
    } as unknown as RespI<GetProductsResp>);
  }

  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>('/products', {
    params: {
      name: params.name,
      type: params.type,
      categoryIds: params.categoryIds,
      page: params.page || 0,
      size: params.size || 100,
      ["modelFilter.category"]: params["modelFilter.category"],
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
