import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Modal, Form, Input, message, Select, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { useState, useEffect, useCallback } from 'react';

import { DataTable } from '@/components/common/DataTable';
import ImportMseNacosModal from '@/components/console/ImportMseNacosModal';
import type { NacosImportType } from '@/components/console/NacosTypeSelector';
import NacosTypeSelector from '@/components/console/NacosTypeSelector';
import { nacosApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { CreateNacosRequest, UpdateNacosRequest } from '@/types';
import type { NacosInstance, NacosNamespace } from '@/types/gateway';

// 开源创建表单数据由 antd 表单直接管理，无需额外类型声明

export default function NacosConsoles() {
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
      message.success(`成功删除Nacos实例: ${nacosName}`);
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
      message.error('获取命名空间列表失败，请检查 Nacos 连接信息');
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
      message.success('已设为默认 Nacos 实例');
      setSetDefaultVisible(false);
      fetchNacosInstances();
    } catch {
      message.error('设置默认失败');
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
        message.success('更新成功');
      } else {
        // 创建模式
        // 若是 MSE 导入来源并带有 importNacosId，则将其作为 nacosId 一并提交
        if (creationMode === 'MSE' && importNacosId) {
          payload.nacosId = importNacosId;
        }
        await nacosApi.createNacos(payload as CreateNacosRequest);
        message.success('创建成功');
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
                默认
                <EditOutlined style={{ fontSize: '10px' }} />
              </button>
            )}
          </span>
          <Tooltip title="点击复制">
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[200px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.nacosId).then(() => {
                  message.success('已复制到剪贴板');
                })
              }
              type="button"
            >
              {record.nacosId}
            </button>
          </Tooltip>
          {record.isDefault && (
            <span className="text-xs text-gray-400 mt-0.5">
              默认命名空间: {record.defaultNamespace || 'public'}
            </span>
          )}
        </div>
      ),
      title: '实例名称/ID',
    },
    {
      dataIndex: 'serverUrl',
      key: 'serverUrl',
      title: '服务器地址',
    },
    {
      dataIndex: 'displayServerUrl',
      key: 'displayServerUrl',
      render: (url: string) => url || <span style={{ color: '#999' }}>-</span>,
      title: '展示地址',
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
      title: '创建时间',
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
            编辑
          </Button>
          <Button
            className="text-red-500 hover:text-red-600 hover:bg-red-50 !px-2 text-xs"
            danger
            disabled={record.isDefault}
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.nacosId, record.nacosName)}
            title={record.isDefault ? '默认实例不允许删除' : undefined}
            type="text"
          >
            删除
          </Button>
        </div>
      ),
      title: '操作',
      width: 180,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Nacos实例管理</h1>
          <p className="text-gray-500 mt-2">管理Nacos配置中心实例</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={() => setTypeSelectorVisible(true)} type="primary">
          导入/创建实例
        </Button>
      </div>

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
        cancelText="取消"
        okText={editingNacos ? '更新' : '创建'}
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={modalVisible}
        title={editingNacos ? '编辑Nacos实例' : '创建Nacos实例'}
        width={600}
      >
        <Form form={form} initialValues={{}} layout="vertical">
          <Form.Item
            label="实例名称"
            name="nacosName"
            rules={[{ message: '请输入实例名称', required: true }]}
          >
            <Input placeholder="请输入Nacos实例名称" />
          </Form.Item>

          <Form.Item
            label="服务器地址"
            name="serverUrl"
            rules={[{ message: '请选择或输入服务器地址', required: true }]}
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
                          label: `公网地址：${importEndpoints.internet}`,
                          value: importEndpoints.internet,
                        },
                      ]
                    : []),
                  ...(importEndpoints.intranet
                    ? [
                        {
                          label: `内网地址：${importEndpoints.intranet}`,
                          value: importEndpoints.intranet,
                        },
                      ]
                    : []),
                ]}
                placeholder="请选择地址"
              />
            ) : (
              <Input
                onChange={() => {
                  // 已移除 namespace 重置
                }}
                placeholder="例如: http://localhost:8848"
              />
            )}
          </Form.Item>
          {/* 命名空间字段已移除 */}

          <Form.Item
            extra="配置后，开发者门户中 Skill / Worker 的下载命令将使用此地址替代上方的服务器地址。适用于服务器地址为内网地址、但需要通过公网域名或 IP 对外提供访问的场景。"
            label="展示地址"
            name="displayServerUrl"
          >
            <Input placeholder="例如 https://nacos.example.com:8848" />
          </Form.Item>

          {/* 用户名/密码改为非必填 */}
          <Form.Item label="用户名" name="username" rules={[]}>
            <Input placeholder="请输入Nacos用户名（可选）" />
          </Form.Item>

          {/* 编辑和创建都允许填写密码（可选） */}
          <Form.Item label="密码" name="password" rules={[]}>
            <Input.Password placeholder="请输入Nacos密码（可选）" />
          </Form.Item>

          {/* AK/SK：编辑时允许修改；创建时仅在 MSE 导入展示 */}
          {(editingNacos || creationMode === 'MSE') && (
            <>
              <Form.Item label="Access Key" name="accessKey" rules={[]}>
                <Input placeholder="可选：用于记录 AK" />
              </Form.Item>
              <Form.Item label="Secret Key" name="secretKey" rules={[]}>
                <Input.Password placeholder="可选：用于记录 SK" />
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
        cancelText="取消"
        confirmLoading={setDefaultSaving}
        okText="确认"
        onCancel={() => {
          setSetDefaultVisible(false);
          setSetDefaultNacosId('');
          setSetDefaultNamespaces([]);
        }}
        onOk={handleSaveDefault}
        open={setDefaultVisible}
        title="设置默认 Nacos 实例"
        width={480}
      >
        <div className="space-y-4">
          <div>
            <div className="text-sm text-gray-700 mb-1">选择实例</div>
            <Select
              onChange={handleNacosChange}
              options={nacosInstances.map((inst) => ({
                label: inst.nacosName,
                value: inst.nacosId,
              }))}
              placeholder="选择 Nacos 实例"
              style={{ width: '100%' }}
              value={setDefaultNacosId || undefined}
            />
          </div>
          <div>
            <div className="text-sm text-gray-700 mb-1">选择命名空间</div>
            <Select
              loading={setDefaultNsLoading}
              onChange={(val) => setSetDefaultSelectedNs(val)}
              options={setDefaultNamespaces.map((ns: NacosNamespace) => ({
                label: `${ns.namespaceName || ns.namespaceId}${ns.namespaceDesc ? ` (${ns.namespaceDesc})` : ''}`,
                value: ns.namespaceId || 'public',
              }))}
              placeholder="选择命名空间"
              style={{ width: '100%' }}
              value={setDefaultSelectedNs}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
