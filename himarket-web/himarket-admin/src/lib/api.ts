import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { getToken, removeToken } from './utils'
import {  message } from 'antd'
import type {
  CreatePortalRequest,
  UpdatePortalRequest,
  UpdatePortalSettingsRequest,
  GetApiProductsParams,
  CreateApiProductRequest,
  UpdateApiProductRequest,
  CreateApiProductRefRequest,
  GetApiProductPublicationsParams,
  GetGatewaysParams,
  GetApigGatewayParams,
  GetApsaraGatewaysRequest,
  GetAdpGatewaysRequest,
  ImportGatewayRequest,
  UpdateGatewayRequest,
  GetGatewayApisParams,
  GetNacosParams,
  CreateNacosRequest,
  UpdateNacosRequest,
  GetNacosMcpServersParams,
} from '@/types'



const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // 确保跨域请求时携带 cookie
})

// 请求拦截器
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data
  },
  (error) => {
    message.error(error.response?.data?.message || '请求发生错误');
    if (error.response?.status === 403 || error.response?.status === 401) {
      removeToken()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)


export default api

// 用户相关API
export const authApi = {
  getNeedInit: () => {
    return api.get('/admins/need-init')
  }
}
// Portal相关API
export const portalApi = {
  // 获取portal列表
  getPortals: (params?: { page?: number; size?: number }) => {
    return api.get(`/portals`, { params })
  },
  // 获取Portal Dashboard URL
  getPortalDashboard: (portalId: string, type: string = 'Portal') => {
    return api.get(`/portals/${portalId}/dashboard`, { params: { type } })
  },
  deletePortal: (portalId: string) => {
    return api.delete(`/portals/${portalId}`)
  },
  createPortal: (data: CreatePortalRequest) => {
    return api.post(`/portals`, data)
  },
  // 获取portal详情
  getPortalDetail: (portalId: string) => {
    return api.get(`/portals/${portalId}`)
  },
  // 绑定域名
  bindDomain: (portalId: string, domainData: { domain: string; type: string }) => {
    return api.post(`/portals/${portalId}/domains`, domainData)
  },
  // 解绑域名
  unbindDomain: (portalId: string, domain: string) => {
    const encodedDomain = encodeURIComponent(domain)
    return api.delete(`/portals/${portalId}/domains/${encodedDomain}`)
  },
  // 更新Portal
  updatePortal: (portalId: string, data: UpdatePortalRequest) => {
    return api.put(`/portals/${portalId}`, data)
  },
  // 更新Portal设置
  updatePortalSettings: (portalId: string, settings: UpdatePortalSettingsRequest) => {
    return api.put(`/portals/${portalId}/setting`, settings)
  },
  // 获取Portal的开发者列表
  getDeveloperList: (portalId: string, pagination?: { page: number; size: number }) => {
    return api.get(`/developers`, {
      params: {
        portalId,
        ...pagination
      }
    })
  },
  // 更新开发者状态
  updateDeveloperStatus: (portalId: string, developerId: string, status: string) => {
    return api.patch(`/developers/${developerId}/status`, {
      portalId,
      status
    })
  },
  deleteDeveloper: (developerId: string) => {
    return api.delete(`/developers/${developerId}`)
  },
  getConsumerList: (portalId: string, developerId: string, pagination?: { page: number; size: number }) => {
    return api.get(`/consumers`, {
      params: {
        portalId,
        developerId,
        ...pagination
      }
    })
  },
  // 审批consumer
  approveConsumer: (consumerId: string) => {
    return api.patch(`/consumers/${consumerId}/status`)
  },
  // 获取Consumer的订阅列表
  getConsumerSubscriptions: (consumerId: string, params?: { page?: number; size?: number; status?: string }) => {
    return api.get(`/consumers/${consumerId}/subscriptions`, { params })
  },
  // 审批订阅申请
  approveSubscription: (consumerId: string, productId: string) => {
    return api.patch(`/consumers/${consumerId}/subscriptions/${productId}`)
  },
  // 获取Portal已发布的产品列表
  getPortalPublications: (portalId: string, params?: { page?: number; size?: number }) => {
    return api.get(`/portals/${portalId}/publications`, { params })
  },
  // 删除订阅
  deleteSubscription: (consumerId: string, productId: string) => {
    return api.delete(`/consumers/${consumerId}/subscriptions/${productId}`)
  }
}

// API Product相关API
export const apiProductApi = {
  // 获取API产品列表
  getApiProducts: (params?: GetApiProductsParams) => {
    return api.get(`/products`, { params })
  },
  // 获取API产品详情
  getApiProductDetail: (productId: string) => {
    return api.get(`/products/${productId}`)
  },
  // 创建API产品
  createApiProduct: (data: CreateApiProductRequest) => {
    return api.post(`/products`, data)
  },
  // 删除API产品
  deleteApiProduct: (productId: string) => {
    return api.delete(`/products/${productId}`)
  },
  // 更新API产品
  updateApiProduct: (productId: string, data: UpdateApiProductRequest) => {
    return api.put(`/products/${productId}`, data)
  },
  // 获取API产品关联的服务
  getApiProductRef: (productId: string) => {
    return api.get(`/products/${productId}/ref`)
  },
  // 创建API产品关联
  createApiProductRef: (productId: string, data: any) => {
    return api.post(`/products/${productId}/ref`, data)
  },
  // 删除API产品关联
  deleteApiProductRef: (productId: string) => {
    return api.delete(`/products/${productId}/ref`)
  },
  // 获取API产品已发布的门户列表
  getApiProductPublications: (productId: string, params?: GetApiProductPublicationsParams) => {
    return api.get(`/products/${productId}/publications`, { params })
  },
  // 发布API产品到门户
  publishToPortal: (productId: string, portalId: string) => {
    return api.post(`/products/${productId}/publications`, { portalId })
  },
  // 取消发布API产品到门户
  cancelPublishToPortal: (productId: string, publicationId: string) => {
    return api.delete(`/products/${productId}/publications/${publicationId}`)
  },
  // 获取API产品的Dashboard监控面板URL
  getProductDashboard: (productId: string) => {
    return api.get(`/products/${productId}/dashboard`)
  },
  // 获取产品关联的类别
  getProductCategories: (productId: string) => {
    return api.get(`/products/${productId}/categories`)
  },
  // 更新 Skill 的 Nacos 关联
  updateSkillNacos: (productId: string, data: { nacosId: string; namespace: string }) => {
    return api.put(`/products/${productId}/skill-nacos`, data)
  },
  // 更新 Worker 的 Nacos 关联
  updateWorkerNacos: (productId: string, data: { nacosId: string; namespace: string }) => {
    return api.put(`/products/${productId}/worker-nacos`, data)
  },
  // 获取产品的订阅列表
  getProductSubscriptions: (productId: string, params?: { page?: number; size?: number; status?: string }) => {
    return api.get(`/products/${productId}/subscriptions`, { params })
  },
  // 批量导入 AI API 资源为产品
  importProducts: (data: {
    productType: string;
    sourceType: 'GATEWAY' | 'NACOS';
    gatewayId?: string;
    nacosId?: string;
    namespaceId?: string;
    services: Array<{
      name: string;
      description?: string;
      apiId?: string;
      mcpServerId?: string;
      mcpRouteId?: string;
      agentApiId?: string;
      modelApiId?: string;
      mcpServerName?: string;
      agentName?: string;
      namespaceId?: string;
    }>;
    categories?: string[];
  }) => {
    return api.post(`/products/import`, data, { timeout: 120000 })
  },
}

// Gateway相关API
export const gatewayApi = {
  // 获取网关列表
  getGateways: (params?: GetGatewaysParams) => {
    return api.get(`/gateways`, { params })
  },
  // 获取APIG网关
  getApigGateway: (data: GetApigGatewayParams) => {
    return api.get(`/gateways/apig`, { params: {
      ...data,
    } })
  },
  // 获取Apsara网关
  getApsaraGateways: (data: GetApsaraGatewaysRequest) => {
    return api.post(`/gateways/apsara`, data)
  },
  // 获取ADP网关
  getAdpGateways: (data: GetAdpGatewaysRequest) => {
    return api.post(`/gateways/adp`, data)
  },
  // 删除网关
  deleteGateway: (gatewayId: string) => {
    return api.delete(`/gateways/${gatewayId}`)
  },
  // 导入网关
  importGateway: (data: ImportGatewayRequest) => {
    return api.post(`/gateways`, { ...data })
  },
  // 更新网关
  updateGateway: (gatewayId: string, data: UpdateGatewayRequest) => {
    return api.put(`/gateways/${gatewayId}`, data)
  },
  // 获取网关的REST API列表
  getGatewayRestApis: (gatewayId: string, data: GetGatewayApisParams) => {
    return api.get(`/gateways/${gatewayId}/rest-apis`, {
      params: data
    })
  },
  // 获取网关的MCP Server列表
  getGatewayMcpServers: (gatewayId: string, data: GetGatewayApisParams) => {
    return api.get(`/gateways/${gatewayId}/mcp-servers`, {
      params: data
    })
  },
  // 获取网关的Agent API列表
  getGatewayAgentApis: (gatewayId: string, data: GetGatewayApisParams) => {
    return api.get(`/gateways/${gatewayId}/agent-apis`, {
      params: data
    })
  },
  // 获取网关的Model API列表
  getGatewayModelApis: (gatewayId: string, data: GetGatewayApisParams) => {
    return api.get(`/gateways/${gatewayId}/model-apis`, {
      params: data
    })
  },
  // 获取网关的Dashboard URL
  getDashboard: (gatewayId: string) => {
    return api.get(`/gateways/${gatewayId}/dashboard`)
  }
} 

export const nacosApi = {
  getNacos: (params?: GetNacosParams) => {
    return api.get(`/nacos`, { params })
  },
  // 从阿里云 MSE 获取 Nacos 集群列表
  getMseNacos: (params: { regionId: string; accessKey: string; secretKey: string; page?: number; size?: number }) => {
    return api.get(`/nacos/mse`, { params })
  },
  createNacos: (data: CreateNacosRequest) => {
    return api.post(`/nacos`, data)
  },
  deleteNacos: (nacosId: string) => {
    return api.delete(`/nacos/${nacosId}`)
  },
  updateNacos: (nacosId: string, data: UpdateNacosRequest) => {
    return api.put(`/nacos/${nacosId}`, data)
  },
  getNacosMcpServers: (nacosId: string, data: GetNacosMcpServersParams) => {
    return api.get(`/nacos/${nacosId}/mcp-servers`, {
      params: data
    })
  },
  // 获取 Nacos Agent 列表
  getNacosAgents: (nacosId: string, params?: { 
    page?: number; 
    size?: number; 
    namespaceId?: string 
  }) => {
    return api.get(`/nacos/${nacosId}/agents`, { params })
  },
  // 获取指定 Nacos 实例的命名空间列表
  getNamespaces: (nacosId: string, params?: { page?: number; size?: number }) => {
    return api.get(`/nacos/${nacosId}/namespaces`, { params })
  },
  // 获取默认 Nacos 实例
  getDefaultNacos: () => {
    return api.get(`/nacos/default`)
  },
  // 设置默认 Nacos 实例
  setDefaultNacos: (nacosId: string) => {
    return api.put(`/nacos/${nacosId}/default`)
  },
  // 设置默认命名空间
  setDefaultNamespace: (nacosId: string, namespaceId: string) => {
    return api.put(`/nacos/${nacosId}/default-namespace`, null, { params: { namespaceId } })
  },
}

export const workerApi = {
  delete: (productId: string) =>
    api.delete(`/workers/${productId}`),
  uploadPackage: (productId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/workers/${productId}/package`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
  },
  getDownloadUrl: (productId: string) =>
    `${import.meta.env.VITE_API_BASE_URL}/workers/${productId}/download`,
  getFiles: (productId: string, version?: string) =>
    api.get(`/workers/${productId}/files`, { params: { version } }),
  getFileContent: (productId: string, filePath: string, version?: string) =>
    api.get(`/workers/${productId}/files/${filePath}`, { params: { version } }),
  getVersions: (productId: string) =>
    api.get(`/workers/${productId}/versions`),
  publishVersion: (productId: string, version: string) =>
    api.post(`/workers/${productId}/versions`, { version }),
  offlineVersion: (productId: string, version: string) =>
    api.patch(`/workers/${productId}/versions/${version}`, { status: 'offline' }),
  onlineVersion: (productId: string, version: string) =>
    api.patch(`/workers/${productId}/versions/${version}`, { status: 'online' }),
  deleteDraft: (productId: string) =>
    api.delete(`/workers/${productId}/draft`),
  setLatestVersion: (productId: string, version: string) =>
    api.put(`/workers/${productId}/versions/latest`, { version }),
  // 从 Nacos 导入 Workers
  importFromNacos: (nacosId: string, namespace?: string) => {
    return api.post(`/workers/import`, null, { params: { nacosId, namespace }, timeout: 120000 })
  },
}

export const skillApi = {
  uploadSkillPackage: (productId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/skills/${productId}/package`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
    })
  },
  getDownloadUrl: (productId: string) =>
    `${import.meta.env.VITE_API_BASE_URL}/skills/${productId}/download`,
  getSkillFiles: (productId: string, version?: string) =>
    api.get(`/skills/${productId}/files`, { params: { version } }),
  getSkillFileContent: (productId: string, filePath: string, version?: string) =>
    api.get(`/skills/${productId}/files/${filePath}`, { params: { version } }),
  getVersions: (productId: string) =>
    api.get(`/skills/${productId}/versions`),
  publishVersion: (productId: string, version: string) =>
    api.post(`/skills/${productId}/versions`, { version }),
  offlineVersion: (productId: string, version: string) =>
    api.patch(`/skills/${productId}/versions/${version}`, { status: 'offline' }),
  onlineVersion: (productId: string, version: string) =>
    api.patch(`/skills/${productId}/versions/${version}`, { status: 'online' }),
  deleteDraft: (productId: string) =>
    api.delete(`/skills/${productId}/draft`),
  setLatestVersion: (productId: string, version: string) =>
    api.put(`/skills/${productId}/versions/latest`, { version }),
  forcePublishVersion: (productId: string, version: string) =>
    api.post(`/skills/${productId}/versions/${version}/force-publish`),
  // 从 Nacos 导入 Skills
  importFromNacos: (nacosId: string, namespace?: string) => {
    return api.post(`/skills/import`, null, { params: { nacosId, namespace }, timeout: 120000 })
  },
}

