import {
  PlusOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
  EditOutlined,
  CloudServerOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { Button, Table, message, Modal, Tabs, Tag, Form, Input, Steps, Space, Tooltip } from 'antd';
import { useState, useEffect, useCallback } from 'react';

import { sandboxApi } from '@/lib/api';
import { formatDateTime } from '@/lib/utils';

// ==================== 类型定义 ====================

export type SandboxType = 'AGENT_RUNTIME' | 'SELF_HOSTED';

export interface SandboxInstance {
  sandboxId: string;
  sandboxName: string;
  sandboxType: SandboxType;
  clusterAttribute?: string;
  apiServer: string;
  description?: string;
  extraConfig?: string;
  status: 'RUNNING' | 'STOPPED' | 'ERROR';
  statusMessage?: string;
  lastCheckedAt?: string;
  createAt: string;
}

// ==================== 组件 ====================

export default function SandboxConsoles() {
  const [sandboxes, setSandboxes] = useState<SandboxInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<SandboxType>('AGENT_RUNTIME');
  const [modalVisible, setModalVisible] = useState(false);
  const [editingSandbox, setEditingSandbox] = useState<SandboxInstance | null>(null);
  const [form] = Form.useForm();
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [fetching, setFetching] = useState(false);
  const [clusterFetched, setClusterFetched] = useState(false);
  const [fetchFailed, setFetchFailed] = useState(false);
  const [importStep, setImportStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [checkingId, setCheckingId] = useState<string | null>(null);

  const isAgentRuntime = activeTab === 'AGENT_RUNTIME';

  const fetchList = useCallback(async (type: SandboxType, page = 1, size = 10) => {
    setLoading(true);
    try {
      const res: unknown = await sandboxApi.getSandboxes({
        page: page,
        sandboxType: type,
        size,
      });
      const data =
        (res as { data?: { content?: unknown[]; totalElements?: number } }).data ||
        (res as { content?: unknown[]; totalElements?: number });
      setSandboxes((data.content || []) as SandboxInstance[]);
      setPagination({ current: page, pageSize: size, total: data.totalElements || 0 });
    } catch {
      setSandboxes([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList(activeTab);
  }, [fetchList, activeTab]);

  const handleTabChange = (key: string) => {
    setActiveTab(key as SandboxType);
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const resetModalState = () => {
    setClusterFetched(false);
    setFetchFailed(false);
    setImportStep(0);
  };

  const handleDelete = (record: SandboxInstance) => {
    Modal.confirm({
      content: `确定要删除 Sandbox 实例「${record.sandboxName}」吗？`,
      onOk: async () => {
        await sandboxApi.deleteSandbox(record.sandboxId);
        message.success('删除成功');
        fetchList(activeTab, pagination.current, pagination.pageSize);
      },
      title: '确认删除',
    });
  };

  const handleEdit = (record: SandboxInstance) => {
    setEditingSandbox(record);
    form.setFieldsValue({
      description: record.description,
      sandboxName: record.sandboxName,
    });
    setClusterFetched(false);
    setFetchFailed(false);
    // 编辑时跳到 step 0（基本信息），用户可选择是否更新 KubeConfig
    setImportStep(0);
    setModalVisible(true);
  };

  const handleAdd = () => {
    setEditingSandbox(null);
    form.resetFields();
    resetModalState();
    setModalVisible(true);
  };

  const handleHealthCheck = async (record: SandboxInstance) => {
    setCheckingId(record.sandboxId);
    try {
      const res: unknown = await sandboxApi.healthCheck(record.sandboxId);
      const updated =
        (res as { data?: Partial<SandboxInstance> }).data || (res as Partial<SandboxInstance>);
      setSandboxes((prev) =>
        prev.map((s) => (s.sandboxId === record.sandboxId ? { ...s, ...updated } : s)),
      );
      if (updated.status === 'RUNNING') {
        message.success(`${record.sandboxName} 连接正常`);
      } else {
        message.warning(`${record.sandboxName} 状态异常: ${updated.statusMessage || '未知错误'}`);
      }
    } catch {
      message.error('健康检查失败');
    } finally {
      setCheckingId(null);
    }
  };

  const handleFetchCluster = async () => {
    const kubeConfig = form.getFieldValue('kubeConfig');
    if (!kubeConfig) {
      message.warning('请先粘贴 KubeConfig');
      return;
    }
    setFetching(true);
    setClusterFetched(false);
    setFetchFailed(false);
    try {
      const res: unknown = await sandboxApi.fetchClusterInfo(kubeConfig);
      const result =
        (res as { data?: { ok?: boolean; message?: string } }).data ||
        (res as { ok?: boolean; message?: string });
      if (result.ok) {
        setClusterFetched(true);
        message.success('集群连接成功');
      } else {
        setFetchFailed(true);
        message.error(result.message || '获取集群信息失败，请检查 KubeConfig');
      }
    } catch {
      setFetchFailed(true);
      message.error('获取集群信息异常');
    } finally {
      setFetching(false);
    }
  };

  const handleModalOk = async () => {
    if (!clusterFetched && !editingSandbox) {
      message.warning('请先验证集群连通性');
      return;
    }
    // 编辑模式下，如果填了新的 KubeConfig 但未验证，也需要先验证
    if (editingSandbox && form.getFieldValue('kubeConfig') && !clusterFetched) {
      message.warning('检测到新的 KubeConfig，请先验证连通性');
      return;
    }

    // 编辑模式下更换 KubeConfig 时，检查是否有活跃的 MCP 部署
    if (editingSandbox && form.getFieldValue('kubeConfig') && clusterFetched) {
      try {
        const res: unknown = await sandboxApi.getActiveDeployments(editingSandbox.sandboxId);
        const count =
          ((res as { data?: { count?: number } }).data || (res as { count?: number })).count || 0;
        if (count > 0) {
          Modal.confirm({
            cancelText: '取消',
            content: `该沙箱上仍有 ${count} 个 MCP 部署正在运行。更换 KubeConfig 后，这些部署将指向新集群，旧集群上的资源将变为孤儿资源且无法自动清理。确定要继续吗？`,
            okText: '确认更换',
            okType: 'danger',
            onOk: () => doSubmit(),
            title: '⚠️ 检测到活跃的 MCP 部署',
          });
          return;
        }
      } catch {
        // 查询失败不阻塞提交
      }
    }

    // 新导入直接提交；编辑更新时二次确认
    if (!editingSandbox) {
      await doSubmit();
      return;
    }
    const values = form.getFieldsValue(true);
    const sandboxName = values.sandboxName || '未命名';
    Modal.confirm({
      cancelText: '再想想',
      content: `即将更新 Sandbox 实例「${sandboxName}」的配置，更新后可能影响正在运行的 MCP 服务。是否确认提交？`,
      okText: '确认提交',
      onOk: () => doSubmit(),
      title: '确认更新 Sandbox 实例',
    });
  };

  const doSubmit = async () => {
    try {
      const values = form.getFieldsValue(true);
      setSubmitting(true);

      if (editingSandbox) {
        await sandboxApi.updateSandbox(editingSandbox.sandboxId, {
          description: values.description,
          kubeConfig: values.kubeConfig,
          sandboxName: values.sandboxName,
        });
        message.success('更新成功');
      } else {
        await sandboxApi.importSandbox({
          description: values.description,
          kubeConfig: values.kubeConfig,
          sandboxName: values.sandboxName,
          sandboxType: activeTab,
        });
        message.success('导入成功');
      }
      setModalVisible(false);
      form.resetFields();
      setEditingSandbox(null);
      resetModalState();
      fetchList(activeTab, pagination.current, pagination.pageSize);
    } catch {
      /* validation or API error */
    } finally {
      setSubmitting(false);
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    form.resetFields();
    setEditingSandbox(null);
    resetModalState();
  };

  const statusTag = (status: SandboxInstance['status']) => {
    const map = {
      ERROR: { color: 'red', text: '异常' },
      RUNNING: { color: 'green', text: '运行中' },
      STOPPED: { color: 'default', text: '已停止' },
    };
    const s = map[status];
    return <Tag color={s.color}>{s.text}</Tag>;
  };

  const columns = [
    {
      key: 'nameAndId',
      render: (_: unknown, record: SandboxInstance) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.sandboxName}</div>
          <div className="text-xs text-gray-500 truncate">{record.sandboxId}</div>
        </div>
      ),
      title: '实例名称/ID',
      width: 260,
    },
    {
      key: 'clusterId',
      render: (_: unknown, record: SandboxInstance) => {
        try {
          const attr = record.clusterAttribute ? JSON.parse(record.clusterAttribute) : {};
          return attr.clusterId ? (
            <Tooltip title={attr.clusterId}>
              <span className="text-xs font-mono text-gray-600 truncate block max-w-[200px]">
                {attr.clusterId}
              </span>
            </Tooltip>
          ) : (
            <span className="text-xs text-gray-400">-</span>
          );
        } catch {
          return <span className="text-xs text-gray-400">-</span>;
        }
      },
      title: '集群 ID',
      width: 220,
    },
    {
      dataIndex: 'apiServer',
      ellipsis: true,
      key: 'apiServer',
      render: (v: string) => (
        <Tooltip title={v}>
          <span className="text-xs font-mono">{v}</span>
        </Tooltip>
      ),
      title: 'API Server',
      width: 220,
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (_: SandboxInstance['status'], record: SandboxInstance) => (
        <div>
          {statusTag(record.status)}
          {record.statusMessage && record.status === 'ERROR' && (
            <Tooltip title={record.statusMessage}>
              <div className="text-xs text-red-400 truncate mt-0.5 max-w-[140px] cursor-help">
                {record.statusMessage}
              </div>
            </Tooltip>
          )}
          {record.lastCheckedAt && (
            <div className="text-xs text-gray-400 mt-0.5">
              检查于 {formatDateTime(record.lastCheckedAt)}
            </div>
          )}
        </div>
      ),
      title: '状态',
      width: 160,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
      width: 180,
    },
    {
      key: 'action',
      render: (_: unknown, record: SandboxInstance) => (
        <>
          <Tooltip title="检查集群连通性">
            <Button
              icon={checkingId === record.sandboxId ? <SyncOutlined spin /> : <ReloadOutlined />}
              loading={checkingId === record.sandboxId}
              onClick={() => handleHealthCheck(record)}
              size="small"
              type="link"
            >
              检查
            </Button>
          </Tooltip>
          <Button onClick={() => handleEdit(record)} type="link">
            编辑
          </Button>
          <Button danger onClick={() => handleDelete(record)} type="link">
            删除
          </Button>
        </>
      ),
      title: '操作',
      width: 200,
    },
  ];

  const renderTable = () => (
    <Table
      columns={columns}
      dataSource={sandboxes}
      loading={loading}
      pagination={{
        ...pagination,
        onChange: (page: number, size: number) => fetchList(activeTab, page, size),
        onShowSizeChange: (_: number, size: number) => fetchList(activeTab, 1, size),
        showQuickJumper: true,
        showSizeChanger: true,
        showTotal: (total: number) => `共 ${total} 条`,
      }}
      rowKey="sandboxId"
    />
  );

  const tabItems = [
    {
      children: (
        <div className="bg-white rounded-lg">
          <div className="py-4 pl-4 border-b border-gray-200">
            <h3 className="text-lg font-medium text-gray-900">AgentRuntime 实例</h3>
            <p className="text-sm text-gray-500 mt-1">
              导入 AgentRuntime 实例，用于 MCP Server 沙箱运行
            </p>
          </div>
          {renderTable()}
        </div>
      ),
      key: 'AGENT_RUNTIME',
      label: 'AgentRuntime',
    },
    {
      children: (
        <div className="bg-white rounded-lg">
          <div className="py-4 pl-4 border-b border-gray-200">
            <h3 className="text-lg font-medium text-gray-900">自建 Sandbox 实例</h3>
            <p className="text-sm text-gray-500 mt-1">
              导入自建的 Sandbox 实例，用于自定义 MCP 运行环境
            </p>
          </div>
          {renderTable()}
        </div>
      ),
      disabled: true,
      key: 'SELF_HOSTED',
      label: '自建 Sandbox（即将支持）',
    },
  ];

  const stepItems = [
    { icon: <EditOutlined />, title: '基本信息' },
    { icon: <CloudServerOutlined />, title: '连接集群' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Sandbox 实例</h1>
          <p className="text-gray-500 mt-2">管理和配置您的沙箱运行环境</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={handleAdd} type="primary">
          导入{isAgentRuntime ? ' AgentRuntime' : ' Sandbox'} 实例
        </Button>
      </div>

      <Tabs activeKey={activeTab} items={tabItems} onChange={handleTabChange} />

      <Modal
        destroyOnClose
        footer={null}
        onCancel={handleModalCancel}
        open={modalVisible}
        title={
          editingSandbox
            ? '编辑 Sandbox 实例'
            : `导入${isAgentRuntime ? ' AgentRuntime' : ' Sandbox'} 实例`
        }
        width={720}
      >
        <Steps className="mt-2 mb-6 px-4" current={importStep} items={stepItems} size="small" />

        <Form className="px-1" form={form} layout="vertical" preserve>
          {/* ── Step 0: 基本信息 ── */}
          {importStep === 0 && (
            <div style={{ minHeight: 200 }}>
              <div className="text-sm text-gray-500 mb-4">
                为 Sandbox 实例设置一个名称，方便后续管理和识别。
              </div>
              <Form.Item
                label="实例名称"
                name="sandboxName"
                rules={[{ message: '请输入实例名称', required: true }]}
              >
                <Input placeholder="例如：生产环境 AgentRuntime" size="large" />
              </Form.Item>
              <Form.Item label="描述（选填）" name="description">
                <Input.TextArea
                  autoSize={{ maxRows: 4, minRows: 2 }}
                  placeholder="简要描述该实例的用途"
                />
              </Form.Item>
            </div>
          )}

          {/* ── Step 1: 连接集群 ── */}
          {importStep === 1 && (
            <div style={{ minHeight: 200 }}>
              <div className="text-sm text-gray-500 mb-4">
                {editingSandbox
                  ? '如需更换集群，请粘贴新的 KubeConfig 并验证连通性。留空则保持原有集群配置不变。'
                  : '粘贴 Kubernetes 集群的 KubeConfig 文件内容，然后点击下方按钮验证连接。'}
              </div>
              <Form.Item
                label="KubeConfig"
                name="kubeConfig"
                rules={[{ message: '请粘贴 KubeConfig 内容', required: !editingSandbox }]}
              >
                <Input.TextArea
                  autoSize={{ maxRows: 18, minRows: 10 }}
                  className="font-mono text-xs"
                  onChange={() => {
                    setClusterFetched(false);
                    setFetchFailed(false);
                  }}
                  placeholder={`apiVersion: v1\nclusters:\n- cluster:\n    server: https://your-k8s-api:6443\n  name: my-cluster\n...`}
                />
              </Form.Item>
              {clusterFetched ? (
                <div className="flex items-center gap-2 text-green-600 text-sm">
                  <CheckCircleOutlined /> 集群连接成功
                </div>
              ) : fetchFailed ? (
                <div className="flex items-center gap-2 text-red-500 text-sm">
                  <CloseCircleOutlined /> 连接失败，请检查 KubeConfig
                </div>
              ) : null}
            </div>
          )}
        </Form>

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-100">
          <div>
            {importStep > 0 && !editingSandbox && (
              <Button onClick={() => setImportStep(importStep - 1)}>上一步</Button>
            )}
          </div>
          <Space>
            <Button onClick={handleModalCancel}>取消</Button>
            {importStep === 0 && (
              <Button
                onClick={async () => {
                  try {
                    await form.validateFields(['sandboxName']);
                    setImportStep(1);
                  } catch {
                    /* */
                  }
                }}
                type="primary"
              >
                下一步
              </Button>
            )}
            {importStep === 1 &&
              (clusterFetched || (editingSandbox && !form.getFieldValue('kubeConfig')) ? (
                <Button loading={submitting} onClick={handleModalOk} type="primary">
                  {editingSandbox ? '保存' : '确认导入'}
                </Button>
              ) : (
                <Button
                  icon={fetching ? <LoadingOutlined /> : <ApiOutlined />}
                  loading={fetching}
                  onClick={handleFetchCluster}
                  type="primary"
                >
                  验证连通性
                </Button>
              ))}
          </Space>
        </div>
      </Modal>
    </div>
  );
}
