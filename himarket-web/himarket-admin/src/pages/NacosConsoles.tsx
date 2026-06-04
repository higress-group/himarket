import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Modal, Form, Input, message, Select, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { useState, useEffect, useCallback } from 'react';

import { AdminPageHeader } from '@/components/common';
import { DataTable } from '@/components/common/DataTable';
import ImportMseNacosModal from '@/components/console/ImportMseNacosModal';
import type { NacosImportType } from '@/components/console/NacosTypeSelector';
import NacosTypeSelector from '@/components/console/NacosTypeSelector';
import { useLocale } from '@/contexts/LocaleContext';
import { nacosApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { CreateNacosRequest, UpdateNacosRequest } from '@/types';
import type { NacosInstance, NacosNamespace } from '@/types/gateway';

// 开源创建表单数据由 antd 表单直接管理，无需额外类型声明

export default function NacosConsoles() {
  const { t } = useLocale();
  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([]);
  const [loading, setLoading] = useState(false);
  // 开源 Nacos 创建/编辑弹窗
  const [modalVisible, setModalVisible] = useState(false);
  const [editingNacos, setEditingNacos] = useState<NacosInstance | null>(null);
  const [form] = Form.useForm();
  // 导入类型选择与 MSE 导入
  const [typeSelectorVisible, setTypeSelectorVisible] = useState(false);
  const [mseImportVisible, setMseImportVisible] = useState(false);
  // 由 MSE 导入时可能带入的两个地址
  const [importEndpoints, setImportEndpoints] = useState<{ internet?: string; intranet?: string }>(
    {},
  );
  // 当从 MSE 导入时，保存 MSE 返回的 instanceId 以作为 nacosId 提交
  const [importNacosId, setImportNacosId] = useState<string | null>(null);
  // 创建来源：OPEN_SOURCE 或 MSE（用于控制是否展示 AK/SK）
  const [creationMode, setCreationMode] = useState<'OPEN_SOURCE' | 'MSE' | null>(null);
  // 设置默认弹窗
  const [setDefaultVisible, setSetDefaultVisible] = useState(false);
  const [setDefaultNacosId, setSetDefaultNacosId] = useState<string>('');
  const [setDefaultNamespaces, setSetDefaultNamespaces] = useState<NacosNamespace[]>([]);
  const [setDefaultNsLoading, setSetDefaultNsLoading] = useState(false);
  const [setDefaultSelectedNs, setSetDefaultSelectedNs] = useState<string>('public');
  const [setDefaultSaving, setSetDefaultSaving] = useState(false);

  // 分页状态
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const fetchNacosInstances = useCallback(async () => {
    setLoading(true);
    try {
      const response = await nacosApi.getNacos({
        page: currentPage,
        size: pageSize,
      });
      setNacosInstances(response.data.content || []);
      setTotal(response.data.totalElements || 0);
    } catch (error) {
      console.error('获取Nacos实例列表失败:', error);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize]);

  useEffect(() => {
    fetchNacosInstances();
  }, [fetchNacosInstances]);

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size) {
      setPageSize(size);
    }
  };

  const handleEdit = (record: NacosInstance) => {
    setEditingNacos(record);
    form.setFieldsValue({
      accessKey: record.accessKey,
      displayServerUrl: record.displayServerUrl,
      nacosName: record.nacosName,
      password: record.password,
      secretKey: record.secretKey,
      serverUrl: record.serverUrl,
      username: record.username,
    });
    setModalVisible(true);
  };

  const handleDelete = async (nacosId: string, nacosName: string) => {
    try {
      await nacosApi.deleteNacos(nacosId);
      message.success(t('page.nacos.deleteSuccess', { name: nacosName }));
      fetchNacosInstances();
    } catch (error) {
      console.error('删除失败:', error);
    }
  };

  const loadNamespacesForNacos = async (nacosId: string) => {
    setSetDefaultNsLoading(true);
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
      const list = res.data?.content || [];
      setSetDefaultNamespaces(list);
      // 默认选中该实例已保存的 defaultNamespace，若不存在则取列表第一个或 public
      const instance = nacosInstances.find((i) => i.nacosId === nacosId);
      const savedNs = instance?.defaultNamespace;
      if (savedNs && list.some((ns: NacosNamespace) => ns.namespaceId === savedNs)) {
        setSetDefaultSelectedNs(savedNs);
      } else if (list.length > 0) {
        setSetDefaultSelectedNs(list[0].namespaceId || 'public');
      } else {
        setSetDefaultSelectedNs('public');
      }
    } catch {
      setSetDefaultNamespaces([]);
      setSetDefaultSelectedNs('public');
      message.error(t('page.nacos.fetchNamespacesFailed'));
    } finally {
      setSetDefaultNsLoading(false);
    }
  };

  const handleOpenSetDefault = () => {
    // 默认选中当前默认实例，若无则选中列表第一个
    const defaultInstance = nacosInstances.find((i) => i.isDefault);
    const targetId = defaultInstance?.nacosId || nacosInstances[0]?.nacosId || '';
    setSetDefaultNacosId(targetId);
    setSetDefaultVisible(true);
    if (targetId) {
      loadNamespacesForNacos(targetId);
    }
  };

  const handleNacosChange = (nacosId: string) => {
    setSetDefaultNacosId(nacosId);
    loadNamespacesForNacos(nacosId);
  };

  const handleSaveDefault = async () => {
    if (!setDefaultNacosId) return;
    setSetDefaultSaving(true);
    try {
      await nacosApi.setDefaultNacos(setDefaultNacosId, setDefaultSelectedNs);
      message.success(t('page.nacos.saveDefault'));
      setSetDefaultVisible(false);
      fetchNacosInstances();
    } catch {
      message.error(t('page.nacos.setDefaultFailed'));
    } finally {
      setSetDefaultSaving(false);
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      // 避免将空的敏感字段覆盖后端，移除空值
      const payload: Partial<Record<string, unknown>> = { ...values };
      ['password', 'accessKey', 'secretKey'].forEach((k) => {
        if (payload[k] === undefined || payload[k] === null || payload[k] === '') {
          delete payload[k];
        }
      });

      if (editingNacos) {
        // 编辑模式
        await nacosApi.updateNacos(editingNacos.nacosId, payload as UpdateNacosRequest);
        message.success(t('page.categoryDetail.updateSuccess'));
      } else {
        // 创建模式
        // 若是 MSE 导入来源并带有 importNacosId，则将其作为 nacosId 一并提交
        if (creationMode === 'MSE' && importNacosId) {
          payload.nacosId = importNacosId;
        }
        await nacosApi.createNacos(payload as CreateNacosRequest);
        message.success(t('page.categoryDetail.createSuccess'));
      }

      setModalVisible(false);
      form.resetFields();
      fetchNacosInstances();
      setImportNacosId(null);
    } catch (error) {
      console.error('操作失败:', error);
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
    setEditingNacos(null);
    setCreationMode(null);
    setImportEndpoints({});
    form.resetFields();
  };

  const columns = [
    {
      dataIndex: 'nacosName',
      key: 'nacosName',
      render: (name: string, record: NacosInstance) => (
        <div className="flex flex-col">
          <span className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-900 truncate">{name}</span>
            {record.isDefault && (
              <button
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-white cursor-pointer border-none"
                onClick={handleOpenSetDefault}
                type="button"
              >
                {t('page.nacos.default')}
                <EditOutlined style={{ fontSize: '10px' }} />
              </button>
            )}
          </span>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.nacosId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.nacosId}
            </button>
          </Tooltip>
          {record.isDefault && (
            <span className="text-xs text-gray-400 mt-0.5">
              {t('page.nacos.defaultNamespace', {
                namespace: record.defaultNamespace || 'public',
              })}
            </span>
          )}
        </div>
      ),
      title: t('page.nacos.nameAndId'),
    },
    {
      dataIndex: 'serverUrl',
      key: 'serverUrl',
      title: t('page.nacos.serverAddress'),
    },
    {
      dataIndex: 'displayServerUrl',
      key: 'displayServerUrl',
      render: (url: string) => url || <span style={{ color: '#999' }}>-</span>,
      title: t('page.nacos.displayAddress'),
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (val: unknown, record: NacosInstance) => {
        const extra = record as unknown as { createTime?: unknown; gmtCreate?: unknown };
        const t = val ?? record.createAt ?? extra.createTime ?? extra.gmtCreate;
        if (t === null || t === undefined || t === '') return '-';
        return dayjs(t as string | number | Date).format('YYYY-MM-DD HH:mm:ss');
      },
      title: t('product.overview.createAt'),
    },
    {
      key: 'action',
      render: (_: NacosInstance, record: NacosInstance) => (
        <div className="flex items-center gap-1">
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
            disabled={record.isDefault}
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.nacosId, record.nacosName)}
            title={record.isDefault ? t('page.nacos.defaultDeleteDisabled') : undefined}
            type="text"
          >
            {t('common.delete')}
          </Button>
        </div>
      ),
      title: t('common.operation'),
      width: 180,
    },
  ];

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button
            icon={<PlusOutlined />}
            onClick={() => setTypeSelectorVisible(true)}
            type="primary"
          >
            {t('page.nacos.import')}
          </Button>
        }
        description={t('page.nacos.description')}
        title={t('page.nacos.title')}
      />

      <DataTable<NacosInstance>
        columns={columns}
        dataSource={nacosInstances}
        loading={loading}
        pagination={{
          current: currentPage,
          onChange: handlePageChange,
          pageSize: pageSize,
          total: total,
        }}
        rowKey="nacosId"
      />

      {/* 开源 Nacos 创建/编辑弹窗（保持原有） */}
      <Modal
        cancelText={t('common.cancel')}
        okText={editingNacos ? t('action.update') : t('action.create')}
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={modalVisible}
        title={editingNacos ? t('page.nacos.editTitle') : t('page.nacos.createTitle')}
        width={600}
      >
        <Form form={form} initialValues={{}} layout="vertical">
          <Form.Item
            label={t('page.nacos.instanceName')}
            name="nacosName"
            rules={[{ message: t('page.nacos.nameRequired'), required: true }]}
          >
            <Input placeholder={t('page.nacos.namePlaceholder')} />
          </Form.Item>

          <Form.Item
            label={t('page.nacos.serverAddress')}
            name="serverUrl"
            rules={[{ message: t('page.nacos.serverAddressRequired'), required: true }]}
          >
            {importEndpoints.internet || importEndpoints.intranet ? (
              <Select
                onChange={() => {
                  /* 地址变更无需处理命名空间 */
                }}
                options={[
                  ...(importEndpoints.internet
                    ? [
                        {
                          label: t('page.nacos.internetAddress', {
                            value: importEndpoints.internet,
                          }),
                          value: importEndpoints.internet,
                        },
                      ]
                    : []),
                  ...(importEndpoints.intranet
                    ? [
                        {
                          label: t('page.nacos.intranetAddress', {
                            value: importEndpoints.intranet,
                          }),
                          value: importEndpoints.intranet,
                        },
                      ]
                    : []),
                ]}
                placeholder={t('page.nacos.selectAddress')}
              />
            ) : (
              <Input
                onChange={() => {
                  // 已移除 namespace 重置
                }}
                placeholder="http://localhost:8848"
              />
            )}
          </Form.Item>
          {/* 命名空间字段已移除 */}

          <Form.Item
            extra={t('page.nacos.displayAddressExtra')}
            label={t('page.nacos.displayAddress')}
            name="displayServerUrl"
          >
            <Input placeholder="https://nacos.example.com:8848" />
          </Form.Item>

          {/* 用户名/密码改为非必填 */}
          <Form.Item label={t('page.nacos.username')} name="username" rules={[]}>
            <Input placeholder={t('page.nacos.usernamePlaceholder')} />
          </Form.Item>

          {/* 编辑和创建都允许填写密码（可选） */}
          <Form.Item label={t('page.nacos.password')} name="password" rules={[]}>
            <Input.Password placeholder={t('page.nacos.passwordPlaceholder')} />
          </Form.Item>

          {/* AK/SK：编辑时允许修改；创建时仅在 MSE 导入展示 */}
          {(editingNacos || creationMode === 'MSE') && (
            <>
              <Form.Item label="Access Key" name="accessKey" rules={[]}>
                <Input placeholder={t('page.nacos.accessKeyPlaceholder')} />
              </Form.Item>
              <Form.Item label="Secret Key" name="secretKey" rules={[]}>
                <Input.Password placeholder={t('page.nacos.secretKeyPlaceholder')} />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      {/* 导入类型选择器 */}
      <NacosTypeSelector
        onCancel={() => setTypeSelectorVisible(false)}
        onSelect={(type: NacosImportType) => {
          setTypeSelectorVisible(false);
          if (type === 'MSE') {
            setMseImportVisible(true);
          } else {
            setEditingNacos(null);
            setCreationMode('OPEN_SOURCE');
            setImportEndpoints({});
            setModalVisible(true);
          }
        }}
        visible={typeSelectorVisible}
      />

      {/* MSE 导入弹窗 */}
      <ImportMseNacosModal
        onCancel={() => setMseImportVisible(false)}
        onPrefill={(values) => {
          // 打开创建弹窗并回填数据，等待用户补充后提交
          setMseImportVisible(false);
          setEditingNacos(null);
          setModalVisible(true);
          setCreationMode('MSE');
          setImportEndpoints({
            internet: values.serverInternetEndpoint,
            intranet: values.serverIntranetEndpoint,
          });
          form.setFieldsValue({
            accessKey: values.accessKey,
            nacosName: values.nacosName,
            secretKey: values.secretKey,
            serverUrl: values.serverUrl,
          });
          // 保存导入来源的 nacosId
          setImportNacosId(values.nacosId || null);
        }}
        visible={mseImportVisible}
      />

      {/* 设置默认 Nacos 实例 + 命名空间弹窗 */}
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={setDefaultSaving}
        okText={t('common.confirm')}
        onCancel={() => {
          setSetDefaultVisible(false);
          setSetDefaultNacosId('');
          setSetDefaultNamespaces([]);
        }}
        onOk={handleSaveDefault}
        open={setDefaultVisible}
        title={t('page.nacos.setDefaultTitle')}
        width={480}
      >
        <div className="space-y-4">
          <div>
            <div className="text-sm text-gray-700 mb-1">{t('page.nacos.selectInstance')}</div>
            <Select
              onChange={handleNacosChange}
              options={nacosInstances.map((inst) => ({
                label: inst.nacosName,
                value: inst.nacosId,
              }))}
              placeholder={t('page.nacos.selectInstance')}
              style={{ width: '100%' }}
              value={setDefaultNacosId || undefined}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 mb-1">{t('page.nacos.selectNamespace')}</div>
            <Select
              loading={setDefaultNsLoading}
              onChange={(val) => setSetDefaultSelectedNs(val)}
              options={setDefaultNamespaces.map((ns: NacosNamespace) => ({
                label: `${ns.namespaceName || ns.namespaceId}${ns.namespaceDesc ? ` (${ns.namespaceDesc})` : ''}`,
                value: ns.namespaceId || 'public',
              }))}
              placeholder={t('page.nacos.selectNamespace')}
              style={{ width: '100%' }}
              value={setDefaultSelectedNs}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
