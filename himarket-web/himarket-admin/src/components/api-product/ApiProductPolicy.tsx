import { Card, Button, Table, Tag, Space, Modal, Form, Input, Select, Switch, message } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined, SettingOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { useState } from 'react'
import type { ApiProduct } from '@/types/api-product';
import { formatDateTime } from '@/lib/utils'

interface ApiProductPolicyProps {
  apiProduct: ApiProduct
}

interface Policy {
  id: string
  name: string
  type: string
  status: string
  description: string
  createdAt: string
  config: any
}

const mockPolicies: Policy[] = [
  {
    id: "1",
    name: "Rate Limiting",
    type: "rate-limiting",
    status: "enabled",
    description: "限制API调用频率",
    createdAt: "2025-01-01T10:00:00Z",
    config: {
      minute: 100,
      hour: 1000
    }
  },
  {
    id: "2",
    name: "Authentication",
    type: "key-auth",
    status: "enabled",
    description: "API密钥认证",
    createdAt: "2025-01-02T11:00:00Z",
    config: {
      key_names: ["apikey"],
      hide_credentials: true
    }
  },
  {
    id: "3",
    name: "CORS",
    type: "cors",
    status: "enabled",
    description: "跨域资源共享",
    createdAt: "2025-01-03T12:00:00Z",
    config: {
      origins: ["*"],
      methods: ["GET", "POST", "PUT", "DELETE"]
    }
  }
]

export function ApiProductPolicy({ }: ApiProductPolicyProps) {
  const [policies, setPolicies] = useState<Policy[]>(mockPolicies)
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [editingPolicy, setEditingPolicy] = useState<Policy | null>(null)
  const [form] = Form.useForm()

  const columns = [
    {
      title: '策略名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => {
        const typeMap: { [key: string]: string } = {
          'rate-limiting': '限流',
          'key-auth': '认证',
          'cors': 'CORS',
          'acl': '访问控制'
        }
        return <Tag color="blue">{typeMap[type] || type}</Tag>
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'enabled' ? 'green' : 'red'}>
          {status === 'enabled' ? '启用' : '禁用'}
        </Tag>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Policy) => (
        <Space size="middle">
          <Button type="link" icon={<SettingOutlined />}>
            配置
          </Button>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button 
            type="link" 
            danger 
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.id, record.name)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ]

  const handleAdd = () => {
    setEditingPolicy(null)
    setIsModalVisible(true)
  }

  const handleEdit = (policy: Policy) => {
    setEditingPolicy(policy)
    form.setFieldsValue({
      name: policy.name,
      type: policy.type,
      description: policy.description,
      status: policy.status
    })
    setIsModalVisible(true)
  }

  const handleDelete = (id: string, policyName: string) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除策略 "${policyName}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        setPolicies(policies.filter(policy => policy.id !== id))
        message.success('策略删除成功')
      },
    })
  }

  const handleModalOk = () => {
    form.validateFields().then((values) => {
      if (editingPolicy) {
        // 编辑现有策略
        setPolicies(policies.map(policy => 
          policy.id === editingPolicy.id 
            ? { ...policy, ...values }
            : policy
        ))
      } else {
        // 添加新策略
        const newPolicy: Policy = {
          id: Date.now().toString(),
          name: values.name,
          type: values.type,
          status: values.status,
          description: values.description,
          createdAt: new Date().toISOString(),
          config: {}
        }
        setPolicies([...policies, newPolicy])
      }
      setIsModalVisible(false)
      form.resetFields()
      setEditingPolicy(null)
    })
  }

  const handleModalCancel = () => {
    setIsModalVisible(false)
    form.resetFields()
    setEditingPolicy(null)
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">策略管理</h1>
          <p className="text-gray-600">管理API产品的策略配置</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          添加策略
        </Button>
      </div>

      <Card>
        <Table 
          columns={columns} 
          dataSource={policies}
          rowKey="id"
          pagination={false}
        />
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
        title={editingPolicy ? "编辑策略" : "添加策略"}
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okText={editingPolicy ? "更新" : "添加"}
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="策略名称"
            rules={[{ required: true, message: '请输入策略名称' }]}
          >
            <Input placeholder="请输入策略名称" />
          </Form.Item>
          <Form.Item
            name="type"
            label="策略类型"
            rules={[{ required: true, message: '请选择策略类型' }]}
          >
            <Select placeholder="请选择策略类型">
              <Select.Option value="rate-limiting">限流</Select.Option>
              <Select.Option value="key-auth">认证</Select.Option>
              <Select.Option value="cors">CORS</Select.Option>
              <Select.Option value="acl">访问控制</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
            rules={[{ required: true, message: '请输入策略描述' }]}
          >
            <Input.TextArea placeholder="请输入策略描述" rows={3} />
          </Form.Item>
          <Form.Item
            name="status"
            label="状态"
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select placeholder="请选择状态">
              <Select.Option value="enabled">启用</Select.Option>
              <Select.Option value="disabled">禁用</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
} 