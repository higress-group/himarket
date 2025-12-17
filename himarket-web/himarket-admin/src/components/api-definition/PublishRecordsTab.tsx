import { useState, useEffect, forwardRef, useImperativeHandle } from 'react';
import {
  Table,
  Button,
  Modal,
  Form,
  Select,
  Input,
  message,
  Space,
  Tag,
  Popconfirm,
  Card
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import { apiDefinitionApi, gatewayApi } from '@/lib/api';

const { TextArea } = Input;

interface PublishRecord {
  recordId: string;
  apiDefinitionId: string;
  gatewayId: string;
  gatewayName: string;
  gatewayType: string;
  status: string;
  publishConfig?: any;
  gatewayResourceId?: string;
  accessEndpoint?: string;
  errorMessage?: string;
  publishedAt?: string;
  lastSyncAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface Gateway {
  gatewayId: string;
  gatewayName: string;
  gatewayType: string;
}

interface PublishRecordsTabProps {
  apiDefinitionId: string;
  status?: string;
}

// 发布状态颜色映射
const STATUS_COLOR_MAP: Record<string, string> = {
  ACTIVE: 'green',
  INACTIVE: 'default',
  FAILED: 'red'
};

// 发布状态文本映射
const STATUS_TEXT_MAP: Record<string, string> = {
  ACTIVE: '已发布',
  INACTIVE: '已下线',
  FAILED: '发布失败'
};

const PublishRecordsTab = forwardRef<{ handlePublish: () => void }, PublishRecordsTabProps>(
  function PublishRecordsTab({ apiDefinitionId, status }, ref) {
  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<PublishRecord[]>([]);
  const [modalVisible, setModalVisible] = useState(false);

  // 调试日志：查看接收到的 status 值
  console.log('[PublishRecordsTab] status:', status, 'type:', typeof status, 'isDraft:', status === 'DRAFT');
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [loadingGateways, setLoadingGateways] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchRecords();
  }, [apiDefinitionId]);

  const fetchRecords = async () => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getPublishRecords(apiDefinitionId);
      const data = response?.data?.content || response?.content || response?.data || response || [];
      setRecords(Array.isArray(data) ? data : []);
    } catch (error) {
      message.error('获取发布记录失败');
      setRecords([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchGateways = async () => {
    setLoadingGateways(true);
    try {
      const response: any = await gatewayApi.getGateways();
      const data = response?.data?.content || response?.content || response?.data || response || [];
      setGateways(Array.isArray(data) ? data : []);
    } catch (error) {
      message.error('获取网关列表失败');
      setGateways([]);
    } finally {
      setLoadingGateways(false);
    }
  };

  const handlePublish = () => {
    form.resetFields();
    setModalVisible(true);
    fetchGateways();
  };

  // 暴露 handlePublish 方法给父组件
  useImperativeHandle(ref, () => ({
    handlePublish
  }));

  const handleUnpublish = async (recordId: string) => {
    try {
      await apiDefinitionApi.unpublishApi(apiDefinitionId, recordId);
      message.success('取消发布成功');
      fetchRecords();
    } catch (error) {
      message.error('取消发布失败');
    }
  };

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields();
      
      const publishData = {
        apiDefinitionId,
        gatewayId: values.gatewayId,
        publishConfig: {
          basePath: values.basePath || '/',
          domains: values.domains ? values.domains.split(',').map((d: string) => d.trim()) : []
        },
        comment: values.comment
      };

      await apiDefinitionApi.publishApi(apiDefinitionId, publishData);
      message.success('发布成功');
      setModalVisible(false);
      fetchRecords();
    } catch (error) {
      // API 调用失败或表单验证失败
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
  };

  const columns: ColumnsType<PublishRecord> = [
    {
      title: '网关名称',
      dataIndex: 'gatewayName',
      key: 'gatewayName',
      width: 200
    },
    {
      title: '网关类型',
      dataIndex: 'gatewayType',
      key: 'gatewayType',
      width: 120,
      render: (type) => <Tag>{type}</Tag>
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => (
        <Tag color={STATUS_COLOR_MAP[status] || 'default'}>
          {STATUS_TEXT_MAP[status] || status}
        </Tag>
      )
    },
    {
      title: '访问端点',
      dataIndex: 'accessEndpoint',
      key: 'accessEndpoint',
      ellipsis: true,
      render: (endpoint) => endpoint || '-'
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 180,
      render: (time) => time ? new Date(time).toLocaleString('zh-CN') : '-'
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'ACTIVE' && (
            <Popconfirm
              title="确认取消发布"
              description="确定要取消发布到此网关吗？"
              onConfirm={() => handleUnpublish(record.recordId)}
              okText="确认"
              cancelText="取消"
            >
              <Button
                type="link"
                danger
                size="small"
                icon={<DeleteOutlined />}
              >
                取消发布
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  // 展开行显示详细信息
  const expandedRowRender = (record: PublishRecord) => {
    return (
      <div className="p-4 bg-gray-50">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <span className="text-gray-500">记录 ID:</span>
            <span className="ml-2">{record.recordId}</span>
          </div>
          <div>
            <span className="text-gray-500">网关 ID:</span>
            <span className="ml-2">{record.gatewayId}</span>
          </div>
          {record.gatewayResourceId && (
            <div>
              <span className="text-gray-500">网关资源 ID:</span>
              <span className="ml-2">{record.gatewayResourceId}</span>
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
        </div>
      </div>
    );
  };

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">发布记录</h1>
        <p className="text-gray-600">管理 API 在各个网关的发布状态</p>
      </div>

      <Card
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={fetchRecords}
            >
              刷新
            </Button>
            {status === 'DRAFT' && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handlePublish}
              >
                发布到网关
              </Button>
            )}
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={records}
          rowKey="recordId"
          loading={loading}
          expandable={{
            expandedRowRender,
            rowExpandable: (record) => !!record.errorMessage || !!record.gatewayResourceId
          }}
          pagination={false}
          locale={{
            emptyText: '暂无发布记录，点击"发布到网关"开始发布'
          }}
        />
      </Card>

      <Modal
        title="发布到网关"
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={600}
        okText="发布"
        cancelText="取消"
      >
        <Form form={form} layout="vertical" className="mt-4">
          <Form.Item
            label="选择网关"
            name="gatewayId"
            rules={[{ required: true, message: '请选择网关' }]}
          >
            <Select
              placeholder="选择要发布的网关"
              loading={loadingGateways}
              options={gateways.map((gw) => ({
                label: `${gw.gatewayName} (${gw.gatewayType})`,
                value: gw.gatewayId
              }))}
            />
          </Form.Item>

          <Form.Item
            label="基础路径"
            name="basePath"
            initialValue="/"
            rules={[{ required: true, message: '请输入基础路径' }]}
          >
            <Input placeholder="例如：/api" />
          </Form.Item>

          <Form.Item
            label="域名列表"
            name="domains"
            extra="多个域名用逗号分隔"
          >
            <Input placeholder="例如：api.example.com,api2.example.com" />
          </Form.Item>

          <Form.Item
            label="备注"
            name="comment"
          >
            <TextArea
              rows={3}
              placeholder="发布说明或备注信息"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
});

export default PublishRecordsTab;
