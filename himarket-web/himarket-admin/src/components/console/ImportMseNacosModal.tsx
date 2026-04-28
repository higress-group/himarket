import { Button, Table, Modal, Form, Input, message } from 'antd';
import { useState } from 'react';

import { nacosApi } from '@/lib/api';

interface MseNacosItem {
  instanceId: string;
  name: string;
  serverIntranetEndpoint?: string;
  serverInternetEndpoint?: string;
  version?: string;
}

interface MseNacosAuthFormValues {
  regionId: string;
  accessKey: string;
  secretKey: string;
}

interface ImportMseNacosModalProps {
  visible: boolean;
  onCancel: () => void;
  // 将选中的 MSE Nacos 信息带入创建表单
  onPrefill: (values: {
    nacosName?: string;
    serverUrl?: string;
    serverInternetEndpoint?: string;
    serverIntranetEndpoint?: string;
    username?: string;
    password?: string;
    accessKey?: string;
    secretKey?: string;
    description?: string;
    nacosId?: string;
  }) => void;
}

export default function ImportMseNacosModal({
  onCancel,
  onPrefill,
  visible,
}: ImportMseNacosModalProps) {
  const [importForm] = Form.useForm();

  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<MseNacosItem[]>([]);
  const [selected, setSelected] = useState<MseNacosItem | null>(null);
  const [auth, setAuth] = useState<MseNacosAuthFormValues>({
    accessKey: '',
    regionId: '',
    secretKey: '',
  });
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchMseNacos = async (values: MseNacosAuthFormValues, page = 1, size = 10) => {
    setLoading(true);
    try {
      const res = await nacosApi.getMseNacos({ ...values, page, size });
      setList(res.data?.content || []);
      setPagination({ current: page, pageSize: size, total: res.data?.totalElements || 0 });
    } catch (_e: unknown) {
      // message.error(e?.response?.data?.message || '获取 MSE Nacos 列表失败')
    } finally {
      setLoading(false);
    }
  };

  const handleImport = async () => {
    if (!selected) {
      message.warning('请选择一个 Nacos 实例');
      return;
    }
    // 将关键信息带出到创建表单，供用户补充
    onPrefill({
      accessKey: auth.accessKey,
      nacosId: selected.instanceId,
      nacosName: selected.name,
      secretKey: auth.secretKey,
      serverInternetEndpoint: selected.serverInternetEndpoint,
      serverIntranetEndpoint: selected.serverIntranetEndpoint,
      serverUrl: selected.serverInternetEndpoint || selected.serverIntranetEndpoint,
    });
    handleCancel();
  };

  const handleCancel = () => {
    setSelected(null);
    setList([]);
    setPagination({ current: 1, pageSize: 10, total: 0 });
    importForm.resetFields();
    onCancel();
  };

  return (
    <Modal
      footer={null}
      onCancel={handleCancel}
      open={visible}
      title="导入 MSE Nacos 实例"
      width={800}
    >
      <Form form={importForm} layout="vertical" preserve={false}>
        {list.length === 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item
              label="Region"
              name="regionId"
              rules={[{ message: '请输入region', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Access Key"
              name="accessKey"
              rules={[{ message: '请输入accessKey', required: true }]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              label="Secret Key"
              name="secretKey"
              rules={[{ message: '请输入secretKey', required: true }]}
            >
              <Input.Password />
            </Form.Item>
            <Button
              loading={loading}
              onClick={() => {
                importForm.validateFields().then((values) => {
                  const creds = values as MseNacosAuthFormValues;
                  setAuth(creds);
                  fetchMseNacos(creds);
                });
              }}
              type="primary"
            >
              获取实例列表
            </Button>
          </div>
        )}

        {list.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">选择 Nacos 实例</h3>
            <Table
              columns={[
                { dataIndex: 'instanceId', title: '实例ID' },
                { dataIndex: 'name', title: '名称' },
                { dataIndex: 'version', title: '版本' },
              ]}
              dataSource={list}
              pagination={{
                current: pagination.current,
                onChange: (page, pageSize) => fetchMseNacos(auth, page, pageSize),
                pageSize: pagination.pageSize,
                showQuickJumper: true,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
                total: pagination.total,
              }}
              rowKey="instanceId"
              rowSelection={{
                onChange: (_selectedRowKeys, selectedRows) => setSelected(selectedRows[0] ?? null),
                selectedRowKeys: selected ? [selected.instanceId] : [],
                type: 'radio',
              }}
              size="small"
            />
          </div>
        )}

        {selected && (
          <div className="text-right">
            <Button onClick={handleImport} type="primary">
              导入
            </Button>
          </div>
        )}
      </Form>
    </Modal>
  );
}
