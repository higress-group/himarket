import { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Tag,
  Card,
  message,
  Modal
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  StopOutlined,
  DownOutlined,
  UpOutlined
} from '@ant-design/icons';
import { apiDefinitionApi } from '@/lib/api';
import MonacoEditor, { MonacoDiffEditor } from 'react-monaco-editor';

interface PublishHistory {
  recordId: string;
  apiDefinitionId: string;
  gatewayId: string;
  action: string;
  version?: string;
  publishConfig?: any;
  gatewayResourceConfig?: any;
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

// 可展开文本组件
interface ExpandableTextProps {
  text: string;
  maxLength?: number;
}

const ExpandableText = ({ text, maxLength = 100 }: ExpandableTextProps) => {
  const [expanded, setExpanded] = useState(false);
  
  if (!text) {
    return <span>-</span>;
  }
  
  if (text.length <= maxLength) {
    return <span>{text}</span>;
  }
  
  return (
    <div style={{ maxWidth: '100%' }}>
      <div style={{ wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
        {expanded ? text : `${text.substring(0, maxLength)}...`}
      </div>
      <Button
        type="link"
        size="small"
        icon={expanded ? <UpOutlined /> : <DownOutlined />}
        onClick={() => setExpanded(!expanded)}
        style={{ padding: 0, height: 'auto', marginTop: 4 }}
      >
        {expanded ? '收起' : '展开'}
      </Button>
    </div>
  );
};

const formatGatewayResourceConfig = (value: any) => {
  if (!value) {
    return '';
  }

  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value);
      return JSON.stringify(parsed, null, 2);
    } catch (error) {
      return value;
    }
  }

  return JSON.stringify(value, null, 2);
};

export default function PublishHistoryTab({ apiDefinitionId }: PublishHistoryTabProps) {
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<PublishHistory[]>([]);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });

  const [viewModalVisible, setViewModalVisible] = useState(false);
  const [diffModalVisible, setDiffModalVisible] = useState(false);
  const [currentViewSnapshot, setCurrentViewSnapshot] = useState('');
  const [diffOriginal, setDiffOriginal] = useState('');
  const [diffModified, setDiffModified] = useState('');
  const [latestSnapshot, setLatestSnapshot] = useState<string>('');

  useEffect(() => {
    fetchHistory(1, 10);
    fetchLatestSnapshot();
  }, [apiDefinitionId]);

  const fetchLatestSnapshot = async () => {
    try {
      const response: any = await apiDefinitionApi.getPublishRecords(apiDefinitionId, {
        page: 0,
        size: 50
      });
      const data = response?.data?.content || response?.content || response?.data || response || [];
      if (Array.isArray(data)) {
        const latest = data.find((item: PublishHistory) => item.action === 'PUBLISH' && item.status === 'SUCCESS');
        if (latest) {
          const content = latest.snapshot || latest.publishConfig;
          setLatestSnapshot(typeof content === 'string' ? content : JSON.stringify(content, null, 2));
        }
      }
    } catch (error) {
      console.error('获取最新发布配置失败', error);
    }
  };

  const handleViewSnapshot = (record: PublishHistory) => {
    const content = record.snapshot || record.publishConfig;
    setCurrentViewSnapshot(typeof content === 'string' ? content : JSON.stringify(content, null, 2));
    setViewModalVisible(true);
  };

  const handleDiffSnapshot = (record: PublishHistory) => {
    const content = record.snapshot || record.publishConfig;
    const recordSnapshot = typeof content === 'string' ? content : JSON.stringify(content, null, 2);
    
    setDiffOriginal(latestSnapshot);
    setDiffModified(recordSnapshot);
    setDiffModalVisible(true);
  };

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
      width: 100,
      render: (_, record) => {
        switch (record.status) {
          case 'PUBLISHING':
            return <Tag color="processing" icon={<SyncOutlined spin />}>发布中</Tag>;
          case 'UNPUBLISHING':
            return <Tag color="processing" icon={<SyncOutlined spin />}>下线中</Tag>;
          case 'ACTIVE':
            return <Tag color="success" icon={<CheckCircleOutlined />}>已发布</Tag>;
          case 'INACTIVE':
            return <Tag color="default" icon={<StopOutlined />}>已下线</Tag>;
          case 'FAILED':
            return <Tag color="error" icon={<CloseCircleOutlined />}>失败</Tag>;
          default:
            if (record.errorMessage) {
              return <Tag color="error" icon={<CloseCircleOutlined />}>失败</Tag>;
            }
            return <Tag color="success" icon={<CheckCircleOutlined />}>成功</Tag>;
        }
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
      width: 200,
      render: (note) => <ExpandableText text={note} maxLength={50} />
    },
    {
      title: '操作',
      key: 'snapshot',
      width: 150,
      render: (_, record) => (
        <div className="space-x-2">
          <Button size="small" onClick={() => handleViewSnapshot(record)}>查看</Button>
          <Button size="small" onClick={() => handleDiffSnapshot(record)}>对比</Button>
        </div>
      )
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
                <ExpandableText text={record.errorMessage} maxLength={200} />
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
          {record.gatewayResourceConfig && (
            <div className="col-span-2">
              <span className="text-gray-500">网关资源配置:</span>
              <pre className="mt-1 p-2 bg-gray-100 rounded text-xs overflow-auto">
                {formatGatewayResourceConfig(record.gatewayResourceConfig)}
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
            rowExpandable: (record) =>
              !!record.errorMessage ||
              !!record.publishConfig ||
              !!record.gatewayResourceConfig ||
              !!record.operator
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

      <Modal
        title="配置快照"
        open={viewModalVisible}
        onCancel={() => setViewModalVisible(false)}
        footer={null}
        width={800}
      >
        <div className="h-[500px]">
          <MonacoEditor
            language="json"
            theme="vs-light"
            value={currentViewSnapshot}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false
            }}
          />
        </div>
      </Modal>

      <Modal
        title="配置对比 (左: 当前发布配置, 右: 选定记录配置)"
        open={diffModalVisible}
        onCancel={() => setDiffModalVisible(false)}
        footer={null}
        width={1000}
      >
        <div className="h-[600px]">
          <MonacoDiffEditor
            language="json"
            theme="vs-light"
            original={diffOriginal}
            value={diffModified}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false
            }}
          />
        </div>
      </Modal>
    </div>
  );
}
