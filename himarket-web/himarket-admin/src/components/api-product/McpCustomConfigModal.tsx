import { useState, useEffect } from 'react'
import { Modal, Form, Input, Button, Tag, Radio, Space, Select, Switch, Table, message, Steps } from 'antd'
import {
  InfoCircleOutlined, SettingOutlined, FileTextOutlined, CloudServerOutlined,
  PlusOutlined, CheckCircleFilled,
  CodeOutlined, GlobalOutlined, ApiOutlined, DeleteOutlined, EditOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import { sandboxApi } from '@/lib/api'
import type { ProductIcon } from '@/types/api-product'

interface McpCustomConfigModalProps {
  visible: boolean
  onCancel: () => void
  onOk: (values: any) => void | Promise<void>
  productName?: string
  productDescription?: string
  productIcon?: ProductIcon
  productDocument?: string
  /** 编辑模式：传入已有 MCP 元数据，表单将预填这些值 */
  initialMcpMeta?: {
    mcpName?: string
    displayName?: string
    description?: string
    protocolType?: string
    connectionConfig?: string
    tags?: string
    icon?: string
    repoUrl?: string
    extraParams?: string
    serviceIntro?: string
    sandboxRequired?: boolean
  } | null
}

interface ExtraParam {
  key: string
  name: string
  position: string
  required: boolean
  description: string
  example: string
}

const NAV_ITEMS_FULL = [
  { key: 0, label: '基础信息', icon: <InfoCircleOutlined />, desc: '名称、仓库、标签' },
  { key: 1, label: 'MCP 配置', icon: <SettingOutlined />, desc: '协议与连接方式' },
  { key: 2, label: '服务介绍', icon: <FileTextOutlined />, desc: 'Markdown 文档' },
  { key: 3, label: '沙箱部署', icon: <CloudServerOutlined />, desc: '沙箱与参数配置' },
]

const NAV_ITEMS_SHORT = [
  { key: 0, label: '基础信息', icon: <InfoCircleOutlined />, desc: '名称、仓库、标签' },
  { key: 1, label: 'MCP 配置', icon: <SettingOutlined />, desc: '协议与连接方式' },
  { key: 2, label: '服务介绍', icon: <FileTextOutlined />, desc: 'Markdown 文档' },
]

export function McpCustomConfigModal({ visible, onCancel, onOk, productName, productDescription, productIcon, productDocument, initialMcpMeta }: McpCustomConfigModalProps) {
  const [form] = Form.useForm()
  const [currentStep, setCurrentStep] = useState(0)
  const [tagInput, setTagInput] = useState('')
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set())
  const [extraParams, setExtraParams] = useState<ExtraParam[]>([])
  const [paramModalVisible, setParamModalVisible] = useState(false)
  const [paramForm] = Form.useForm()
  const [editingParamKey, setEditingParamKey] = useState<string | null>(null)
  const [adminParamValues, setAdminParamValues] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [deployStep, setDeployStep] = useState(-1) // -1=未开始, 0=保存配置, 1=部署沙箱, 2=获取工具

  const protocolType: string = Form.useWatch('protocolType', form) || 'sse'
  const sandboxRequired: boolean = Form.useWatch('sandboxRequired', form) ?? true
  const watchedTags: string[] = Form.useWatch('tags', form) || []
  const resourcePreset: string = Form.useWatch('resourcePreset', form) || 'small'

  // 编辑模式判断：initialMcpMeta 存在且包含 mcpName 时为编辑模式
  const isEditMode = !!initialMcpMeta?.mcpName

  // 关闭沙箱托管时，如果当前在第四步则自动回退到第三步
  useEffect(() => {
    if (!sandboxRequired && currentStep === 3) {
      setCurrentStep(2)
    }
  }, [sandboxRequired, currentStep])

  const [sandboxList, setSandboxList] = useState<any[]>([])
  const [sandboxLoading, setSandboxLoading] = useState(false)
  const [namespaceList, setNamespaceList] = useState<string[]>([])
  const [namespaceLoading, setNamespaceLoading] = useState(false)

  useEffect(() => {
    if (visible && sandboxRequired) {
      setSandboxLoading(true)
      sandboxApi.getActiveSandboxes().then((res: any) => {
        const list = res?.data || []
        setSandboxList(Array.isArray(list) ? list : [])
      }).catch(() => setSandboxList([])).finally(() => setSandboxLoading(false))
    }
  }, [visible, sandboxRequired])

  // 打开弹窗时自动填充产品信息到展示字段（只读）
  useEffect(() => {
    if (visible) {
      const formValues: Record<string, any> = {
        mcpDisplayName: productName || '',
        description: productDescription || '',
        serviceIntro: productDocument || '',
        sandboxRequired: true,
      }

      // 编辑模式：预填已有 MCP 元数据
      if (initialMcpMeta) {
        if (initialMcpMeta.mcpName) formValues.mcpServerName = initialMcpMeta.mcpName
        if (initialMcpMeta.displayName) formValues.mcpDisplayName = initialMcpMeta.displayName
        if (initialMcpMeta.description) formValues.description = initialMcpMeta.description
        if (initialMcpMeta.protocolType) formValues.protocolType = initialMcpMeta.protocolType
        if (initialMcpMeta.connectionConfig) formValues.mcpConfigJson = initialMcpMeta.connectionConfig
        if (initialMcpMeta.repoUrl) formValues.repoUrl = initialMcpMeta.repoUrl
        if (initialMcpMeta.serviceIntro) formValues.serviceIntro = initialMcpMeta.serviceIntro
        formValues.sandboxRequired = initialMcpMeta.sandboxRequired ?? true

        // 解析 tags JSON
        if (initialMcpMeta.tags) {
          try {
            const tags = JSON.parse(initialMcpMeta.tags)
            if (Array.isArray(tags)) formValues.tags = tags
          } catch { /* ignore */ }
        }

        // 解析 extraParams JSON
        if (initialMcpMeta.extraParams) {
          try {
            const params = JSON.parse(initialMcpMeta.extraParams)
            if (Array.isArray(params)) {
              setExtraParams(params.map((p: any, i: number) => ({
                key: p.name || `param-${i}`,
                name: p.name || '',
                position: p.position || 'env',
                required: p.required ?? false,
                description: p.description || '',
                example: p.example || '',
              })))
            }
          } catch { /* ignore */ }
        }
      }

      form.setFieldsValue(formValues)
    }
  }, [visible, productName, productDescription, productDocument, initialMcpMeta])

  const handleAddTag = () => {
    const val = tagInput.trim()
    if (!val) return
    const tags: string[] = form.getFieldValue('tags') || []
    if (tags.includes(val)) { message.warning('标签已存在'); return }
    form.setFieldsValue({ tags: [...tags, val] })
    setTagInput('')
  }

  const handleRemoveTag = (tag: string) => {
    const tags: string[] = form.getFieldValue('tags') || []
    form.setFieldsValue({ tags: tags.filter((t) => t !== tag) })
  }

  const resetAll = () => {
    form.resetFields()
    setCurrentStep(0)
    setTagInput('')
    setCompletedSteps(new Set())
    setExtraParams([])
    setEditingParamKey(null)
    setAdminParamValues({})
    setSubmitting(false)
    setDeployStep(-1)
    setNamespaceList([])
    setNamespaceLoading(false)
  }

  const handleCancel = () => { resetAll(); onCancel() }

  const stepFields: string[][] = [
    ['mcpServerName'],
    ['mcpConfigJson'],
    [], // 服务介绍 - 无必填
    [], // 沙箱部署 - 按钮级别校验
  ]

  const navigateTo = async (target: number) => {
    // 从当前步跳转时，先校验当前步
    if (target > currentStep) {
      try {
        await form.validateFields(stepFields[currentStep])
        setCompletedSteps((prev) => new Set(prev).add(currentStep))
      } catch { return }
    }
    setCurrentStep(target)
  }

  const handleNext = async () => {
    try {
      await form.validateFields(stepFields[currentStep])
      setCompletedSteps((prev) => new Set(prev).add(currentStep))
      setCurrentStep(currentStep + 1)
    } catch { /* validation failed */ }
  }

  const handleSubmit = async (withDeploy: boolean) => {
    // "保存并部署" 需要校验沙箱配置字段
    if (withDeploy && sandboxRequired) {
      try {
        await form.validateFields(['sandboxId', 'namespace'])
      } catch { return }
    }
    const values = form.getFieldsValue(true)
    // 标记 deployNow 供 onOk 回调判断
    values.deployNow = withDeploy && sandboxRequired
    setCompletedSteps((prev) => new Set(prev).add(3))
    setSubmitting(true)
    if (values.deployNow) {
      setDeployStep(0)
    }
    try {
      await onOk({ ...values, extraParams, adminParamValues })
      if (values.deployNow) {
        setDeployStep(2)
      }
      message.success(values.deployNow ? '保存并部署成功' : '配置已保存')
      resetAll()
    } catch (e: any) {
      const msg = e?.response?.data?.message || e?.message || '保存失败'
      if (values.deployNow) {
        if (msg.includes('部署沙箱') || msg.includes('创建连接')) setDeployStep(1)
      }
      message.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const handleSaveOnly = () => {
    if (!sandboxRequired) {
      // 不需要沙箱，直接保存
      handleSubmit(false)
      return
    }
    // 需要沙箱但跳过部署，弹窗提醒
    Modal.confirm({
      title: '仅保存配置',
      content: '沙箱尚未部署，保存后可在 MCP 配置信息页面点击「部署到沙箱」按钮手动部署。',
      okText: '确认保存',
      cancelText: '取消',
      onOk: () => handleSubmit(false),
    })
  }

  const DEPLOY_STEPS = [
    { title: '保存配置' },
    { title: '部署到沙箱' },
  ]

  // ==================== Step 1: 基础信息 ====================
  const renderBasicInfo = () => (
    <>
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="mcpServerName"
          label={<span>MCP 英文名称 <span className="text-xs text-gray-400 font-normal ml-1">唯一标识</span></span>}
          rules={[
            { required: true, message: '请输入 MCP 英文名称' },
            { pattern: /^[a-z][a-z0-9-]*$/, message: '小写字母开头，仅含小写字母、数字、连字符' },
            { max: 63, message: '不超过 63 个字符' },
          ]}
        >
          <Input placeholder="weather-mcp-server" disabled={isEditMode} />
        </Form.Item>
        <Form.Item
          name="mcpDisplayName"
          label="MCP 中文名称"
        >
          <Input disabled />
        </Form.Item>
      </div>

      <Form.Item name="description" label="描述">
        <Input.TextArea disabled rows={2} autoSize={{ minRows: 2, maxRows: 4 }} />
      </Form.Item>

      <Form.Item
        name="repoUrl"
        label="仓库地址"
        rules={[
          { type: 'url', message: '请输入合法的 URL' },
        ]}
      >
        <Input placeholder="https://github.com/org/mcp-server" />
      </Form.Item>

      {/* 标签 */}
      <Form.Item label="自定义标签">
        <Form.Item name="tags" hidden><Input /></Form.Item>
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Input
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onPressEnter={(e) => { e.preventDefault(); handleAddTag() }}
              placeholder="输入后按回车添加"
              size="small"
              suffix={
                <PlusOutlined
                  className="text-gray-400 hover:text-blue-500 cursor-pointer transition-colors"
                  onClick={handleAddTag}
                />
              }
            />
          </div>
          <div className="flex flex-wrap gap-1.5 min-h-[24px]">
            {watchedTags.map((tag: string) => (
              <Tag key={tag} closable onClose={() => handleRemoveTag(tag)} color="blue" className="m-0">{tag}</Tag>
            ))}
            {!watchedTags.length && (
              <span className="text-xs text-gray-300">暂无标签</span>
            )}
          </div>
        </div>
      </Form.Item>

      {/* Icon（继承自产品，只读展示） */}
      <Form.Item label="Icon">
        <div className="w-16 h-16 rounded-xl border-2 border-dashed border-gray-200 flex items-center justify-center flex-shrink-0">
          {productIcon ? (
            <img
              src={productIcon.value}
              alt="icon"
              className="w-full h-full object-cover rounded-[10px]"
            />
          ) : (
            <span className="text-xs text-gray-300">无图标</span>
          )}
        </div>
      </Form.Item>

    </>
  )

  // ==================== Step 2: MCP 配置 ====================
  const renderMcpConfig = () => {
    const protocols = [
      { key: 'stdio', label: 'Stdio', icon: <CodeOutlined /> },
      { key: 'sse', label: 'SSE', icon: <ApiOutlined /> },
      { key: 'http', label: 'Streamable HTTP', icon: <GlobalOutlined /> },
    ]
    const isStdio = protocolType === 'stdio'
    const exampleJson = isStdio
      ? `{
  "mcpServers": {
    "your-mcp-server": {
      "command": "npx",
      "args": ["-y", "@mcp/your-server"]
    }
  }
}`
      : protocolType === 'sse'
        ? `{
  "mcpServers": {
    "your-mcp-server": {
      "type": "sse",
      "url": "https://mcp.example.com/sse"
    }
  }
}`
        : `{
  "mcpServers": {
    "your-mcp-server": {
      "url": "https://mcp.example.com/mcp"
    }
  }
}`

    return (
      <>
        {/* 协议选择卡片 */}
        <Form.Item name="protocolType" label="协议类型" initialValue="sse" rules={[{ required: true }]}>
          <div className="grid grid-cols-3 gap-2">
            {protocols.map((p) => {
              const selected = protocolType === p.key
              return (
                <div
                  key={p.key}
                  onClick={() => { form.setFieldsValue({ protocolType: p.key, ...(p.key === 'stdio' ? { sandboxRequired: true } : {}) }); setExtraParams([]) }}
                  className={`relative flex items-center gap-2 rounded-lg border px-3 py-2.5 cursor-pointer transition-all duration-150 ${
                    selected
                      ? 'border-blue-500 bg-blue-50/50 ring-1 ring-blue-500/20'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
                  }`}
                >
                  {selected && <CheckCircleFilled className="absolute top-1.5 right-1.5 text-blue-500 text-[10px]" />}
                  <span className={`text-sm ${selected ? 'text-blue-500' : 'text-gray-400'}`}>{p.icon}</span>
                  <span className={`text-xs font-medium ${selected ? 'text-blue-700' : 'text-gray-600'}`}>{p.label}</span>
                </div>
              )
            })}
          </div>
        </Form.Item>

        {/* MCP 连接配置 JSON */}
        <Form.Item
          name="mcpConfigJson"
          label="MCP 连接配置"
          rules={[
            { required: true, message: '请输入 MCP 配置' },
            {
              validator: (_, value) => {
                if (!value) return Promise.resolve()
                try { JSON.parse(value); return Promise.resolve() }
                catch { return Promise.reject('请输入合法的 JSON 格式') }
              },
            },
          ]}
        >
          <Input.TextArea
            placeholder={exampleJson}
            autoSize={{ minRows: 8, maxRows: 14 }}
            className="font-mono text-xs"
          />
        </Form.Item>

        {/* 解析 JSON 按钮 */}
        <div className="flex items-center gap-3 -mt-3 mb-4">
          <Button
            size="small"
            type="primary"
            ghost
            icon={<CodeOutlined />}
            onClick={() => {
              const raw = form.getFieldValue('mcpConfigJson')
              if (!raw) { message.warning('请先粘贴 JSON 配置'); return }
              try {
                const parsed = JSON.parse(raw)
                const servers = parsed.mcpServers || parsed
                const serverKey = Object.keys(servers)[0]
                if (!serverKey) { message.error('未找到有效的 MCP Server 配置'); return }
                const cfg = servers[serverKey]

                if (cfg.command) {
                  form.setFieldsValue({ protocolType: 'stdio', sandboxRequired: true })
                } else if (cfg.type === 'sse' || (!cfg.type && cfg.url && cfg.url.endsWith('/sse'))) {
                  form.setFieldsValue({ protocolType: 'sse' })
                } else if (cfg.type === 'streamable-http') {
                  form.setFieldsValue({ protocolType: 'http' })
                } else {
                  // 有 url 但无法确定具体协议，默认 sse
                  form.setFieldsValue({ protocolType: cfg.url ? 'sse' : 'http' })
                }

                const params: ExtraParam[] = []
                if (cfg.env && typeof cfg.env === 'object') {
                  Object.entries(cfg.env).forEach(([k, v]) => {
                    params.push({ key: `param_${Date.now()}_${k}`, name: k, position: 'env', required: true, description: '', example: String(v) })
                  })
                }
                if (cfg.headers && typeof cfg.headers === 'object') {
                  Object.entries(cfg.headers).forEach(([k, v]) => {
                    params.push({ key: `param_${Date.now()}_${k}`, name: k, position: 'header', required: true, description: '', example: String(v) })
                  })
                }
                if (cfg.query && typeof cfg.query === 'object') {
                  Object.entries(cfg.query).forEach(([k, v]) => {
                    params.push({ key: `param_${Date.now()}_${k}`, name: k, position: 'query', required: true, description: '', example: String(v) })
                  })
                }

                // 保留原始 JSON 不做修改，只提取参数用于展示
                setExtraParams(params)
                message.success(`已解析：${serverKey}，识别到 ${params.length} 个参数`)
              } catch {
                message.error('JSON 解析失败，请检查格式')
              }
            }}
          >
            解析 JSON
          </Button>
          <span className="text-xs text-gray-400">粘贴 JSON 后点击可自动解析协议类型及参数</span>
        </div>

        {/* 额外参数配置 - 列表 */}
        <div className="mt-1">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-gray-700">{isStdio ? '环境变量配置' : '请求参数配置'}</span>
            <Space size={8}>
              <Button
                size="small"
                type="dashed"
                icon={<PlusOutlined />}
                onClick={() => { setEditingParamKey(null); paramForm.resetFields(); setParamModalVisible(true) }}
              >
                添加{isStdio ? '变量' : '参数'}
              </Button>
              {extraParams.length > 0 && (
                <Button
                  size="small"
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => setExtraParams([])}
                >
                  清除所有
                </Button>
              )}
            </Space>
          </div>
          {extraParams.length > 0 ? (
            <Table
              dataSource={extraParams}
              rowKey="key"
              size="small"
              pagination={false}
              className="border border-gray-100 rounded-lg overflow-hidden"
              columns={[
                {
                  title: '参数名', dataIndex: 'name', width: 120,
                  render: (v: string) => <span className="font-mono text-xs">{v}</span>,
                },
                {
                  title: '位置', dataIndex: 'position', width: 80,
                  render: (v: string) => <Tag className="m-0 border-0 bg-gray-100 text-gray-600 text-xs">{v}</Tag>,
                },
                {
                  title: '必填', dataIndex: 'required', width: 50, align: 'center' as const,
                  render: (v: boolean) => v ? <Tag color="red" className="m-0 border-0 text-xs">是</Tag> : <span className="text-xs text-gray-400">否</span>,
                },
                {
                  title: '说明', dataIndex: 'description', ellipsis: true,
                  render: (v: string) => <span className="text-xs text-gray-500">{v || '-'}</span>,
                },
                {
                  title: '', width: 60, align: 'center' as const,
                  render: (_: any, record: ExtraParam) => (
                    <Space size={4}>
                      <Button
                        type="text" size="small" icon={<EditOutlined />}
                        className="text-gray-400 hover:text-blue-500"
                        onClick={() => {
                          setEditingParamKey(record.key)
                          paramForm.setFieldsValue(record)
                          setParamModalVisible(true)
                        }}
                      />
                      <Button
                        type="text" size="small" icon={<DeleteOutlined />}
                        className="text-gray-400 hover:text-red-500"
                        onClick={() => setExtraParams((prev) => prev.filter((p) => p.key !== record.key))}
                      />
                    </Space>
                  ),
                },
              ]}
            />
          ) : (
            <div className="border border-dashed border-gray-200 rounded-lg py-6 text-center text-xs text-gray-400">
              暂无{isStdio ? '环境变量' : '请求参数'}，点击上方按钮添加
            </div>
          )}
        </div>

        {/* 沙箱托管开关 */}
        <div className="flex items-center justify-between py-2.5 px-4 bg-gray-50 rounded-lg mt-4">
          <div>
            <div className="text-sm text-gray-700">是否需要沙箱托管</div>
            <div className="text-xs text-gray-400 mt-0.5">
              {isStdio ? 'Stdio 协议必须通过沙箱运行' : '开启后可将 MCP Server 部署到沙箱集群'}
            </div>
          </div>
          <Switch
            checked={sandboxRequired}
            checkedChildren="开启"
            unCheckedChildren="关闭"
            disabled={isStdio}
            onChange={(checked) => form.setFieldsValue({ sandboxRequired: checked })}
          />
        </div>
      </>
    )
  }

  // ==================== Step 3: 沙箱部署 ====================

  const handleSandboxChange = async (sandboxId: string) => {
    setNamespaceList([])
    form.setFieldsValue({ namespace: undefined })
    setNamespaceLoading(true)
    try {
      const res: any = await sandboxApi.listNamespaces(sandboxId)
      const list = res?.data || res || []
      setNamespaceList(Array.isArray(list) ? list : [])
    } catch {
      message.error('获取 Namespace 列表失败')
      setNamespaceList([])
    } finally {
      setNamespaceLoading(false)
    }
  }

  const renderSandboxConfig = () => {
    return (
      <>
        {sandboxRequired ? (
          <div className="space-y-4">
            {/* ── 部署目标 ── */}
            <div className="rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                <span className="text-xs font-medium text-gray-600">部署目标</span>
              </div>
              <div className="p-4 space-y-3">
                <Form.Item
                  name="sandboxId"
                  label="沙箱实例"
                  className="mb-0"
                  rules={[{ required: true, message: '请选择沙箱实例' }]}
                >
                  <Select
                    placeholder="选择沙箱实例"
                    loading={sandboxLoading}
                    onChange={handleSandboxChange}
                    options={sandboxList.map((s: any) => ({
                      value: s.sandboxId,
                      label: s.sandboxName,
                    }))}
                  />
                </Form.Item>
                <Form.Item
                  name="namespace"
                  label="Namespace"
                  className="mb-0"
                  rules={[{ required: true, message: '请选择 Namespace' }]}
                  extra={!form.getFieldValue('sandboxId') ? <span className="text-[10px] text-gray-400">请先选择沙箱实例</span> : undefined}
                >
                  <Select
                    placeholder={namespaceLoading ? '加载中...' : '选择 Namespace'}
                    loading={namespaceLoading}
                    disabled={!form.getFieldValue('sandboxId')}
                    showSearch
                    options={namespaceList.map((ns) => ({ value: ns, label: ns }))}
                  />
                </Form.Item>
                <Form.Item name="transportType" label="传输协议" initialValue="sse" className="mb-0">
                  <Radio.Group size="small" optionType="button" buttonStyle="solid">
                    <Radio.Button value="sse">SSE</Radio.Button>
                    <Radio.Button value="http">Streamable HTTP</Radio.Button>
                  </Radio.Group>
                </Form.Item>
                <Form.Item name="authType" label="鉴权方式" initialValue="none" className="mb-0">
                  <Select>
                    <Select.Option value="none">无鉴权</Select.Option>
                    <Select.Option value="apikey">API Key</Select.Option>
                  </Select>
                </Form.Item>
              </div>
            </div>

            {/* ── 资源规格 ── */}
            <div className="rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                <span className="text-xs font-medium text-gray-600">资源规格</span>
              </div>
              <div className="p-4">
                <Form.Item name="resourcePreset" initialValue="small" className={resourcePreset === 'custom' ? 'mb-3' : 'mb-0'}>
                  <Radio.Group
                    className="w-full"
                    onChange={(e) => {
                      const presets: Record<string, any> = {
                        small:  { cpuRequest: '250m', cpuLimit: '500m', memoryRequest: '256Mi', memoryLimit: '512Mi', ephemeralStorage: '1Gi' },
                        medium: { cpuRequest: '500m', cpuLimit: '1',    memoryRequest: '512Mi', memoryLimit: '1Gi',  ephemeralStorage: '2Gi' },
                        large:  { cpuRequest: '1',    cpuLimit: '2',    memoryRequest: '1Gi',   memoryLimit: '2Gi',  ephemeralStorage: '4Gi' },
                      }
                      const p = presets[e.target.value]
                      if (p) form.setFieldsValue(p)
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
                {resourcePreset === 'custom' ? (
                  <div className="grid grid-cols-2 gap-x-3 gap-y-2 pt-1 border-t border-gray-100">
                    <Form.Item name="cpuRequest" label="CPU Request" className="mb-0" initialValue="250m">
                      <Input size="small" className="font-mono text-xs" />
                    </Form.Item>
                    <Form.Item name="cpuLimit" label="CPU Limit" className="mb-0" initialValue="500m">
                      <Input size="small" className="font-mono text-xs" />
                    </Form.Item>
                    <Form.Item name="memoryRequest" label="Memory Request" className="mb-0" initialValue="256Mi">
                      <Input size="small" className="font-mono text-xs" />
                    </Form.Item>
                    <Form.Item name="memoryLimit" label="Memory Limit" className="mb-0" initialValue="512Mi">
                      <Input size="small" className="font-mono text-xs" />
                    </Form.Item>
                    <Form.Item name="ephemeralStorage" label="临时存储" className="mb-0" initialValue="1Gi">
                      <Input size="small" className="font-mono text-xs" />
                    </Form.Item>
                  </div>
                ) : (
                  <>
                    <Form.Item name="cpuRequest" hidden initialValue="250m"><Input /></Form.Item>
                    <Form.Item name="cpuLimit" hidden initialValue="500m"><Input /></Form.Item>
                    <Form.Item name="memoryRequest" hidden initialValue="256Mi"><Input /></Form.Item>
                    <Form.Item name="memoryLimit" hidden initialValue="512Mi"><Input /></Form.Item>
                    <Form.Item name="ephemeralStorage" hidden initialValue="1Gi"><Input /></Form.Item>
                  </>
                )}
              </div>
            </div>

            {/* ── 参数值配置 ── */}
            {extraParams.length > 0 && (
              <div className="rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                  <span className="text-xs font-medium text-gray-600">参数值配置</span>
                  <span className="text-[10px] text-gray-400 ml-2">部署时注入的环境变量 / 请求参数</span>
                </div>
                <div className="p-4 space-y-3">
                  {extraParams.map((p) => (
                    <div key={p.key}>
                      <div className="flex items-center gap-1.5 mb-1">
                        <span className="text-xs font-mono text-gray-700">{p.name}</span>
                        {p.required && <span className="text-red-400 text-[10px]">*</span>}
                        <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">{p.position}</Tag>
                      </div>
                      {p.description && <div className="text-[10px] text-gray-400 mb-1">{p.description}</div>}
                      <Input
                        size="small"
                        placeholder={p.example || `请输入 ${p.name}`}
                        value={adminParamValues[p.name] || ''}
                        onChange={(e) => setAdminParamValues(prev => ({ ...prev, [p.name]: e.target.value }))}
                        className="font-mono text-xs"
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="border border-dashed border-gray-200 rounded-lg py-12 text-center">
            <CloudServerOutlined className="text-2xl text-gray-300 mb-2" />
            <div className="text-xs text-gray-400">不需要沙箱托管，用户将自行配置连接方式</div>
          </div>
        )}
      </>
    )
  }

  // ==================== Step 4: 服务介绍 ====================
  const renderServiceIntro = () => (
    <>
      <div className="flex items-center justify-between mb-3">
        <div>
          <div className="text-sm font-medium text-gray-700">服务详情文档</div>
          <div className="text-xs text-gray-400 mt-0.5">使用 Markdown 语法编写，发布后将渲染为富文本</div>
        </div>
        <Tag color="blue" className="m-0 border-0">Markdown</Tag>
      </div>
      <Form.Item name="serviceIntro" className="mb-0">
        <Input.TextArea
          placeholder={`# 服务介绍\n\n简要描述你的 MCP Server...\n\n## 功能特性\n\n- 特性一\n- 特性二\n\n## 使用方式\n\n\`\`\`bash\nnpx -y @your/mcp-server\n\`\`\`\n\n## 注意事项\n\n> 请确保已配置必要的环境变量`}
          autoSize={{ minRows: 16, maxRows: 22 }}
          className="font-mono text-xs"
        />
      </Form.Item>
    </>
  )

  const stepContent = sandboxRequired
    ? [renderBasicInfo, renderMcpConfig, renderServiceIntro, renderSandboxConfig]
    : [renderBasicInfo, renderMcpConfig, renderServiceIntro]

  const navItems = sandboxRequired ? NAV_ITEMS_FULL : NAV_ITEMS_SHORT
  const isLastStep = currentStep === navItems.length - 1

  return (
    <Modal
      open={visible}
      onCancel={handleCancel}
      maskClosable={false}
      width={820}
      footer={null}
      closable={false}
      styles={{
        body: { padding: 0 },
        header: { display: 'none' },
      }}
    >
      <div className="flex" style={{ minHeight: 540 }}>
        {/* 左侧导航 */}
        <div className="w-52 bg-gray-50/80 border-r border-gray-100 p-5 flex flex-col flex-shrink-0">
          <div className="mb-6">
            <h3 className="text-base font-semibold text-gray-800 leading-tight">配置 MCP Server</h3>
            <p className="text-xs text-gray-400 mt-1.5 leading-relaxed">自定义数据源配置</p>
          </div>

          <nav className="flex-1">
            {navItems.map((item, idx) => {
              const isActive = currentStep === item.key
              const isDone = completedSteps.has(item.key)
              const isLast = idx === navItems.length - 1
              return (
                <div key={item.key} className="flex gap-3" onClick={() => navigateTo(item.key)}>
                  {/* 左侧：圆点 + 连线 */}
                  <div className="flex flex-col items-center flex-shrink-0">
                    <div
                      className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium transition-all duration-200 cursor-pointer ${
                        isActive
                          ? 'bg-blue-500 text-white shadow-sm shadow-blue-200'
                          : isDone
                            ? 'bg-green-500 text-white'
                            : 'bg-gray-200 text-gray-500'
                      }`}
                    >
                      {isDone && !isActive ? <CheckCircleFilled className="text-xs" /> : idx + 1}
                    </div>
                    {!isLast && (
                      <div className={`w-0.5 flex-1 my-1 min-h-[28px] rounded-full transition-colors duration-300 ${
                        isDone ? 'bg-green-300' : 'bg-gray-200'
                      }`} />
                    )}
                  </div>
                  {/* 右侧：文字 */}
                  <div className={`pt-0.5 pb-4 cursor-pointer ${isLast ? '' : ''}`}>
                    <div className={`text-sm leading-tight ${isActive ? 'font-semibold text-gray-900' : isDone ? 'font-medium text-gray-700' : 'text-gray-500'}`}>
                      {item.label}
                    </div>
                    <div className="text-[11px] text-gray-400 mt-0.5 leading-tight">{item.desc}</div>
                  </div>
                </div>
              )
            })}
          </nav>


        </div>

        {/* 右侧内容 */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* 顶部标题栏 */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
            <div className="flex items-center gap-2">
              <span className="text-gray-400">{navItems[currentStep].icon}</span>
              <span className="text-sm font-medium text-gray-800">{navItems[currentStep].label}</span>
              <span className="text-xs text-gray-400">— {navItems[currentStep].desc}</span>
            </div>
            <Button type="text" size="small" onClick={handleCancel} className="text-gray-400 hover:text-gray-600">
              ✕
            </Button>
          </div>

          {/* 表单内容 */}
          <div className="flex-1 overflow-auto px-6 py-5">
            <Form form={form} layout="vertical" requiredMark={false}>
              {/* 保持 sandboxRequired 字段始终挂载，避免 useWatch 在 Form.Item 卸载后返回 undefined */}
              <Form.Item name="sandboxRequired" hidden><input type="hidden" /></Form.Item>
              {stepContent[currentStep]()}
            </Form>
          </div>

          {/* 底部操作栏 */}
          <div className="border-t border-gray-100">
            {/* 部署进度条 - 仅在提交沙箱部署时显示 */}
            {submitting && deployStep >= 0 && (
              <div className="px-6 pt-3 pb-1">
                <Steps
                  size="small"
                  current={deployStep}
                  status={!submitting && deployStep >= 0 && deployStep < 2 ? 'error' : 'process'}
                  items={DEPLOY_STEPS.map((step, idx) => ({
                    title: <span className="text-xs">{step.title}</span>,
                    icon: submitting && idx === deployStep ? <LoadingOutlined /> : undefined,
                  }))}
                />
              </div>
            )}
            {/* 部署失败提示 */}
            {!submitting && deployStep >= 0 && deployStep < 2 && (
              <div className="px-6 pt-3 pb-1">
                <Steps
                  size="small"
                  current={deployStep}
                  status="error"
                  items={DEPLOY_STEPS.map((step) => ({
                    title: <span className="text-xs">{step.title}</span>,
                  }))}
                />
              </div>
            )}
            <div className="flex items-center justify-between px-6 py-3">
              <div>
                {currentStep > 0 && !submitting ? (
                  <Button onClick={() => setCurrentStep(currentStep - 1)}>上一步</Button>
                ) : <span />}
              </div>
              <Space>
                <Button onClick={handleCancel} disabled={submitting}>取消</Button>
                {!isLastStep ? (
                  <Button type="primary" onClick={handleNext}>下一步</Button>
                ) : (
                  <>
                    {sandboxRequired && (
                      <Button onClick={handleSaveOnly} disabled={submitting}>
                        仅保存配置
                      </Button>
                    )}
                    <Button type="primary" onClick={() => handleSubmit(true)} loading={submitting} disabled={submitting}>
                      {submitting ? '处理中...' : (sandboxRequired ? '保存并部署' : '保存配置')}
                    </Button>
                  </>
                )}
              </Space>
            </div>
          </div>
        </div>
      </div>

      {/* 添加/编辑参数弹窗 */}
      <Modal
        title={editingParamKey ? '编辑参数' : '添加参数'}
        open={paramModalVisible}
        onCancel={() => { setParamModalVisible(false); paramForm.resetFields(); setEditingParamKey(null) }}
        onOk={() => {
          paramForm.validateFields().then((values) => {
            if (editingParamKey) {
              setExtraParams((prev) => prev.map((p) => p.key === editingParamKey ? { ...values, key: editingParamKey } : p))
            } else {
              setExtraParams((prev) => [...prev, { ...values, key: `param_${Date.now()}` }])
            }
            setParamModalVisible(false)
            paramForm.resetFields()
            setEditingParamKey(null)
          })
        }}
        okText="确定"
        cancelText="取消"
        width={480}
        destroyOnClose
      >
        <Form form={paramForm} layout="vertical" className="mt-4">
          <Form.Item
            name="name"
            label="参数名"
            rules={[{ required: true, message: '请输入参数名' }]}
          >
            <Input placeholder={protocolType === 'stdio' ? '例如: API_KEY' : '例如: Authorization'} />
          </Form.Item>
          <Form.Item
            name="position"
            label="参数位置"
            initialValue={protocolType === 'stdio' ? 'env' : 'header'}
            rules={[{ required: true, message: '请选择参数位置' }]}
          >
            <Select>
              {protocolType === 'stdio' ? (
                <Select.Option value="env">环境变量 (env)</Select.Option>
              ) : (
                <>
                  <Select.Option value="header">请求头 (header)</Select.Option>
                  <Select.Option value="query">查询参数 (query)</Select.Option>
                </>
              )}
            </Select>
          </Form.Item>
          <Form.Item name="required" label="是否必填" valuePropName="checked" initialValue={false}>
            <Switch checkedChildren="必填" unCheckedChildren="可选" />
          </Form.Item>
          <Form.Item name="description" label="参数说明">
            <Input.TextArea placeholder="描述该参数的用途" rows={2} autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
          <Form.Item name="example" label="参数示例">
            <Input placeholder={protocolType === 'stdio' ? '例如: sk-xxxxxxxxxxxx' : '例如: Bearer sk-xxx'} />
          </Form.Item>
        </Form>
      </Modal>
    </Modal>
  )
}
