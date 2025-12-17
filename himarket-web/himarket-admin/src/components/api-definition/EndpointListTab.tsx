import { useState, useEffect } from 'react';
import {
  Table,
  message,
  Tag,
  Card
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Endpoint, EndpointType } from '@/types/endpoint';
import { apiDefinitionApi } from '@/lib/api';

interface EndpointListTabProps {
  apiDefinitionId: string;
  apiType?: string;
}

// Endpoint 类型选项
const ENDPOINT_TYPE_OPTIONS = [
  { label: 'MCP Tool', value: 'MCP_TOOL' },
  { label: 'REST Route', value: 'REST_ROUTE' },
  { label: 'Agent', value: 'AGENT' },
  { label: 'Model', value: 'MODEL' }
];

// Endpoint 类型颜色映射
const ENDPOINT_TYPE_COLOR_MAP: Record<EndpointType, string> = {
  MCP_TOOL: 'purple',
  REST_ROUTE: 'blue',
  AGENT: 'green',
  MODEL: 'orange'
};

export default function EndpointListTab({ apiDefinitionId }: EndpointListTabProps) {
  const [loading, setLoading] = useState(false);
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);

  useEffect(() => {
    fetchEndpoints();
  }, [apiDefinitionId]);

  const fetchEndpoints = async () => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getEndpoints(apiDefinitionId);
      const data = response?.data || response || [];
      setEndpoints(Array.isArray(data) ? data : []);
    } catch (error) {
      message.error('获取端点列表失败');
      setEndpoints([]);
    } finally {
      setLoading(false);
    }
  };

  const columns: ColumnsType<Endpoint> = [
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
      render: (sortOrder) => sortOrder + 1
    },
    {
      title: '端点名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: EndpointType) => (
        <Tag color={ENDPOINT_TYPE_COLOR_MAP[type]}>
          {ENDPOINT_TYPE_OPTIONS.find((opt) => opt.value === type)?.label || type}
        </Tag>
      )
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (time) => time ? new Date(time).toLocaleString('zh-CN') : '-'
    }
  ];

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">端点配置</h1>
        <p className="text-gray-600">管理 API Definition 的端点配置</p>
      </div>

      <Card>
        <Table
          columns={columns}
          dataSource={endpoints}
          rowKey="endpointId"
          loading={loading}
          pagination={false}
          locale={{
            emptyText: '暂无端点配置'
          }}
        />
      </Card>
    </div>
  );
}
