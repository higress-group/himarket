import { PlusOutlined, ExclamationCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Modal, Form, Input, message, Space } from 'antd';
import { useState } from 'react';

import { DataTable } from '@/components/common/DataTable';
import { useLocale } from '@/contexts/LocaleContext';
import { portalApi } from '@/lib/api';
import type { Portal, PortalDomainConfig } from '@/types';

interface PortalDomainProps {
  portal: Portal;
  onRefresh?: () => void;
}

export function PortalDomain({ onRefresh, portal }: PortalDomainProps) {
  const { t } = useLocale();
  const [domainModalVisible, setDomainModalVisible] = useState(false);
  const [domainForm] = Form.useForm();
  const [domainLoading, setDomainLoading] = useState(false);

  const handleAddDomain = () => {
    setDomainModalVisible(true);
  };

  const handleDomainModalOk = async () => {
    try {
      setDomainLoading(true);
      const values = await domainForm.validateFields();

      await portalApi.bindDomain(portal.portalId, {
        domain: values.domain,
        type: 'CUSTOM',
      });

      message.success(t('portal.domain.bindSuccess'));
      setDomainModalVisible(false);
      domainForm.resetFields();
      onRefresh?.();
    } catch (_error) {
      message.error(t('portal.domain.bindFailed'));
    } finally {
      setDomainLoading(false);
    }
  };

  const handleDomainModalCancel = () => {
    setDomainModalVisible(false);
    domainForm.resetFields();
  };

  const handleDeleteDomain = async (domain: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('portal.domain.unbindConfirm', { domain }),
      icon: <ExclamationCircleOutlined />,
      okText: t('portal.domain.unbindTitle'),
      okType: 'danger',
      async onOk() {
        try {
          await portalApi.unbindDomain(portal.portalId, domain);
          message.success(t('portal.domain.unbindSuccess'));
          onRefresh?.();
        } catch (_error) {
          message.error(t('portal.domain.unbindFailed'));
        }
      },
      title: t('portal.domain.unbindTitle'),
    });
  };

  const domains = portal.portalDomainConfig || [];

  const domainColumns = [
    {
      dataIndex: 'domain',
      key: 'domain',
      title: t('common.domain'),
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) =>
        type === 'CUSTOM' ? t('portal.domain.custom') : t('portal.domain.system'),
      title: t('common.type'),
    },
    {
      key: 'action',
      render: (_: unknown, record: PortalDomainConfig) =>
        record.type === 'CUSTOM' ? (
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDeleteDomain(record.domain)}
            type="text"
          />
        ) : (
          <span className="text-gray-400">-</span>
        ),
      title: t('common.operation'),
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">{t('portal.domain.title')}</h1>
          <p className="text-gray-600">{t('portal.domain.description')}</p>
        </div>
        <Space>
          <Button icon={<PlusOutlined />} onClick={handleAddDomain} type="primary">
            {t('portal.domain.bind')}
          </Button>
        </Space>
      </div>

      <DataTable<PortalDomainConfig>
        columns={domainColumns}
        dataSource={domains}
        locale={{
          emptyText: t('portal.domain.empty'),
        }}
        rowKey="domain"
      />

      {/* 域名绑定模态框 */}
      <Modal
        cancelText={t('common.cancel')}
        confirmLoading={domainLoading}
        destroyOnClose
        onCancel={handleDomainModalCancel}
        onOk={handleDomainModalOk}
        open={domainModalVisible}
        title={t('portal.domain.bind')}
      >
        <Form form={domainForm} layout="vertical">
          <Form.Item
            label={t('common.domain')}
            name="domain"
            rules={[{ message: t('portal.domain.required'), required: true }]}
          >
            <Input placeholder={t('portal.domain.inputPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
