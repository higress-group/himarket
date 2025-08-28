import { Card, Button, Modal, Form, Select, message } from 'antd'
import { PlusOutlined, DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useState, useEffect } from 'react'
import type { ApiProduct } from '@/types/api-product'
import { apiProductApi, gatewayApi, nacosApi } from '@/lib/api'
import { getServiceName } from '@/lib/utils'

interface ApiProductLinkApiProps {
  apiProduct: ApiProduct
  handleRefresh: () => void
}

interface RestAPIItem {
  apiId: string
  apiName: string
}

interface HigressMCPItem {
  mcpServerName: string
  fromGatewayType: 'HIGRESS'
}

interface NacosMCPItem {
  mcpServerName: string
  fromGatewayType: 'NACOS'
  namespaceId: string
}

interface APIGAIMCPItem {
  mcpServerName: string
  fromGatewayType: 'APIG_AI'
  mcpRouteId: string
  apiId: string
}

type ApiItem = RestAPIItem | HigressMCPItem | APIGAIMCPItem | NacosMCPItem;

interface LinkedService {
  productId: string
  gatewayId?: string
  nacosId?: string
  sourceType: 'GATEWAY' | 'NACOS'
  apigRefConfig?: RestAPIItem | APIGAIMCPItem
  higressRefConfig?: HigressMCPItem
  nacosRefConfig?: NacosMCPItem
}

interface Gateway {
  gatewayId: string
  gatewayName: string
  gatewayType: 'APIG_API' | 'HIGRESS' | 'APIG_AI' | 'ADP_AI_GATEWAY'
  createAt: string
  apigConfig?: {
    region: string
  }
  higressConfig?: {
    host: string
    port: number
  }
}

interface NacosInstance {
  nacosId: string
  nacosName: string
  serverUrl: string
  username: string
  description: string
  adminId: string
}

