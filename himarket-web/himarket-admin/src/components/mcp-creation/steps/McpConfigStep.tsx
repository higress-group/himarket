import {
  CodeOutlined,
  ApiOutlined,
  GlobalOutlined,
  CheckCircleFilled,
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
} from '@ant-design/icons';
import { Form, Input, Button, Switch, Table, Tag, Space, Modal, Select, message } from 'antd';
import { useState, useEffect } from 'react';

import type { ExtraParam, ProtocolType } from '../types';

const PROTOCOLS: { key: ProtocolType; label: string; icon: React.ReactNode }[] = [
  { icon: <ApiOutlined />, key: 'sse', label: 'SSE' },
  { icon: <GlobalOutlined />, key: 'http', label: 'Streamable HTTP' },
  { icon: <CodeOutlined />, key: 'stdio', label: 'Stdio' },
];

export default function McpConfigStep() {
  const form = Form.useFormInstance();

  const protocolType: ProtocolType = Form.useWatch('protocolType', form) ?? 'sse';
  const isStdio = protocolType === 'stdio';

  // ---- Extra params local state ----
  const [extraParams, setExtraParams] = useState<ExtraParam[]>([]);
  const [paramModalVisible, setParamModalVisible] = useState(false);
  const [paramForm] = Form.useForm();
  const [editingParamKey, setEditingParamKey] = useState<string | null>(null);

  // Sync extraParams to form field whenever it changes
  useEffect(() => {
    form.setFieldsValue({ extraParams });
  }, [extraParams, form]);

  // ---- Protocol selection handler ----
  const handleProtocolChange = (key: ProtocolType) => {
    form.setFieldsValue({
      protocolType: key,
      ...(key === 'stdio' ? { sandboxRequired: true } : {}),
    });
    setExtraParams([]);
  };

  // ---- Parse JSON handler ----
  const handleParseJson = () => {
    const raw = form.getFieldValue('connectionConfig');
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

      // Auto-detect protocol type
      if (cfg.command) {
        form.setFieldsValue({ protocolType: 'stdio', sandboxRequired: true });
      } else if (cfg.type === 'sse' || (!cfg.type && cfg.url?.endsWith('/sse'))) {
        form.setFieldsValue({ protocolType: 'sse' });
      } else if (cfg.type === 'streamable-http') {
        form.setFieldsValue({ protocolType: 'http' });
      } else {
        form.setFieldsValue({ protocolType: cfg.url ? 'sse' : 'http' });
      }

      // Extract extra params
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
      if (cfg.args && Array.isArray(cfg.args)) {
        cfg.args.forEach((arg: string, i: number) => {
          if (typeof arg === 'string' && arg.startsWith('--')) {
            params.push({
              description: '',
              example: '',
              key: `param_${Date.now()}_arg${i}`,
              name: arg,
              position: 'args',
              required: false,
            });
          }
        });
      }

      setExtraParams(params);
      message.success(`已解析：${serverKey}，识别到 ${params.length} 个参数`);
    } catch {
      message.error('JSON 解析失败，请检查格式');
    }
  };

  // ---- Param modal handlers ----
  const openAddParam = () => {
    setEditingParamKey(null);
    paramForm.resetFields();
    setParamModalVisible(true);
  };

  const openEditParam = (record: ExtraParam) => {
    setEditingParamKey(record.key);
    paramForm.setFieldsValue(record);
    setParamModalVisible(true);
  };

  const handleParamOk = () => {
    paramForm.validateFields().then((values) => {
      if (editingParamKey) {
        setExtraParams((prev) =>
          prev.map((p) => (p.key === editingParamKey ? { ...values, key: editingParamKey } : p)),
        );
      } else {
        setExtraParams((prev) => [...prev, { ...values, key: `param_${Date.now()}` }]);
      }
      setParamModalVisible(false);
      paramForm.resetFields();
      setEditingParamKey(null);
    });
  };

  const handleParamCancel = () => {
    setParamModalVisible(false);
    paramForm.resetFields();
    setEditingParamKey(null);
  };

  const handleDeleteParam = (key: string) => {
    setExtraParams((prev) => prev.filter((p) => p.key !== key));
  };

  // ---- Example JSON placeholder ----
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

  // ---- Table columns ----
  const columns = [
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
            onClick={() => openEditParam(record)}
            size="small"
            type="text"
          />
          <Button
            className="text-gray-400 hover:text-red-500"
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteParam(record.key)}
            size="small"
            type="text"
          />
        </Space>
      ),
      title: '',
      width: 60,
    },
  ];

  return (
    <div className="space-y-5">
      {/* ---- 协议类型选择卡片 ---- */}
      <Form.Item
        initialValue="sse"
        label="协议类型"
        name="protocolType"
        rules={[{ required: true }]}
      >
        <div className="grid grid-cols-3 gap-2">
          {PROTOCOLS.map((p) => {
            const selected = protocolType === p.key;
            return (
              <div
                className={`relative flex items-center gap-2 rounded-lg border px-3 py-2.5 cursor-pointer transition-all duration-150 ${
                  selected
                    ? 'border-blue-500 bg-blue-50/50 ring-1 ring-blue-500/20'
                    : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50/50'
                }`}
                key={p.key}
                onClick={() => handleProtocolChange(p.key)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleProtocolChange(p.key);
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

      {/* ---- MCP 连接配置 JSON ---- */}
      <Form.Item
        label="MCP 连接配置"
        name="connectionConfig"
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

      {/* ---- 解析 JSON 按钮 ---- */}
      <div className="flex items-center gap-3 -mt-3">
        <Button ghost icon={<CodeOutlined />} onClick={handleParseJson} size="small" type="primary">
          解析 JSON
        </Button>
        <span className="text-xs text-gray-400">粘贴 JSON 后点击可自动解析协议类型及参数</span>
      </div>

      {/* ---- 额外参数配置表格 ---- */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-gray-700">{isStdio ? '环境变量配置' : '请求参数配置'}</span>
          <Space size={8}>
            <Button icon={<PlusOutlined />} onClick={openAddParam} size="small" type="dashed">
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
            columns={columns}
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

      {/* ---- 沙箱托管开关 ---- */}
      <div className="flex items-center justify-between py-2.5 px-4 bg-gray-50 rounded-lg">
        <div>
          <div className="text-sm text-gray-700">是否需要沙箱托管</div>
          <div className="text-xs text-gray-400 mt-0.5">
            {isStdio ? 'Stdio 协议必须通过沙箱运行' : '开启后可将 MCP Server 部署到沙箱集群'}
          </div>
        </div>
        <Form.Item className="mb-0" name="sandboxRequired" valuePropName="checked">
          <Switch checkedChildren="开启" disabled={isStdio} unCheckedChildren="关闭" />
        </Form.Item>
      </div>

      {/* Hidden field to keep extraParams in form */}
      <Form.Item hidden name="extraParams">
        <Input />
      </Form.Item>

      {/* ---- 添加/编辑参数弹窗 ---- */}
      <Modal
        cancelText="取消"
        destroyOnClose
        okText="确定"
        onCancel={handleParamCancel}
        onOk={handleParamOk}
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
            <Input placeholder={isStdio ? '例如: API_KEY' : '例如: Authorization'} />
          </Form.Item>
          <Form.Item
            initialValue={isStdio ? 'env' : 'header'}
            label="参数位置"
            name="position"
            rules={[{ message: '请选择参数位置', required: true }]}
          >
            <Select>
              <Select.Option value="env">环境变量 (env)</Select.Option>
              <Select.Option value="args">启动参数 (args)</Select.Option>
              <Select.Option value="header">请求头 (header)</Select.Option>
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
            <Input placeholder={isStdio ? '例如: sk-xxxxxxxxxxxx' : '例如: Bearer sk-xxx'} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