// MCP Server 管理 API
export const mcpServerApi = {
  // 保存 MCP 元信息（创建/更新）
  saveMeta: (data: {
    productId: string
    mcpName: string
    displayName: string
    description?: string
    repoUrl?: string
    sourceType?: string
    origin?: string
    gatewayId?: string
    nacosId?: string
    refConfig?: string
    tags?: string
    icon?: string
    protocolType: string
    connectionConfig: string
    extraParams?: string
    serviceIntro?: string
    visibility?: string
    publishStatus?: string
    toolsConfig?: string
    sandboxRequired?: boolean
    sandboxId?: string
    transportType?: string
    authType?: string
    paramValues?: string
    namespace?: string
    resourceSpec?: string
  }) => {
    return api.post(`/mcp-servers/meta`, data, { timeout: 120000 })
  },
  // 获取 MCP 元信息
  getMeta: (mcpServerId: string) => {
    return api.get(`/mcp-servers/meta/${mcpServerId}`)
  },
  // 获取产品下所有 MCP 元信息
  listMetaByProduct: (productId: string) => {
    return api.get(`/mcp-servers/meta`, { params: { productId } })
  },
  // 删除 MCP 元信息
  deleteMeta: (mcpServerId: string) => {
    return api.delete(`/mcp-servers/meta/${mcpServerId}`)
  },
  // 删除产品下所有 MCP 配置（meta + endpoint + ref + 重置状态）
  deleteMetaByProduct: (productId: string) => {
    return api.delete(`/mcp-servers/meta/by-product/${productId}`)
  },
  // 保存 endpoint
  saveEndpoint: (data: {
    mcpServerId: string
    endpointUrl: string
    hostingType: string
    protocol: string
    userId?: string
    hostingInstanceId?: string
    hostingIdentifier?: string
  }) => {
    return api.post(`/mcp-servers/endpoints`, data)
  },
  // 获取 endpoint 列表
  listEndpoints: (mcpServerId: string) => {
    return api.get(`/mcp-servers/endpoints`, { params: { mcpServerId } })
  },
  // 删除 endpoint
  deleteEndpoint: (endpointId: string) => {
    return api.delete(`/mcp-servers/endpoints/${endpointId}`)
  },
  // 市场列表
  listPublished: (params?: { page?: number; size?: number }) => {
    return api.get(`/mcp-servers/published`, { params })
  },
  // 刷新工具列表
  refreshTools: (mcpServerId: string) => {
    return api.post(`/mcp-servers/meta/${mcpServerId}/refresh-tools`, {}, { timeout: 120000 })
  },
  // 更新服务介绍
  updateServiceIntro: (mcpServerId: string, serviceIntro: string) => {
    return api.put(`/mcp-servers/meta/${mcpServerId}/service-intro`, { serviceIntro })
  },
  // 取消沙箱托管
  undeploySandbox: (mcpServerId: string) => {
    return api.delete(`/mcp-servers/meta/${mcpServerId}/deploy-sandbox`)
  },
  // 部署沙箱（独立于 saveMeta，管理员手动触发）
  deploySandbox: (mcpServerId: string, data: {
    sandboxId: string
    transportType?: string
    authType?: string
    paramValues?: string
    namespace?: string
    resourceSpec?: string
  }) => {
    return api.post(`/mcp-servers/meta/${mcpServerId}/deploy-sandbox`, data, { timeout: 120000 })
  },
  // 更新工具配置（手动编辑）
  updateToolsConfig: (mcpServerId: string, toolsConfig: string) => {
    return api.put(`/mcp-servers/meta/${mcpServerId}/tools-config`, toolsConfig, {
      headers: { 'Content-Type': 'application/json' }
    })
  },
}

