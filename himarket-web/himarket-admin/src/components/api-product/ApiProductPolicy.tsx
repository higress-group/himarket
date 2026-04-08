import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SettingOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { Card, Button, Table, Tag, Space, Modal, Form, Input, Select, Switch, message } from 'antd';
import { useState } from 'react';

import { formatDateTime } from '@/lib/utils';
import type { ApiProduct } from '@/types/api-product';

interface ApiProductPolicyProps {
  apiProduct: ApiProduct;
}

interface Policy {
  id: string;
  name: string;
  type: string;
  status: string;
  description: string;
  createdAt: string;
  config: Record<string, unknown>;
}

const mockPolicies: Policy[] = [
  {
    config: {
      hour: 1000,
      minute: 100,
    },
    createdAt: '2025-01-01T10:00:00Z',
    description: '限制API调用频率',
    id: '1',
    name: 'Rate Limiting',
    status: 'enabled',
    type: 'rate-limiting',
  },
  {
    config: {
      hide_credentials: true,
      key_names: ['apikey'],
    },
    createdAt: '2025-01-02T11:00:00Z',
    description: 'API密钥认证',
    id: '2',
    name: 'Authentication',
    status: 'enabled',
    type: 'key-auth',
  },
  {
    config: {
      methods: ['GET', 'POST', 'PUT', 'DELETE'],
      origins: ['*'],
    },
    createdAt: '2025-01-03T12:00:00Z',
    description: '跨域资源共享',
    id: '3',
    name: 'CORS',
    status: 'enabled',
    type: 'cors',
  },
];

export function ApiProductPolicy({}: ApiProductPolicyProps) {
  const [policies, setPolicies] = useState<Policy[]>(mockPolicies);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<Policy | null>(null);
  const [form] = Form.useForm();

  const columns = [
    {
      dataIndex: 'name',
      key: 'name',
      title: '策略名称',
    },
    {
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const typeMap: { [key: string]: string } = {
          acl: '访问控制',
          cors: 'CORS',
          'key-auth': '认证',
          'rate-limiting': '限流',
        };
        return <Tag color="blue">{typeMap[type] || type}</Tag>;
      },
      title: '类型',
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'enabled' ? 'green' : 'red'}>
          {status === 'enabled' ? '启用' : '禁用'}
        </Tag>
      ),
      title: '状态',
    },
    {
      dataIndex: 'description',
      ellipsis: true,
      key: 'description',
      title: '描述',
    },
    {
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => formatDateTime(date),
      title: '创建时间',
    },
    {
      key: 'action',
      render: (_text: unknown, record: Policy) => (
        <Space size="middle">
          <Button icon={<SettingOutlined />} type="link">
            配置
          </Button>
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)} type="link">
            编辑
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.id, record.name)}
            type="link"
          >
            删除
          </Button>
        </Space>
      ),
      title: '操作',
    },
  ];

  const handleAdd = () => {
    setEditingPolicy(null);
    setIsModalVisible(true);
  };

  const handleEdit = (policy: Policy) => {
    setEditingPolicy(policy);
    form.setFieldsValue({
      description: policy.description,
      name: policy.name,
      status: policy.status,
      type: policy.type,
    });
    setIsModalVisible(true);
  };

  const handleDelete = (id: string, policyName: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要删除策略 "${policyName}" 吗？此操作不可恢复。`,
      icon: <ExclamationCircleOutlined />,
      okText: '确认删除',
      okType: 'danger',
      onOk() {
        setPolicies(policies.filter((policy) => policy.id !== id));
        message.success('策略删除成功');
      },
      title: '确认删除',
    });
  };

  const handleModalOk = () => {
    form.validateFields().then((values) => {
      if (editingPolicy) {
        // 编辑现有策略
        setPolicies(
          policies.map((policy) =>
            policy.id === editingPolicy.id ? { ...policy, ...values } : policy,
          ),
        );
      } else {
        // 添加新策略
        const newPolicy: Policy = {
          config: {},
          createdAt: new Date().toISOString(),
          description: values.description,
          id: Date.now().toString(),
          name: values.name,
          status: values.status,
          type: values.type,
        };
        setPolicies([...policies, newPolicy]);
      }
      setIsModalVisible(false);
      form.resetFields();
      setEditingPolicy(null);
    });
  };

  const handleModalCancel = () => {
    setIsModalVisible(false);
    form.resetFields();
    setEditingPolicy(null);
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">策略管理</h1>
          <p className="text-gray-600">管理API产品的策略配置</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={handleAdd} type="primary">
          添加策略
        </Button>
      </div>

      <Card>
        <Table columns={columns} dataSource={policies} pagination={false} rowKey="id" />
      </Card>

      <Card title="策略设置">
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <span>策略继承</span>
            <Switch defaultChecked />
          </div>
          <div className="flex justify-between items-center">
            <span>策略优先级</span>
            <Switch defaultChecked />
          </div>
          <div className="flex justify-between items-center">
            <span>策略日志</span>
            <Switch defaultChecked />
          </div>
        </div>
      </Card>

      <Modal
        cancelText="取消"
        okText={editingPolicy ? '更新' : '添加'}
        onCancel={handleModalCancel}
        onOk={handleModalOk}
        open={isModalVisible}
        title={editingPolicy ? '编辑策略' : '添加策略'}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="策略名称"
            name="name"
            rules={[{ message: '请输入策略名称', required: true }]}
          >
            <Input placeholder="请输入策略名称" />
          </Form.Item>
          <Form.Item
            label="策略类型"
            name="type"
            rules={[{ message: '请选择策略类型', required: true }]}
          >
            <Select placeholder="请选择策略类型">
              <Select.Option value="rate-limiting">限流</Select.Option>
              <Select.Option value="key-auth">认证</Select.Option>
              <Select.Option value="cors">CORS</Select.Option>
              <Select.Option value="acl">访问控制</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            label="描述"
            name="description"
            rules={[{ message: '请输入策略描述', required: true }]}
          >
            <Input.TextArea placeholder="请输入策略描述" rows={3} />
          </Form.Item>
          <Form.Item label="状态" name="status" rules={[{ message: '请选择状态', required: true }]}>
            <Select placeholder="请选择状态">
              <Select.Option value="enabled">启用</Select.Option>
              <Select.Option value="disabled">禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
