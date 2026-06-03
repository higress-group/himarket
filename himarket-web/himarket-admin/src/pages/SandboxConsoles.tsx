import {
  PlusOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  LoadingOutlined,
  EditOutlined,
  DeleteOutlined,
  CloudServerOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { Button, message, Modal, Tabs, Form, Input, Steps, Space, Tooltip } from 'antd';
import { useState, useEffect, useCallback } from 'react';

import { AdminPageHeader } from '@/components/common';
import { DataTable } from '@/components/common/DataTable';
import { StatusIndicator } from '@/components/common/StatusIndicator';
import { useLocale } from '@/contexts/LocaleContext';
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
  const { t } = useLocale();
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
      cancelText: t('common.cancel'),
      content: t('page.sandbox.deleteConfirm', { name: record.sandboxName }),
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk: async () => {
        await sandboxApi.deleteSandbox(record.sandboxId);
        message.success(t('common.deleteSuccess'));
        fetchList(activeTab, pagination.current, pagination.pageSize);
      },
      title: t('common.confirmDelete'),
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
        message.success(`${record.sandboxName} ${t('page.sandbox.clusterConnected')}`);
      } else {
        message.warning(
          `${record.sandboxName} ${t('page.sandbox.statusError')}: ${
            updated.statusMessage || t('common.unknown')
          }`,
        );
      }
    } catch {
      message.error(t('page.sandbox.healthCheckFailed'));
    } finally {
      setCheckingId(null);
    }
  };

  const handleFetchCluster = async () => {
    const kubeConfig = form.getFieldValue('kubeConfig');
    if (!kubeConfig) {
      message.warning(t('page.sandbox.kubeConfigRequired'));
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
        message.success(t('page.sandbox.clusterConnected'));
      } else {
        setFetchFailed(true);
        message.error(result.message || t('page.sandbox.clusterConnectionFailed'));
      }
    } catch {
      setFetchFailed(true);
      message.error(t('page.sandbox.clusterConnectionFailed'));
    } finally {
      setFetching(false);
    }
  };

  const handleModalOk = async () => {
    if (!clusterFetched && !editingSandbox) {
      message.warning(t('page.sandbox.verifyFirst'));
      return;
    }
    // 编辑模式下，如果填了新的 KubeConfig 但未验证，也需要先验证
    if (editingSandbox && form.getFieldValue('kubeConfig') && !clusterFetched) {
      message.warning(t('page.sandbox.newKubeConfigWarning'));
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
            cancelText: t('common.cancel'),
            content: t('page.sandbox.runningDeploymentsConfirm', { count }),
            okText: t('page.sandbox.continueChange'),
            okType: 'danger',
            onOk: () => doSubmit(),
            title: t('page.sandbox.runningDeploymentsTitle'),
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
    const sandboxName = values.sandboxName || t('page.sandbox.unknownName');
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('page.sandbox.kubeConfigChangedConfirm', { name: sandboxName }),
      okText: t('common.confirm'),
      onOk: () => doSubmit(),
      title: t('page.sandbox.kubeConfigChangedTitle'),
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
        message.success(t('page.categoryDetail.updateSuccess'));
      } else {
        await sandboxApi.importSandbox({
          description: values.description,
          kubeConfig: values.kubeConfig,
          sandboxName: values.sandboxName,
          sandboxType: activeTab,
        });
        message.success(t('action.importSuccess'));
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
      ERROR: { text: t('page.sandbox.statusError'), tone: 'warning' as const },
      RUNNING: { text: t('page.sandbox.running'), tone: 'success' as const },
      STOPPED: { text: t('page.sandbox.statusStopped'), tone: 'neutral' as const },
    };
    const s = map[status];
    return <StatusIndicator tone={s.tone}>{s.text}</StatusIndicator>;
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
      title: t('page.sandbox.nameAndId'),
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
      title: t('page.sandbox.clusterId'),
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
              {t('page.sandbox.checkAt', { time: formatDateTime(record.lastCheckedAt) })}
            </div>
          )}
        </div>
      ),
      title: t('common.status'),
      width: 160,
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date),
      title: t('product.overview.createAt'),
      width: 180,
    },
    {
      key: 'action',
      render: (_: unknown, record: SandboxInstance) => (
        <div className="flex items-center gap-1">
          <Tooltip title={t('page.sandbox.checkHealth')}>
            <Button
              className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
              icon={checkingId === record.sandboxId ? <SyncOutlined spin /> : <ReloadOutlined />}
              loading={checkingId === record.sandboxId}
              onClick={() => handleHealthCheck(record)}
              type="text"
            >
              {t('page.sandbox.check')}
            </Button>
          </Tooltip>
          <Button
            className="text-blue-600 hover:text-blue-700 hover:bg-blue-50 !px-2 text-xs"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            type="text"
          >
            {t('common.edit')}
          </Button>
          <Button
            className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record)}
            type="text"
          >
            {t('common.delete')}
          </Button>
        </div>
      ),
      title: t('common.operation'),
      width: 220,
    },
  ];

  const tabItems = [
    {
      children: (
        <div>
          <div className="px-1 pb-3">
            <h3 className="text-lg font-medium text-gray-900">
              {t('page.sandbox.agentRuntimeTitle')}
            </h3>
            <p className="text-sm text-gray-500 mt-1">
              {t('page.sandbox.agentRuntimeDescription')}
            </p>
          </div>
          <DataTable<SandboxInstance>
            columns={columns}
            dataSource={sandboxes}
            loading={loading}
            pagination={{
              current: pagination.current,
              onChange: (page: number, size?: number) => fetchList(activeTab, page, size || 10),
              pageSize: pagination.pageSize,
              total: pagination.total,
            }}
            rowKey="sandboxId"
          />
        </div>
      ),
      key: 'AGENT_RUNTIME',
      label: 'AgentRuntime',
    },
    {
      children: (
        <div>
          <div className="px-1 pb-3">
            <h3 className="text-lg font-medium text-gray-900">
              {t('page.sandbox.selfHostedTitle')}
            </h3>
            <p className="text-sm text-gray-500 mt-1">{t('page.sandbox.selfHostedDescription')}</p>
          </div>
          <DataTable<SandboxInstance>
            columns={columns}
            dataSource={sandboxes}
            loading={loading}
            pagination={{
              current: pagination.current,
              onChange: (page: number, size?: number) => fetchList(activeTab, page, size || 10),
              pageSize: pagination.pageSize,
              total: pagination.total,
            }}
            rowKey="sandboxId"
          />
        </div>
      ),
      disabled: true,
      key: 'SELF_HOSTED',
      label: t('page.sandbox.selfHostedLabel'),
    },
  ];

  const stepItems = [
    { icon: <EditOutlined />, title: t('page.sandbox.basicInfo') },
    { icon: <CloudServerOutlined />, title: t('page.sandbox.connectCluster') },
  ];

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button icon={<PlusOutlined />} onClick={handleAdd} type="primary">
            {t('page.sandbox.create')}
          </Button>
        }
        description={t('page.sandbox.description')}
        title={t('page.sandbox.title')}
      />

      <Tabs activeKey={activeTab} items={tabItems} onChange={handleTabChange} />

      <Modal
        destroyOnClose
        footer={null}
        onCancel={handleModalCancel}
        open={modalVisible}
        title={
          editingSandbox
            ? t('page.sandbox.editTitle')
            : t('page.sandbox.importTitle', {
                type: isAgentRuntime ? 'AgentRuntime' : 'Sandbox',
              })
        }
        width={720}
      >
        <Steps className="mt-2 mb-6 px-4" current={importStep} items={stepItems} size="small" />

        <Form className="px-1" form={form} layout="vertical" preserve>
          {/* ── Step 0: 基本信息 ── */}
          {importStep === 0 && (
            <div style={{ minHeight: 200 }}>
              <div className="text-sm text-gray-500 mb-4">{t('page.sandbox.nameTip')}</div>
              <Form.Item
                label={t('page.nacos.instanceName')}
                name="sandboxName"
                rules={[{ message: t('page.sandbox.nameRequired'), required: true }]}
              >
                <Input placeholder={t('page.sandbox.namePlaceholder')} size="large" />
              </Form.Item>
              <Form.Item label={t('page.sandbox.descriptionField')} name="description">
                <Input.TextArea
                  autoSize={{ maxRows: 4, minRows: 2 }}
                  placeholder={t('page.sandbox.descriptionPlaceholder')}
                />
              </Form.Item>
            </div>
          )}

          {/* ── Step 1: 连接集群 ── */}
          {importStep === 1 && (
            <div style={{ minHeight: 200 }}>
              <div className="text-sm text-gray-500 mb-4">
                {editingSandbox
                  ? t('page.sandbox.kubeConfigEditTip')
                  : t('page.sandbox.kubeConfigImportTip')}
              </div>
              <Form.Item
                label="KubeConfig"
                name="kubeConfig"
                rules={[
                  { message: t('page.sandbox.kubeConfigRequired'), required: !editingSandbox },
                ]}
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
                  <CheckCircleOutlined /> {t('page.sandbox.clusterConnected')}
                </div>
              ) : fetchFailed ? (
                <div className="flex items-center gap-2 text-red-500 text-sm">
                  <CloseCircleOutlined /> {t('page.sandbox.clusterConnectionFailed')}
                </div>
              ) : null}
            </div>
          )}
        </Form>

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between mt-6 pt-4 border-t border-gray-100">
          <div>
            {importStep > 0 && !editingSandbox && (
              <Button onClick={() => setImportStep(importStep - 1)}>
                {t('page.sandbox.previous')}
              </Button>
            )}
          </div>
          <Space>
            <Button onClick={handleModalCancel}>{t('common.cancel')}</Button>
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
                {t('page.sandbox.next')}
              </Button>
            )}
            {importStep === 1 &&
              (clusterFetched || (editingSandbox && !form.getFieldValue('kubeConfig')) ? (
                <Button loading={submitting} onClick={handleModalOk} type="primary">
                  {editingSandbox ? t('page.sandbox.save') : t('page.sandbox.confirmImport')}
                </Button>
              ) : (
                <Button
                  icon={fetching ? <LoadingOutlined /> : <ApiOutlined />}
                  loading={fetching}
                  onClick={handleFetchCluster}
                  type="primary"
                >
                  {t('page.sandbox.validateConnectivity')}
                </Button>
              ))}
          </Space>
        </div>
      </Modal>
    </div>
  );
}