// Sandbox 实例相关 API
export const sandboxApi = {
  // 获取所有运行中的沙箱实例（部署用，不按 adminId 过滤）
  getActiveSandboxes: () => {
    return api.get(`/sandboxes/active`)
  },
  // 获取沙箱实例列表
  getSandboxes: (params?: { sandboxType?: string; page?: number; size?: number }) => {
    return api.get(`/sandboxes`, { params })
  },
  // 导入沙箱实例
  importSandbox: (data: { sandboxName: string; sandboxType: string; kubeConfig: string; description?: string }) => {
    return api.post(`/sandboxes`, data)
  },
  // 更新沙箱实例
  updateSandbox: (sandboxId: string, data: { sandboxName?: string; kubeConfig?: string; description?: string }) => {
    return api.put(`/sandboxes/${sandboxId}`, data)
  },
  // 删除沙箱实例
  deleteSandbox: (sandboxId: string) => {
    return api.delete(`/sandboxes/${sandboxId}`)
  },
  // 获取集群信息
  fetchClusterInfo: (kubeConfig: string) => {
    return api.post(`/sandboxes/cluster-info`, { kubeConfig })
  },
  // 手动触发健康检查
  healthCheck: (sandboxId: string) => {
    return api.post(`/sandboxes/${sandboxId}/health-check`)
  },
  // 获取沙箱集群的 Namespace 列表
  listNamespaces: (sandboxId: string) => {
    return api.get(`/sandboxes/${sandboxId}/namespaces`)
  },
  // 查询沙箱上的活跃 MCP 部署数量
  getActiveDeployments: (sandboxId: string) => {
    return api.get(`/sandboxes/${sandboxId}/active-deployments`)
  },
}

// MCP 供应商导入 API
export const mcpVendorApi = {
  // 查询供应商 MCP 列表
  listRemoteMcpItems: (params: { vendorType: string; keyword?: string; page?: number; size?: number }) => {
    return api.get('/admin/mcp-vendor/mcp-list', { params })
  },
  // 批量导入（超时设长一些，因为后端需要逐条调详情 API 补充数据）
  batchImport: (data: { vendorType: string; items: any[] }) => {
    return api.post('/admin/mcp-vendor/import', data, { timeout: 300000 })
  },
}
