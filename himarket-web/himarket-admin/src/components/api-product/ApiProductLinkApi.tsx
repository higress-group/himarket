import { Card, Button, Modal, Form, Select, message, Collapse, Tabs, Row, Col } from 'antd'
import { PlusOutlined, DeleteOutlined, ExclamationCircleOutlined, CopyOutlined, EditOutlined, CloudUploadOutlined } from '@ant-design/icons'
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ApiProduct, LinkedService, RestAPIItem, NacosMCPItem, APIGAIMCPItem, AIGatewayAgentItem, AIGatewayModelItem, ApiItem } from '@/types/api-product'
import type { Endpoint } from '@/types/endpoint'
import type { Gateway, NacosInstance } from '@/types/gateway'
import { apiProductApi, gatewayApi, nacosApi, apiDefinitionApi } from '@/lib/api'
import { getGatewayTypeLabel } from '@/lib/constant'
import { copyToClipboard, formatDomainWithPort } from '@/lib/utils'
import * as yaml from 'js-yaml'
import { SwaggerUIWrapper } from './SwaggerUIWrapper'

interface ApiProductLinkApiProps {
  apiProduct: ApiProduct
  linkedService: LinkedService | null
  onLinkedServiceUpdate: (linkedService: LinkedService | null) => void
  handleRefresh: () => void
}

