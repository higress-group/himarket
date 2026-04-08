import { Modal, Form, Input, message } from 'antd';
import { useEffect } from 'react';

import { portalApi } from '@/lib/api';
import type { Portal } from '@/types';

interface PortalFormModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  portal: Portal | null;
}

export default function PortalFormModal({
  onCancel,
  onSuccess,
  portal,
  visible,
}: PortalFormModalProps) {
  const [form] = Form.useForm();

  useEffect(() => {
    if (visible && portal) {
      form.setFieldsValue({
        description: portal.description || '',
        name: portal.name,
      });
    }
  }, [visible, portal, form]);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (!portal) return;

      await portalApi.updatePortal(portal.portalId, {
        description: values.description,
        name: values.name,
        portalDomainConfig: portal.portalDomainConfig,
        portalSettingConfig: portal.portalSettingConfig,
        portalUiConfig: portal.portalUiConfig,
      });

      message.success('Portal信息更新成功');
      form.resetFields();
      onSuccess();
    } catch (_error) {
      message.error('更新失败，请稍后重试');
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  return (
    <Modal
      cancelText="取消"
      destroyOnClose
      okText="保存"
      onCancel={handleCancel}
      onOk={handleOk}
      open={visible}
      title="编辑Portal"
      width={600}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          label="Portal名称"
          name="name"
          rules={[{ message: '请输入Portal名称', required: true }]}
        >
          <Input placeholder="请输入Portal名称" />
        </Form.Item>

        <Form.Item label="描述" name="description">
          <Input.TextArea placeholder="请输入Portal描述" rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
