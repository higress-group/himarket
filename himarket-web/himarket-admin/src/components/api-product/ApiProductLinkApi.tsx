import { Card, Button, Modal, Form, Select, message, Collapse, Tabs, Row, Col, Tag, Input, Spin, Space, Radio } from 'antd'
import 'highlight.js/styles/github.css'
import 'github-markdown-css/github-markdown-light.css'
import { PlusOutlined, DeleteOutlined, ExclamationCircleOutlined, CopyOutlined, CloudUploadOutlined, SettingOutlined, SyncOutlined, EditOutlined } from '@ant-design/icons'
import { useState, useEffect } from 'react'
import type { ApiProduct, LinkedService, RestAPIItem, NacosMCPItem, APIGAIMCPItem, AIGatewayAgentItem, AIGatewayModelItem, ApiItem, AdpAIGatewayModelItem, ApsaraGatewayModelItem } from '@/types/api-product'
import type { Gateway, NacosInstance } from '@/types/gateway'
import { apiProductApi, gatewayApi, nacosApi, mcpServerApi, sandboxApi } from '@/lib/api'
import { getGatewayTypeLabel } from '@/lib/constant'
import { copyToClipboard, formatDomainWithPort, formatDateTime } from '@/lib/utils'
import * as yaml from 'js-yaml'
import { SwaggerUIWrapper } from './SwaggerUIWrapper'
import { McpCustomConfigModal } from './McpCustomConfigModal'
import ToolsConfigEditorModal from '../mcp/ToolsConfigEditorModal'

interface ApiProductLinkApiProps {
  apiProduct: ApiProduct
  linkedService: LinkedService | null
  onLinkedServiceUpdate: (linkedService: LinkedService | null) => void
  handleRefresh: () => void
}

function ApiKeyDisplay({ apiKey }: { apiKey: string }) {
  const [visible, setVisible] = useState(false)
  const masked = apiKey.length > 7 ? apiKey.substring(0, 3) + '****' + apiKey.substring(apiKey.length - 4) : '****'
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="font-mono text-gray-700">{visible ? apiKey : masked}</span>
      <Button type="link" size="small" className="p-0 text-[11px]" onClick={() => setVisible(!visible)}>
        {visible ? '隐藏' : '查看'}
      </Button>
      <Button type="link" size="small" className="p-0 text-[11px]" onClick={() => {
        copyToClipboard(apiKey)
        message.success('已复制 API Key')
      }}>复制</Button>
    </span>
  )
}

function AuthCredentialPanel({ secretName, apiKey }: { secretName?: string; apiKey?: string }) {
  const [expanded, setExpanded] = useState(false)
  if (!expanded) {
    return (
      <div className="mt-2">
        <Button type="link" size="small" className="p-0 text-xs text-green-600" onClick={() => setExpanded(true)}>查看鉴权凭证</Button>
      </div>
    )
  }
  return (
    <div className="mt-2 rounded-lg border border-green-100 bg-green-50/50 p-3 space-y-1.5 text-xs">
      <div className="flex items-center justify-between mb-1">
        <span className="text-gray-500 text-[11px]">鉴权凭证</span>
        <Button type="link" size="small" className="p-0 text-[11px]" onClick={() => setExpanded(false)}>收起</Button>
      </div>
      {secretName && (
        <div className="flex items-center gap-2">
          <span className="text-gray-500 shrink-0 w-20">Secret</span>
          <span className="font-mono text-gray-700 truncate" title={secretName}>{secretName}</span>
          <Button type="link" size="small" className="p-0 text-[11px] shrink-0" onClick={() => {
            copyToClipboard(secretName)
            message.success('已复制 Secret 名称')
          }}>复制</Button>
        </div>
      )}
      {apiKey && (
        <div className="flex items-center gap-2">
          <span className="text-gray-500 shrink-0 w-20">API Key</span>
          <ApiKeyDisplay apiKey={apiKey} />
        </div>
      )}
    </div>
  )
}

