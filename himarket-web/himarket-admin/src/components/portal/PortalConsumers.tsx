import { SearchOutlined, UserAddOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { Card, Table, Badge, Button, Space, Avatar, Tag, Input } from 'antd';
import { useState } from 'react';

import { formatDateTime } from '@/lib/utils';
import type { Portal, DeveloperStats } from '@/types';

interface PortalConsumersProps {
  portal: Portal;
}

const mockConsumers: DeveloperStats[] = [
  {
    apiCalls: 15420,
    email: 'contact@company-a.com',
    id: '1',
    joinedAt: '2025-01-01T10:00:00Z',
    lastActive: '2025-01-08T15:30:00Z',
    name: '企业A',
    plan: 'premium',
    status: 'active',
    subscriptions: 3,
  },
  {
    apiCalls: 8765,
    email: 'dev@company-b.com',
    id: '2',
    joinedAt: '2025-01-02T11:00:00Z',
    lastActive: '2025-01-08T14:20:00Z',
    name: '企业B',
    plan: 'standard',
    status: 'active',
    subscriptions: 2,
  },
  {
    apiCalls: 1200,
    email: 'api@company-c.com',
    id: '3',
    joinedAt: '2025-01-03T12:00:00Z',
    lastActive: '2025-01-05T09:15:00Z',
    name: '企业C',
    plan: 'basic',
    status: 'inactive',
    subscriptions: 1,
  },
];

export function PortalConsumers({ portal: _portal }: PortalConsumersProps) {
  const [searchText, setSearchText] = useState('');
  const filteredConsumers = mockConsumers.filter(
    (consumer) =>
      consumer.name.toLowerCase().includes(searchText.toLowerCase()) ||
      consumer.email.toLowerCase().includes(searchText.toLowerCase()),
  );

  const getPlanColor = (plan: string) => {
    switch (plan) {
      case 'premium':
        return 'gold';
      case 'standard':
        return 'blue';
      case 'basic':
        return 'green';
      default:
        return 'default';
    }
  };

  const getPlanText = (plan: string) => {
    switch (plan) {
      case 'premium':
        return '高级版';
      case 'standard':
        return '标准版';
      case 'basic':
        return '基础版';
      default:
        return plan;
    }
  };

  const columns = [
    {
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: DeveloperStats) => (
        <div className="flex items-center space-x-3">
          <Avatar className="bg-green-500">{name.charAt(0).toUpperCase()}</Avatar>
          <div>
            <div className="font-medium">{name}</div>
            <div className="text-sm text-gray-500">{record.email}</div>
          </div>
        </div>
      ),
      title: '消费者',
    },
    {
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge
          status={status === 'active' ? 'success' : 'default'}
          text={status === 'active' ? '活跃' : '非活跃'}
        />
      ),
      title: '状态',
    },
    {
      dataIndex: 'plan',
      key: 'plan',
      render: (plan: string) => <Tag color={getPlanColor(plan)}>{getPlanText(plan)}</Tag>,
      title: '套餐',
    },
    {
      dataIndex: 'apiCalls',
      key: 'apiCalls',
      render: (calls: number) => calls.toLocaleString(),
      title: 'API调用',
    },
    {
      dataIndex: 'subscriptions',
      key: 'subscriptions',
      render: (subscriptions: number) => subscriptions.toLocaleString(),
      title: '订阅数',
    },
    {
      dataIndex: 'joinedAt',
      key: 'joinedAt',
      render: (date: string) => formatDateTime(date),
      title: '加入时间',
    },
    {
      dataIndex: 'lastActive',
      key: 'lastActive',
      render: (date: string) => formatDateTime(date),
      title: '最后活跃',
    },
    {
      key: 'action',
      render: (_text: unknown, _record: DeveloperStats) => (
        <Space size="middle">
          <Button icon={<EditOutlined />} type="link">
            编辑
          </Button>
          <Button danger icon={<DeleteOutlined />} type="link">
            删除
          </Button>
        </Space>
      ),
      title: '操作',
    },
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">消费者</h1>
          <p className="text-gray-600">管理Portal的消费者用户</p>
        </div>
        <Button icon={<UserAddOutlined />} type="primary">
          添加消费者
        </Button>
      </div>

      <Card>
        <div className="mb-4">
          <Input
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="搜索消费者..."
            prefix={<SearchOutlined />}
            style={{ width: 300 }}
            value={searchText}
          />
        </div>
        <Table columns={columns} dataSource={filteredConsumers} pagination={false} rowKey="id" />
      </Card>

      {/* <Card title="消费者统计">
        <div className="grid grid-cols-4 gap-4">
          <div className="text-center">
            <div className="text-2xl font-bold text-blue-600">{consumers.length}</div>
            <div className="text-sm text-gray-500">总消费者</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-green-600">
              {consumers.filter(c => c.status === 'active').length}
            </div>
            <div className="text-sm text-gray-500">活跃消费者</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-purple-600">
              {consumers.reduce((sum, c) => sum + c.apiCalls, 0).toLocaleString()}
            </div>
            <div className="text-sm text-gray-500">总API调用</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-orange-600">
              {consumers.reduce((sum, c) => sum + c.subscriptions, 0)}
            </div>
            <div className="text-sm text-gray-500">总订阅数</div>
          </div>
        </div>
      </Card> */}
    </div>
  );
}
