import { CheckCircleOutlined, DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Form, Input, message, Modal, Select, Tooltip } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';

import { AdminPageHeader } from '@/components/common';
import { DataTable } from '@/components/common/DataTable';
import { useLocale } from '@/contexts/LocaleContext';
import { adminSettingApi, airegistryApi } from '@/lib/api';
import { copyToClipboard } from '@/lib/utils';
import type { CreateAiRegistryRequest, UpdateAiRegistryRequest } from '@/types';
import type { AiRegistryInstance } from '@/types/gateway';

import type { TableProps } from 'antd';

type FormValues = CreateAiRegistryRequest & {
  description?: string;
  endpoint?: string;
  securityToken?: string;
};

type SkillRegistryType = 'NACOS' | 'AIREGISTRY';

export default function AiRegistryConsoles() {
  const { t } = useLocale();
  const [instances, setInstances] = useState<AiRegistryInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editing, setEditing] = useState<AiRegistryInstance | null>(null);
  const [saving, setSaving] = useState(false);
  const [validatingId, setValidatingId] = useState<string | null>(null);
  const [form] = Form.useForm<FormValues>();
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [defaultRegistryType, setDefaultRegistryType] = useState<SkillRegistryType>('NACOS');
  const [defaultRegistrySaving, setDefaultRegistrySaving] = useState(false);
  const lastAutoFetchKeyRef = useRef('');

  const fetchInstances = useCallback(async () => {
    setLoading(true);
    try {
      const response = await airegistryApi.list({ page: currentPage, size: pageSize });
      setInstances(response.data?.content || []);
      setTotal(response.data?.totalElements || 0);
    } finally {
      setLoading(false);
    }
  }, [currentPage, pageSize]);

  useEffect(() => {
    const key = `${currentPage}-${pageSize}`;
    if (lastAutoFetchKeyRef.current === key) {
      return;
    }
    lastAutoFetchKeyRef.current = key;
    fetchInstances();
  }, [currentPage, fetchInstances, pageSize]);

  useEffect(() => {
    adminSettingApi
      .getSetting('defaultSkillRegistryType')
      .then((response: { data?: { settingValue?: string } }) => {
        setDefaultRegistryType(
          response.data?.settingValue === 'AIREGISTRY' ? 'AIREGISTRY' : 'NACOS',
        );
      })
      .catch(() => {});
  }, []);

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page);
    if (size) {
      setPageSize(size);
    }
  };

  const handleCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (record: AiRegistryInstance) => {
    setEditing(record);
    form.setFieldsValue({
      description: record.description,
      endpoint: record.endpoint,
      name: record.name,
      namespaceId: record.namespaceId,
      regionId: record.regionId,
    });
    setModalVisible(true);
  };

  const handleDelete = async (record: AiRegistryInstance) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('page.airegistry.deleteConfirm', { name: record.name }),
      okText: t('common.confirmDelete'),
      okType: 'danger',
      onOk: async () => {
        await airegistryApi.delete(record.airegistryId);
        message.success(t('common.deleteSuccess'));
        fetchInstances();
      },
      title: t('common.confirmDelete'),
    });
  };

  const handleValidate = async (record: AiRegistryInstance) => {
    setValidatingId(record.airegistryId);
    try {
      await airegistryApi.validate(record.airegistryId, record.namespaceId);
      message.success(t('page.airegistry.validateSuccess'));
    } finally {
      setValidatingId(null);
    }
  };

  const handleSetDefault = async (record: AiRegistryInstance) => {
    await airegistryApi.setDefault(record.airegistryId, record.namespaceId);
    message.success(t('page.airegistry.saveDefault'));
    fetchInstances();
  };

  const handleDefaultRegistryTypeChange = async (value: SkillRegistryType) => {
    setDefaultRegistryType(value);
    setDefaultRegistrySaving(true);
    try {
      await adminSettingApi.saveSetting('defaultSkillRegistryType', value);
      message.success(t('page.airegistry.defaultRegistrySaved'));
    } finally {
      setDefaultRegistrySaving(false);
    }
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: Partial<FormValues> = { ...values };
    ['accessKeyId', 'accessKeySecret', 'securityToken'].forEach((key) => {
      if (payload[key] === undefined || payload[key] === null || payload[key] === '') {
        delete payload[key];
      }
    });

    setSaving(true);
    try {
      if (editing) {
        await airegistryApi.update(editing.airegistryId, payload as UpdateAiRegistryRequest);
        message.success(t('page.categoryDetail.updateSuccess'));
      } else {
        await airegistryApi.create(payload as CreateAiRegistryRequest);
        message.success(t('page.categoryDetail.createSuccess'));
      }
      setModalVisible(false);
      form.resetFields();
      fetchInstances();
    } finally {
      setSaving(false);
    }
  };

  const columns: TableProps<AiRegistryInstance>['columns'] = [
    {
      key: 'nameAndId',
      render: (_text: unknown, record: AiRegistryInstance) => (
        <div className="flex flex-col">
          <span className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-900 truncate">{record.name}</span>
            {record.isDefault && (
              <button
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-slate-800 text-white border-none"
                type="button"
              >
                {t('page.airegistry.default')}
              </button>
            )}
          </span>
          <Tooltip title={t('common.copyToClipboard')}>
            <button
              className="text-xs text-gray-400 mt-0.5 truncate max-w-[220px] cursor-pointer hover:text-blue-500 bg-transparent border-none p-0 block text-left"
              onClick={() =>
                copyToClipboard(record.airegistryId).then(() => {
                  message.success(t('common.copiedToClipboard'));
                })
              }
              type="button"
            >
              {record.airegistryId}
            </button>
          </Tooltip>
        </div>
      ),
      title: t('page.airegistry.nameAndId'),
      width: 280,
    },
    {
      dataIndex: 'regionId',
      key: 'regionId',
      title: t('page.airegistry.regionId'),
      width: 160,
    },
    {
      dataIndex: 'namespaceId',
      key: 'namespaceId',
      title: t('page.airegistry.namespaceId'),
      width: 220,
    },
    {
      dataIndex: 'endpoint',
      key: 'endpoint',
      render: (endpoint?: string) => endpoint || '-',
      title: t('page.airegistry.endpoint'),
    },
    {
      dataIndex: 'createAt',
      key: 'createAt',
      render: (value?: string | number) =>
        value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-',
      title: t('product.overview.createAt'),
      width: 180,
    },
    {
      key: 'operation',
      render: (_text: unknown, record: AiRegistryInstance) => (
        <div className="flex items-center gap-1">
          <Button
            className="text-green-600 hover:text-green-700 hover:bg-green-50 !px-2 text-xs"
            icon={<CheckCircleOutlined />}
            loading={validatingId === record.airegistryId}
            onClick={() => handleValidate(record)}
            type="text"
          >
            {t('page.airegistry.validate')}
          </Button>
          {!record.isDefault && (
            <Button
              className="text-gray-600 hover:text-gray-700 hover:bg-gray-50 !px-2 text-xs"
              onClick={() => handleSetDefault(record)}
              type="text"
            >
              {t('page.airegistry.setDefault')}
            </Button>
          )}
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
            onClick={() => handleDelete(record)}
            title={record.isDefault ? t('page.airegistry.defaultDeleteDisabled') : undefined}
            type="text"
          >
            {t('common.delete')}
          </Button>
        </div>
      ),
      title: t('common.operation'),
      width: 280,
    },
  ];

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <Button icon={<PlusOutlined />} onClick={handleCreate} type="primary">
            {t('page.airegistry.create')}
          </Button>
        }
        description={t('page.airegistry.description')}
        title={t('page.airegistry.title')}
      />

      <div className="rounded-lg border border-gray-100 bg-white p-4 flex items-center justify-between gap-4">
        <div>
          <div className="text-sm font-medium text-gray-900">
            {t('page.airegistry.defaultRegistryType')}
          </div>
          <div className="text-xs text-gray-500 mt-1">
            {t('page.airegistry.defaultRegistryDescription')}
          </div>
        </div>
        <Select
          loading={defaultRegistrySaving}
          onChange={handleDefaultRegistryTypeChange}
          options={[
            { label: 'Nacos', value: 'NACOS' },
            { label: t('nav.airegistryInstances'), value: 'AIREGISTRY' },
          ]}
          style={{ minWidth: 180 }}
          value={defaultRegistryType}
        />
      </div>

      <DataTable<AiRegistryInstance>
        columns={columns}
        dataSource={instances}
        loading={loading}
        pagination={{
          current: currentPage,
          onChange: handlePageChange,
          pageSize,
          total,
        }}
        rowKey="airegistryId"
      />

      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={saving}
        okText={editing ? t('action.update') : t('action.create')}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={handleSave}
        open={modalVisible}
        title={editing ? t('page.airegistry.editTitle') : t('page.airegistry.createTitle')}
        width={620}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label={t('page.airegistry.name')}
            name="name"
            rules={[{ message: t('page.airegistry.nameRequired'), required: true }]}
          >
            <Input placeholder={t('page.airegistry.namePlaceholder')} />
          </Form.Item>
          <Form.Item
            label={t('page.airegistry.regionId')}
            name="regionId"
            rules={[{ message: t('page.airegistry.regionIdRequired'), required: true }]}
          >
            <Input placeholder="cn-hangzhou" />
          </Form.Item>
          <Form.Item label={t('page.airegistry.endpoint')} name="endpoint">
            <Input placeholder="airegistry.cn-hangzhou.aliyuncs.com" />
          </Form.Item>
          <Form.Item
            label={t('page.airegistry.namespaceId')}
            name="namespaceId"
            rules={[{ message: t('page.airegistry.namespaceIdRequired'), required: true }]}
          >
            <Input placeholder={t('page.airegistry.namespaceIdPlaceholder')} />
          </Form.Item>
          <Form.Item
            label="Access Key ID"
            name="accessKeyId"
            rules={
              editing ? [] : [{ message: t('page.airegistry.accessKeyIdRequired'), required: true }]
            }
          >
            <Input
              placeholder={editing ? t('page.airegistry.accessKeyIdPlaceholder') : 'LTAI...'}
            />
          </Form.Item>
          <Form.Item
            label="Access Key Secret"
            name="accessKeySecret"
            rules={
              editing
                ? []
                : [{ message: t('page.airegistry.accessKeySecretRequired'), required: true }]
            }
          >
            <Input.Password placeholder={t('page.airegistry.secretPlaceholder')} />
          </Form.Item>
          <Form.Item label="Security Token" name="securityToken">
            <Input.Password placeholder={t('page.airegistry.tokenPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('common.description')} name="description">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