export function ApiProductLinkApi({ apiProduct, linkedService, onLinkedServiceUpdate, handleRefresh }: ApiProductLinkApiProps) {
  // 移除了内部的 linkedService 状态，现在从 props 接收
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [isCustomConfigModalVisible, setIsCustomConfigModalVisible] = useState(false)
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
  const [hotSseJson, setHotSseJson] = useState('')
  const [hotHttpJson, setHotHttpJson] = useState('')
  const [selectedDomainIndex, setSelectedDomainIndex] = useState<number>(0)
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState<number>(0)
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState<number>(0)
  const [mcpMetaList, setMcpMetaList] = useState<any[]>([])
  const [fetchingTools, setFetchingTools] = useState(false)
  const [toolsEditorOpen, setToolsEditorOpen] = useState(false)
  const [mcpToolsTab, setMcpToolsTab] = useState('tools')
  const [deployModalMcpServerId, setDeployModalMcpServerId] = useState<string | null>(null)
  const [deploying, setDeploying] = useState(false)
  const [deploySandboxList, setDeploySandboxList] = useState<any[]>([])
  const [deploySandboxLoading, setDeploySandboxLoading] = useState(false)

  useEffect(() => {
    fetchGateways()
    fetchNacosInstances()
    if (apiProduct.type === 'MCP_SERVER') {
      fetchMcpMeta()
    }
  }, [])

  // 解析MCP tools配置：优先从冷数据 mcpMetaList[0].toolsConfig 读取，fallback 到 apiProduct.mcpConfig.tools
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER') {
      setParsedTools([])
      return
    }

    // 优先使用冷数据 meta.toolsConfig（JSON 格式，refreshTools 直接更新这里）
    const metaToolsConfig = mcpMetaList[0]?.toolsConfig
    if (metaToolsConfig) {
      try {
        // toolsConfig 可能是 JSON 字符串，也可能已经被反序列化为对象/数组
        let toolsArr = metaToolsConfig
        if (typeof toolsArr === 'string') {
          toolsArr = JSON.parse(toolsArr)
        }
        if (Array.isArray(toolsArr) && toolsArr.length > 0) {
          // toolsConfig 存的是 McpSchema.Tool[]，字段为 name/description/inputSchema
          const mapped = toolsArr.map((t: any) => ({
            name: t.name || '',
            description: t.description || '',
            args: t.inputSchema?.properties
              ? Object.entries(t.inputSchema.properties).map(([key, val]: [string, any]) => ({
                  name: key,
                  description: val.description || '',
                  type: val.type || 'string',
                  required: Array.isArray(t.inputSchema.required) && t.inputSchema.required.includes(key),
                  position: 'query',
                }))
              : undefined,
          }))
          setParsedTools(mapped)
          return
        }
      } catch {
        // JSON 解析失败，尝试 YAML 解析（网关导入的 tools 可能是 YAML 格式）
        try {
          const parsedConfig = parseYamlConfig(typeof metaToolsConfig === 'string' ? metaToolsConfig : '')
          if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
            setParsedTools(parsedConfig.tools)
            return
          }
        } catch {
          // YAML 也解析失败，fallback
        }
      }
    }

    // fallback: 从 apiProduct.mcpConfig.tools（YAML 格式，网关/Nacos 导入时写入）
    if (apiProduct.mcpConfig?.tools) {
      const parsedConfig = parseYamlConfig(apiProduct.mcpConfig.tools)
      if (parsedConfig && parsedConfig.tools && Array.isArray(parsedConfig.tools)) {
        setParsedTools(parsedConfig.tools)
      } else {
        setParsedTools([])
      }
    } else {
      setParsedTools([])
    }
  }, [apiProduct, mcpMetaList])

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
          // 检查是否是 AdpAIGatewayModelItem 类型（有 modelApiName 属性）
          if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
            mcpServerName = linkedService.adpAIGatewayRefConfig.modelApiName || apiProduct.name
          } else {
            // APIGAIMCPItem 类型
            mcpServerName = linkedService.adpAIGatewayRefConfig.mcpServerName || apiProduct.name
          }
        } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
          if ('modelApiName' in linkedService.apsaraGatewayRefConfig) {
            mcpServerName = linkedService.apsaraGatewayRefConfig.modelApiName || apiProduct.name
          } else {
            mcpServerName = linkedService.apsaraGatewayRefConfig.mcpServerName || apiProduct.name
          }
        } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
          mcpServerName = linkedService.nacosRefConfig.mcpServerName || apiProduct.name
        }
      }

      generateConnectionConfig(
        apiProduct.mcpConfig.mcpServerConfig.domains,
        apiProduct.mcpConfig.mcpServerConfig.path,
        mcpServerName,
        apiProduct.mcpConfig.mcpServerConfig.rawConfig,
        // 优先用冷数据 meta.protocolType（数据库真实值），
        // mcpConfig.meta.protocol 是从 JSON type 字段推断的，不可靠（无 type 时默认 sse）
        mcpMetaList[0]?.protocolType || apiProduct.mcpConfig.meta?.protocol,
        selectedDomainIndex
      )
    }
  }, [apiProduct, linkedService, selectedDomainIndex, mcpMetaList])

  // 根据热数据（endpoint）生成连接配置
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER' || mcpMetaList.length === 0) {
      setHotSseJson('')
      setHotHttpJson('')
      return
    }
    const meta = mcpMetaList[0]
    if (!meta.endpointUrl || meta.endpointStatus !== 'ACTIVE') {
      setHotSseJson('')
      setHotHttpJson('')
      return
    }
    const serverName = meta.mcpName || apiProduct.name
    const protocol = (meta.endpointProtocol || '').toLowerCase()
    const endpointUrl = meta.endpointUrl
    if (protocol === 'sse') {
      setHotSseJson(JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url: endpointUrl } } }, null, 2))
      setHotHttpJson('')
    } else if (protocol === 'streamablehttp' || protocol === 'http') {
      setHotHttpJson(JSON.stringify({ mcpServers: { [serverName]: { type: 'streamable-http', url: endpointUrl } } }, null, 2))
      setHotSseJson('')
    } else {
      // 默认当 SSE 处理
      setHotSseJson(JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url: endpointUrl } } }, null, 2))
      setHotHttpJson('')
    }
  }, [mcpMetaList, apiProduct])

  // 从冷数据 mcpMetaList[0].connectionConfig 生成连接配置（原始配置，始终展示）
  useEffect(() => {
    if (apiProduct.type !== 'MCP_SERVER') return
    if (mcpMetaList.length === 0) return

    const meta = mcpMetaList[0]
    const connCfg = meta.connectionConfig
    if (!connCfg) {
      // 没有 mcpConfig 时才清空（避免覆盖网关 useEffect 的结果）
      if (!apiProduct.mcpConfig) {
        setLocalJson('')
        setSseJson('')
        setHttpJson('')
      }
      return
    }

    const serverName = meta.mcpName || apiProduct.name
    const protocol = (meta.protocolType || '').toUpperCase()

    // 根据协议类型，将 connectionConfig 放到对应的 state
    try {
      const parsed = JSON.parse(connCfg)

      if (protocol === 'STDIO' || protocol === '') {
        setLocalJson(JSON.stringify(parsed, null, 2))
        if (!apiProduct.mcpConfig) { setSseJson(''); setHttpJson('') }
        return
      }

      // SSE / HTTP：尝试提取 URL 并标准化格式
      const servers = parsed?.mcpServers || parsed
      const firstKey = servers ? Object.keys(servers)[0] : null
      const entry = firstKey ? servers[firstKey] : null
      const url = entry?.url

      if (protocol === 'SSE') {
        const json = url
          ? JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url } } }, null, 2)
          : JSON.stringify(parsed, null, 2)
        setSseJson(json)
        if (!apiProduct.mcpConfig) { setLocalJson(''); setHttpJson('') }
      } else if (protocol === 'STREAMABLEHTTP' || protocol === 'HTTP') {
        const json = url
          ? JSON.stringify({ mcpServers: { [serverName]: { type: 'streamable-http', url } } }, null, 2)
          : JSON.stringify(parsed, null, 2)
        setHttpJson(json)
        if (!apiProduct.mcpConfig) { setLocalJson(''); setSseJson('') }
      } else {
        // 未知协议 fallback
        setLocalJson(JSON.stringify(parsed, null, 2))
        if (!apiProduct.mcpConfig) { setSseJson(''); setHttpJson('') }
      }
    } catch {
      setLocalJson(connCfg)
      if (!apiProduct.mcpConfig) { setSseJson(''); setHttpJson('') }
    }
  }, [mcpMetaList, apiProduct])

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
    // 自定义 MCP（rawConfig 存在）：根据 protocolType 判断，而不是一律当 stdio
    if (localConfig) {
      const upperProto = (protocolType || '').toUpperCase()
      if (upperProto === 'STDIO' || upperProto === '') {
        // stdio 或未指定协议：显示原始 JSON 配置
        const localConfigJson = JSON.stringify(localConfig, null, 2);
        setLocalJson(localConfigJson);
        setHttpJson("");
        setSseJson("");
        return;
      }
      // SSE / HTTP 类型的自定义 MCP：尝试从 rawConfig 中提取 URL
      const cfg = typeof localConfig === 'object' && localConfig !== null ? localConfig as Record<string, any> : null
      const servers = cfg?.mcpServers || cfg
      const firstKey = servers ? Object.keys(servers)[0] : null
      const entry = firstKey ? servers[firstKey] : null
      const url = entry?.url
      if (url) {
        if (upperProto === 'SSE') {
          setSseJson(JSON.stringify({ mcpServers: { [serverName]: { type: 'sse', url } } }, null, 2))
          setHttpJson("")
        } else {
          setHttpJson(JSON.stringify({ mcpServers: { [serverName]: { type: 'streamable-http', url } } }, null, 2))
          setSseJson("")
        }
        setLocalJson("")
        return;
      }
      // URL 提取失败，fallback 显示原始 JSON
      setLocalJson(JSON.stringify(localConfig, null, 2));
      setHttpJson("");
      setSseJson("");
      return;
    }

    // 网关导入的 MCP（有 domains + path）
    if (domains && domains.length > 0 && path && domainIndex < domains.length) {
      const domain = domains[domainIndex]
      const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
      const baseUrl = `${domain.protocol}://${formattedDomain}`;
      let fullUrl = `${baseUrl}${path || '/'}`;

      // 标准化协议：兼容 SSE / sse / streamable-http / StreamableHTTP / streamableHttp / http 等写法
      const protoLower = (protocolType || '').toLowerCase()
      const isSSE = protoLower === 'sse'
      const isHTTP = protoLower.includes('http')

      if (isSSE) {
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
      } else if (isHTTP) {
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
        // Model API 支持 APIG_AI 网关、HIGRESS 网关、ADP AI 网关、APSARA 网关
        result = res.data?.content?.filter?.((item: Gateway) => item.gatewayType === 'APIG_AI' || item.gatewayType === 'HIGRESS' || item.gatewayType === 'ADP_AI_GATEWAY' || item.gatewayType === 'APSARA_GATEWAY');
      } else if (apiProduct.type === 'AGENT_SKILL') {
          // Agent Skill 不需要关联网关
          result = [];
      } else {
        // MCP Server 支持 HIGRESS、APIG_AI、ADP AI 网关、APSARA 网关
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

  const fetchMcpMeta = async () => {
    try {
      const res = await mcpServerApi.listMetaByProduct(apiProduct.productId)
      setMcpMetaList(res.data || [])
    } catch {
      setMcpMetaList([])
    }
  }

  const handleRefreshTools = async () => {
    const meta = mcpMetaList[0]
    if (!meta?.mcpServerId) return
    setFetchingTools(true)
    try {
      await mcpServerApi.refreshTools(meta.mcpServerId)
      message.success('工具列表获取成功')
      await fetchMcpMeta()
      await handleRefresh()
    } catch (e: any) {
      message.error(e?.response?.data?.message || '获取工具列表失败')
    } finally {
      setFetchingTools(false)
    }
  }

  // 打开部署沙箱弹窗时加载沙箱列表
  useEffect(() => {
    if (!deployModalMcpServerId) return
    setDeploySandboxLoading(true)
    sandboxApi.getActiveSandboxes().then((res: any) => {
      const list = res?.data || []
      setDeploySandboxList(Array.isArray(list) ? list : [])
    }).catch(() => setDeploySandboxList([])).finally(() => setDeploySandboxLoading(false))
    // 重置 namespace 和参数值
    setDeployNamespaceList([])
    setDeployNamespaceLoading(false)
    setDeployParamValues({})
  }, [deployModalMcpServerId])

  const [deployForm] = Form.useForm()
  const deploySandboxIdValue = Form.useWatch('sandboxId', deployForm)
  const [deployNamespaceList, setDeployNamespaceList] = useState<string[]>([])
  const [deployNamespaceLoading, setDeployNamespaceLoading] = useState(false)
  const [deployParamValues, setDeployParamValues] = useState<Record<string, string>>({})
  const [deployResourcePreset, setDeployResourcePreset] = useState('small')

  const handleDeploySandboxChange = async (sandboxId: string) => {
    setDeployNamespaceList([])
    deployForm.setFieldsValue({ namespace: undefined })
    setDeployNamespaceLoading(true)
    try {
      const res: any = await sandboxApi.listNamespaces(sandboxId)
      const list = res?.data || res || []
      setDeployNamespaceList(Array.isArray(list) ? list : [])
    } catch {
      message.error('获取 Namespace 列表失败')
      setDeployNamespaceList([])
    } finally {
      setDeployNamespaceLoading(false)
    }
  }

  // 获取当前部署弹窗对应 meta 的额外参数定义
  const getDeployExtraParamDefs = (): any[] => {
    const meta = mcpMetaList.find((m: any) => m.mcpServerId === deployModalMcpServerId)
    if (!meta?.extraParams) return []
    try {
      const parsed = typeof meta.extraParams === 'string' ? JSON.parse(meta.extraParams) : meta.extraParams
      return Array.isArray(parsed) ? parsed : []
    } catch { return [] }
  }

  const handleDeploySandbox = async () => {
    if (!deployModalMcpServerId) return
    try {
      const values = await deployForm.validateFields()
      // 校验必填的额外参数
      const paramDefs = getDeployExtraParamDefs()
      const missingParams = paramDefs
        .filter((p: any) => p.required && !deployParamValues[p.name]?.trim())
        .map((p: any) => p.name)
      if (missingParams.length > 0) {
        message.error(`请填写必填参数: ${missingParams.join(', ')}`)
        return
      }
      setDeploying(true)
      const paramValuesJson = paramDefs.length > 0 && Object.keys(deployParamValues).length > 0
        ? JSON.stringify(deployParamValues)
        : undefined
      const resourceSpec = (values.cpuRequest || values.cpuLimit || values.memoryRequest || values.memoryLimit || values.ephemeralStorage)
        ? JSON.stringify({
            cpuRequest: values.cpuRequest || undefined,
            cpuLimit: values.cpuLimit || undefined,
            memoryRequest: values.memoryRequest || undefined,
            memoryLimit: values.memoryLimit || undefined,
            ephemeralStorage: values.ephemeralStorage || undefined,
          })
        : undefined
      await mcpServerApi.deploySandbox(deployModalMcpServerId, {
        sandboxId: values.sandboxId,
        transportType: values.transportType || 'sse',
        authType: values.authType || 'none',
        namespace: values.namespace,
        paramValues: paramValuesJson,
        resourceSpec,
      })
      message.success('沙箱部署已提交，等待部署完成...')
      const targetMcpServerId = deployModalMcpServerId
      setDeployModalMcpServerId(null)
      deployForm.resetFields()
      setDeployParamValues({})
      setDeployResourcePreset('small')
      // 沙箱部署是异步的（事务提交后由 listener 执行 K8s CRD 部署），
      // 需要轮询等待 endpoint 状态变为 ACTIVE
      const maxAttempts = 15
      let deployed = false
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise(r => setTimeout(r, 2000))
        const metaRes = await mcpServerApi.listMetaByProduct(apiProduct.productId)
        const metaList = metaRes?.data || []
        const activeMeta = (Array.isArray(metaList) ? metaList : []).find((m: any) => m.mcpServerId === targetMcpServerId)
        if (activeMeta?.endpointStatus === 'ACTIVE' && activeMeta?.endpointUrl) {
          message.success('沙箱部署完成')
          deployed = true
          break
        }
      }
      if (!deployed) {
        message.warning('沙箱部署超时，请稍后刷新页面查看状态')
      }
      await fetchMcpMeta()
      await handleRefresh()
    } catch (e: any) {
      if (e?.errorFields) return // form validation error
      message.error(e?.response?.data?.message || '沙箱部署失败')
    } finally {
      setDeploying(false)
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
        if (apiProduct.type === 'MODEL_API') {
          // ADP_AI_GATEWAY类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'ADP_AI_GATEWAY' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
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
        }
      } else if (gateway.gatewayType === 'APSARA_GATEWAY') {
        if (apiProduct.type === 'AGENT_API') {
          // APSARA_GATEWAY类型 + Agent API产品：获取Agent API列表
          const res = await gatewayApi.getGatewayAgentApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Agent API
          })
          const agentApis = (res.data?.content || []).map((api: any) => ({
            agentApiId: api.agentApiId,
            agentApiName: api.agentApiName,
            fromGatewayType: 'APSARA_GATEWAY' as const,
            type: 'Agent API'
          }))
          setApiList(agentApis)
        } else if (apiProduct.type === 'MODEL_API') {
          // APSARA_GATEWAY类型 + Model API产品：获取Model API列表
          const res = await gatewayApi.getGatewayModelApis(gatewayId, {
            page: 1,
            size: 500 // 获取所有Model API
          })
          const modelApis = (res.data?.content || []).map((api: any) => ({
            modelApiId: api.modelApiId,
            modelApiName: api.modelApiName,
            fromGatewayType: 'APSARA_GATEWAY' as const,
            type: 'Model API'
          }))
          setApiList(modelApis)
        } else {
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


  const handleModalOk = () => {
    form.validateFields().then(async (values) => {
      const { sourceType, gatewayId, nacosId, apiId } = values
      const selectedApi = apiList.find((item: any) => {
        if ('apiId' in item) {
          if ('mcpRouteId' in item) {
            return item.mcpRouteId === apiId
          } else {
            return item.apiId === apiId
          }
        } else if ('mcpServerName' in item) {
          return item.mcpServerName === apiId
        } else if ('agentApiId' in item || 'agentApiName' in item) {
          return item.agentApiId === apiId || item.agentApiName === apiId
        } else if ('modelApiId' in item || 'modelApiName' in item) {
          return item.modelApiId === apiId || item.modelApiName === apiId
        } else if ('modelRouteName' in item && item.fromGatewayType === 'HIGRESS') {
          return item.modelRouteName === apiId
        } else if ('agentName' in item) {
          return item.agentName === apiId
        }
        return false
      })

      // MCP 产品：统一走 saveMeta 接口
      if (apiProduct.type === 'MCP_SERVER' && selectedApi) {
        try {
          const mcpServerName = (selectedApi as any).mcpServerName || apiId
          await mcpServerApi.saveMeta({
            productId: apiProduct.productId,
            mcpName: mcpServerName,
            displayName: apiProduct.name,
            protocolType: 'sse',
            connectionConfig: '{}',
            origin: sourceType,
            gatewayId: sourceType === 'GATEWAY' ? gatewayId : undefined,
            nacosId: sourceType === 'NACOS' ? nacosId : undefined,
            refConfig: JSON.stringify(
              sourceType === 'NACOS'
                ? { ...selectedApi, namespaceId: selectedNamespace || 'public' }
                : selectedApi
            ),
            visibility: 'PUBLIC',
            publishStatus: 'DRAFT',
          })
          message.success('MCP 配置导入成功')
          setIsModalVisible(false)

          // 刷新关联信息
          try {
            const res = await apiProductApi.getApiProductRef(apiProduct.productId)
            onLinkedServiceUpdate(res.data || null)
          } catch {
            onLinkedServiceUpdate(null)
          }

          await handleRefresh()
          await fetchMcpMeta()

          form.resetFields()
          setSelectedGateway(null)
          setSelectedNacos(null)
          setApiList([])
          setSourceType('GATEWAY')
        } catch {
          message.error('MCP 配置导入失败')
        }
        return
      }

      // 非 MCP 产品：走原有 createApiProductRef 接口
      const newService: LinkedService = {
        gatewayId: sourceType === 'GATEWAY' ? gatewayId : undefined,
        nacosId: sourceType === 'NACOS' ? nacosId : undefined,
        sourceType,
        productId: apiProduct.productId,
        apigRefConfig: selectedApi && ('apiId' in selectedApi || 'agentApiId' in selectedApi || 'agentApiName' in selectedApi || 'modelApiId' in selectedApi || 'modelApiName' in selectedApi) && (!('fromGatewayType' in selectedApi) || (selectedApi.fromGatewayType !== 'HIGRESS' && selectedApi.fromGatewayType !== 'ADP_AI_GATEWAY' && selectedApi.fromGatewayType !== 'APSARA_GATEWAY')) ? selectedApi as RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem : undefined,
        higressRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'HIGRESS' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelRouteName: (selectedApi as any).modelRouteName, fromGatewayType: 'HIGRESS' as const }
            : { mcpServerName: (selectedApi as any).mcpServerName, fromGatewayType: 'HIGRESS' as const }
        ) : undefined,
        nacosRefConfig: sourceType === 'NACOS' && selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'NACOS' ? {
          ...selectedApi,
          namespaceId: selectedNamespace || 'public'
        } : undefined,
        adpAIGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'ADP_AI_GATEWAY' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelApiId: (selectedApi as any).modelApiId, modelApiName: (selectedApi as any).modelApiName, fromGatewayType: 'ADP_AI_GATEWAY' as const } as AdpAIGatewayModelItem
            : selectedApi as APIGAIMCPItem
        ) : undefined,
        apsaraGatewayRefConfig: selectedApi && 'fromGatewayType' in selectedApi && selectedApi.fromGatewayType === 'APSARA_GATEWAY' ? (
          apiProduct.type === 'MODEL_API'
            ? { modelApiId: (selectedApi as any).modelApiId, modelApiName: (selectedApi as any).modelApiName, fromGatewayType: 'APSARA_GATEWAY' as const } as ApsaraGatewayModelItem
            : selectedApi as APIGAIMCPItem
        ) : undefined,
      }
      apiProductApi.createApiProductRef(apiProduct.productId, newService).then(async () => {
        message.success('关联成功')
        setIsModalVisible(false)

        try {
          const res = await apiProductApi.getApiProductRef(apiProduct.productId)
          onLinkedServiceUpdate(res.data || null)
        } catch (error) {
          console.error('获取关联API失败:', error)
          onLinkedServiceUpdate(null)
        }

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
    const isMcp = apiProduct.type === 'MCP_SERVER'

    // MCP 产品：即使没有 linkedService，也可能有 mcpMetaList 需要清除
    if (!isMcp && !linkedService) return

    Modal.confirm({
      title: isMcp ? '确认解除配置' : '确认解除关联',
      content: isMcp ? '确定要解除当前MCP配置吗？这将同时删除关联数据和MCP元信息。' : '确定要解除与当前API的关联吗？',
      icon: <ExclamationCircleOutlined />,
      onOk() {
        const deletePromise = isMcp
          ? mcpServerApi.deleteMetaByProduct(apiProduct.productId)
          : apiProductApi.deleteApiProductRef(apiProduct.productId)

        return deletePromise.then(() => {
          message.success(isMcp ? '解除配置成功' : '解除关联成功')
          onLinkedServiceUpdate(null)
          if (isMcp) {
            setMcpMetaList([])
          }
          handleRefresh()
        }).catch(() => {
          message.error(isMcp ? '解除配置失败' : '解除关联失败')
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
        // 检查是否是 AdpAIGatewayModelItem 类型（有 modelApiName 属性）
        if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
          // 专有云AI网关上的Model API
          apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名'
          sourceInfo = '专有云AI网关'
          gatewayInfo = linkedService.gatewayId || '未知'
        } else {
          // 专有云AI网关上的MCP Server
          apiName = linkedService.adpAIGatewayRefConfig.mcpServerName || '未命名'
          sourceInfo = '专有云AI网关'
          gatewayInfo = linkedService.gatewayId || '未知'
        }
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        // 飞天企业版AI网关上的MCP Server
        if ('mcpServerName' in linkedService.apsaraGatewayRefConfig) {
          apiName = linkedService.apsaraGatewayRefConfig.mcpServerName || '未命名'
        } else {
          apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名'
        }
        sourceInfo = '飞天企业版AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'NACOS' && linkedService.nacosRefConfig && 'mcpServerName' in linkedService.nacosRefConfig) {
        // Nacos上的MCP Server
        apiName = linkedService.nacosRefConfig.mcpServerName || '未命名'
        sourceInfo = 'Nacos服务发现'
        gatewayInfo = linkedService.nacosId || '未知'
      } else if (linkedService.sourceType === 'CUSTOM') {
        // 自定义配置的MCP Server
        apiName = apiProduct.name || '未命名'
        sourceInfo = '自定义配置'
        gatewayInfo = '-'
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
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig && 'modelApiName' in linkedService.adpAIGatewayRefConfig) {
        // 专有云AI网关上的Model API
        apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名'
        sourceInfo = '专有云AI网关'
        gatewayInfo = linkedService.gatewayId || '未知'
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig && 'modelApiName' in linkedService.apsaraGatewayRefConfig) {
        // 飞天企业版AI网关上的Model API
        apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名'
        sourceInfo = '飞天企业版AI网关'
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
    const isMcp = apiProduct.type === 'MCP_SERVER'

    // MCP 产品：优先根据 mcpMetaList 判断是否已配置
    if (isMcp && mcpMetaList.length > 0) {
      return (
        <Card
          className="mb-6"
          title="MCP 配置信息"
          extra={
            <Space>
              {mcpMetaList[0]?.sandboxRequired && !mcpMetaList[0]?.endpointUrl && (
                <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setDeployModalMcpServerId(mcpMetaList[0].mcpServerId)}>
                  部署到沙箱
                </Button>
              )}
              {mcpMetaList[0]?.sandboxRequired && mcpMetaList[0]?.endpointUrl && (
                <Button danger onClick={() => {
                  Modal.confirm({
                    title: '确认取消托管',
                    icon: <ExclamationCircleOutlined />,
                    content: '取消托管将删除沙箱中的部署实例和连接地址，已订阅的用户将无法继续使用。确定要取消吗？',
                    okText: '确认取消',
                    cancelText: '返回',
                    okButtonProps: { danger: true },
                    onOk: async () => {
                      try {
                        await mcpServerApi.undeploySandbox(mcpMetaList[0].mcpServerId)
                        message.success('已取消沙箱托管')
                        fetchMcpMeta()
                      } catch {
                        // 错误已由拦截器处理
                      }
                    },
                  })
                }}>
                  取消托管
                </Button>
              )}
              <Button
                icon={<SettingOutlined />}
                disabled={!!mcpMetaList[0]?.endpointUrl}
                title={mcpMetaList[0]?.endpointUrl ? '请先取消沙箱部署后再修改配置' : undefined}
                onClick={() => {
                  if (mcpMetaList[0]?.endpointUrl) {
                    message.warning('请先取消沙箱部署后再修改配置')
                    return
                  }
                  setIsCustomConfigModalVisible(true)
                }}
              >
                修改配置
              </Button>
              <Button type="primary" danger icon={<DeleteOutlined />} onClick={handleDelete}>
                解除配置
              </Button>
            </Space>
          }
        >
          {mcpMetaList.map((meta: any) => (
            <div key={meta.mcpServerId} className="space-y-1">
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                <span className="text-xs text-gray-600">MCP 名称:</span>
                <span className="col-span-2 text-xs text-gray-900 font-mono">{meta.mcpName}</span>
                <span className="text-xs text-gray-600">展示名称:</span>
                <span className="col-span-2 text-xs text-gray-900">{meta.displayName}</span>
              </div>
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                <span className="text-xs text-gray-600">协议类型:</span>
                <span className="col-span-2 text-xs text-gray-900">{meta.protocolType?.toUpperCase()}</span>
                <span className="text-xs text-gray-600">来源:</span>
                <span className="col-span-2 text-xs text-gray-900">{meta.origin === 'GATEWAY' ? '网关导入' : meta.origin === 'NACOS' ? 'Nacos导入' : meta.origin === 'ADMIN' ? '管理员手动创建' : meta.origin === 'AGENTRUNTIME' ? 'AgentRuntime导入' : meta.origin === 'OPEN_API' ? 'Open API 导入' : '自定义配置'}</span>
              </div>
              {meta.repoUrl && (
                <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                  <span className="text-xs text-gray-600">仓库地址:</span>
                  <a href={meta.repoUrl} target="_blank" rel="noopener noreferrer" className="col-span-5 text-xs text-blue-500 hover:underline truncate">
                    {meta.repoUrl}
                  </a>
                </div>
              )}
              {meta.description && (
                <div className="grid grid-cols-6 gap-8 pt-2 pb-2">
                  <span className="text-xs text-gray-600">描述:</span>
                  <span className="col-span-5 text-xs text-gray-700 leading-relaxed">{meta.description}</span>
                </div>
              )}
              {meta.sandboxRequired && (
                <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                  <span className="text-xs text-gray-600">沙箱托管:</span>
                  <div className="col-span-5 flex items-center gap-2">
                    {meta.endpointUrl ? (
                      <>
                        <Tag color={meta.endpointStatus === 'ACTIVE' ? 'green' : 'default'} className="m-0">
                          {meta.endpointStatus === 'ACTIVE' ? '运行中' : meta.endpointStatus || '未知'}
                        </Tag>
                        <span className="text-xs text-gray-700 font-mono break-all">{meta.endpointUrl}</span>
                        <CopyOutlined
                          className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors flex-shrink-0"
                          style={{ fontSize: '12px' }}
                          onClick={async () => {
                            try {
                              await copyToClipboard(meta.endpointUrl);
                              message.success('连接地址已复制');
                            } catch {
                              message.error('复制失败');
                            }
                          }}
                        />
                      </>
                    ) : meta.endpointStatus === 'INACTIVE' ? (
                      <>
                        <Tag color="red" className="m-0">部署失败</Tag>
                        <Button
                          type="link"
                          size="small"
                          className="p-0 text-xs"
                          onClick={() => setDeployModalMcpServerId(meta.mcpServerId)}
                        >
                          重新部署
                        </Button>
                      </>
                    ) : (
                      <Tag color="default" className="m-0">未部署</Tag>
                    )}
                  </div>
                </div>
              )}
              {meta.tags && (() => {
                try {
                  const tags = JSON.parse(meta.tags)
                  return Array.isArray(tags) && tags.length > 0 ? (
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                      <span className="text-xs text-gray-600">标签:</span>
                      <div className="col-span-5 flex flex-wrap gap-1">
                        {tags.map((tag: string) => (
                          <Tag key={tag} color="blue" className="m-0">{tag}</Tag>
                        ))}
                      </div>
                    </div>
                  ) : null
                } catch { return null }
              })()}
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                {meta.createdBy && (
                  <>
                    <span className="text-xs text-gray-600">创建人:</span>
                    <span className="col-span-2 text-xs text-gray-900">{meta.createdBy}</span>
                  </>
                )}
                {meta.createAt && (
                  <>
                    <span className="text-xs text-gray-600">创建时间:</span>
                    <span className="col-span-2 text-xs text-gray-700">{formatDateTime(meta.createAt)}</span>
                  </>
                )}
              </div>
            </div>
          ))}
        </Card>
      )
    }

    // 没有关联任何API
    if (!linkedService || !serviceInfo) {
      return (
        <Card className="mb-6">
          <div className="text-center py-8">
            <div className="text-gray-500 mb-4">{isMcp ? '暂未配置MCP Server' : '暂未关联任何API'}</div>
            {isMcp ? (
              <div className="max-w-2xl mx-auto">
                <div className="grid grid-cols-2 gap-4 mt-2">
                  <div
                    onClick={() => setIsModalVisible(true)}
                    className="group cursor-pointer rounded-xl border-2 border-dashed border-gray-200 hover:border-blue-400 p-5 transition-all duration-200 hover:bg-blue-50/50"
                  >
                    <div className="w-10 h-10 rounded-lg bg-blue-50 group-hover:bg-blue-100 flex items-center justify-center mx-auto mb-3 transition-colors">
                      <CloudUploadOutlined className="text-blue-500 text-lg" />
                    </div>
                    <div className="font-medium text-sm text-gray-800 mb-1">从网关/Nacos导入</div>
                    <div className="text-xs text-gray-400 leading-relaxed">关联已有网关或 Nacos 中注册的 MCP Server</div>
                  </div>
                  <div
                    onClick={() => setIsCustomConfigModalVisible(true)}
                    className="group cursor-pointer rounded-xl border-2 border-dashed border-gray-200 hover:border-purple-400 p-5 transition-all duration-200 hover:bg-purple-50/50"
                  >
                    <div className="w-10 h-10 rounded-lg bg-purple-50 group-hover:bg-purple-100 flex items-center justify-center mx-auto mb-3 transition-colors">
                      <SettingOutlined className="text-purple-500 text-lg" />
                    </div>
                    <div className="font-medium text-sm text-gray-800 mb-1">自定义数据</div>
                    <div className="text-xs text-gray-400 leading-relaxed">手动配置 MCP Server 的连接信息和工具定义</div>
                  </div>
                </div>
              </div>
            ) : (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalVisible(true)}>
                关联API
              </Button>
            )}
          </div>
        </Card>
      )
    }

    return (
      <Card
        className="mb-6"
        title="关联详情"
        extra={
          <Button type="primary" danger icon={<DeleteOutlined />} onClick={handleDelete}>
            解除关联
          </Button>
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
            {linkedService?.sourceType !== 'CUSTOM' && (
              <>
                <span className="text-xs text-gray-600">
                  {linkedService?.sourceType === 'NACOS' ? 'Nacos ID:' : '网关ID:'}
                </span>
                <span className="col-span-2 text-xs text-gray-700">{serviceInfo.gatewayInfo}</span>
              </>
            )}
          </div>

          {/* CUSTOM 类型：展示 MCP Meta 详情 */}
          {linkedService?.sourceType === 'CUSTOM' && mcpMetaList.length > 0 && (
            <>
              {mcpMetaList.map((meta: any) => (
                <div key={meta.mcpServerId}>
                  <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                    <span className="text-xs text-gray-600">MCP 名称:</span>
                    <span className="col-span-2 text-xs text-gray-900 font-mono">{meta.mcpName}</span>
                    <span className="text-xs text-gray-600">展示名称:</span>
                    <span className="col-span-2 text-xs text-gray-900">{meta.displayName}</span>
                  </div>
                  <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                    <span className="text-xs text-gray-600">协议类型:</span>
                    <span className="col-span-2 text-xs text-gray-900">{meta.protocolType?.toUpperCase()}</span>
                  </div>
                  {meta.repoUrl && (
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                      <span className="text-xs text-gray-600">仓库地址:</span>
                      <a href={meta.repoUrl} target="_blank" rel="noopener noreferrer" className="col-span-5 text-xs text-blue-500 hover:underline truncate">
                        {meta.repoUrl}
                      </a>
                    </div>
                  )}
                </div>
              ))}
            </>
          )}
        </div>
      </Card>
    )
  }

  const renderApiConfig = () => {
    const isMcp = apiProduct.type === 'MCP_SERVER'
    const isOpenApi = apiProduct.type === 'REST_API'
    const isAgent = apiProduct.type === 'AGENT_API'
    const isModel = apiProduct.type === 'MODEL_API'

    // MCP Server类型：展示工具列表和连接点配置
    if (isMcp && (apiProduct.mcpConfig || mcpMetaList.length > 0)) {
      return (
        <Card title="配置详情">
          <Row gutter={24}>
            {/* 左侧：工具列表 */}
            <Col span={15}>
              <Card>
                <Tabs
                  activeKey={mcpToolsTab}
                  onChange={setMcpToolsTab}
                  tabBarExtraContent={
                    mcpToolsTab === 'tools' ? (
                      <Space>
                        <Button
                          type="text"
                          size="small"
                          icon={<EditOutlined />}
                          onClick={() => setToolsEditorOpen(true)}
                          style={{ fontSize: 12, color: '#1677ff' }}
                        >
                          编辑工具
                        </Button>
                        {mcpMetaList[0]?.endpointUrl && (
                          <Button
                            type="text"
                            size="small"
                            icon={<SyncOutlined spin={fetchingTools} />}
                            loading={fetchingTools}
                            onClick={handleRefreshTools}
                            style={{ fontSize: 12, color: '#1677ff' }}
                          >
                            {parsedTools.length === 0 ? '获取工具列表' : '刷新工具'}
                          </Button>
                        )}
                      </Space>
                    ) : null
                  }
                  items={[
                    {
                      key: "tools",
                      label: `工具列表 (${parsedTools.length})`,
                      children: fetchingTools ? (
                        <div className="text-center py-12">
                          <Spin tip="正在获取工具列表，请稍候..." />
                        </div>
                      ) : parsedTools.length > 0 ? (
                        <div className="border border-gray-200 rounded-lg bg-gray-50">
                          {parsedTools.map((tool, idx) => (
                            <div key={idx} className={idx < parsedTools.length - 1 ? "border-b border-gray-200" : ""}>
                              <Collapse
                                ghost
                                expandIconPosition="end"
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
                        <div className="text-center py-12">
                          <div className="text-gray-400 mb-3">暂无工具信息，请先获取工具列表</div>
                          <div className="text-xs text-gray-400">点击右上角「获取工具列表」按钮</div>
                        </div>
                      ),
                    },
                    {
                      key: "details",
                      label: "部署参数",
                      children: (() => {
                        const meta = mcpMetaList[0]
                        let extraParamDefs: any[] = []
                        try {
                          const raw = meta?.extraParams
                          extraParamDefs = raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : []
                          if (!Array.isArray(extraParamDefs)) extraParamDefs = []
                        } catch { extraParamDefs = [] }

                        if (extraParamDefs.length === 0) {
                          return <div className="text-gray-400 text-center py-8">暂无部署参数</div>
                        }

                        return (
                          <div className="overflow-hidden rounded-lg border border-gray-200">
                            <table className="w-full text-sm">
                              <thead>
                                <tr className="bg-gray-50 text-left text-xs text-gray-500">
                                  <th className="px-4 py-2.5 font-medium">参数名</th>
                                  <th className="px-4 py-2.5 font-medium">必填</th>
                                  <th className="px-4 py-2.5 font-medium">描述</th>
                                  <th className="px-4 py-2.5 font-medium">默认值</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-gray-100">
                                {extraParamDefs.map((p: any, i: number) => (
                                  <tr key={i} className="hover:bg-gray-50/50 transition-colors">
                                    <td className="px-4 py-3 align-top">
                                      <div className="flex items-center gap-1.5">
                                        <code className="text-xs font-mono text-gray-800 bg-gray-100 px-1.5 py-0.5 rounded">{p.name}</code>
                                        {p.position && (
                                          <span className="text-[10px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">{p.position}</span>
                                        )}
                                      </div>
                                    </td>
                                    <td className="px-4 py-3 align-top">
                                      {p.required
                                        ? <Tag color="red" style={{ margin: 0 }}>必填</Tag>
                                        : <Tag style={{ margin: 0 }}>可选</Tag>
                                      }
                                    </td>
                                    <td className="px-4 py-3 align-top">
                                      <div className="text-gray-600 text-xs leading-relaxed">{p.description || '-'}</div>
                                      {p.example && (
                                        <div className="mt-1">
                                          <span className="text-[10px] text-gray-400">示例: </span>
                                          <code className="text-[11px] font-mono text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">{p.example}</code>
                                        </div>
                                      )}
                                    </td>
                                    <td className="px-4 py-3 align-top">
                                      {p.default !== undefined
                                        ? <code className="text-xs font-mono text-gray-700 bg-gray-100 px-1.5 py-0.5 rounded">{String(p.default)}</code>
                                        : <span className="text-gray-300">-</span>
                                      }
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        )
                      })(),
                    },
                  ]}
                />
              </Card>
            </Col>

            {/* 右侧：连接点配置 */}
            <Col span={9}>
              <Card>
                <div className="mb-4">
                  <h3 className="text-sm font-semibold mb-3">连接点配置</h3>

                  {/* 域名选择器 */}
                  {apiProduct.mcpConfig?.mcpServerConfig?.domains && apiProduct.mcpConfig.mcpServerConfig.domains.length > 1 && (
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
                    defaultActiveKey={(() => {
                      if (hotSseJson) return "sse-hot"
                      if (hotHttpJson) return "http-hot"
                      if (localJson) return "local"
                      if (sseJson) return "sse-cold"
                      if (httpJson) return "http-cold"
                      return "local"
                    })()}
                    items={(() => {
                      const tabs: { key: string; label: React.ReactNode; children: React.ReactNode }[] = [];
                      const hasHot = !!(hotSseJson || hotHttpJson)
                      const hotHostingType = mcpMetaList[0]?.endpointHostingType || ''
                      const isSandboxHosted = hotHostingType === 'SANDBOX'
                      // 网关/Nacos 导入的 MCP 不展示冷数据"原始"tab（冷数据和热数据同源，无意义）
                      const metaOrigin = (mcpMetaList[0]?.origin || '').toUpperCase()
                      const isRemoteImport = metaOrigin === 'GATEWAY' || metaOrigin === 'NACOS'

                      // 热数据 tag 文案
                      const hotTagLabel = isSandboxHosted ? '沙箱' : hotHostingType === 'NACOS' ? 'Nacos' : hotHostingType === 'GATEWAY' ? '网关' : '直连'
                      const hotTagColor = isSandboxHosted ? 'green' : hotHostingType === 'DIRECT' ? 'cyan' : 'blue'

                      // 渲染沙箱托管配置
                      const renderSandboxConfig = () => {
                        const meta = mcpMetaList[0]
                        if (!meta?.subscribeParams) return null
                        let sp: Record<string, any> = {}
                        try {
                          sp = typeof meta.subscribeParams === 'string' ? JSON.parse(meta.subscribeParams) : meta.subscribeParams
                        } catch { return null }

                        const sandboxId = sp.sandboxId || '-'
                        const namespace = sp.namespace || 'default'
                        const authType = sp.authType || 'none'
                        const extraParams: Record<string, any> = sp.extraParams || {}
                        const extraEntries = Object.entries(extraParams).filter(([, v]) => v !== null && v !== undefined && v !== '')

                        // 从 meta.extraParams 获取参数定义（name/position/required/description）
                        let paramDefs: any[] = []
                        try {
                          paramDefs = meta.extraParams ? (typeof meta.extraParams === 'string' ? JSON.parse(meta.extraParams) : meta.extraParams) : []
                        } catch { /* */ }

                        return (
                          <div className="mt-3 pt-3 border-t border-gray-100">
                            <div className="text-xs text-gray-400 mb-2">托管配置</div>
                            <div className="grid grid-cols-3 gap-x-3 gap-y-2 text-xs">
                              <div>
                                <div className="text-gray-400 mb-0.5">沙箱</div>
                                <div className="font-mono text-gray-700 truncate" title={sp.sandboxName || sandboxId}>{sp.sandboxName || sandboxId}</div>
                              </div>
                              <div>
                                <div className="text-gray-400 mb-0.5">Namespace</div>
                                <div className="font-mono text-gray-700">{namespace}</div>
                              </div>
                              <div>
                                <div className="text-gray-400 mb-0.5">鉴权类型</div>
                                <div className="text-gray-700">{authType === 'apikey' ? <span className="text-green-600">API Key</span> : '无鉴权'}</div>
                              </div>
                            </div>
                            {/* 鉴权信息卡片 - 默认折叠 */}
                            {authType === 'apikey' && (sp.secretName || sp.apiKey) && (
                              <AuthCredentialPanel secretName={sp.secretName} apiKey={sp.apiKey} />
                            )}
                            {extraEntries.length > 0 && (
                              <div className="mt-3 rounded-lg border border-gray-200 overflow-hidden">
                                <div className="px-3 py-1.5 bg-gray-50 border-b border-gray-200">
                                  <span className="text-xs font-medium text-gray-600">额外参数</span>
                                </div>
                                <div className="p-3 space-y-2.5">
                                  {extraEntries.map(([key, val]) => {
                                    const def = Array.isArray(paramDefs) ? paramDefs.find((d: any) => d.name === key) : null
                                    return (
                                      <div key={key}>
                                        <div className="flex items-center gap-1.5 mb-0.5">
                                          <span className="text-xs font-mono text-gray-700">{key}</span>
                                          {def?.required && <span className="text-red-400 text-[10px]">*</span>}
                                          {def?.position && <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">{def.position}</Tag>}
                                        </div>
                                        {def?.description && <div className="text-[10px] text-gray-400 mb-0.5">{def.description}</div>}
                                        <div className="px-2 py-1 bg-gray-50 border border-gray-200 rounded text-xs font-mono text-gray-800 break-all">{String(val)}</div>
                                      </div>
                                    )
                                  })}
                                </div>
                              </div>
                            )}
                          </div>
                        )
                      }

                      // 渲染配置 JSON 块（admin 版本，带复制按钮）
                      const renderAdminConfigBlock = (json: string, extra?: React.ReactNode) => (
                        <div>
                          <div className="relative bg-gray-50 border border-gray-200 rounded-md p-3">
                            <Button size="small" icon={<CopyOutlined />} className="absolute top-2 right-2 z-10" onClick={() => handleCopy(json)} />
                            <div className="text-gray-800 font-mono text-xs overflow-x-auto">
                              <pre className="whitespace-pre">{json}</pre>
                            </div>
                          </div>
                          {extra}
                        </div>
                      )

                      // Stdio 冷数据（自定义导入的本地配置）
                      if (localJson) {
                        tabs.push({
                          key: "local",
                          label: hasHot ? <span>Stdio <Tag color="default" className="ml-1 mr-0" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>原始</Tag></span> : "Stdio",
                          children: renderAdminConfigBlock(localJson),
                        })
                      }

                      // SSE：冷热分开展示（如果都有）
                      if (hotSseJson) {
                        tabs.push({
                          key: "sse-hot",
                          label: <span>SSE <Tag color={hotTagColor} className="ml-1 mr-0" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>{hotTagLabel}</Tag></span>,
                          children: renderAdminConfigBlock(hotSseJson, renderSandboxConfig()),
                        })
                      }
                      if (sseJson && (!isRemoteImport || !hasHot) && (!hotSseJson || sseJson !== hotSseJson)) {
                        tabs.push({
                          key: "sse-cold",
                          label: hasHot ? <span>SSE <Tag color="default" className="ml-1 mr-0" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>原始</Tag></span> : "SSE",
                          children: renderAdminConfigBlock(sseJson),
                        })
                      }

                      // Streamable HTTP：冷热分开展示（如果都有）
                      if (hotHttpJson) {
                        tabs.push({
                          key: "http-hot",
                          label: <span>HTTP <Tag color={hotTagColor} className="ml-1 mr-0" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>{hotTagLabel}</Tag></span>,
                          children: renderAdminConfigBlock(hotHttpJson, renderSandboxConfig()),
                        })
                      }
                      if (httpJson && (!isRemoteImport || !hasHot) && (!hotHttpJson || httpJson !== hotHttpJson)) {
                        tabs.push({
                          key: "http-cold",
                          label: hasHot ? <span>HTTP <Tag color="default" className="ml-1 mr-0" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>原始</Tag></span> : "HTTP",
                          children: renderAdminConfigBlock(httpJson),
                        })
                      }

                      return tabs;
                    })()}
                  />
                </div>
              </Card>

            </Col>
          </Row>
        </Card>
      )
    }

    // Agent API类型：显示协议支持和路由配置或 AgentCard
    if (isAgent && apiProduct.agentConfig?.agentAPIConfig) {
      const agentAPIConfig = apiProduct.agentConfig.agentAPIConfig
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
            {protocols.length > 0 && (
              <div>
                <div className="text-sm text-gray-600">支持协议</div>
                <div className="font-medium">{protocols.join(', ')}</div>
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
                    ghost expandIconPosition="end"
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
    if (isModel && apiProduct.modelConfig?.modelAPIConfig) {
      const modelAPIConfig = apiProduct.modelConfig.modelAPIConfig
      const routes = modelAPIConfig.routes || []
      const protocols = modelAPIConfig.aiProtocols || []

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

      // 获取适用场景中文翻译
      const getModelCategoryText = (category: string) => {
        switch (category) {
          case 'Text':
            return '文本生成'
          case 'Image':
            return '图片生成'
          case 'Video':
            return '视频生成'
          case 'Audio':
            return '语音合成'
          case 'Embedding':
            return '向量化（Embedding）'
          case 'Rerank':
            return '文本排序（Rerank）'
          case 'Others':
            return '其他'
          default:
            return category || '未知'
        }
      }

      return (
        <Card title="配置详情">
          <div className="space-y-4">
            {/* 适用场景信息 */}
            {modelAPIConfig.modelCategory && (
              <div className="text-sm">
                <span className="text-gray-700">适用场景: </span>
                <span className="font-medium">{getModelCategoryText(modelAPIConfig.modelCategory)}</span>
              </div>
            )}

            {/* 协议信息 */}
            <div className="text-sm">
              <span className="text-gray-700">协议: </span>
              <span className="font-medium">{protocols.join(', ')}</span>
            </div>

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
                  <Collapse ghost expandIconPosition="end">
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
        <h1 className="text-2xl font-bold mb-2">{apiProduct.type === 'MCP_SERVER' ? '配置MCP' : 'API关联'}</h1>
        <p className="text-gray-600">{apiProduct.type === 'MCP_SERVER' ? '管理Product关联的MCP Server' : '管理Product关联的API'}</p>
      </div>

      {renderLinkInfo()}
      {renderApiConfig()}

      <Modal
        title={linkedService ? (apiProduct.type === 'MCP_SERVER' ? '重新关联MCP Server' : '重新关联API') : (apiProduct.type === 'MCP_SERVER' ? '关联MCP Server' : '关联新API')}
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
                    return gateway.gatewayType === 'APIG_AI' || gateway.gatewayType === 'HIGRESS' || gateway.gatewayType === 'ADP_AI_GATEWAY'|| gateway.gatewayType === 'APSARA_GATEWAY';
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

      {/* 自定义数据配置弹窗 */}
      <McpCustomConfigModal
        visible={isCustomConfigModalVisible}
        onCancel={() => setIsCustomConfigModalVisible(false)}
        productName={apiProduct.name}
        productDescription={apiProduct.description}
        productIcon={apiProduct.icon}
        productDocument={apiProduct.document}
        initialMcpMeta={mcpMetaList.length > 0 ? mcpMetaList[0] : null}
        onOk={async (values) => {
          const iconJson = values.icon
            ? JSON.stringify({ type: 'BASE64', data: values.icon })
            : values.iconUrl
              ? JSON.stringify({ type: 'URL', url: values.iconUrl })
              : undefined

          // 不 try-catch，让错误传播到 McpCustomConfigModal 的 handleSubmit
          await mcpServerApi.saveMeta({
            productId: apiProduct.productId,
            mcpName: values.mcpServerName,
            displayName: values.mcpDisplayName,
            description: values.description,
            repoUrl: values.repoUrl,
            sourceType: 'config',
            origin: 'ADMIN',
            tags: values.tags ? JSON.stringify(values.tags) : undefined,
            icon: iconJson,
            protocolType: values.protocolType,
            connectionConfig: values.mcpConfigJson,
            extraParams: values.extraParams?.length ? JSON.stringify(values.extraParams) : undefined,
            serviceIntro: values.serviceIntro,
            visibility: 'PUBLIC',
            publishStatus: 'DRAFT',
            sandboxRequired: values.sandboxRequired || false,
          })

          // 如果需要沙箱部署且选择了立即部署，单独调用 deploySandbox
          if (values.sandboxRequired && values.deployNow && values.sandboxId) {
            // 先刷新 meta 获取 mcpServerId
            const metaRes = await mcpServerApi.listMetaByProduct(apiProduct.productId)
            const metaList = metaRes?.data || []
            const newMeta = metaList.find((m: any) => m.mcpName === values.mcpServerName)
            if (newMeta?.mcpServerId) {
              await mcpServerApi.deploySandbox(newMeta.mcpServerId, {
                sandboxId: values.sandboxId,
                transportType: values.transportType || 'sse',
                authType: values.authType || 'none',
                paramValues: values.adminParamValues && Object.keys(values.adminParamValues).length > 0
                  ? JSON.stringify(values.adminParamValues)
                  : undefined,
                namespace: values.namespace,
                resourceSpec: (values.cpuRequest || values.cpuLimit || values.memoryRequest || values.memoryLimit || values.ephemeralStorage)
                  ? JSON.stringify({
                      cpuRequest: values.cpuRequest || undefined,
                      cpuLimit: values.cpuLimit || undefined,
                      memoryRequest: values.memoryRequest || undefined,
                      memoryLimit: values.memoryLimit || undefined,
                      ephemeralStorage: values.ephemeralStorage || undefined,
                    })
                  : undefined,
              })

              // 轮询等待 endpoint 状态变为 ACTIVE
              const maxAttempts = 15
              let deployed = false
              for (let i = 0; i < maxAttempts; i++) {
                await new Promise(r => setTimeout(r, 3000))
                const pollRes = await mcpServerApi.listMetaByProduct(apiProduct.productId)
                const pollList = pollRes?.data || []
                const activeMeta = (Array.isArray(pollList) ? pollList : []).find((m: any) => m.mcpServerId === newMeta.mcpServerId)
                if (activeMeta?.endpointStatus === 'ACTIVE' && activeMeta?.endpointUrl) {
                  message.success('沙箱部署完成')
                  deployed = true
                  break
                }
              }
              if (!deployed) {
                message.warning('沙箱部署超时，请稍后刷新页面查看状态')
              }
            }
          }

          // 成功后刷新数据
          setIsCustomConfigModalVisible(false)
          await fetchMcpMeta()
          await handleRefresh()
        }}
      />

      {/* 部署到沙箱弹窗 */}
      <Modal
        title="部署到沙箱"
        open={!!deployModalMcpServerId}
        maskClosable={false}
        onCancel={() => { setDeployModalMcpServerId(null); deployForm.resetFields(); setDeployParamValues({}); setDeployResourcePreset('small') }}
        onOk={handleDeploySandbox}
        confirmLoading={deploying}
        okText="开始部署"
        width={600}
        destroyOnClose
      >
        <Form form={deployForm} layout="vertical" initialValues={{ transportType: 'sse', authType: 'none', resourcePreset: 'small', cpuRequest: '250m', cpuLimit: '500m', memoryRequest: '256Mi', memoryLimit: '512Mi', ephemeralStorage: '1Gi' }}>
          <div className="space-y-4">
            {/* 部署目标 */}
            <div className="rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                <span className="text-xs font-medium text-gray-600">部署目标</span>
              </div>
              <div className="p-4 space-y-3">
                <Form.Item label="沙箱实例" name="sandboxId" rules={[{ required: true, message: '请选择沙箱' }]} className="mb-0">
                  <Select
                    placeholder="选择沙箱实例"
                    loading={deploySandboxLoading}
                    onChange={handleDeploySandboxChange}
                    options={deploySandboxList.map((s: any) => ({
                      value: s.sandboxId,
                      label: s.sandboxName || s.sandboxId,
                    }))}
                  />
                </Form.Item>
                <Form.Item
                  label="Namespace" name="namespace"
                  rules={[{ required: true, message: '请选择 Namespace' }]}
                  className="mb-0"
                  extra={!deploySandboxIdValue ? <span className="text-[10px] text-gray-400">请先选择沙箱实例</span> : undefined}
                >
                  <Select
                    placeholder={deployNamespaceLoading ? '加载中...' : '选择 Namespace'}
                    loading={deployNamespaceLoading}
                    disabled={!deploySandboxIdValue}
                    showSearch
                    options={deployNamespaceList.map((ns) => ({ value: ns, label: ns }))}
                  />
                </Form.Item>
                <Form.Item label="传输协议" name="transportType" className="mb-0">
                  <Radio.Group size="small" optionType="button" buttonStyle="solid">
                    <Radio.Button value="sse">SSE</Radio.Button>
                    <Radio.Button value="http">Streamable HTTP</Radio.Button>
                  </Radio.Group>
                </Form.Item>
                <Form.Item label="鉴权方式" name="authType" className="mb-0">
                  <Select>
                    <Select.Option value="none">无鉴权</Select.Option>
                    <Select.Option value="apikey">API Key</Select.Option>
                  </Select>
                </Form.Item>
              </div>
            </div>

            {/* 资源规格 */}
            <div className="rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                <span className="text-xs font-medium text-gray-600">资源规格</span>
              </div>
              <div className="p-4">
                <Form.Item name="resourcePreset" className={deployResourcePreset === 'custom' ? 'mb-3' : 'mb-0'}>
                  <Radio.Group
                    className="w-full"
                    value={deployResourcePreset}
                    onChange={(e) => {
                      setDeployResourcePreset(e.target.value)
                      const presets: Record<string, any> = {
                        small:  { cpuRequest: '250m', cpuLimit: '500m', memoryRequest: '256Mi', memoryLimit: '512Mi', ephemeralStorage: '1Gi' },
                        medium: { cpuRequest: '500m', cpuLimit: '1',    memoryRequest: '512Mi', memoryLimit: '1Gi',  ephemeralStorage: '2Gi' },
                        large:  { cpuRequest: '1',    cpuLimit: '2',    memoryRequest: '1Gi',   memoryLimit: '2Gi',  ephemeralStorage: '4Gi' },
                      }
                      const p = presets[e.target.value]
                      if (p) deployForm.setFieldsValue(p)
                    }}
                  >
                    <div className="grid grid-cols-4 gap-2">
                      {[
                        { value: 'small',  label: '小型', desc: '0.5C / 512Mi' },
                        { value: 'medium', label: '中型', desc: '1C / 1Gi' },
                        { value: 'large',  label: '大型', desc: '2C / 2Gi' },
                        { value: 'custom', label: '自定义', desc: '手动配置' },
                      ].map((item) => (
                        <Radio.Button
                          key={item.value}
                          value={item.value}
                          className="h-auto text-center flex-1"
                          style={{ padding: '8px 0', lineHeight: 1.3 }}
                        >
                          <div className="text-xs font-medium">{item.label}</div>
                          <div className="text-[10px] text-gray-400 mt-0.5">{item.desc}</div>
                        </Radio.Button>
                      ))}
                    </div>
                  </Radio.Group>
                </Form.Item>
                {deployResourcePreset === 'custom' ? (
                  <div className="grid grid-cols-2 gap-x-3 gap-y-2 pt-1 border-t border-gray-100">
                    <Form.Item name="cpuRequest" label="CPU Request" className="mb-0"><Input size="small" className="font-mono text-xs" /></Form.Item>
                    <Form.Item name="cpuLimit" label="CPU Limit" className="mb-0"><Input size="small" className="font-mono text-xs" /></Form.Item>
                    <Form.Item name="memoryRequest" label="Memory Request" className="mb-0"><Input size="small" className="font-mono text-xs" /></Form.Item>
                    <Form.Item name="memoryLimit" label="Memory Limit" className="mb-0"><Input size="small" className="font-mono text-xs" /></Form.Item>
                    <Form.Item name="ephemeralStorage" label="临时存储" className="mb-0"><Input size="small" className="font-mono text-xs" /></Form.Item>
                  </div>
                ) : (
                  <>
                    <Form.Item name="cpuRequest" hidden><Input /></Form.Item>
                    <Form.Item name="cpuLimit" hidden><Input /></Form.Item>
                    <Form.Item name="memoryRequest" hidden><Input /></Form.Item>
                    <Form.Item name="memoryLimit" hidden><Input /></Form.Item>
                    <Form.Item name="ephemeralStorage" hidden><Input /></Form.Item>
                  </>
                )}
              </div>
            </div>

            {/* 参数值配置 */}
            {(() => {
              const paramDefs = getDeployExtraParamDefs()
              if (paramDefs.length === 0) return null
              return (
                <div className="rounded-lg border border-gray-200 overflow-hidden">
                  <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                    <span className="text-xs font-medium text-gray-600">参数值配置</span>
                    <span className="text-[10px] text-gray-400 ml-2">部署时注入的环境变量 / 请求参数</span>
                  </div>
                  <div className="p-4 space-y-3">
                    {paramDefs.map((p: any) => (
                      <div key={p.name}>
                        <div className="flex items-center gap-1.5 mb-1">
                          <span className="text-xs font-mono text-gray-700">{p.name}</span>
                          {p.required && <span className="text-red-400 text-[10px]">*</span>}
                          <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">{p.position}</Tag>
                        </div>
                        {p.description && <div className="text-[10px] text-gray-400 mb-1">{p.description}</div>}
                        <Input
                          size="small"
                          placeholder={p.example || `请输入 ${p.name}`}
                          value={deployParamValues[p.name] || ''}
                          onChange={(e) => setDeployParamValues(prev => ({ ...prev, [p.name]: e.target.value }))}
                          className="font-mono text-xs"
                        />
                      </div>
                    ))}
                  </div>
                </div>
              )
            })()}
          </div>
        </Form>
      </Modal>

      {/* 编辑工具配置弹窗 */}
      <ToolsConfigEditorModal
        open={toolsEditorOpen}
        mcpServerId={mcpMetaList[0]?.mcpServerId || ''}
        initialValue={(() => {
          const tc = mcpMetaList[0]?.toolsConfig
          if (!tc) return ''
          if (typeof tc === 'string') return tc
          return JSON.stringify(tc, null, 2)
        })()}
        onSave={async () => {
          setToolsEditorOpen(false)
          await fetchMcpMeta()
          await handleRefresh()
        }}
        onCancel={() => setToolsEditorOpen(false)}
      />

    </div>
  )
} 