export function ApiProductLinkApi({ apiProduct, linkedService, onLinkedServiceUpdate, handleRefresh }: ApiProductLinkApiProps) {
  const navigate = useNavigate()
  // 移除了内部的 linkedService 状态，现在从 props 接收
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
  const [publishRecords, setPublishRecords] = useState<any[]>([])

  const [parsedTools, setParsedTools] = useState<Array<{
    name: string;
    description: string;
    args?: Array<{
      name: string;
      description: string;
      type: string;
      required: boolean;
      position: string;
      default?: string;
      enum?: string[];
    }>;
  }>>([])
  const [httpJson, setHttpJson] = useState('')
  const [sseJson, setSseJson] = useState('')
  const [localJson, setLocalJson] = useState('')
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0)
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState<number>(0)
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState<number>(0)

  useEffect(() => {
    fetchGateways()
    fetchNacosInstances()
  }, [])

  const convertEndpointToTool = (endpoint: Endpoint) => {
    const config = endpoint.config || {};
    let inputSchema = config.inputSchema || {};
    
    if (typeof inputSchema === 'string') {
      try {
        inputSchema = JSON.parse(inputSchema);
      } catch (e) {
        console.warn('Failed to parse input schema', e);
        inputSchema = {};
      }
    }

    const properties = inputSchema.properties || {};
    const required = inputSchema.required || [];

    const args = Object.keys(properties).map(key => {
      const prop = properties[key];
      return {
        name: key,
        description: prop.description || '',
        type: prop.type || 'string',
        required: required.includes(key),
        position: 'body',
        default: prop.default,
        enum: prop.enum
      };
    });

    return {
      name: endpoint.name,
      description: endpoint.description || '',
      args: args
    };
  }

  // 解析MCP tools配置
  useEffect(() => {
    if (apiProduct.type === 'MCP_SERVER') {
      if (linkedService?.sourceType === 'MANAGED' && linkedService.apiDefinitions?.[0]?.endpoints) {
        // 处理 Managed API 的 endpoints
        const tools = linkedService.apiDefinitions[0].endpoints
          .filter((ep: Endpoint) => ep.type === 'MCP_TOOL')
          .map((ep: Endpoint) => convertEndpointToTool(ep));
        setParsedTools(tools);
      } else if (apiProduct.mcpConfig?.tools) {
        const parsedConfig = parseYamlConfig(apiProduct.mcpConfig.tools)
        if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
          setParsedTools(parsedConfig.tools)
        } else {
          // 如果tools字段存在但是空数组，也设置为空数组
          setParsedTools([])
        }
      } else {
        setParsedTools([])
      }
    } else {
      setParsedTools([])
    }
  }, [apiProduct, linkedService])

  // 获取 Publish Records
  useEffect(() => {
    if (linkedService?.sourceType === 'MANAGED' && linkedService.apiDefinitions?.[0]?.apiDefinitionId) {
      const apiDefId = linkedService.apiDefinitions[0].apiDefinitionId;
      apiDefinitionApi.getPublishRecords(apiDefId).then((res: any) => {
         const records = res.data?.content || res.data || [];
         setPublishRecords(records);
      }).catch(e => {
        console.error('Failed to fetch publish records', e);
        setPublishRecords([]);
      });
    } else {
      setPublishRecords([]);
    }
  }, [linkedService]);

  // 生成连接配置
  // 当产品切换时重置域名选择索引
  useEffect(() => {
    setSelectedDomainIndex(0);
    setSelectedAgentDomainIndex(0);
    setSelectedModelDomainIndex(0);
  }, [apiProduct.productId]);

  useEffect(() => {
    if (apiProduct.type === 'MCP_SERVER' && apiProduct.mcpConfig) {
      // 获取关联的MCP Server名称
      let mcpServerName = apiProduct.name // 默认使用产品名称

      if (linkedService) {
        // 从linkedService中获取真实的MCP Server名称
        if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'mcpServerName' in linkedService.apigRefConfig) {
          mcpServerName = linkedService.apigRefConfig.mcpServerName || apiProduct.name
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
          mcpServerName = linkedService.higressRefConfig.mcpServerName || apiProduct.name
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
          mcpServerName = linkedService.adpAIGatewayRefConfig.mcpServerName || apiProduct.name
        } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
          mcpServerName = linkedService.nacosRefConfig.mcpServerName || apiProduct.name
        }
      }

      generateConnectionConfig(
        apiProduct.mcpConfig.mcpServerConfig.domains,
        apiProduct.mcpConfig.mcpServerConfig.path,
        mcpServerName,
        apiProduct.mcpConfig.mcpServerConfig.rawConfig,
        apiProduct.mcpConfig.meta?.protocol,
        selectedDomainIndex
      )
    }
  }, [apiProduct, linkedService, selectedDomainIndex])

  // 生成域名选项的函数
  const getDomainOptions = (domains: Array<{ domain: string; port?: number; protocol: string; networkType?: string }>) => {
    return domains.map((domain, index) => {
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      return {
        value: index,
        label: `${domain.protocol}://${formattedDomain}`,
        domain: domain
      }
    })
  }

  // 解析YAML配置的函数
  const parseYamlConfig = (yamlString: string): {
    tools?: Array<{
      name: string;
      description: string;
      args?: Array<{
        name: string;
        description: string;
        type: string;
        required: boolean;
        position: string;
        default?: string;
        enum?: string[];
      }>;
    }>;
  } | null => {
    try {
      const parsed = yaml.load(yamlString) as {
        tools?: Array<{
          name: string;
          description: string;
          args?: Array<{
            name: string;
            description: string;
            type: string;
            required: boolean;
            position: string;
            default?: string;
            enum?: string[];
          }>;
        }>;
      };
      return parsed;
    } catch (error) {
      console.error('YAML解析失败:', error)
      return null
    }
  }

  // 生成连接配置
  const generateConnectionConfig = (
    domains: Array<{ domain: string; port?: number; protocol: string }> | null | undefined,
    path: string | null | undefined,
    serverName: string,
    localConfig?: unknown,
    protocolType?: string,
    domainIndex: number = 0
  ) => {
    // 互斥：优先判断本地模式
    if (localConfig) {
      const localConfigJson = JSON.stringify(localConfig, null, 2);
      setLocalJson(localConfigJson);
      setHttpJson("");
      setSseJson("");
      return;
    }

    // HTTP/SSE 模式
    if (domains && domains.length > 0 && path && domainIndex < domains.length) {
      const domain = domains[domainIndex]
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      const baseUrl = `${domain.protocol}://${formattedDomain}`;
      let fullUrl = `${baseUrl}${path || '/'}`;

      if (apiProduct.mcpConfig?.meta?.source === 'ADP_AI_GATEWAY' ||
        apiProduct.mcpConfig?.meta?.source === 'APSARA_GATEWAY') {
        fullUrl = `${baseUrl}/mcp-servers${path || '/'}`;
      }

      if (protocolType === 'SSE') {
        // 仅生成SSE配置，不追加/sse
        const sseConfig = {
          mcpServers: {
            [serverName]: {
              type: "sse",
              url: fullUrl
            }
          }
        }
        setSseJson(JSON.stringify(sseConfig, null, 2))
        setHttpJson("")
        setLocalJson("")
        return;
      } else if (protocolType === 'StreamableHTTP') {
        // 仅生成HTTP配置
        const httpConfig = {
          mcpServers: {
            [serverName]: {
              url: fullUrl
            }
          }
        }
        setHttpJson(JSON.stringify(httpConfig, null, 2))
        setSseJson("")
        setLocalJson("")
        return;
      } else {
        // protocol为null或其他值：生成两种配置
        const sseConfig = {
          mcpServers: {
            [serverName]: {
              type: "sse",
              url: `${fullUrl}/sse`
            }
          }
        }

        const httpConfig = {
          mcpServers: {
            [serverName]: {
              url: fullUrl
            }
          }
        }

        setSseJson(JSON.stringify(sseConfig, null, 2))
        setHttpJson(JSON.stringify(httpConfig, null, 2))
        setLocalJson("")
        return;
      }
    }

    // 无有效配置
    setHttpJson("");
    setSseJson("");
    setLocalJson("");
  }

  const handleCopy = async (text: string) => {
    try {
      await copyToClipboard(text);
      message.success("已复制到剪贴板");
    } catch {
      message.error("复制失败，请手动复制");
    }
  }

  const fetchGateways = async () => {
    setGatewayLoading(true)
    try {
      const res = await gatewayApi.getGateways({
        page: 1,
        size: 1000,
      })
      let result;
      if (apiProduct.type === 'REST_API') {
        // REST API 只支持 APIG_API 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_API');
      } else if (apiProduct.type === 'AGENT_API') {
        // Agent API 只支持 APIG_AI 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_AI');
      } else if (apiProduct.type === 'MODEL_API') {
        // Model API 支持 APIG_AI 和 HIGRESS 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_AI' || item.gatewayType === 'HIGRESS');
      } else {
        // MCP Server 支持 HIGRESS、APIG_AI、ADP_AI_GATEWAY
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'HIGRESS' || item.gatewayType === 'APIG_AI' || item.gatewayType === 'ADP_AI_GATEWAY' || item.gatewayType === 'APSARA_GATEWAY');
      }
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
      apiId: undefined,
      apiDefinitionId: undefined
    })
  }

  const handleGatewayChange = async (gatewayId: string) => {
    const gateway = gateways.find(g => g.gatewayId === gatewayId)
    setSelectedGateway(gateway || null)

    if (!gateway) return

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
        // HIGRESS类型：对于Model API产品，获取Model API列表；其他情况获取MCP Server列表
        if (apiProduct.type === 'MODEL_API') {
          // HIGRESS类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 1000 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelRouteName: api.modelRouteName,
            fromGatewayType: 'HIGRESS' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
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
        }
      } else if (gateway.gatewayType === 'APIG_AI') {
        if (apiProduct.type === 'AGENT_API') {
          // APIG_AI类型 + Agent API产品：获取Agent API列表
          const res = await gatewayApi.getGatewayAgentApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Agent API
          })
          const agentApis = (res.data?.content || []).map((api: any) => ({
            agentApiId: api.agentApiId,
            agentApiName: api.agentApiName,
            fromGatewayType: 'APIG_AI' as const,
            type: 'Agent API'
          }))
          setApiList(agentApis)
        } else if (apiProduct.type === 'MODEL_API') {
          // APIG_AI类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'APIG_AI' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
          // APIG_AI类型 + MCP Server产品：获取MCP Server列表
          const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
            page: 1,
            size: 500 // 获取所有MCP Server
          })
          const mcpServers = (res.data?.content || []).map((api: any) => ({
            mcpServerName: api.mcpServerName,
            fromGatewayType: 'APIG_AI' as const,
            mcpRouteId: api.mcpRouteId,
            apiId: api.apiId,
            mcpServerId: api.mcpServerId,
            type: 'MCP Server'
          }))
          setApiList(mcpServers)
        }
      } else if (gateway.gatewayType === 'ADP_AI_GATEWAY') {
        // ADP_AI_GATEWAY类型：获取MCP Server列表
        const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
          page: 1,
          size: 500 // 获取所有MCP Server
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName || api.name,
          fromGatewayType: 'ADP_AI_GATEWAY' as const,
          mcpRouteId: api.mcpRouteId,
          mcpServerId: api.mcpServerId,
          type: 'MCP Server'
        }))
        setApiList(mcpServers)
      } else if (gateway.gatewayType === 'APSARA_GATEWAY') {
        // APSARA_GATEWAY类型：获取MCP Server列表
        const res = await gatewayApi.getGatewayMcpServers(gatewayId, {
          page: 1,
          size: 500 // 获取所有MCP Server
        })
        const mcpServers = (res.data?.content || []).map((api: any) => ({
          mcpServerName: api.mcpServerName || api.name,
          fromGatewayType: 'APSARA_GATEWAY' as const,
          mcpRouteId: api.mcpRouteId,
          mcpServerId: api.mcpServerId,
          type: 'MCP Server'
        }))
        setApiList(mcpServers)
      }
    } catch (error) {
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

      // 根据产品类型获取不同的列表
      if (apiProduct.type === 'AGENT_API') {
        // 获取 Agent 列表
        const res = await nacosApi.getNacosAgents(selectedNacos.nacosId, {
          page: 1,
          size: 1000,
          namespaceId
        })
        const agents = (res.data?.content || []).map((api: any) => ({
          agentName: api.agentName,
          description: api.description,
          fromGatewayType: 'NACOS' as const,
          type: `Agent API (${namespaceId})`
        }))
        setApiList(agents)
      } else if (apiProduct.type === 'MCP_SERVER') {
        // 获取 MCP Server 列表（现有逻辑）
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
      }
    } catch (e) {
      console.error('获取 Nacos 资源列表失败:', e)
    } finally {
      setApiLoading(false)
    }
  }


  // TODO
  const handleModalOk = () => {
    form.validateFields().then((values) => {
      const { sourceType, gatewayId, nacosId, apiId, apiDefinitionId } = values
      const selectedApi = apiList.find((item: any) => {
        if ('apiId' in item) {
          // REST API或MCP server 会返回apiId和mcpRouteId，此时mcpRouteId为唯一值，apiId不是
          if ('mcpRouteId' in item) {
            return item.mcpRouteId === apiId
          } else {
            return item.apiId === apiId
          }
        } else if ('mcpServerName' in item) {
          return item.mcpServerName === apiId
        } else if ('agentApiId' in item || 'agentApiName' in item) {
          // Agent API: 匹配agentApiId或agentApiName
          return item.agentApiId === apiId || item.agentApiName === apiId
        } else if ('modelApiId' in item || 'modelApiName' in item) {
          // Model API (AI Gateway): 匹配modelApiId或modelApiName
          return item.modelApiId === apiId || item.modelApiName === apiId
        } else if ('modelRouteName' in item && item.fromGatewayType === 'HIGRESS') {
          // Model API (Higress): 匹配modelRouteName字段
          return item.modelRouteName === apiId
        } else if ('agentName' in item) {
          // Nacos Agent: 匹配agentName
          return item.agentName === apiId
        }
        return false
      })
      const newService: LinkedService = {
        gatewayId: sourceType === 'GATEWAY' ? gatewayId : undefined, // 对于 Nacos，使用 nacosId 作为 gatewayId
        nacosId: sourceType === 'NACOS' ? nacosId : undefined,
        sourceType,
        productId: apiProduct.productId,
        apigRefConfig: selectedApi && ('apiId' in selectedApi || 'agentApiId' in selectedApi || 'agentApiName' in selectedApi || 'modelApiId' in selectedApi || 'modelApiName' in selectedApi) && (!('fromGatewayType' in selectedApi) || selectedApi.fromGatewayType !== 'HIGRESS') ? selectedApi as RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem : undefined,
        higressRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'HIGRESS' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelRouteName: (selectedApi as any).modelRouteName, fromGatewayType: 'HIGRESS' as const }
            : { mcpServerName: (selectedApi as any).mcpServerName, fromGatewayType: 'HIGRESS' as const }
        ) : undefined,
        nacosRefConfig: sourceType === 'NACOS' && selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'NACOS' ? {
          ...selectedApi,
          namespaceId: selectedNamespace || 'public'
        } : undefined,
        adpAIGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'ADP_AI_GATEWAY' ? selectedApi as APIGAIMCPItem : undefined,
        apsaraGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'APSARA_GATEWAY' ? selectedApi as APIGAIMCPItem : undefined,
        apiDefinitionIds: undefined,
      }
      apiProductApi.createApiProductRef(apiProduct.productId, newService).then(async () => {
        message.success('关联成功')
        setIsModalVisible(false)

        // 重新获取关联信息并更新
        try {
          const res = await apiProductApi.getApiProductRef(apiProduct.productId)
          onLinkedServiceUpdate(res.data || null)
        } catch (error) {
          console.error('获取关联API失败:', error)
          onLinkedServiceUpdate(null)
        }

        // 重新获取产品详情（特别重要，因为关联API后apiProduct.apiConfig可能会更新）
        handleRefresh()

        form.resetFields()
        setSelectedGateway(null)
        setSelectedNacos(null)
        setApiList([])
        setSourceType('GATEWAY')
      }).catch(() => {
        message.error('关联失败')
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


  const handleDelete = () => {
    if (!linkedService) return

    Modal.confirm({
      title: '确认解除关联',
      content: '确定要解除与当前API的关联吗？',
      icon: <ExclamationCircleOutlined />,
      onOk() {
        return apiProductApi.deleteApiProductRef(apiProduct.productId).then(() => {
          message.success('解除关联成功')
          onLinkedServiceUpdate(null)
          // 重新获取产品详情（解除关联后apiProduct.apiConfig可能会更新）
          handleRefresh()
        }).catch(() => {
          message.error('解除关联失败')
        })
      }
    })
  }

  const getServiceInfo = () => {
    if (!linkedService) return null

    let apiName = ''
    let apiType = ''
    let sourceInfo = ''
    let gatewayInfo = ''

    if (linkedService.sourceType === 'MANAGED') {
      if (linkedService.apiDefinitions && linkedService.apiDefinitions.length > 0) {
        const apiDef = linkedService.apiDefinitions[0];
        apiName = apiDef.name;
        apiType = apiDef.type;
        sourceInfo = 'Managed API';
        gatewayInfo = apiDef.apiDefinitionId;
      } else {
        apiName = 'Managed API';
        apiType = apiProduct.type;
        sourceInfo = 'Managed API';
        gatewayInfo = linkedService.apiDefinitionIds?.[0] || 'Unknown';
      }
      return {
        apiName,
        apiType,
        sourceInfo,
        gatewayInfo
      }
    }

    // 首先根据 Product 的 type 确定基本类型
    if (apiProduct.type === 'REST_API') {
      // REST API 类型产品 - 只能关联 API 网关上的 REST API
      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'apiName' in linkedService.apigRefConfig) {
        apiName = linkedService.apigRefConfig.apiName || '未命名'
        apiType = 'REST API'
        sourceInfo = 'API网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      }
    } else if (apiProduct.type === 'MCP_SERVER') {
      // MCP Server 类型产品 - 可以关联多种平台上的 MCP Server
      apiType = 'MCP Server'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'mcpServerName' in linkedService.apigRefConfig) {
        // AI网关上的MCP Server
        apiName = linkedService.apigRefConfig.mcpServerName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
        // Higress网关上的MCP Server
        apiName = linkedService.higressRefConfig.mcpServerName || '未命名'
        sourceInfo = 'Higress网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
        // 专有云AI网关上的MCP Server
        apiName = linkedService.adpAIGatewayRefConfig.mcpServerName || '未命名'
        sourceInfo = '专有云AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        // 飞天企业版AI网关上的MCP Server
        apiName = linkedService.apsaraGatewayRefConfig.mcpServerName || '未命名'
        sourceInfo = '飞天企业版AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
        // Nacos上的MCP Server
        apiName = linkedService.nacosRefConfig.mcpServerName || '未命名'
        sourceInfo = 'Nacos服务发现'
        gatewayInfo = linkedService.nacosId || '未知'
      }
    } else if (apiProduct.type === 'AGENT_API') {
      // Agent API 类型产品 - 可以关联 AI 网关或 Nacos 上的 Agent API
      apiType = 'Agent API'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'agentApiName' in linkedService.apigRefConfig) {
        // AI网关上的Agent API
        apiName = linkedService.apigRefConfig.agentApiName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'agentName' in linkedService.nacosRefConfig) {
        // Nacos 上的 Agent API
        apiName = linkedService.nacosRefConfig.agentName || '未命名'
        sourceInfo = 'Nacos Agent Registry'
        gatewayInfo = linkedService.nacosId || '未知'
      }
      // 注意：Agent API 不支持专有云AI网关（ADP_AI_GATEWAY）
    } else if (apiProduct.type === 'MODEL_API') {
      // Model API 类型产品 - 可以关联 AI 网关或 Higress 网关上的 Model API
      apiType = 'Model API'

      if (linkedService.sourceType === 'GATEWAY' && linkedService.apigRefConfig && 'modelApiName' in linkedService.apigRefConfig) {
        // AI网关上的Model API
        apiName = linkedService.apigRefConfig.modelApiName || '未命名'
        sourceInfo = 'AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig && 'modelRouteName' in linkedService.higressRefConfig) {
        // Higress网关上的Model API（AI路由）
        apiName = linkedService.higressRefConfig.modelRouteName || '未命名'
        sourceInfo = 'Higress网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      }
    }

    return {
      apiName,
      apiType,
      sourceInfo,
      gatewayInfo
    }
  }

  const renderLinkInfo = () => {
    const serviceInfo = getServiceInfo()

    // 没有关联任何API
    if (!linkedService || !serviceInfo) {
      return (
        <Card className="mb-6">
          <div className="text-center py-8">
            <div className="text-gray-500 mb-4">暂未关联任何API</div>
            <div className="space-x-4">
              <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/api-definitions/create', { state: { productName: apiProduct.name, productId: apiProduct.productId, productType: apiProduct.type } })}>
                创建 API
              </Button>
              <Button icon={<PlusOutlined />} onClick={() => setIsModalVisible(true)}>
                关联已有的 API
              </Button>
            </div>
          </div>
        </Card>
      )
    }

    return (
      <Card
        className="mb-6"
        title="关联详情"
        extra={
          <div className="space-x-2">
            {linkedService.sourceType === 'MANAGED' && (
              <>
                <Button
                  icon={<EditOutlined />}
                  onClick={() => {
                    const apiId = linkedService.apiDefinitions?.[0]?.apiDefinitionId || linkedService.apiDefinitionIds?.[0];
                    if (apiId) {
                      navigate(`/api-definitions/edit?id=${apiId}`, { state: { productName: apiProduct.name, productId: apiProduct.productId, productType: apiProduct.type } });
                    }
                  }}
                >
                  编辑 API
                </Button>
                <Button
                  icon={<CloudUploadOutlined />}
                  onClick={() => {
                    const apiId = linkedService.apiDefinitions?.[0]?.apiDefinitionId || linkedService.apiDefinitionIds?.[0];
                    if (apiId) {
                      navigate(`/api-definitions/publish?id=${apiId}`, { state: { productName: apiProduct.name, productId: apiProduct.productId, productType: apiProduct.type } });
                    }
                  }}
                >
                  发布 API
                </Button>
              </>
            )}
            <Button type="primary" danger icon={<DeleteOutlined />} onClick={handleDelete}>
              解除关联
            </Button>
          </div>
        }
      >
        <div>
          {/* 第一行：名称 + 类型 */}
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">名称:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiName || '未命名'}</span>
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiType}</span>
          </div>

          {/* 第二行：来源 + ID */}
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">来源:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.sourceInfo}</span>
            <span className="text-xs text-gray-600">
              {linkedService?.sourceType === 'NACOS' ? 'Nacos ID:' : 
               linkedService?.sourceType === 'MANAGED' ? 'API ID:' : '网关ID:'}
            </span>
            <span className="col-span-2 text-xs text-gray-700">{serviceInfo.gatewayInfo}</span>
          </div>
        </div>
      </Card>
    )
  }

  const convertEndpointsToOpenApiSpec = (endpoints: Endpoint[]) => {
    const paths: any = {};

    endpoints.forEach(endpoint => {
      if (endpoint.type !== 'REST_ROUTE') return;

      const config = endpoint.config || {};
      const path = config.path;
      const method = (config.method || 'get').toLowerCase();

      if (!path) return;

      if (!paths[path]) {
        paths[path] = {};
      }

      const parameters: any[] = [];

      // Path parameters
      if (config.pathParams) {
        config.pathParams.forEach((p: any) => {
          parameters.push({
            name: p.name,
            in: 'path',
            required: p.required !== false, // Default to true for path params usually
            description: p.description,
            schema: { type: 'string' } // Simplified
          });
        });
      }

      // Query parameters
      if (config.parameters) {
        config.parameters.forEach((p: any) => {
          parameters.push({
            name: p.name,
            in: 'query',
            required: p.required === true,
            description: p.description,
            schema: { type: 'string' } // Simplified
          });
        });
      }

      // Headers
      if (config.headers) {
        config.headers.forEach((h: any) => {
          parameters.push({
            name: h.name,
            in: 'header',
            required: h.required === true,
            description: h.description,
            schema: { type: 'string' }
          });
        });
      }

      const operation: any = {
        summary: endpoint.name,
        description: endpoint.description,
        parameters: parameters,
        responses: {
          '200': {
            description: 'Successful response'
          }
        }
      };

      // Request Body
      if (config.requestBody) {
        try {
          const schema = typeof config.requestBody === 'string' 
            ? JSON.parse(config.requestBody) 
            : config.requestBody;
          operation.requestBody = {
            content: {
              'application/json': {
                schema: schema
              }
            }
          };
        } catch (e) {
          console.warn('Failed to parse request body schema', e);
        }
      }

      paths[path][method] = operation;
    });

    const spec = {
      openapi: '3.0.0',
      info: {
        title: apiProduct.name,
        version: '1.0.0'
      },
      paths: paths
    };

    return JSON.stringify(spec, null, 2);
  }

  const convertEndpointsToModelAPIConfig = (endpoints: Endpoint[], metadata?: any, publishRecords?: any[]) => {
    // Extract domains from publish records
    const domains: Array<{ domain: string; protocol: string }> = [];
    
    if (publishRecords) {
      publishRecords.forEach(record => {
        // Check for ACTIVE or PUBLISHED status
        if ((record.status === 'PUBLISHED' || record.status === 'ACTIVE') && record.publishConfig?.domains) {
           // publishConfig.domains is string[]
           const recordDomains = record.publishConfig.domains;
           if (Array.isArray(recordDomains)) {
             recordDomains.forEach((d: string) => {
               // Simple heuristic for protocol, or default to HTTPS
               // If domain starts with http:// or https://, strip it and set protocol
               let protocol = 'HTTPS';
               let domain = d;
               if (d.startsWith('http://')) {
                 protocol = 'HTTP';
                 domain = d.substring(7);
               } else if (d.startsWith('https://')) {
                 protocol = 'HTTPS';
                 domain = d.substring(8);
               }
               
               // Avoid duplicates
               if (!domains.some(existing => existing.domain === domain && existing.protocol === protocol)) {
                 domains.push({ domain, protocol });
               }
             });
           }
        }
      });
    }

    const routes = endpoints
      .filter(ep => ep.type === 'MODEL')
      .map(ep => {
        const config = ep.config || {};
        const matchConfig = config.matchConfig || {};
        
        return {
          domains: domains, 
          description: ep.description || '',
          match: {
            methods: matchConfig.methods || [],
            path: {
              value: matchConfig.path?.value || '/',
              type: matchConfig.path?.type || 'Exact'
            },
            headers: [],
            queryParams: []
          }
        };
      });

    return {
      routes
    };
  }

  const convertEndpointsToAgentAPIConfig = (endpoints: Endpoint[], metadata?: any, publishRecords?: any[]) => {
    // Extract domains from publish records
    const domains: Array<{ domain: string; protocol: string }> = [];
    
    if (publishRecords) {
      publishRecords.forEach(record => {
        if ((record.status === 'PUBLISHED' || record.status === 'ACTIVE') && record.publishConfig?.domains) {
           const recordDomains = record.publishConfig.domains;
           if (Array.isArray(recordDomains)) {
             recordDomains.forEach((d: string) => {
               let protocol = 'HTTPS';
               let domain = d;
               if (d.startsWith('http://')) {
                 protocol = 'HTTP';
                 domain = d.substring(7);
               } else if (d.startsWith('https://')) {
                 protocol = 'HTTPS';
                 domain = d.substring(8);
               }
               
               if (!domains.some(existing => existing.domain === domain && existing.protocol === protocol)) {
                 domains.push({ domain, protocol });
               }
             });
           }
        }
      });
    }

    const routes = endpoints
      .filter(ep => ep.type === 'AGENT')
      .map(ep => {
        const config = ep.config || {};
        const matchConfig = config.matchConfig || {};
        
        return {
          domains: domains, 
          description: ep.description || '',
          match: {
            methods: matchConfig.methods || [],
            path: {
              value: matchConfig.path?.value || '/',
              type: matchConfig.path?.type || 'Exact'
            },
            headers: matchConfig.headers || [],
            queryParams: matchConfig.queryParams || []
          }
        };
      });

    return {
      routes
    };
  }

  const renderApiConfig = () => {
    const isMcp = apiProduct.type === 'MCP_SERVER'
    const isOpenApi = apiProduct.type === 'REST_API'
    const isAgent = apiProduct.type === 'AGENT_API'
    const isModel = apiProduct.type === 'MODEL_API'

    // Check if we have tools from Managed API
    const hasManagedTools = isMcp && linkedService?.sourceType === 'MANAGED' && parsedTools.length > 0;

    // MCP Server类型：无论是否有linkedService都显示tools和连接点配置  
    if (isMcp && (apiProduct.mcpConfig || hasManagedTools)) {
      return (
        <Card title="配置详情">
          <Row gutter={24}>
            {/* 左侧：工具列表 */}
            <Col span={15}>
              <Card>
                <Tabs
                  defaultActiveKey="tools"
                  items={[
                    {
                      key: "tools",
                      label: `Tools (${parsedTools.length})`,
                      children: parsedTools.length > 0 ? (
                        <div className="border border-gray-200 rounded-lg bg-gray-50">
                          {parsedTools.map((tool, idx) => (
                            <div key={idx} className={idx < parsedTools.length - 1 ? "border-b border-gray-200" : ""}>
                              <Collapse
                                ghost
                                expandIconPlacement="end"
                                items={[{
                                  key: idx.toString(),
                                  label: tool.name,
                                  children: (
                                    <div className="px-4 pb-2">
                                      <div className="text-gray-600 mb-4">{tool.description}</div>

                                      {tool.args && tool.args.length > 0 && (
                                        <div>
                                          <p className="font-medium text-gray-700 mb-3">输入参数:</p>
                                          {tool.args.map((arg, argIdx) => (
                                            <div key={argIdx} className="mb-3">
                                              <div className="flex items-center mb-2">
                                                <span className="font-medium text-gray-800 mr-2">{arg.name}</span>
                                                <span className="text-xs bg-gray-200 text-gray-600 px-2 py-1 rounded mr-2">
                                                  {arg.type}
                                                </span>
                                                {arg.required && (
                                                  <span className="text-red-500 text-xs mr-2">*</span>
                                                )}
                                                {arg.description && (
                                                  <span className="text-xs text-gray-500">
                                                    {arg.description}
                                                  </span>
                                                )}
                                              </div>
                                              <input
                                                type="text"
                                                placeholder={arg.description || `请输入${arg.name}`}
                                                className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent mb-2"
                                                defaultValue={arg.default !== undefined ? JSON.stringify(arg.default) : ''}
                                              />
                                              {arg.enum && (
                                                <div className="text-xs text-gray-500">
                                                  可选值: {arg.enum.map(value => <code key={value} className="mr-1">{value}</code>)}
                                                </div>
                                              )}
                                            </div>
                                          ))}
                                        </div>
                                      )}
                                    </div>
                                  )
                                }]}
                              />
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="text-gray-500 text-center py-8">
                          No tools available
                        </div>
                      ),
                    },
                  ]}
                />
              </Card>
            </Col>

            {/* 右侧：连接点配置 */}
            <Col span={9}>
              {apiProduct.mcpConfig ? (
              <Card>
                <div className="mb-4">
                  <h3 className="text-sm font-semibold mb-3">连接点配置</h3>

                  {/* 域名选择器 */}
                  {apiProduct.mcpConfig?.mcpServerConfig?.domains && apiProduct.mcpConfig.mcpServerConfig.domains.length > 0 && (
                    <div className="mb-2">
                      <div className="flex border border-gray-200 rounded-md overflow-hidden">
                        <div className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                          域名
                        </div>
                        <div className="flex-1 min-w-0">
                          <Select
                            value={selectedDomainIndex}
                            onChange={setSelectedDomainIndex}
                            className="w-full"
                            placeholder="选择域名"
                            size="middle"
                            variant='borderless'
                            style={{
                              fontSize: '12px',
                              height: '100%'
                            }}
                          >
                            {getDomainOptions(apiProduct.mcpConfig.mcpServerConfig.domains).map((option) => (
                              <Select.Option key={option.value} value={option.value}>
                                <span title={option.label} className="text-xs text-gray-900 font-mono">
                                  {option.label}
                                </span>
                              </Select.Option>
                            ))}
                          </Select>
                        </div>
                      </div>
                    </div>
                  )}

                  <Tabs
                    size="small"
                    defaultActiveKey={localJson ? "local" : (sseJson ? "sse" : "http")}
                    items={(() => {
                      const tabs = [];

                      if (localJson) {
                        tabs.push({
                          key: "local",
                          label: "Stdio",
                          children: (
                            <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                              <Button
                                size="small"
                                icon={<CopyOutlined />}
                                className="absolute top-2 right-2 z-10"
                                onClick={() => handleCopy(localJson)}
                              >
                              </Button>
                              <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                                <pre className="whitespace-pre">{localJson}</pre>
                              </div>
                            </div>
                          ),
                        });
                      } else {
                        if (sseJson) {
                          tabs.push({
                            key: "sse",
                            label: "SSE",
                            children: (
                              <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                                <Button
                                  size="small"
                                  icon={<CopyOutlined />}
                                  className="absolute top-2 right-2 z-10"
                                  onClick={() => handleCopy(sseJson)}
                                >
                                </Button>
                                <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                                  <pre className="whitespace-pre">{sseJson}</pre>
                                </div>
                              </div>
                            ),
                          });
                        }

                        if (httpJson) {
                          tabs.push({
                            key: "http",
                            label: "Streamable HTTP",
                            children: (
                              <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                                <Button
                                  size="small"
                                  icon={<CopyOutlined />}
                                  className="absolute top-2 right-2 z-10"
                                  onClick={() => handleCopy(httpJson)}
                                >
                                </Button>
                                <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                                  <pre className="whitespace-pre">{httpJson}</pre>
                                </div>
                              </div>
                            ),
                          });
                        }
                      }

                      return tabs;
                    })()}
                  />
                </div>
              </Card>
              ) : (
                <Card>
                  <div className="text-gray-500 text-center py-8">
                    暂无连接配置信息
                  </div>
                </Card>
              )}
            </Col>
          </Row>
        </Card>
      )
    }

    // Agent API类型：显示协议支持和路由配置或 AgentCard
    if (isAgent && (apiProduct.agentConfig?.agentAPIConfig || (linkedService?.sourceType === 'MANAGED' && linkedService?.apiDefinitions?.[0]))) {
      let agentAPIConfig = apiProduct.agentConfig?.agentAPIConfig;

      // Check if it is Managed Agent API
      if (!agentAPIConfig && linkedService?.sourceType === 'MANAGED' && linkedService.apiDefinitions?.[0]?.endpoints) {
        const endpoints = linkedService.apiDefinitions[0].endpoints;
        const hasRestRoutes = endpoints.some((ep: Endpoint) => ep.type === 'REST_ROUTE');
        const hasAgentRoutes = endpoints.some((ep: Endpoint) => ep.type === 'AGENT');

        if (hasRestRoutes) {
          const spec = convertEndpointsToOpenApiSpec(endpoints);
          const protocol = (linkedService.apiDefinitions[0] as any).metadata?.protocol;
          return (
            <Card title="配置详情">
              {protocol && (
                <div className="mb-4">
                  <div className="text-sm text-gray-600">支持协议</div>
                  <div className="font-medium">{protocol}</div>
                </div>
              )}
              <SwaggerUIWrapper apiSpec={spec} />
            </Card>
          )
        } else if (hasAgentRoutes) {
           const metadata = (linkedService.apiDefinitions[0] as any).metadata;
           agentAPIConfig = convertEndpointsToAgentAPIConfig(endpoints, metadata, publishRecords);
        }
      }

      agentAPIConfig = agentAPIConfig || {} as any
      const routes = agentAPIConfig.routes || []
      const protocols = agentAPIConfig.agentProtocols || []
      const isA2A = protocols.includes('a2a')
      const agentCard = agentAPIConfig.agentCard


      // 生成匹配类型前缀文字
      const getMatchTypePrefix = (matchType: string) => {
        switch (matchType) {
          case 'Exact':
            return '等于'
          case 'Prefix':
            return '前缀是'
          case 'Regex':
            return '正则是'
          default:
            return '等于'
        }
      }

      // 获取所有唯一域名的简化版本
      const getAllUniqueDomains = () => {
        const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>()

        routes.forEach(route => {
          if (route.domains && route.domains.length > 0) {
            route.domains.forEach((domain: any) => {
              const key = `${domain.protocol}://${domain.domain}${domain.port ? `:${domain.port}` : ''}`
              domainsMap.set(key, domain)
            })
          }
        })

        return Array.from(domainsMap.values())
      }

      const allUniqueDomains = getAllUniqueDomains()

      // 生成域名选择器选项
      const agentDomainOptions = allUniqueDomains.map((domain, index) => {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        return {
          value: index,
          label: `${domain.protocol.toLowerCase()}://${formattedDomain}`
        }
      })

      // 生成路由显示文本（优化方法显示）
      const getRouteDisplayText = (route: any, domainIndex: number = 0) => {
        if (!route.match) return 'Unknown Route'

        const path = route.match.path?.value || '/'
        const pathType = route.match.path?.type

        // 拼接域名信息 - 使用选择的域名索引
        let domainInfo = ''
        if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
          const selectedDomain = allUniqueDomains[domainIndex]
          const formattedDomain = formatDomainWithPort(selectedDomain.domain, selectedDomain.port, selectedDomain.protocol);
          domainInfo = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`
        } else if (route.domains && route.domains.length > 0) {
          // 回退到路由的第一个域名
          const domain = route.domains[0]
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`
        }

        // 构建基本路由信息（匹配符号直接加到path后面）
        let pathWithSuffix = path
        if (pathType === 'Prefix') {
          pathWithSuffix = `${path}*`
        } else if (pathType === 'Regex') {
          pathWithSuffix = `${path}~`
        }
        // 精确匹配不加任何符号

        let routeText = `${domainInfo}${pathWithSuffix}`

        // 添加描述信息
        if (route.description && route.description.trim()) {
          routeText += ` - ${route.description.trim()}`
        }

        return routeText
      }

      // 生成完整URL
      const getFullUrl = (route: any, domainIndex: number = 0) => {
        if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
          const selectedDomain = allUniqueDomains[domainIndex]
          const formattedDomain = formatDomainWithPort(selectedDomain.domain, selectedDomain.port, selectedDomain.protocol);
          const path = route.match?.path?.value || '/'
          return `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`
        } else if (route.domains && route.domains.length > 0) {
          const domain = route.domains[0]
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          const path = route.match?.path?.value || '/'
          return `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`
        }
        return ''
      }

      return (
        <Card title="配置详情">
          <div className="space-y-6">
            {/* 协议信息 */}
            {(linkedService?.apiDefinitions?.[0]?.metadata?.protocol || protocols.length > 0) && (
              <div>
                <div className="text-sm text-gray-600">支持协议</div>
                <div className="font-medium">
                  {linkedService?.apiDefinitions?.[0]?.metadata?.protocol || protocols.join(', ')}
                </div>
              </div>
            )}

            {/* A2A 协议：额外显示 AgentCard */}
            {isA2A && agentCard && (
              <div className="border-t pt-4">
                <h3 className="text-lg font-semibold mb-4">Agent Card 信息</h3>
                <div className="space-y-4">
                  {/* 基本信息 */}
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <div className="text-sm text-gray-600">名称</div>
                      <div className="font-medium">{agentCard.name}</div>
                    </div>
                    <div>
                      <div className="text-sm text-gray-600">版本</div>
                      <div className="font-medium">{agentCard.version}</div>
                    </div>
                  </div>

                  {agentCard.protocolVersion && (
                    <div>
                      <div className="text-sm text-gray-600">协议版本</div>
                      <div className="font-mono text-sm">{agentCard.protocolVersion}</div>
                    </div>
                  )}

                  {agentCard.description && (
                    <div>
                      <div className="text-sm text-gray-600">描述</div>
                      <div>{agentCard.description}</div>
                    </div>
                  )}

                  {agentCard.url && (
                    <div>
                      <div className="text-sm text-gray-600">URL</div>
                      <div className="font-mono text-sm">{agentCard.url}</div>
                    </div>
                  )}

                  {agentCard.preferredTransport && (
                    <div>
                      <div className="text-sm text-gray-600">传输协议</div>
                      <div>{agentCard.preferredTransport}</div>
                    </div>
                  )}

                  {/* Additional Interfaces */}
                  {agentCard.additionalInterfaces && agentCard.additionalInterfaces.length > 0 && (
                    <div>
                      <div className="text-sm text-gray-600 mb-2">附加接口</div>
                      <div className="space-y-2">
                        {agentCard.additionalInterfaces.map((iface: any, idx: number) => (
                          <div key={idx} className="border border-gray-200 rounded p-3 bg-gray-50">
                            <div className="flex items-center gap-2 mb-1">
                              <span className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded font-medium">
                                {iface.transport || 'Unknown'}
                              </span>
                            </div>
                            <div className="font-mono text-sm text-gray-700 break-all">
                              {iface.url}
                            </div>
                            {/* 显示其他附加字段 */}
                            {Object.keys(iface).filter(k => k !== 'transport' && k !== 'url').length > 0 && (
                              <div className="mt-2 text-xs text-gray-500">
                                {Object.entries(iface)
                                  .filter(([k]) => k !== 'transport' && k !== 'url')
                                  .map(([k, v]) => (
                                    <div key={k}>
                                      <span className="font-medium">{k}:</span> {String(v)}
                                    </div>
                                  ))
                                }
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Skills */}
                  {agentCard.skills && agentCard.skills.length > 0 && (
                    <div>
                      <div className="text-sm text-gray-600 mb-2">技能列表</div>
                      <div className="space-y-2">
                        {agentCard.skills.map((skill: any, idx: number) => (
                          <div key={idx} className="border border-gray-200 rounded p-3">
                            <div className="font-medium">{skill.name}</div>
                            {skill.description && (
                              <div className="text-sm text-gray-600 mt-1">{skill.description}</div>
                            )}
                            {skill.tags && skill.tags.length > 0 && (
                              <div className="flex gap-2 mt-2">
                                {skill.tags.map((tag: string, tagIdx: number) => (
                                  <span key={tagIdx} className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded">
                                    {tag}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Capabilities */}
                  {agentCard.capabilities && (
                    <div>
                      <div className="text-sm text-gray-600 mb-2">能力</div>
                      <pre className="bg-gray-50 p-3 rounded text-sm overflow-auto">
                        {JSON.stringify(agentCard.capabilities, null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* 路由配置（如果有）*/}
            {routes.length > 0 && (
              <div className={isA2A && agentCard ? 'border-t pt-4' : ''}>
                <div className="text-sm text-gray-600 mb-3">路由配置:</div>

                {/* 域名选择器 */}
                {agentDomainOptions.length > 1 && (
                  <div className="mb-2">
                    <div className="flex items-stretch border border-gray-200 rounded-md overflow-hidden">
                      <div className="bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                        域名
                      </div>
                      <div className="flex-1">
                        <Select
                          value={selectedAgentDomainIndex}
                          onChange={setSelectedAgentDomainIndex}
                          className="w-full"
                          placeholder="选择域名"
                          size="middle"
                          bordered={false}
                          style={{
                            fontSize: '12px',
                            height: '100%'
                          }}
                        >
                          {agentDomainOptions.map((option) => (
                            <Select.Option key={option.value} value={option.value}>
                              <span className="text-xs text-gray-900 font-mono">
                                {option.label}
                              </span>
                            </Select.Option>
                          ))}
                        </Select>
                      </div>
                    </div>
                  </div>
                )}

                <div className="border border-gray-200 rounded-lg overflow-hidden">
                  <Collapse
                    ghost expandIconPlacement="end"
                  >
                    {routes.map((route, index) => (
                      <Collapse.Panel
                        key={index}
                        header={
                          <div className="flex items-center justify-between py-2 px-4 hover:bg-gray-50">
                            <div className="flex-1">
                              <div className="font-mono text-sm font-medium text-blue-600">
                                {getRouteDisplayText(route, selectedAgentDomainIndex)}
                              </div>
                            </div>
                            <Button
                              size="small"
                              type="text"
                              onClick={async (e) => {
                                e.stopPropagation()
                                const fullUrl = getFullUrl(route, selectedAgentDomainIndex)
                                if (fullUrl) {
                                  try {
                                    await copyToClipboard(fullUrl)
                                    message.success('链接已复制到剪贴板')
                                  } catch (error) {
                                    message.error('复制失败')
                                  }
                                }
                              }}
                            >
                              <CopyOutlined />
                            </Button>
                          </div>
                        }
                        style={{
                          borderBottom: index < routes.length - 1 ? '1px solid #e5e7eb' : 'none'
                        }}
                      >
                        <div className="pl-4 space-y-3">
                          {/* 域名信息 */}
                          <div>
                            <div className="text-xs text-gray-500 mb-1">域名:</div>
                            {route.domains?.map((domain: any, domainIndex: number) => {
                              const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
                              return (
                                <div key={domainIndex} className="text-sm">
                                  <span className="font-mono">{domain.protocol.toLowerCase()}://{formattedDomain}</span>
                                </div>
                              )
                            })}
                          </div>

                          {/* 匹配规则 */}
                          <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                              <div className="text-xs text-gray-500">路径:</div>
                              <div className="font-mono">
                                {getMatchTypePrefix(route.match?.path?.type)} {route.match?.path?.value}
                              </div>
                            </div>
                            <div>
                              <div className="text-xs text-gray-500">方法:</div>
                              <div>{route.match?.methods ? route.match.methods.join(', ') : 'ANY'}</div>
                            </div>
                          </div>

                          {/* 请求头匹配 */}
                          {route.match?.headers && route.match.headers.length > 0 && (
                            <div>
                              <div className="text-xs text-gray-500 mb-1">请求头匹配:</div>
                              <div className="space-y-1">
                                {route.match.headers.map((header: any, headerIndex: number) => (
                                  <div key={headerIndex} className="text-sm font-mono">
                                    {header.name} {getMatchTypePrefix(header.type)} {header.value}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* 查询参数匹配 */}
                          {route.match?.queryParams && route.match.queryParams.length > 0 && (
                            <div>
                              <div className="text-xs text-gray-500 mb-1">查询参数匹配:</div>
                              <div className="space-y-1">
                                {route.match.queryParams.map((param: any, paramIndex: number) => (
                                  <div key={paramIndex} className="text-sm font-mono">
                                    {param.name} {getMatchTypePrefix(param.type)} {param.value}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* 描述 */}
                          {route.description && (
                            <div>
                              <div className="text-xs text-gray-500">描述:</div>
                              <div className="text-sm">{route.description}</div>
                            </div>
                          )}
                        </div>
                      </Collapse.Panel>
                    ))}
                  </Collapse>
                </div>
              </div>
            )}
          </div>
        </Card>
      )
    }

    // Model API类型：显示协议支持和路由配置
    let modelAPIConfig = apiProduct.modelConfig?.modelAPIConfig;

    if (isModel && !modelAPIConfig && linkedService?.sourceType === 'MANAGED' && linkedService.apiDefinitions?.[0]?.endpoints) {
      const endpoints = linkedService.apiDefinitions[0].endpoints;
      const metadata = (linkedService.apiDefinitions[0] as any).metadata;
      modelAPIConfig = convertEndpointsToModelAPIConfig(endpoints, metadata, publishRecords);
    }

    if (isModel && modelAPIConfig) {
      const routes = modelAPIConfig.routes || []

      // 获取所有唯一域名的简化版本
      const getAllModelUniqueDomains = () => {
        const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>()

        routes.forEach(route => {
          if (route.domains && route.domains.length > 0) {
            route.domains.forEach((domain: any) => {
              const key = `${domain.protocol}://${domain.domain}${domain.port ? `:${domain.port}` : ''}`
              domainsMap.set(key, domain)
            })
          }
        })

        return Array.from(domainsMap.values())
      }

      const allModelUniqueDomains = getAllModelUniqueDomains()

      // 生成域名选择器选项
      const modelDomainOptions = allModelUniqueDomains.map((domain, index) => {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        return {
          value: index,
          label: `${domain.protocol.toLowerCase()}://${formattedDomain}`
        }
      })

      // 生成匹配类型前缀文字
      const getMatchTypePrefix = (matchType: string) => {
        switch (matchType) {
          case 'Exact':
            return '等于'
          case 'Prefix':
            return '前缀是'
          case 'Regex':
            return '正则是'
          default:
            return '等于'
        }
      }

      // 生成路由显示文本
      const getRouteDisplayText = (route: any, domainIndex: number = 0) => {
        if (!route.match) return 'Unknown Route'

        const path = route.match.path?.value || '/'
        const pathType = route.match.path?.type

        // 拼接域名信息 - 使用选择的域名索引
        let domainInfo = ''
        if (allModelUniqueDomains.length > 0 && allModelUniqueDomains.length > domainIndex) {
          const selectedDomain = allModelUniqueDomains[domainIndex]
          const formattedDomain = formatDomainWithPort(selectedDomain.domain, selectedDomain.port, selectedDomain.protocol);
          domainInfo = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`
        } else if (route.domains && route.domains.length > 0) {
          // 回退到路由的第一个域名
          const domain = route.domains[0]
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`
        }

        // 构建基本路由信息（匹配符号直接加到path后面）
        let pathWithSuffix = path
        if (pathType === 'Prefix') {
          pathWithSuffix = `${path}*`
        } else if (pathType === 'Regex') {
          pathWithSuffix = `${path}~`
        }

        let routeText = `${domainInfo}${pathWithSuffix}`

        // 添加描述信息
        if (route.description && route.description.trim()) {
          routeText += ` - ${route.description}`
        }

        return routeText
      }

      // 生成方法文本
      const getMethodsText = (route: any) => {
        const methods = route.match?.methods
        if (!methods || methods.length === 0) {
          return 'ANY'
        }
        return methods.join(', ')
      }

      // 获取完整URL用于复制
      const getFullUrl = (route: any, domainIndex: number = 0) => {
        if (allModelUniqueDomains.length > 0 && allModelUniqueDomains.length > domainIndex) {
          const selectedDomain = allModelUniqueDomains[domainIndex]
          const formattedDomain = formatDomainWithPort(selectedDomain.domain, selectedDomain.port, selectedDomain.protocol);
          const path = route.match?.path?.value || '/'
          return `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`
        } else if (route.domains && route.domains.length > 0) {
          const domain = route.domains[0]
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          const path = route.match?.path?.value || '/'
          return `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`
        }
        return null
      }

      return (
        <Card title="配置详情">
          <div className="space-y-4">
            {/* 路由配置表格 */}
            {routes.length > 0 && (
              <div>
                <div className="text-sm text-gray-600 mb-3">路由配置:</div>

                {/* 域名选择器 */}
                {modelDomainOptions.length > 0 && (
                  <div className="mb-2">
                    <div className="flex items-stretch border border-gray-200 rounded-md overflow-hidden">
                      <div className="bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-200 flex items-center whitespace-nowrap">
                        域名
                      </div>
                      <div className="flex-1">
                        <Select
                          value={selectedModelDomainIndex}
                          onChange={setSelectedModelDomainIndex}
                          className="w-full"
                          placeholder="选择域名"
                          size="middle"
                          bordered={false}
                          style={{
                            fontSize: '12px',
                            height: '100%'
                          }}
                        >
                          {modelDomainOptions.map((option) => (
                            <Select.Option key={option.value} value={option.value}>
                              <span className="text-xs text-gray-900 font-mono">
                                {option.label}
                              </span>
                            </Select.Option>
                          ))}
                        </Select>
                      </div>
                    </div>
                  </div>
                )}

                <div className="border border-gray-200 rounded-lg overflow-hidden">
                  <Collapse ghost expandIconPlacement="end">
                    {routes.map((route, index) => (
                      <Collapse.Panel
                        key={index}
                        header={
                          <div className="flex items-center justify-between py-3 px-4 hover:bg-gray-50">
                            <div className="flex-1">
                              <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                                {getRouteDisplayText(route, selectedModelDomainIndex)}
                                {route.builtin && (
                                  <span className="ml-2 px-2 py-0.5 text-xs bg-green-100 text-green-800 rounded-full">默认</span>
                                )}
                              </div>
                              <div className="text-xs text-gray-500">
                                方法: <span className="font-medium text-gray-700">{getMethodsText(route)}</span>
                              </div>
                            </div>
                            <Button
                              size="small"
                              type="text"
                              onClick={async (e) => {
                                e.stopPropagation()
                                const fullUrl = getFullUrl(route, selectedModelDomainIndex)
                                if (fullUrl) {
                                  try {
                                    await copyToClipboard(fullUrl)
                                    message.success('链接已复制到剪贴板')
                                  } catch (error) {
                                    message.error('复制失败')
                                  }
                                }
                              }}
                            >
                              <CopyOutlined />
                            </Button>
                          </div>
                        }
                        style={{
                          borderBottom: index < routes.length - 1 ? '1px solid #e5e7eb' : 'none'
                        }}
                      >
                        <div className="pl-4 space-y-3">
                          {/* 域名信息 */}
                          <div>
                            <div className="text-xs text-gray-500 mb-1">域名:</div>
                            {route.domains?.map((domain: any, domainIndex: number) => {
                              const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
                              return (
                                <div key={domainIndex} className="text-sm">
                                  <span className="font-mono">{domain.protocol.toLowerCase()}://{formattedDomain}</span>
                                </div>
                              )
                            })}
                          </div>

                          {/* 匹配规则 */}
                          <div className="grid grid-cols-2 gap-4 text-sm">
                            <div>
                              <div className="text-xs text-gray-500">路径:</div>
                              <div className="font-mono">
                                {getMatchTypePrefix(route.match?.path?.type)} {route.match?.path?.value}
                              </div>
                            </div>
                            <div>
                              <div className="text-xs text-gray-500">方法:</div>
                              <div>{route.match?.methods ? route.match.methods.join(', ') : 'ANY'}</div>
                            </div>
                          </div>

                          {/* 请求头匹配 */}
                          {route.match?.headers && route.match.headers.length > 0 && (
                            <div>
                              <div className="text-xs text-gray-500 mb-1">请求头匹配:</div>
                              <div className="space-y-1">
                                {route.match.headers.map((header: any, headerIndex: number) => (
                                  <div key={headerIndex} className="text-sm font-mono">
                                    {header.name} {getMatchTypePrefix(header.type)} {header.value}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* 查询参数匹配 */}
                          {route.match?.queryParams && route.match.queryParams.length > 0 && (
                            <div>
                              <div className="text-xs text-gray-500 mb-1">查询参数匹配:</div>
                              <div className="space-y-1">
                                {route.match.queryParams.map((param: any, paramIndex: number) => (
                                  <div key={paramIndex} className="text-sm font-mono">
                                    {param.name} {getMatchTypePrefix(param.type)} {param.value}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      </Collapse.Panel>
                    ))}
                  </Collapse>
                </div>
              </div>
            )}
          </div>
        </Card>
      )
    }

    // REST API类型：需要linkedService才显示
    if (!linkedService) {
      return null
    }

    return (
      <Card title="配置详情">

        {isOpenApi && apiProduct.apiConfig && apiProduct.apiConfig.spec && (
          <div>
            <h4 className="text-base font-medium mb-4">REST API接口文档</h4>
            <SwaggerUIWrapper apiSpec={apiProduct.apiConfig.spec} />
          </div>
        )}
      </Card>
    )
  }

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">API关联</h1>
        <p className="text-gray-600">管理Product关联的API</p>
      </div>

      {renderLinkInfo()}
      {renderApiConfig()}

      <Modal
        title={linkedService ? '重新关联API' : '关联新API'}
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
              <Select.Option value="NACOS" disabled={apiProduct.type === 'REST_API' || apiProduct.type === 'MODEL_API'}>Nacos</Select.Option>
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
                  (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                }
                onChange={handleGatewayChange}
                optionLabelProp="label"
              >
                {gateways.filter(gateway => {
                  // 如果是Agent API类型，只显示AI网关（APIG_AI）
                  if (apiProduct.type === 'AGENT_API') {
                    return gateway.gatewayType === 'APIG_AI';
                  }
                  // 如果是Model API类型，只显示AI网关（APIG_AI）和Higress网关
                  if (apiProduct.type === 'MODEL_API') {
                    return gateway.gatewayType === 'APIG_AI' || gateway.gatewayType === 'HIGRESS';
                  }
                  return true;
                }).map(gateway => (
                  <Select.Option
                    key={gateway.gatewayId}
                    value={gateway.gatewayId}
                    label={gateway.gatewayName}
                  >
                    <div>
                      <div className="font-medium">{gateway.gatewayName}</div>
                      <div className="text-sm text-gray-500">
                        {gateway.gatewayId} - {getGatewayTypeLabel(gateway.gatewayType as any)}
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
                  (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
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
              label={apiProduct.type === 'REST_API' ? '选择REST API' :
                apiProduct.type === 'AGENT_API' ? '选择Agent API' :
                  apiProduct.type === 'MODEL_API' ? '选择Model API' : '选择MCP Server'}
              rules={[{
                required: true, message: apiProduct.type === 'REST_API' ? '请选择REST API' :
                  apiProduct.type === 'AGENT_API' ? '请选择Agent API' :
                    apiProduct.type === 'MODEL_API' ? '请选择Model API' : '请选择MCP Server'
              }]}
            >
              <Select
                placeholder={apiProduct.type === 'REST_API' ? '请选择REST API' :
                  apiProduct.type === 'AGENT_API' ? '请选择Agent API' :
                    apiProduct.type === 'MODEL_API' ? '请选择Model API' : '请选择MCP Server'}
                loading={apiLoading}
                showSearch
                filterOption={(input, option) =>
                  (option?.value as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                }
                optionLabelProp="label"
              >
                {apiList.map((api: any) => {
                  let key, value, displayName;
                  if (apiProduct.type === 'REST_API') {
                    key = api.apiId;
                    value = api.apiId;
                    displayName = api.apiName;
                  } else if (apiProduct.type === 'AGENT_API') {
                    // Gateway Agent: 使用 agentApiId/agentApiName
                    // Nacos Agent: 使用 agentName
                    if ('agentName' in api) {
                      // Nacos Agent
                      key = api.agentName;
                      value = api.agentName;
                      displayName = api.agentName;
                    } else {
                      // Gateway Agent
                      key = api.agentApiId || api.agentApiName;
                      value = api.agentApiId || api.agentApiName;
                      displayName = api.agentApiName;
                    }
                  } else if (apiProduct.type === 'MODEL_API') {
                    if (api.fromGatewayType === 'HIGRESS') {
                      // Higress: 只有 modelRouteName 字段
                      key = api.modelRouteName;
                      value = api.modelRouteName;
                      displayName = api.modelRouteName;
                    } else {
                      // AI Gateway (APIG_AI): 有 modelApiId 和 modelApiName
                      key = api.modelApiId || api.modelApiName;
                      value = api.modelApiId || api.modelApiName;
                      displayName = api.modelApiName;
                    }
                  } else {
                    // MCP Server
                    key = api.mcpRouteId || api.mcpServerName || api.name;
                    value = api.mcpRouteId || api.mcpServerName || api.name;
                    displayName = api.mcpServerName || api.name;
                  }

                  return (
                    <Select.Option
                      key={key}
                      value={value}
                      label={displayName}
                    >
                      <div>
                        <div className="font-medium">{displayName}</div>
                        <div className="text-sm text-gray-500">
                          {api.type} - {api.description || key}
                        </div>
                      </div>
                    </Select.Option>
                  );
                })}
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
} 