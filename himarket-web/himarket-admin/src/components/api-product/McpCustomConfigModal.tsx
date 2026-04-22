import {
  InfoCircleOutlined,
  SettingOutlined,
  FileTextOutlined,
  CloudServerOutlined,
  PlusOutlined,
  CheckCircleFilled,
  CodeOutlined,
  GlobalOutlined,
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import {
  Modal,
  Form,
  Input,
  Button,
  Tag,
  Radio,
  Space,
  Select,
  Switch,
  Table,
  message,
  Steps,
} from 'antd';
import { useState, useEffect } from 'react';

import { sandboxApi } from '@/lib/api';
import type { ProductIcon } from '@/types/api-product';

interface McpCustomConfigModalProps {
  visible: boolean;
  onCancel: () => void;
  onOk: (values: unknown) => void | Promise<void>;
  productName?: string;
  productDescription?: string;
  productIcon?: ProductIcon;
  productDocument?: string;
  /** 编辑模式：传入已有 MCP 元数据，表单将预填这些值 */
  initialMcpMeta?: {
    mcpName?: string;
    displayName?: string;
    description?: string;
    protocolType?: string;
    connectionConfig?: string;
    tags?: string;
    icon?: string;
    repoUrl?: string;
    extraParams?: string;
    serviceIntro?: string;
    sandboxRequired?: boolean;
  } | null;
}

interface ExtraParam {
  key: string;
  name: string;
  position: string;
  required: boolean;
  description: string;
  example: string;
}

const NAV_ITEMS_FULL = [
  { desc: '名称、仓库、标签', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '协议与连接方式', icon: <SettingOutlined />, key: 1, label: 'MCP 配置' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
  { desc: '沙箱与参数配置', icon: <CloudServerOutlined />, key: 3, label: '沙箱部署' },
];

const NAV_ITEMS_SHORT = [
  { desc: '名称、仓库、标签', icon: <InfoCircleOutlined />, key: 0, label: '基础信息' },
  { desc: '协议与连接方式', icon: <SettingOutlined />, key: 1, label: 'MCP 配置' },
  { desc: 'Markdown 文档', icon: <FileTextOutlined />, key: 2, label: '服务介绍' },
];

export function McpCustomConfigModal({
  initialMcpMeta,
  onCancel,
  onOk,
  productDescription,
  productDocument,
  productIcon,
  productName,
  visible,
}: McpCustomConfigModalProps) {
  const [form] = Form.useForm();
  const [currentStep, setCurrentStep] = useState(0);
  const [tagInput, setTagInput] = useState('');
  const [completedSteps, setCompletedSteps] = useState<Set<number>>(new Set());
  const [extraParams, setExtraParams] = useState<ExtraParam[]>([]);
  const [paramModalVisible, setParamModalVisible] = useState(false);
  const [paramForm] = Form.useForm();
  const [editingParamKey, setEditingParamKey] = useState<string | null>(null);
  const [adminParamValues, setAdminParamValues] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [deployStep, setDeployStep] = useState(-1); // -1=未开始, 0=保存配置, 1=部署沙箱, 2=获取工具

  const protocolType: string = Form.useWatch('protocolType', form) || 'sse';
  const sandboxRequired: boolean = Form.useWatch('sandboxRequired', form) ?? true;
  const watchedTags: string[] = Form.useWatch('tags', form) || [];
  const resourcePreset: string = Form.useWatch('resourcePreset', form) || 'small';

  // 编辑模式判断：initialMcpMeta 存在且包含 mcpName 时为编辑模式
  const isEditMode = !!initialMcpMeta?.mcpName;

  // 关闭沙箱托管时，如果当前在第四步则自动回退到第三步
  useEffect(() => {
    if (!sandboxRequired && currentStep === 3) {
      setCurrentStep(2);
    }
  }, [sandboxRequired, currentStep]);

  const [sandboxList, setSandboxList] = useState<unknown[]>([]);
  const [sandboxLoading, setSandboxLoading] = useState(false);
  const [namespaceList, setNamespaceList] = useState<string[]>([]);
  const [namespaceLoading, setNamespaceLoading] = useState(false);

  useEffect(() => {
    if (visible && sandboxRequired) {
      setSandboxLoading(true);
      sandboxApi
        .getActiveSandboxes()
        .then((res: unknown) => {
          const list = (res as { data?: unknown[] }).data || [];
          setSandboxList(Array.isArray(list) ? list : []);
        })
        .catch(() => setSandboxList([]))
        .finally(() => setSandboxLoading(false));
    }
  }, [visible, sandboxRequired]);

  // 打开弹窗时自动填充产品信息到展示字段（只读）
  useEffect(() => {
    if (visible) {
      const formValues: Record<string, unknown> = {
        description: productDescription || '',
        mcpDisplayName: productName || '',
        sandboxRequired: true,
        serviceIntro: productDocument || '',
      };

      // 编辑模式：预填已有 MCP 元数据
      if (initialMcpMeta) {
        if (initialMcpMeta.mcpName) formValues.mcpServerName = initialMcpMeta.mcpName;
        if (initialMcpMeta.displayName) formValues.mcpDisplayName = initialMcpMeta.displayName;
        if (initialMcpMeta.description) formValues.description = initialMcpMeta.description;
        if (initialMcpMeta.protocolType) formValues.protocolType = initialMcpMeta.protocolType;
        if (initialMcpMeta.connectionConfig)
          formValues.mcpConfigJson = initialMcpMeta.connectionConfig;
        if (initialMcpMeta.repoUrl) formValues.repoUrl = initialMcpMeta.repoUrl;
        if (initialMcpMeta.serviceIntro) formValues.serviceIntro = initialMcpMeta.serviceIntro;
        formValues.sandboxRequired = initialMcpMeta.sandboxRequired ?? true;

        // 解析 tags JSON
        if (initialMcpMeta.tags) {
          try {
            const tags = JSON.parse(initialMcpMeta.tags);
            if (Array.isArray(tags)) formValues.tags = tags;
          } catch {
            /* ignore */
          }
        }

        // 解析 extraParams JSON
        if (initialMcpMeta.extraParams) {
          try {
            const params = JSON.parse(initialMcpMeta.extraParams);
            if (Array.isArray(params)) {
              setExtraParams(
                (params as unknown[]).map((p: unknown, i: number) => {
                  const param = p as Record<string, unknown>;
                  return {
                    description: (param.description as string) || '',
                    example: (param.example as string) || '',
                    key: (param.name as string) || `param-${i}`,
                    name: (param.name as string) || '',
                    position: (param.position as string) || 'env',
                    required: (param.required as boolean) ?? false,
                  };
                }),
              );
            }
          } catch {
            /* ignore */
          }
        }
      }

      form.setFieldsValue(formValues);
    }
  }, [visible, productName, productDescription, productDocument, initialMcpMeta, form]);

  const handleAddTag = () => {
    const val = tagInput.trim();
    if (!val) return;
    const tags: string[] = form.getFieldValue('tags') || [];
    if (tags.includes(val)) {
      message.warning('标签已存在');
      return;
    }
    form.setFieldsValue({ tags: [...tags, val] });
    setTagInput('');
  };

  const handleRemoveTag = (tag: string) => {
    const tags: string[] = form.getFieldValue('tags') || [];
    form.setFieldsValue({ tags: tags.filter((t) => t !== tag) });
  };

  const resetAll = () => {
    form.resetFields();
    setCurrentStep(0);
    setTagInput('');
    setCompletedSteps(new Set());
    setExtraParams([]);
    setEditingParamKey(null);
    setAdminParamValues({});
    setSubmitting(false);
    setDeployStep(-1);
    setNamespaceList([]);
    setNamespaceLoading(false);
  };

  const handleCancel = () => {
    resetAll();
    onCancel();
  };

  const stepFields: string[][] = [
    ['mcpServerName'],
    ['mcpConfigJson'],
    [], // 服务介绍 - 无必填
    [], // 沙箱部署 - 按钮级别校验
  ];

  const navigateTo = async (target: number) => {
    // 从当前步跳转时，先校验当前步
    if (target > currentStep) {
      try {
        await form.validateFields(stepFields[currentStep]);
        setCompletedSteps((prev) => new Set(prev).add(currentStep));
      } catch {
        return;
      }
    }
    setCurrentStep(target);
  };

  const handleNext = async () => {
    try {
      await form.validateFields(stepFields[currentStep]);
      setCompletedSteps((prev) => new Set(prev).add(currentStep));
      setCurrentStep(currentStep + 1);
    } catch {
      /* validation failed */
    }
  };

  const handleSubmit = async (withDeploy: boolean) => {
    // "保存并部署" 需要校验沙箱配置字段
    if (withDeploy && sandboxRequired) {
      try {
        await form.validateFields(['sandboxId', 'namespace']);
      } catch {
        return;
      }
    }
    const values = form.getFieldsValue(true);
    // 标记 deployNow 供 onOk 回调判断
    values.deployNow = withDeploy && sandboxRequired;
    setCompletedSteps((prev) => new Set(prev).add(3));
    setSubmitting(true);
    if (values.deployNow) {
      setDeployStep(0);
    }
    try {
      await onOk({ ...values, adminParamValues, extraParams });
      if (values.deployNow) {
        setDeployStep(2);
      }
      message.success(values.deployNow ? '保存并部署成功' : '配置已保存');
      resetAll();
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string };
      const msg = err?.response?.data?.message || err?.message || '保存失败';
      if (values.deployNow) {
        if (msg.includes('部署沙箱') || msg.includes('创建连接')) setDeployStep(1);
      }
      message.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSaveOnly = () => {
    if (!sandboxRequired) {
      // 不需要沙箱，直接保存
      handleSubmit(false);
      return;
    }
    // 需要沙箱但跳过部署，弹窗提醒
    Modal.confirm({
      cancelText: '取消',
      content: '沙箱尚未部署，保存后可在 MCP 配置信息页面点击「部署到沙箱」按钮手动部署。',
      okText: '确认保存',
      onOk: () => handleSubmit(false),
      title: '仅保存配置',
    });
  };

  const DEPLOY_STEPS = [{ title: '保存配置' }, { title: '部署到沙箱' }];

  // ==================== Step 1: 基础信息 ====================
  const renderBasicInfo = () => (
    <>
      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          label={
            <span>
              MCP 英文名称 <span className="text-xs text-gray-400 font-normal ml-1">唯一标识</span>
            </span>
          }
          name="mcpServerName"
          rules={[
            { message: '请输入 MCP 英文名称', required: true },
            { message: '小写字母开头，仅含小写字母、数字、连字符', pattern: /^[a-z][a-z0-9-]*$/ },
            { max: 63, message: '不超过 63 个字符' },
          ]}
        >
          <Input disabled={isEditMode} placeholder="weather-mcp-server" />
        </Form.Item>
        <Form.Item label="MCP 中文名称" name="mcpDisplayName">
          <Input disabled />
        </Form.Item>
      </div>

      <Form.Item label="描述" name="description">
        <Input.TextArea autoSize={{ maxRows: 4, minRows: 2 }} disabled rows={2} />
      </Form.Item>

      <Form.Item
        label="仓库地址"
        name="repoUrl"
        rules={[{ message: '请输入合法的 URL', type: 'url' }]}
      >
        <Input placeholder="https://github.com/org/mcp-server" />
      </Form.Item>

      {/* 标签 */}
      <Form.Item label="自定义标签">
        <Form.Item hidden name="tags">
          <Input />
        </Form.Item>
        <div>
          <div className="flex items-center gap-2 mb-2">
            <Input
              onChange={(e) => setTagInput(e.target.value)}
              onPressEnter={(e) => {
                e.preventDefault();
                handleAddTag();
              }}
              placeholder="输入后按回车添加"
              size="small"
              suffix={
                <PlusOutlined
                  className="text-gray-400 hover:text-blue-500 cursor-pointer transition-colors"
                  onClick={handleAddTag}
                />
              }
              value={tagInput}
            />
          </div>
          <div className="flex flex-wrap gap-1.5 min-h-[24px]">
            {watchedTags.map((tag: string) => (
              <Tag
                className="m-0"
                closable
                color="blue"
                key={tag}
                onClose={() => handleRemoveTag(tag)}
              >
                {tag}
              </Tag>
            ))}
            {!watchedTags.length && <span className="text-xs text-gray-300">暂无标签</span>}
          </div>
        </div>
      </Form.Item>

      {/* Icon（继承自产品，只读展示） */}
      <Form.Item label="Icon">
        <div className="w-16 h-16 rounded-xl border-2 border-dashed border-gray-200 flex items-center justify-center flex-shrink-0">
          {productIcon ? (
            <img
              alt="icon"
              className="w-full h-full object-cover rounded-[10px]"
              src={productIcon.value}
            />
          ) : (
            <span className="text-xs text-gray-300">无图标</span>
          )}
        </div>
      </Form.Item>
    </>
  );

  // ==================== Step 2: MCP 配置 ====================
  const renderMcpConfig = () => {
    const protocols = [
      { icon: <CodeOutlined />, key: 'stdio', label: 'Stdio' },
      { icon: <ApiOutlined />, key: 'sse', label: 'SSE' },
      { icon: <GlobalOutlined />, key: 'http', label: 'Streamable HTTP' },
    ];
    const isStdio = protocolType === 'stdio';
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
}`;

    return (
      <>
        {/* 协议选择卡片 */}
        <Form.Item
          initialValue="sse"
          label="协议类型"
          name="protocolType"
          rules={[{ required: true }]}
        >
          <div className="grid grid-cols-3 gap-2">
            {protocols.map((p) => {
              const selected = protocolType === p.key;
              return (
                <div
                  className={`relative flex items-center gap-2 rounded-lg border px-3 py-2.5 cursor-pointer transition-all duration-150 ${
                    selected
                      ? 'border-blue-500 bg-blue-50/50 ring-1 ring-blue-500/20'
                      : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
                  }`}
                  key={p.key}
                  onClick={() => {
                    form.setFieldsValue({
                      protocolType: p.key,
                      ...(p.key === 'stdio' ? { sandboxRequired: true } : {}),
                    });
                    setExtraParams([]);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      form.setFieldsValue({
                        protocolType: p.key,
                        ...(p.key === 'stdio' ? { sandboxRequired: true } : {}),
                      });
                      setExtraParams([]);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
                  {selected && (
                    <CheckCircleFilled className="absolute top-1.5 right-1.5 text-blue-500 text-[10px]" />
                  )}
                  <span className={`text-sm ${selected ? 'text-blue-500' : 'text-gray-400'}`}>
                    {p.icon}
                  </span>
                  <span
                    className={`text-xs font-medium ${selected ? 'text-blue-700' : 'text-gray-600'}`}
                  >
                    {p.label}
                  </span>
                </div>
              );
            })}
          </div>
        </Form.Item>

        {/* MCP 连接配置 JSON */}
        <Form.Item
          label="MCP 连接配置"
          name="mcpConfigJson"
          rules={[
            { message: '请输入 MCP 配置', required: true },
            {
              validator: (_, value) => {
                if (!value) return Promise.resolve();
                try {
                  JSON.parse(value);
                  return Promise.resolve();
                } catch {
                  return Promise.reject('请输入合法的 JSON 格式');
                }
              },
            },
          ]}
        >
          <Input.TextArea
            autoSize={{ maxRows: 14, minRows: 8 }}
            className="font-mono text-xs"
            placeholder={exampleJson}
          />
        </Form.Item>

        {/* 解析 JSON 按钮 */}
        <div className="flex items-center gap-3 -mt-3 mb-4">
          <Button
            ghost
            icon={<CodeOutlined />}
            onClick={() => {
              const raw = form.getFieldValue('mcpConfigJson');
              if (!raw) {
                message.warning('请先粘贴 JSON 配置');
                return;
              }
              try {
                const parsed = JSON.parse(raw);
                const servers = parsed.mcpServers || parsed;
                const serverKey = Object.keys(servers)[0];
                if (!serverKey) {
                  message.error('未找到有效的 MCP Server 配置');
                  return;
                }
                const cfg = servers[serverKey];

                if (cfg.command) {
                  form.setFieldsValue({ protocolType: 'stdio', sandboxRequired: true });
                } else if (
                  cfg.type === 'sse' ||
                  (!cfg.type && cfg.url && cfg.url.endsWith('/sse'))
                ) {
                  form.setFieldsValue({ protocolType: 'sse' });
                } else if (cfg.type === 'streamable-http') {
                  form.setFieldsValue({ protocolType: 'http' });
                } else {
                  // 有 url 但无法确定具体协议，默认 sse
                  form.setFieldsValue({ protocolType: cfg.url ? 'sse' : 'http' });
                }

                const params: ExtraParam[] = [];
                if (cfg.env && typeof cfg.env === 'object') {
                  Object.entries(cfg.env).forEach(([k, v]) => {
                    params.push({
                      description: '',
                      example: String(v),
                      key: `param_${Date.now()}_${k}`,
                      name: k,
                      position: 'env',
                      required: true,
                    });
                  });
                }
                if (cfg.headers && typeof cfg.headers === 'object') {
                  Object.entries(cfg.headers).forEach(([k, v]) => {
                    params.push({
                      description: '',
                      example: String(v),
                      key: `param_${Date.now()}_${k}`,
                      name: k,
                      position: 'header',
                      required: true,
                    });
                  });
                }
                if (cfg.query && typeof cfg.query === 'object') {
                  Object.entries(cfg.query).forEach(([k, v]) => {
                    params.push({
                      description: '',
                      example: String(v),
                      key: `param_${Date.now()}_${k}`,
                      name: k,
                      position: 'query',
                      required: true,
                    });
                  });
                }

                // 保留原始 JSON 不做修改，只提取参数用于展示
                setExtraParams(params);
                message.success(`已解析：${serverKey}，识别到 ${params.length} 个参数`);
              } catch {
                message.error('JSON 解析失败，请检查格式');
              }
            }}
            size="small"
            type="primary"
          >
            解析 JSON
          </Button>
          <span className="text-xs text-gray-400">粘贴 JSON 后点击可自动解析协议类型及参数</span>
        </div>

        {/* 额外参数配置 - 列表 */}
        <div className="mt-1">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm text-gray-700">
              {isStdio ? '环境变量配置' : '请求参数配置'}
            </span>
            <Space size={8}>
              <Button
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingParamKey(null);
                  paramForm.resetFields();
                  setParamModalVisible(true);
                }}
                size="small"
                type="dashed"
              >
                添加{isStdio ? '变量' : '参数'}
              </Button>
              {extraParams.length > 0 && (
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => setExtraParams([])}
                  size="small"
                  type="text"
                >
                  清除所有
                </Button>
              )}
            </Space>
          </div>
          {extraParams.length > 0 ? (
            <Table
              className="border border-gray-100 rounded-lg overflow-hidden"
              columns={[
                {
                  dataIndex: 'name',
                  render: (v: string) => <span className="font-mono text-xs">{v}</span>,
                  title: '参数名',
                  width: 120,
                },
                {
                  dataIndex: 'position',
                  render: (v: string) => (
                    <Tag className="m-0 border-0 bg-gray-100 text-gray-600 text-xs">{v}</Tag>
                  ),
                  title: '位置',
                  width: 80,
                },
                {
                  align: 'center' as const,
                  dataIndex: 'required',
                  render: (v: boolean) =>
                    v ? (
                      <Tag className="m-0 border-0 text-xs" color="red">
                        是
                      </Tag>
                    ) : (
                      <span className="text-xs text-gray-400">否</span>
                    ),
                  title: '必填',
                  width: 50,
                },
                {
                  dataIndex: 'description',
                  ellipsis: true,
                  render: (v: string) => <span className="text-xs text-gray-500">{v || '-'}</span>,
                  title: '说明',
                },
                {
                  align: 'center' as const,
                  render: (_: unknown, record: ExtraParam) => (
                    <Space size={4}>
                      <Button
                        className="text-gray-400 hover:text-blue-500"
                        icon={<EditOutlined />}
                        onClick={() => {
                          setEditingParamKey(record.key);
                          paramForm.setFieldsValue(record);
                          setParamModalVisible(true);
                        }}
                        size="small"
                        type="text"
                      />
                      <Button
                        className="text-gray-400 hover:text-red-500"
                        icon={<DeleteOutlined />}
                        onClick={() =>
                          setExtraParams((prev) => prev.filter((p) => p.key !== record.key))
                        }
                        size="small"
                        type="text"
                      />
                    </Space>
                  ),
                  title: '',
                  width: 60,
                },
              ]}
              dataSource={extraParams}
              pagination={false}
              rowKey="key"
              size="small"
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
            disabled={isStdio}
            onChange={(checked) => form.setFieldsValue({ sandboxRequired: checked })}
            unCheckedChildren="关闭"
          />
        </div>
      </>
    );
  };

  // ==================== Step 3: 沙箱部署 ====================

  const handleSandboxChange = async (sandboxId: string) => {
    setNamespaceList([]);
    form.setFieldsValue({ namespace: undefined });
    setNamespaceLoading(true);
    try {
      const res: unknown = await sandboxApi.listNamespaces(sandboxId);
      const list = (res as { data?: unknown[] }).data || res || [];
      setNamespaceList(Array.isArray(list) ? list : []);
    } catch {
      message.error('获取 Namespace 列表失败');
      setNamespaceList([]);
    } finally {
      setNamespaceLoading(false);
    }
  };

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
                  className="mb-0"
                  label="沙箱实例"
                  name="sandboxId"
                  rules={[{ message: '请选择沙箱实例', required: true }]}
                >
                  <Select
                    loading={sandboxLoading}
                    onChange={handleSandboxChange}
                    options={sandboxList.map((s: unknown) => {
                      const sb = s as { sandboxName: string; sandboxId: string };
                      return {
                        label: sb.sandboxName,
                        value: sb.sandboxId,
                      };
                    })}
                    placeholder="选择沙箱实例"
                  />
                </Form.Item>
                <Form.Item
                  className="mb-0"
                  extra={
                    !form.getFieldValue('sandboxId') ? (
                      <span className="text-[10px] text-gray-400">请先选择沙箱实例</span>
                    ) : undefined
                  }
                  label="Namespace"
                  name="namespace"
                  rules={[{ message: '请选择 Namespace', required: true }]}
                >
                  <Select
                    disabled={!form.getFieldValue('sandboxId')}
                    loading={namespaceLoading}
                    options={namespaceList.map((ns) => ({ label: ns, value: ns }))}
                    placeholder={namespaceLoading ? '加载中...' : '选择 Namespace'}
                    showSearch
                  />
                </Form.Item>
                <Form.Item
                  className="mb-0"
                  initialValue="sse"
                  label="传输协议"
                  name="transportType"
                >
                  <Radio.Group buttonStyle="solid" optionType="button" size="small">
                    <Radio.Button value="sse">SSE</Radio.Button>
                    <Radio.Button value="http">Streamable HTTP</Radio.Button>
                  </Radio.Group>
                </Form.Item>
                <Form.Item className="mb-0" initialValue="none" label="鉴权方式" name="authType">
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
                <Form.Item
                  className={resourcePreset === 'custom' ? 'mb-3' : 'mb-0'}
                  initialValue="small"
                  name="resourcePreset"
                >
                  <Radio.Group
                    className="w-full"
                    onChange={(e) => {
                      const presets: Record<string, unknown> = {
                        large: {
                          cpuLimit: '2',
                          cpuRequest: '1',
                          ephemeralStorage: '4Gi',
                          memoryLimit: '2Gi',
                          memoryRequest: '1Gi',
                        },
                        medium: {
                          cpuLimit: '1',
                          cpuRequest: '500m',
                          ephemeralStorage: '2Gi',
                          memoryLimit: '1Gi',
                          memoryRequest: '512Mi',
                        },
                        small: {
                          cpuLimit: '500m',
                          cpuRequest: '250m',
                          ephemeralStorage: '1Gi',
                          memoryLimit: '512Mi',
                          memoryRequest: '256Mi',
                        },
                      };
                      const p = presets[e.target.value] as Record<string, string> | undefined;
                      if (p) form.setFieldsValue(p);
                    }}
                  >
                    <div className="grid grid-cols-4 gap-2">
                      {[
                        { desc: '0.5C / 512Mi', label: '小型', value: 'small' },
                        { desc: '1C / 1Gi', label: '中型', value: 'medium' },
                        { desc: '2C / 2Gi', label: '大型', value: 'large' },
                        { desc: '手动配置', label: '自定义', value: 'custom' },
                      ].map((item) => (
                        <Radio.Button
                          className="h-auto text-center flex-1"
                          key={item.value}
                          style={{ lineHeight: 1.3, padding: '8px 0' }}
                          value={item.value}
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
                    <Form.Item
                      className="mb-0"
                      initialValue="250m"
                      label="CPU Request"
                      name="cpuRequest"
                    >
                      <Input className="font-mono text-xs" size="small" />
                    </Form.Item>
                    <Form.Item
                      className="mb-0"
                      initialValue="500m"
                      label="CPU Limit"
                      name="cpuLimit"
                    >
                      <Input className="font-mono text-xs" size="small" />
                    </Form.Item>
                    <Form.Item
                      className="mb-0"
                      initialValue="256Mi"
                      label="Memory Request"
                      name="memoryRequest"
                    >
                      <Input className="font-mono text-xs" size="small" />
                    </Form.Item>
                    <Form.Item
                      className="mb-0"
                      initialValue="512Mi"
                      label="Memory Limit"
                      name="memoryLimit"
                    >
                      <Input className="font-mono text-xs" size="small" />
                    </Form.Item>
                    <Form.Item
                      className="mb-0"
                      initialValue="1Gi"
                      label="临时存储"
                      name="ephemeralStorage"
                    >
                      <Input className="font-mono text-xs" size="small" />
                    </Form.Item>
                  </div>
                ) : (
                  <>
                    <Form.Item hidden initialValue="250m" name="cpuRequest">
                      <Input />
                    </Form.Item>
                    <Form.Item hidden initialValue="500m" name="cpuLimit">
                      <Input />
                    </Form.Item>
                    <Form.Item hidden initialValue="256Mi" name="memoryRequest">
                      <Input />
                    </Form.Item>
                    <Form.Item hidden initialValue="512Mi" name="memoryLimit">
                      <Input />
                    </Form.Item>
                    <Form.Item hidden initialValue="1Gi" name="ephemeralStorage">
                      <Input />
                    </Form.Item>
                  </>
                )}
              </div>
            </div>

            {/* ── 参数值配置 ── */}
            {extraParams.length > 0 && (
              <div className="rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                  <span className="text-xs font-medium text-gray-600">参数值配置</span>
                  <span className="text-[10px] text-gray-400 ml-2">
                    部署时注入的环境变量 / 请求参数
                  </span>
                </div>
                <div className="p-4 space-y-3">
                  {extraParams.map((p) => (
                    <div key={p.key}>
                      <div className="flex items-center gap-1.5 mb-1">
                        <span className="text-xs font-mono text-gray-700">{p.name}</span>
                        {p.required && <span className="text-red-400 text-[10px]">*</span>}
                        <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">
                          {p.position}
                        </Tag>
                      </div>
                      {p.description && (
                        <div className="text-[10px] text-gray-400 mb-1">{p.description}</div>
                      )}
                      <Input
                        className="font-mono text-xs"
                        onChange={(e) =>
                          setAdminParamValues((prev) => ({ ...prev, [p.name]: e.target.value }))
                        }
                        placeholder={p.example || `请输入 ${p.name}`}
                        size="small"
                        value={adminParamValues[p.name] || ''}
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
    );
  };

  // ==================== Step 4: 服务介绍 ====================
  const renderServiceIntro = () => (
    <>
      <div className="flex items-center justify-between mb-3">
        <div>
          <div className="text-sm font-medium text-gray-700">服务详情文档</div>
          <div className="text-xs text-gray-400 mt-0.5">
            使用 Markdown 语法编写，发布后将渲染为富文本
          </div>
        </div>
        <Tag className="m-0 border-0" color="blue">
          Markdown
        </Tag>
      </div>
      <Form.Item className="mb-0" name="serviceIntro">
        <Input.TextArea
          autoSize={{ maxRows: 22, minRows: 16 }}
          className="font-mono text-xs"
          placeholder={`# 服务介绍\n\n简要描述你的 MCP Server...\n\n## 功能特性\n\n- 特性一\n- 特性二\n\n## 使用方式\n\n\`\`\`bash\nnpx -y @your/mcp-server\n\`\`\`\n\n## 注意事项\n\n> 请确保已配置必要的环境变量`}
        />
      </Form.Item>
    </>
  );

  const stepContent = sandboxRequired
    ? [renderBasicInfo, renderMcpConfig, renderServiceIntro, renderSandboxConfig]
    : [renderBasicInfo, renderMcpConfig, renderServiceIntro];

  const navItems = sandboxRequired ? NAV_ITEMS_FULL : NAV_ITEMS_SHORT;
  const isLastStep = currentStep === navItems.length - 1;

  return (
    <Modal
      closable={false}
      footer={null}
      maskClosable={false}
      onCancel={handleCancel}
      open={visible}
      styles={{
        body: { padding: 0 },
        header: { display: 'none' },
      }}
      width={820}
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
              const isActive = currentStep === item.key;
              const isDone = completedSteps.has(item.key);
              const isLast = idx === navItems.length - 1;
              return (
                <div
                  className="flex gap-3"
                  key={item.key}
                  onClick={() => navigateTo(item.key)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      navigateTo(item.key);
                    }
                  }}
                  role="button"
                  tabIndex={0}
                >
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
                      <div
                        className={`w-0.5 flex-1 my-1 min-h-[28px] rounded-full transition-colors duration-300 ${
                          isDone ? 'bg-green-300' : 'bg-gray-200'
                        }`}
                      />
                    )}
                  </div>
                  {/* 右侧：文字 */}
                  <div className={`pt-0.5 pb-4 cursor-pointer ${isLast ? '' : ''}`}>
                    <div
                      className={`text-sm leading-tight ${isActive ? 'font-semibold text-gray-900' : isDone ? 'font-medium text-gray-700' : 'text-gray-500'}`}
                    >
                      {item.label}
                    </div>
                    <div className="text-[11px] text-gray-400 mt-0.5 leading-tight">
                      {item.desc}
                    </div>
                  </div>
                </div>
              );
            })}
          </nav>
        </div>

        {/* 右侧内容 */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* 顶部标题栏 */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
            <div className="flex items-center gap-2">
              {(() => {
                const nav = navItems[currentStep];
                if (!nav) return null;
                return (
                  <>
                    <span className="text-gray-400">{nav.icon}</span>
                    <span className="text-sm font-medium text-gray-800">{nav.label}</span>
                    <span className="text-xs text-gray-400">— {nav.desc}</span>
                  </>
                );
              })()}
            </div>
            <Button
              className="text-gray-400 hover:text-gray-600"
              onClick={handleCancel}
              size="small"
              type="text"
            >
              ✕
            </Button>
          </div>

          {/* 表单内容 */}
          <div className="flex-1 overflow-auto px-6 py-5">
            <Form form={form} layout="vertical" requiredMark={false}>
              {/* 保持 sandboxRequired 字段始终挂载，避免 useWatch 在 Form.Item 卸载后返回 undefined */}
              <Form.Item hidden name="sandboxRequired">
                <input type="hidden" />
              </Form.Item>
              {(() => {
                const contentFn = stepContent[currentStep];
                return contentFn ? contentFn() : null;
              })()}
            </Form>
          </div>

          {/* 底部操作栏 */}
          <div className="border-t border-gray-100">
            {/* 部署进度条 - 仅在提交沙箱部署时显示 */}
            {submitting && deployStep >= 0 && (
              <div className="px-6 pt-3 pb-1">
                <Steps
                  current={deployStep}
                  items={DEPLOY_STEPS.map((step, idx) => ({
                    icon: submitting && idx === deployStep ? <LoadingOutlined /> : undefined,
                    title: <span className="text-xs">{step.title}</span>,
                  }))}
                  size="small"
                  status={!submitting && deployStep >= 0 && deployStep < 2 ? 'error' : 'process'}
                />
              </div>
            )}
            {/* 部署失败提示 */}
            {!submitting && deployStep >= 0 && deployStep < 2 && (
              <div className="px-6 pt-3 pb-1">
                <Steps
                  current={deployStep}
                  items={DEPLOY_STEPS.map((step) => ({
                    title: <span className="text-xs">{step.title}</span>,
                  }))}
                  size="small"
                  status="error"
                />
              </div>
            )}
            <div className="flex items-center justify-between px-6 py-3">
              <div>
                {currentStep > 0 && !submitting ? (
                  <Button onClick={() => setCurrentStep(currentStep - 1)}>上一步</Button>
                ) : (
                  <span />
                )}
              </div>
              <Space>
                <Button disabled={submitting} onClick={handleCancel}>
                  取消
                </Button>
                {!isLastStep ? (
                  <Button onClick={handleNext} type="primary">
                    下一步
                  </Button>
                ) : (
                  <>
                    {sandboxRequired && (
                      <Button disabled={submitting} onClick={handleSaveOnly}>
                        仅保存配置
                      </Button>
                    )}
                    <Button
                      disabled={submitting}
                      loading={submitting}
                      onClick={() => handleSubmit(true)}
                      type="primary"
                    >
                      {submitting ? '处理中...' : sandboxRequired ? '保存并部署' : '保存配置'}
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
        cancelText="取消"
        destroyOnClose
        okText="确定"
        onCancel={() => {
          setParamModalVisible(false);
          paramForm.resetFields();
          setEditingParamKey(null);
        }}
        onOk={() => {
          paramForm.validateFields().then((values) => {
            if (editingParamKey) {
              setExtraParams((prev) =>
                prev.map((p) =>
                  p.key === editingParamKey ? { ...values, key: editingParamKey } : p,
                ),
              );
            } else {
              setExtraParams((prev) => [...prev, { ...values, key: `param_${Date.now()}` }]);
            }
            setParamModalVisible(false);
            paramForm.resetFields();
            setEditingParamKey(null);
          });
        }}
        open={paramModalVisible}
        title={editingParamKey ? '编辑参数' : '添加参数'}
        width={480}
      >
        <Form className="mt-4" form={paramForm} layout="vertical">
          <Form.Item
            label="参数名"
            name="name"
            rules={[{ message: '请输入参数名', required: true }]}
          >
            <Input
              placeholder={protocolType === 'stdio' ? '例如: API_KEY' : '例如: Authorization'}
            />
          </Form.Item>
          <Form.Item
            initialValue={protocolType === 'stdio' ? 'env' : 'header'}
            label="参数位置"
            name="position"
            rules={[{ message: '请选择参数位置', required: true }]}
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
          <Form.Item initialValue={false} label="是否必填" name="required" valuePropName="checked">
            <Switch checkedChildren="必填" unCheckedChildren="可选" />
          </Form.Item>
          <Form.Item label="参数说明" name="description">
            <Input.TextArea
              autoSize={{ maxRows: 4, minRows: 2 }}
              placeholder="描述该参数的用途"
              rows={2}
            />
          </Form.Item>
          <Form.Item label="参数示例" name="example">
            <Input
              placeholder={
                protocolType === 'stdio' ? '例如: sk-xxxxxxxxxxxx' : '例如: Bearer sk-xxx'
              }
            />
          </Form.Item>
        </Form>
      </Modal>
    </Modal>
  );
}