export function ApiProductLinkApi({ apiProduct, handleRefresh }: ApiProductLinkApiProps) {
  const [linkedService, setLinkedService] = useState<LinkedService | null>(null)
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [form] = Form.useForm()
  const [gateways, setGateways] = useState<Gateway[]>([])
  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([])
  const [gatewayLoading, setGatewayLoading] = useState(false)
  const [nacosLoading, setNacosLoading] = useState(false)
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null)
  const [selectedNacos, setSelectedNacos] = useState<NacosInstance | null>(null)
  const [nacosNamespaces, setNacosNamespaces] = useState<any[]>([])
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>(null)
  const [apiList, setApiList] = useState<ApiItem[] | NacosMCPItem[]>([])
  const [apiLoading, setApiLoading] = useState(false)
  const [sourceType, setSourceType] = useState<'GATEWAY' | 'NACOS'>('GATEWAY')

  useEffect(() => {    
    fetchGateways()
    fetchNacosInstances()
  }, [])

  useEffect(() => {
    if (apiProduct.productId) {
      fetchLinkedService()
    }
  }, [apiProduct.productId])

  const fetchLinkedService = async () => {
    try {
      const res = await apiProductApi.getApiProductRef(apiProduct.productId)
      setLinkedService(res.data || null)
    } catch (error) {
      console.error('获取关联服务失败:', error)
      setLinkedService(null)
    }
  }

  const fetchGateways = async () => {
    setGatewayLoading(true)
    try {
      const res = await gatewayApi.getGateways()
      const result = apiProduct.type === 'REST_API' ?
       res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_API') :
       res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'HIGRESS' || item.gatewayType === 'APIG_AI' || item.gatewayType === 'ADP_AI_GATEWAY')
      setGateways(result || [])
    } catch (error) {
      console.error('获取网关列表失败:', error)
    } finally {
      setGatewayLoading(false)
    }
  }

  const fetchNacosInstances = async () => {
    setNacosLoading(true)
    try {
      const res = await nacosApi.getNacos({
        page: 1,
        size: 1000 // 获取所有 Nacos 实例
      })
      setNacosInstances(res.data.content || [])
    } catch (error) {
      console.error('获取Nacos实例列表失败:', error)
    } finally {
      setNacosLoading(false)
    }
  }

  const handleSourceTypeChange = (value: 'GATEWAY' | 'NACOS') => {
    setSourceType(value)
  setSelectedGateway(null)
  setSelectedNacos(null)
  setSelectedNamespace(null)
  setNacosNamespaces([])
    setApiList([])
    form.setFieldsValue({
      gatewayId: undefined,
      nacosId: undefined,
      apiId: undefined
    })
  }

  const handleGatewayChange = async (gatewayId: string) => {
    const gateway = gateways.find(g => g.gatewayId === gatewayId)
    setSelectedGateway(gateway || null)
    
    if (!gateway) return

    console.log('gatewayId', gatewayId);
    
    setApiLoading(true)
    try {
      if (gateway.gatewayType === 'APIG_API') {
        // APIG_API类型：获取REST API列表
        const restRes = await gatewayApi.getGatewayRestApis(gatewayId, {})
        const restApis = (restRes.data?.content || []).map((api: any) => ({
          apiId: api.apiId,
          apiName: api.apiName,
          type: 'REST API'
        }))
        setApiList(restApis)
      } else if (gateway.gatewayType === 'HIGRESS') {
        // HIGRESS类型：获取MCP Server列表
        const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
          page: 1,
          size: 1000 // 获取所有MCP Server
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName,
          fromGatewayType: 'HIGRESS' as const,
          type: 'MCP Server'
        }))
        setApiList(mcpServers)
      } else if (gateway.gatewayType === 'APIG_AI') {
        // APIG_AI类型：获取MCP Server列表
        const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
          page: 1,
          size: 500 // 获取所有MCP Server
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName,
          fromGatewayType: 'APIG_AI' as const,
          mcpRouteId: api.mcpRouteId,
          apiId: api.apiId,
          type: 'MCP Server'
        }))
        setApiList(mcpServers)
      } else if (gateway.gatewayType === 'ADP_AI_GATEWAY') {
        // ADP_AI_GATEWAY类型：获取MCP Server列表
        const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
          page: 1,
          size: 500 // 获取所有MCP Server
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName,
          fromGatewayType: 'ADP_AI_GATEWAY' as const,
          mcpRouteId: api.mcpRouteId,
          apiId: api.apiId,
          type: 'MCP Server'
        }))
        setApiList(mcpServers)
      }
    } catch (error) {
      // console.error('获取API列表失败:', error)
    } finally {
      setApiLoading(false)
    }
  }

  const handleNacosChange = async (nacosId: string) => {
    const nacos = nacosInstances.find(n => n.nacosId === nacosId)
    setSelectedNacos(nacos || null)
    setSelectedNamespace(null)
    setApiList([])
    setNacosNamespaces([])
    if (!nacos) return

    // 获取命名空间列表
    try {
      const nsRes = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 })
      const namespaces = (nsRes.data?.content || []).map((ns: any) => ({
        namespaceId: ns.namespaceId,
        namespaceName: ns.namespaceName || ns.namespaceId,
        namespaceDesc: ns.namespaceDesc
      }))
      setNacosNamespaces(namespaces)
    } catch (e) {
      console.error('获取命名空间失败', e)
    }
  }

  const handleNamespaceChange = async (namespaceId: string) => {
    setSelectedNamespace(namespaceId)
    setApiLoading(true)
    try {
      if (!selectedNacos) return
      const res = await nacosApi.getNacosMcpServers(selectedNacos.nacosId, {
        page: 1,
        size: 1000,
        namespaceId
      })
      const mcpServers = (res.data?.content || []).map((api: any) => ({
        mcpServerName: api.mcpServerName,
        fromGatewayType: 'NACOS' as const,
        type: `MCP Server (${namespaceId})`
      }))
      setApiList(mcpServers)
    } catch (e) {
      console.error('获取Nacos MCP Server列表失败:', e)
    } finally {
      setApiLoading(false)
    }
  }

  const renderServiceDetails = () => {
    if (!linkedService) {
      return (
        <div className="text-center py-8 text-gray-500">
          <p>暂未关联任何服务</p>
        </div>
      )
    }

    const getServiceType = () => {

      if (linkedService.sourceType === 'NACOS') {
        return 'MCP Server (Nacos)'
      }
      
      if (linkedService.apigRefConfig) {
        if ('apiName' in linkedService.apigRefConfig && linkedService.apigRefConfig.apiName) {
          return 'REST API'
        }
        return 'MCP Server (APIG_AI)'
      }
      if (linkedService.higressRefConfig) {
        return 'MCP Server (HIGRESS)'
      }
      return '未知类型'
    }

    return (
      <div className="space-y-4">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="text-lg font-medium">名称：{getServiceName(linkedService)}</h3>
            <p className="text-sm text-gray-500">类型：{getServiceType()}</p>
          </div>
          <Button 
            type="primary" 
            danger 
            icon={<DeleteOutlined />}
            onClick={handleDelete}
          >
            解除关联
          </Button>
        </div>
        
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="font-medium">来源类型:</span>
            <span className="ml-2">网关</span>
          </div>
          <div>
            <span className="font-medium">{linkedService.sourceType === 'GATEWAY' ? '网关ID:' : 'Nacos实例ID:'}</span>
            <span className="ml-2">{linkedService.gatewayId || linkedService.nacosId}</span>
          </div>
        </div>
      </div>
    )
  }

  const handleAdd = () => {
    if (linkedService) {
      Modal.confirm({
        title: '重新关联',
        content: '重新关联将删除现有的关联关系，确定继续吗？',
        onOk: () => {
          apiProductApi.deleteApiProductRef(apiProduct.productId).then(() => {
            setLinkedService(null)
            setIsModalVisible(true)
          }).catch((err: any) => {
            // message.error('删除现有关联失败')
          })
        }
      })
    } else {
      setIsModalVisible(true)
    }
  }

  const handleDelete = () => {
    Modal.confirm({
      title: '确认解除关联',
      icon: <ExclamationCircleOutlined />,
      content: '确定要解除服务关联吗？此操作不可恢复。',
      okText: '确认解除',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => {
        apiProductApi.deleteApiProductRef(apiProduct.productId).then((res: any) => {
          message.success('解除关联成功')
          fetchLinkedService()
        }).catch((err: any) => {
          // message.error('解除关联失败')
        })
      }
    })
  }

  const handleModalOk = () => {
    form.validateFields().then((values) => {
      const { sourceType, gatewayId, nacosId, apiId } = values
      const selectedApi = apiList.find(item => {
        if ('apiId' in item) {
          // mcp server 会返回apiId和mcpRouteId，此时mcpRouteId为唯一值，apiId不是
          if ('mcpRouteId' in item) {
            return item.mcpRouteId === apiId
          } else {
            return item.apiId === apiId
          }
        } else if ('mcpServerName' in item) {
          return item.mcpServerName === apiId
        }
        return false
      })
      const newService: LinkedService = {
        gatewayId: sourceType === 'GATEWAY' ? gatewayId : undefined, // 对于 Nacos，使用 nacosId 作为 gatewayId
        nacosId: sourceType === 'NACOS' ? nacosId : undefined,
        sourceType,
        productId: apiProduct.productId,
        apigRefConfig: selectedApi && 'apiId' in selectedApi ? selectedApi as RestAPIItem | APIGAIMCPItem : undefined,
        higressRefConfig: selectedApi && 'mcpServerName' in selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'HIGRESS' ? selectedApi as HigressMCPItem : undefined,
        nacosRefConfig: sourceType === 'NACOS' && selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'NACOS' ? {
          ...selectedApi,
          namespaceId: selectedNamespace || 'public'
        } : undefined,
      }
      apiProductApi.createApiProductRef(apiProduct.productId, newService).then((res: any) => {
        message.success('关联成功')
        setIsModalVisible(false)
        fetchLinkedService()
        form.resetFields()
        setSelectedGateway(null)
        setSelectedNacos(null)
        setApiList([])
        setSourceType('GATEWAY')
      }).catch((err: any) => {
        // message.error('关联失败')
      })
    })
  }

  const handleModalCancel = () => {
    setIsModalVisible(false)
    form.resetFields()
    setSelectedGateway(null)
    setSelectedNacos(null)
    setApiList([])
    setSourceType('GATEWAY')
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">关联服务</h1>
          <p className="text-gray-600">
            {apiProduct.type === 'REST_API' 
              ? '关联REST API服务到此产品' 
              : '关联MCP Server服务到此产品'
            }
          </p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          {linkedService ? '重新关联' : '关联服务'}
        </Button>
      </div>

      <Card>
        {renderServiceDetails()}
      </Card>

      <Modal
        title={linkedService ? '重新关联服务' : '关联新服务'}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okText="关联"
        cancelText="取消"
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="sourceType"
            label="来源类型"
            initialValue="GATEWAY"
            rules={[{ required: true, message: '请选择来源类型' }]}
          >
            <Select placeholder="请选择来源类型" onChange={handleSourceTypeChange}>
              <Select.Option value="GATEWAY">网关</Select.Option>
              <Select.Option value="NACOS" disabled={apiProduct.type === 'REST_API'}>Nacos</Select.Option>
            </Select>
          </Form.Item>

          {sourceType === 'GATEWAY' && (
            <Form.Item
              name="gatewayId"
              label="网关实例"
              rules={[{ required: true, message: '请选择网关' }]}
            >
              <Select 
                placeholder="请选择网关实例" 
                loading={gatewayLoading}
                showSearch
                filterOption={(input, option) =>
                  (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                }
                onChange={handleGatewayChange}
                optionLabelProp="label"
              >
                {gateways.map(gateway => (
                  <Select.Option 
                    key={gateway.gatewayId} 
                    value={gateway.gatewayId}
                    label={gateway.gatewayName}
                  >
                    <div>
                      <div className="font-medium">{gateway.gatewayName}</div>
                      <div className="text-sm text-gray-500">
                        {gateway.gatewayId} - {gateway.gatewayType}
                      </div>
                    </div>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'NACOS' && (
            <Form.Item
              name="nacosId"
              label="Nacos实例"
              rules={[{ required: true, message: '请选择Nacos实例' }]}
            >
              <Select 
                placeholder="请选择Nacos实例" 
                loading={nacosLoading}
                showSearch
                filterOption={(input, option) =>
                  (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                }
                onChange={handleNacosChange}
                optionLabelProp="label"
              >
                {nacosInstances.map(nacos => (
                  <Select.Option 
                    key={nacos.nacosId} 
                    value={nacos.nacosId}
                    label={nacos.nacosName}
                  >
                    <div>
                      <div className="font-medium">{nacos.nacosName}</div>
                      <div className="text-sm text-gray-500">
                        {nacos.serverUrl}
                      </div>
                    </div>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}

          {sourceType === 'NACOS' && selectedNacos && (
            <Form.Item
              name="namespaceId"
              label="命名空间"
              rules={[{ required: true, message: '请选择命名空间' }]}
            >
              <Select
                placeholder="请选择命名空间"
                loading={apiLoading && nacosNamespaces.length === 0}
                onChange={handleNamespaceChange}
                showSearch
                filterOption={(input, option) => (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())}
                optionLabelProp="label"
              >
                {nacosNamespaces.map(ns => (
                  <Select.Option key={ns.namespaceId} value={ns.namespaceId} label={ns.namespaceName}>
                    <div>
                      <div className="font-medium">{ns.namespaceName}</div>
                      <div className="text-sm text-gray-500">{ns.namespaceId}</div>
                    </div>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}
          
          {(selectedGateway || (selectedNacos && selectedNamespace)) && (
            <Form.Item
              name="apiId"
              label={apiProduct.type === 'REST_API' ? '选择REST API' : '选择MCP Server'}
              rules={[{ required: true, message: apiProduct.type === 'REST_API' ? '请选择REST API' : '请选择MCP Server' }]}
            >
              <Select 
                placeholder={apiProduct.type === 'REST_API' ? '请选择REST API' : '请选择MCP Server'} 
                loading={apiLoading}
                showSearch
                filterOption={(input, option) =>
                  (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                }
                optionLabelProp="label"
              >
                {apiList.map((api: any) => (
                  <Select.Option 
                    key={apiProduct.type === 'REST_API' ? api.apiId : (api.mcpRouteId || api.mcpServerName)} 
                    value={apiProduct.type === 'REST_API' ? api.apiId : (api.mcpRouteId || api.mcpServerName)}
                    label={api.apiName || api.mcpServerName}
                  >
                    <div>
                      <div className="font-medium">{api.apiName || api.mcpServerName}</div>
                      <div className="text-sm text-gray-500">
                        {api.type} - {apiProduct.type === 'REST_API' ? api.apiId : (api.mcpRouteId || api.mcpServerName)}
                      </div>
                    </div>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
} 