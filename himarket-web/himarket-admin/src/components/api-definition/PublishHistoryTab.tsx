import { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Tag,
  Card,
  message
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined
} from '@ant-design/icons';
import { apiDefinitionApi } from '@/lib/api';

interface PublishHistory {
  recordId: string;
  apiDefinitionId: string;
  gatewayId: string;
  action: string;
  version?: string;
  publishConfig?: any;
  status?: string;
  errorMessage?: string;
  publishNote?: string;
  operator?: string;
  createdAt?: string;
  snapshot?: any;
}

interface PublishHistoryTabProps {
  apiDefinitionId: string;
}

// 操作类型颜色映射
const ACTION_COLOR_MAP: Record<string, string> = {
  PUBLISH: 'blue',
  UNPUBLISH: 'orange',
  UPDATE: 'green'
};

// 操作类型文本映射
const ACTION_TEXT_MAP: Record<string, string> = {
  PUBLISH: '发布',
  UNPUBLISH: '取消发布',
  UPDATE: '更新'
};

export default function PublishHistoryTab({ apiDefinitionId }: PublishHistoryTabProps) {
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<PublishHistory[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  useEffect(() => {
    fetchHistory(1, 10);
  }, [apiDefinitionId]);

  const fetchHistory = async (page: number, size: number) => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getPublishRecords(apiDefinitionId, {
        page: page - 1, // 后端分页从 0 开始
        size
      });
      
      const data = response?.data?.content || response?.content || response?.data || response || [];
      const total = response?.data?.totalElements || response?.totalElements || 0;
      
      setHistory(Array.isArray(data) ? data : []);
      setPagination({
        current: page,
        pageSize: size,
        total
      });
    } catch (error) {
      message.error('获取发布历史失败');
      setHistory([]);
    } finally {
      setLoading(false);
    }
  };

  const handleTableChange = (newPagination: any) => {
    fetchHistory(newPagination.current, newPagination.pageSize);
  };

  const columns: ColumnsType<PublishHistory> = [
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      width: 100,
      render: (action) => (
        <Tag color={ACTION_COLOR_MAP[action] || 'default'}>
          {ACTION_TEXT_MAP[action] || action}
        </Tag>
      )
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: (_, record) => {
        if (record.status === 'FAILED' || record.errorMessage) {
          return <Tag color="error" icon={<CloseCircleOutlined />}>失败</Tag>;
        }
        return <Tag color="success" icon={<CheckCircleOutlined />}>成功</Tag>;
      }
    },
    {
      title: '网关 ID',
      dataIndex: 'gatewayId',
      key: 'gatewayId',
      width: 200,
      ellipsis: true
    },
    {
      title: 'API 版本',
      dataIndex: 'version',
      key: 'version',
      width: 120,
      render: (version) => version || '-'
    },
    {
      title: '备注',
      dataIndex: 'publishNote',
      key: 'publishNote',
      ellipsis: true,
      render: (note) => note || '-'
    },
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (time) => time ? new Date(time).toLocaleString('zh-CN') : '-'
    }
  ];

  // 展开行显示详细信息
  const expandedRowRender = (record: PublishHistory) => {
    return (
      <div className="p-4 bg-gray-50">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <span className="text-gray-500">记录 ID:</span>
            <span className="ml-2">{record.recordId}</span>
          </div>
          {record.operator && (
            <div>
              <span className="text-gray-500">操作人:</span>
              <span className="ml-2">{record.operator}</span>
            </div>
          )}
          {record.errorMessage && (
            <div className="col-span-2">
              <span className="text-gray-500">错误信息:</span>
              <div className="mt-1 p-2 bg-red-50 text-red-600 rounded">
                {record.errorMessage}
              </div>
            </div>
          )}
          {record.publishConfig && (
            <div className="col-span-2">
              <span className="text-gray-500">发布配置:</span>
              <pre className="mt-1 p-2 bg-gray-100 rounded text-xs overflow-auto">
                {JSON.stringify(record.publishConfig, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">发布历史</h1>
        <p className="text-gray-600">查看 API 的所有发布操作历史记录</p>
      </div>

      <Card
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => fetchHistory(pagination.current, pagination.pageSize)}
          >
            刷新
          </Button>
        }
      >
        <Table
          columns={columns}
          dataSource={history}
          rowKey="recordId"
          loading={loading}
          expandable={{
            expandedRowRender,
            rowExpandable: (record) => !!record.errorMessage || !!record.publishConfig || !!record.operator
          }}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条记录`
          }}
          onChange={handleTableChange}
          locale={{
            emptyText: '暂无发布历史记录'
          }}
        />
      </Card>
    </div>
  );
}
