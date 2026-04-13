import { useState, useEffect, useCallback } from 'react'
import { Form, Select, Alert, Button, Spin } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { nacosApi } from '@/lib/api'

interface NacosInstance {
  nacosId: string
  nacosName: string
  nacosType?: 'OPEN_SOURCE' | 'MSE'
  [key: string]: any
}

interface Namespace {
  namespaceId: string
  namespaceName: string
  namespaceDesc?: string
}

interface McpServerItem {
  mcpServerName: string
  [key: string]: any
}

export default function NacosImportStep() {
  const form = Form.useFormInstance()

  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([])
  const [nacosLoading, setNacosLoading] = useState(false)
  const [nacosError, setNacosError] = useState<string | null>(null)

  const [selectedNacosId, setSelectedNacosId] = useState<string | undefined>()
  const [namespaces, setNamespaces] = useState<Namespace[]>([])
  const [nsLoading, setNsLoading] = useState(false)
  const [nsError, setNsError] = useState<string | null>(null)

  const [selectedNamespaceId, setSelectedNamespaceId] = useState<string | undefined>()
  const [mcpServers, setMcpServers] = useState<McpServerItem[]>([])
  const [mcpLoading, setMcpLoading] = useState(false)
  const [mcpError, setMcpError] = useState<string | null>(null)

  const fetchNacosInstances = useCallback(async () => {
    setNacosLoading(true); setNacosError(null)
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 1000 })
      setNacosInstances(res.data?.content || [])
    } catch { setNacosError('获取 Nacos 实例列表失败') }
    finally { setNacosLoading(false) }
  }, [])

  useEffect(() => { fetchNacosInstances() }, [fetchNacosInstances])

  const fetchNamespaces = useCallback(async (nacosId: string) => {
    setNsLoading(true); setNsError(null); setNamespaces([])
    setSelectedNamespaceId(undefined); setMcpServers([])
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 })
      setNamespaces((res.data?.content || []).map((ns: any) => ({
        namespaceId: ns.namespaceId,
        namespaceName: ns.namespaceName || ns.namespaceId,
        namespaceDesc: ns.namespaceDesc,
      })))
    } catch { setNsError('获取命名空间列表失败') }
    finally { setNsLoading(false) }
  }, [])

  const fetchMcpServers = useCallback(async (nacosId: string, namespaceId: string) => {
    setMcpLoading(true); setMcpError(null); setMcpServers([])
    try {
      const res = await nacosApi.getNacosMcpServers(nacosId, { page: 1, size: 1000, namespaceId })
      setMcpServers(res.data?.content || [])
    } catch { setMcpError('获取 MCP Server 列表失败') }
    finally { setMcpLoading(false) }
  }, [])

  const handleNacosChange = (nacosId: string) => {
    setSelectedNacosId(nacosId)
    form.setFieldsValue({ nacosId, mcpName: undefined, nacosRefConfig: undefined })
    fetchNamespaces(nacosId)
  }

  const handleNamespaceChange = (namespaceId: string) => {
    setSelectedNamespaceId(namespaceId)
    form.setFieldsValue({ mcpName: undefined, nacosRefConfig: undefined })
    if (selectedNacosId) fetchMcpServers(selectedNacosId, namespaceId)
  }

  const handleMcpServerChange = (mcpServerName: string) => {
    form.setFieldsValue({
      mcpName: mcpServerName,
      nacosRefConfig: { namespaceId: selectedNamespaceId, mcpServerName },
    })
  }

  return (
    <div className="space-y-4">
      {nacosError && (
        <Alert type="error" message={nacosError} showIcon
          action={<Button size="small" icon={<ReloadOutlined />} onClick={fetchNacosInstances}>重试</Button>} />
      )}
      {nsError && (
        <Alert type="error" message={nsError} showIcon
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => selectedNacosId && fetchNamespaces(selectedNacosId)}>重试</Button>} />
      )}
      {mcpError && (
        <Alert type="error" message={mcpError} showIcon
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => selectedNacosId && selectedNamespaceId && fetchMcpServers(selectedNacosId, selectedNamespaceId)}>重试</Button>} />
      )}

      <Form.Item label="选择 Nacos 实例" required>
        <Select
          placeholder="请选择 Nacos 实例"
          className="w-full"
          loading={nacosLoading}
          value={selectedNacosId}
          onChange={handleNacosChange}
          optionLabelProp="label"
          showSearch
          filterOption={(input, option) =>
            (option?.label as unknown as string || '').toLowerCase().includes(input.toLowerCase())
          }
          notFoundContent={nacosLoading ? <Spin size="small" /> : '暂无可用 Nacos 实例'}
        >
          {nacosInstances.map((n) => (
            <Select.Option key={n.nacosId} value={n.nacosId} label={n.nacosName}>
              <div>
                <div className="font-medium">{n.nacosName}</div>
                <div className="text-sm text-gray-500">
                  {n.serverUrl || n.nacosId}
                  {n.nacosType && ` - ${n.nacosType}`}
                </div>
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item label="选择命名空间" required>
        <Select
          placeholder={selectedNacosId ? '请选择命名空间' : '请先选择 Nacos 实例'}
          className="w-full"
          loading={nsLoading}
          disabled={!selectedNacosId}
          value={selectedNamespaceId}
          onChange={handleNamespaceChange}
          showSearch
          filterOption={(input, option) =>
            (option?.children as unknown as string || '').toLowerCase().includes(input.toLowerCase())
          }
          notFoundContent={nsLoading ? <Spin size="small" /> : '暂无命名空间'}
        >
          {namespaces.map((ns) => (
            <Select.Option key={ns.namespaceId} value={ns.namespaceId}>
              {ns.namespaceName}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item label="选择 MCP Server" required>
        <Select
          placeholder={selectedNamespaceId ? '请选择 MCP Server' : '请先选择命名空间'}
          className="w-full"
          loading={mcpLoading}
          disabled={!selectedNamespaceId}
          value={form.getFieldValue('mcpName')}
          onChange={handleMcpServerChange}
          showSearch
          filterOption={(input, option) =>
            (option?.children as unknown as string || '').toLowerCase().includes(input.toLowerCase())
          }
          notFoundContent={mcpLoading ? <Spin size="small" /> : '暂无 MCP Server'}
        >
          {mcpServers.map((s) => (
            <Select.Option key={s.mcpServerName} value={s.mcpServerName}>
              <div className="flex items-center justify-between">
                <span>{s.mcpServerName}</span>
                {s.version && <span className="text-xs text-gray-400 ml-2">v{s.version}</span>}
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item name="nacosId" hidden><input type="hidden" /></Form.Item>
      <Form.Item name="nacosRefConfig" hidden><input type="hidden" /></Form.Item>
    </div>
  )
}
