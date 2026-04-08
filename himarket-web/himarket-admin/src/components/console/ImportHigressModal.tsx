import { Button, Modal, Form, Input, message } from 'antd';
import { useState } from 'react';

import { gatewayApi } from '@/lib/api';

interface ImportHigressModalProps {
  visible: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

export default function ImportHigressModal({
  onCancel,
  onSuccess,
  visible,
}: ImportHigressModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: Record<string, unknown>) => {
    setLoading(true);
    try {
      // 构建请求参数，将 apiOptions 改为 apiConfig
      const requestData = {
        gatewayName: values.gatewayName,
        gatewayType: 'HIGRESS',
        higressConfig: {
          address: values.address,
          gatewayAddress: values.gatewayAddress,
          password: values.password,
          username: values.username,
        },
      };

      await gatewayApi.importGateway(requestData);
      message.success('导入成功！');
      handleCancel();
      onSuccess();
    } catch (_error: unknown) {
      // message.error(error.response?.data?.message || '导入失败！')
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onCancel();
  };

  return (
    <Modal
      footer={null}
      onCancel={handleCancel}
      open={visible}
      title="导入 Higress 网关"
      width={600}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} preserve={false}>
        <Form.Item
          label="网关名称"
          name="gatewayName"
          rules={[{ message: '请输入网关名称', required: true }]}
        >
          <Input placeholder="请输入网关名称" />
        </Form.Item>

        <Form.Item
          label="Console地址"
          name="address"
          rules={[{ message: '请输入Console地址', required: true }]}
          tooltip={{
            overlayInnerStyle: {
              backgroundColor: '#ffffff',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
              color: '#000000',
            },
            overlayStyle: {
              maxWidth: '300px',
            },
            placement: 'topLeft',
            title: (
              <div
                style={{
                  backgroundColor: '#ffffff',
                  color: '#000000',
                  fontSize: '13px',
                  lineHeight: '1.4',
                  padding: '4px 0',
                }}
              >
                higress-console服务的地址
              </div>
            ),
          }}
        >
          <Input placeholder="例如：higress.example.com" />
        </Form.Item>

        <Form.Item
          label="用户名"
          name="username"
          rules={[{ message: '请输入用户名', required: true }]}
        >
          <Input placeholder="请输入用户名" />
        </Form.Item>

        <Form.Item label="密码" name="password" rules={[{ message: '请输入密码', required: true }]}>
          <Input.Password placeholder="请输入密码" />
        </Form.Item>

        <Form.Item
          label="Gateway地址"
          name="gatewayAddress"
          tooltip={{
            overlayInnerStyle: {
              backgroundColor: '#ffffff',
              border: '1px solid #d9d9d9',
              borderRadius: '6px',
              boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
              color: '#000000',
            },
            overlayStyle: {
              maxWidth: '300px',
            },
            placement: 'topLeft',
            title: (
              <div
                style={{
                  backgroundColor: '#ffffff',
                  color: '#000000',
                  fontSize: '13px',
                  lineHeight: '1.4',
                  padding: '4px 0',
                }}
              >
                higress-gateway服务的地址
              </div>
            ),
          }}
        >
          <Input placeholder="例如：gateway.example.com（可选）" />
        </Form.Item>

        <div className="flex justify-end space-x-2 pt-4">
          <Button onClick={handleCancel}>取消</Button>
          <Button htmlType="submit" loading={loading} type="primary">
            导入
          </Button>
        </div>
      </Form>
    </Modal>
  );
}
