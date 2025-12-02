import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { getToken, removeToken } from './utils'
import {  message } from 'antd'



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
  createPortal: (data: any) => {
    return api.post(`/portals`, data)
  },
  // 获取portal详情
  getPortalDetail: (portalId: string) => {
    return api.get(`/portals/${portalId}`)
  },
  // 绑定域名
  bindDomain: (portalId: string, domainData: { domain: string; protocol: string; type: string }) => {
    return api.post(`/portals/${portalId}/domains`, domainData)
  },
  // 解绑域名
  unbindDomain: (portalId: string, domain: string) => {
    const encodedDomain = encodeURIComponent(domain)
    return api.delete(`/portals/${portalId}/domains/${encodedDomain}`)
  },
  // 更新Portal
  updatePortal: (portalId: string, data: any) => {
    return api.put(`/portals/${portalId}`, data)
  },
  // 更新Portal设置
  updatePortalSettings: (portalId: string, settings: any) => {
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
  // 删除订阅
  deleteSubscription: (consumerId: string, productId: string) => {
    return api.delete(`/consumers/${consumerId}/subscriptions/${productId}`)
  }
}

// API Product相关API
export const apiProductApi = {
  // 获取API产品列表
  getApiProducts: (params?: any) => {
    return api.get(`/products`, { params })
  },
  // 获取API产品详情
  getApiProductDetail: (productId: string) => {
    return api.get(`/products/${productId}`)
  },
  // 创建API产品
  createApiProduct: (data: any) => {
    return api.post(`/products`, data)
  },
  // 删除API产品
  deleteApiProduct: (productId: string) => {
    return api.delete(`/products/${productId}`)
  },
  // 更新API产品
  updateApiProduct: (productId: string, data: any) => {
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
  getApiProductPublications: (productId: string, params?: any) => {
    return api.get(`/products/${productId}/publications`, { params })
  },
  // 发布API产品到门户
  publishToPortal: (productId: string, portalId: string) => {
    return api.post(`/products/${productId}/publications/${portalId}`)
  },
  // 取消发布API产品到门户
  cancelPublishToPortal: (productId: string, portalId: string) => {
    return api.delete(`/products/${productId}/publications/${portalId}`)
  },
  // 获取API产品的Dashboard监控面板URL
  getProductDashboard: (productId: string) => {
    return api.get(`/products/${productId}/dashboard`)
  },
  // 获取产品关联的类别
  getProductCategories: (productId: string) => {
    return api.get(`/products/${productId}/categories`)
  }
}

// Gateway相关API
export const gatewayApi = {
  // 获取网关列表
  getGateways: (params?: any) => {
    return api.get(`/gateways`, { params })
  },
  // 获取APIG网关
  getApigGateway: (data: any) => {
    return api.get(`/gateways/apig`, { params: {
      ...data,
    } })
  },
  // 获取Apsara网关
  getApsaraGateways: (data: any) => {
    return api.post(`/gateways/apsara`, data)
  },
  // 获取ADP网关
  getAdpGateways: (data: any) => {
    return api.post(`/gateways/adp`, data)
  },
  // 删除网关
  deleteGateway: (gatewayId: string) => {
    return api.delete(`/gateways/${gatewayId}`)
  },
  // 导入网关
  importGateway: (data: any) => {
    return api.post(`/gateways`, { ...data })
  },
  // 获取网关的REST API列表
  getGatewayRestApis: (gatewayId: string, data: any) => {
    return api.get(`/gateways/${gatewayId}/rest-apis`, {
      params: data
    })
  },
  // 获取网关的MCP Server列表
  getGatewayMcpServers: (gatewayId: string, data: any) => {
    return api.get(`/gateways/${gatewayId}/mcp-servers`, {
      params: data
    })
  },
  // 获取网关的Agent API列表
  getGatewayAgentApis: (gatewayId: string, data: any) => {
    return api.get(`/gateways/${gatewayId}/agent-apis`, {
      params: data
    })
  },
  // 获取网关的Model API列表
  getGatewayModelApis: (gatewayId: string, data: any) => {
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
  getNacos: (params?: any) => {
    return api.get(`/nacos`, { params })
  },
  // 从阿里云 MSE 获取 Nacos 集群列表
  getMseNacos: (params: { regionId: string; accessKey: string; secretKey: string; page?: number; size?: number }) => {
    return api.get(`/nacos/mse`, { params })
  },
  createNacos: (data: any) => {
    return api.post(`/nacos`, data)
  },
  deleteNacos: (nacosId: string) => {
    return api.delete(`/nacos/${nacosId}`)
  },
  updateNacos: (nacosId: string, data: any) => {
    return api.put(`/nacos/${nacosId}`, data)
  },
  getNacosMcpServers: (nacosId: string, data: any) => {
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
  }
}