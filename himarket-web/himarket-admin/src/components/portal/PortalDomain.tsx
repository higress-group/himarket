import { PlusOutlined, ExclamationCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Table, Modal, Form, Input, message, Space } from 'antd';
import { useState } from 'react';

import { portalApi } from '@/lib/api';
import type { Portal, PortalDomainConfig } from '@/types';

interface PortalDomainProps {
  portal: Portal;
  onRefresh?: () => void;
}

export function PortalDomain({ onRefresh, portal }: PortalDomainProps) {
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

      message.success('域名绑定成功');
      setDomainModalVisible(false);
      domainForm.resetFields();
      onRefresh?.();
    } catch (_error) {
      message.error('绑定域名失败');
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
      cancelText: '取消',
      content: `确定要解绑域名 "${domain}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认解绑',
      okType: 'danger',
      async onOk() {
        try {
          await portalApi.unbindDomain(portal.portalId, domain);
          message.success('域名解绑成功');
          onRefresh?.();
        } catch (_error) {
          message.error('解绑域名失败');
        }
      },
      title: '确认解绑',
    });
  };

  const domains = portal.portalDomainConfig || [];

  const domainColumns = [
    {
      dataIndex: 'domain',
      key: 'domain',
      title: '域名',
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => (type === 'CUSTOM' ? '自定义' : '系统域名'),
      title: '类型',
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
      title: '操作',
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">域名列表</h1>
          <p className="text-gray-600">管理Portal的域名配置</p>
        </div>
        <Space>
          <Button icon={<PlusOutlined />} onClick={handleAddDomain} type="primary">
            绑定域名
          </Button>
        </Space>
      </div>

      <Table
        columns={domainColumns}
        dataSource={domains}
        locale={{
          emptyText: '暂无绑定域名',
        }}
        pagination={false}
        rowKey="domain"
        size="small"
      />

      {/* 域名绑定模态框 */}
      <Modal
        confirmLoading={domainLoading}
        destroyOnClose
        onCancel={handleDomainModalCancel}
        onOk={handleDomainModalOk}
        open={domainModalVisible}
        title="绑定域名"
      >
        <Form form={domainForm} layout="vertical">
          <Form.Item
            label="域名"
            name="domain"
            rules={[{ message: '请输入要绑定的域名', required: true }]}
          >
            <Input placeholder="例如：example.com" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
