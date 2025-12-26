import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Modal,
  Form,
  Select,
  Input,
  message,
  Tabs,
  Descriptions,
  Badge,
  Alert
} from 'antd';
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  GlobalOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  StopOutlined,
  EyeOutlined
} from '@ant-design/icons';
import { apiDefinitionApi, gatewayApi } from '@/lib/api';
import type { Gateway } from '@/types/gateway';
import dayjs from 'dayjs';

const { TextArea } = Input;

export default function ApiPublishManagement() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const apiDefinitionId = searchParams.get('id');
  const { productName, productId, productType } = location.state || {};

  const [apiDefinition, setApiDefinition] = useState<any>(null);
  const [publishRecords, setPublishRecords] = useState<any[]>([]);
  const [publishHistory, setPublishHistory] = useState<any[]>([]);
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [loading, setLoading] = useState(false);
  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [snapshotModalVisible, setSnapshotModalVisible] = useState(false);
  const [currentSnapshot, setCurrentSnapshot] = useState<any>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    if (apiDefinitionId) {
      fetchData();
    }
  }, [apiDefinitionId]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [apiRes, recordsRes, historyRes, gatewaysRes] = await Promise.all([
        apiDefinitionApi.getApiDefinitionDetail(apiDefinitionId!),
        apiDefinitionApi.getPublishRecords(apiDefinitionId!, { page: 1, size: 100 }),
        apiDefinitionApi.getPublishHistory(apiDefinitionId!, { page: 1, size: 100 }),
        gatewayApi.getGateways({ page: 1, size: 100 })
      ]);

      setApiDefinition(apiRes.data || apiRes);
      setPublishRecords(recordsRes.data?.content || recordsRes.content || []);
      setPublishHistory(historyRes.data?.content || historyRes.content || []);
      
      // Filter gateways based on API type if needed
      const allGateways = gatewaysRes.data?.content || gatewaysRes.content || [];
      setGateways(allGateways);
    } catch (error) {
      console.error('Failed to fetch data:', error);
      message.error('获取数据失败');
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async () => {
    try {
      const values = await form.validateFields();
      setPublishing(true);

      const publishData = {
        apiDefinitionId,
        gatewayId: values.gatewayId,
        comment: values.comment,
        publishConfig: {
          serviceConfig: {
            serviceType: 'FIXED', // Currently simplified
            address: '127.0.0.1', // Mock address for now
            port: 8080,
            protocol: 'HTTP'
          },
          domains: [], // Optional
          basePath: '/'
        }
      };

      await apiDefinitionApi.publishApi(apiDefinitionId!, publishData);
      message.success('发布成功');
      setPublishModalVisible(false);
      form.resetFields();
      fetchData(); // Refresh data
    } catch (error: any) {
      message.error(error.message || '发布失败');
    } finally {
      setPublishing(false);
    }
  };

  const handleUnpublish = async (recordId: string) => {
    Modal.confirm({
      title: '确认下线',
      content: '确定要下线该 API 吗？下线后将无法访问。',
      okText: '确认下线',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiDefinitionApi.unpublishApi(apiDefinitionId!, recordId);
          message.success('下线成功');
          fetchData();
        } catch (error: any) {
          message.error(error.message || '下线失败');
        }
      }
    });
  };

  const handleViewSnapshot = (snapshot: any) => {
    setCurrentSnapshot(snapshot);
    setSnapshotModalVisible(true);
  };

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Tag icon={<CheckCircleOutlined />} color="success">已发布</Tag>;
      case 'INACTIVE':
        return <Tag icon={<StopOutlined />} color="default">已下线</Tag>;
      case 'FAILED':
        return <Tag icon={<CloseCircleOutlined />} color="error">发布失败</Tag>;
      default:
        return <Tag icon={<SyncOutlined spin />} color="processing">{status}</Tag>;
    }
  };

  const historyColumns = [
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      render: (action: string) => {
        return action === 'PUBLISH' ? <Tag color="blue">发布</Tag> : <Tag color="orange">下线</Tag>;
      }
    },
    {
      title: '备注',
      dataIndex: 'publishNote',
      key: 'publishNote',
    },
    {
      title: '操作时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (text: string) => dayjs(text).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '结果',
      dataIndex: 'reason',
      key: 'reason',
      render: (reason: string) => reason ? <span className="text-red-500">{reason}</span> : <span className="text-green-500">成功</span>
    },
    {
      title: '快照',
      key: 'snapshot',
      render: (_: any, record: any) => (
        record.snapshot ? (
          <Button 
            type="link" 
            icon={<EyeOutlined />} 
            onClick={() => handleViewSnapshot(record.snapshot)}
          >
            查看
          </Button>
        ) : '-'
      )
    }
  ];

  return (
    <div className="p-6">
      <div className="mb-6 flex justify-between items-center">
        <div className="flex items-center">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(-1)}
            className="mr-4"
          >
            返回
          </Button>
          <div>
            <h1 className="text-2xl font-bold">API 发布管理</h1>
          </div>
        </div>
        <Button
          type="primary"
          icon={<CloudUploadOutlined />}
          onClick={() => setPublishModalVisible(true)}
        >
          发布 API
        </Button>
      </div>

      {apiDefinition && (
        <Card className="mb-6" title="基本信息">
          <Descriptions column={3}>
            <Descriptions.Item label="API 名称">{apiDefinition.name}</Descriptions.Item>
            <Descriptions.Item label="API ID">{apiDefinition.apiDefinitionId}</Descriptions.Item>
            <Descriptions.Item label="类型">{apiDefinition.type}</Descriptions.Item>
            <Descriptions.Item label="更新时间">
              {dayjs(apiDefinition.updatedAt).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={3}>
              {apiDefinition.description || '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Card 
        className="mb-6" 
        title={
          <div className="flex items-center">
            <GlobalOutlined className="mr-2" />
            <span>发布状态</span>
          </div>
        }
      >
        {publishRecords.length > 0 ? (
          (() => {
            const record = publishRecords[0];
            const gateway = gateways.find(g => g.gatewayId === record.gatewayId);
            return (
              <Descriptions column={2}>
                <Descriptions.Item label="发布网关">
                  {gateway ? gateway.gatewayName : record.gatewayId}
                </Descriptions.Item>
                <Descriptions.Item label="发布版本">
                  {record.version}
                </Descriptions.Item>
                <Descriptions.Item label="当前状态">
                  <Space>
                    {getStatusTag(record.status)}
                    {record.status === 'ACTIVE' && (
                      <Button 
                        type="link" 
                        danger 
                        size="small"
                        onClick={() => handleUnpublish(record.recordId)}
                        style={{ padding: 0 }}
                      >
                        下线
                      </Button>
                    )}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="发布时间">
                  {dayjs(record.publishTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
              </Descriptions>
            );
          })()
        ) : (
          <div className="text-center text-gray-500 py-8">暂无发布信息</div>
        )}
      </Card>

      <Card 
        title={
          <div className="flex items-center">
            <HistoryOutlined className="mr-2" />
            <span>操作历史</span>
          </div>
        }
      >
        <Table
          columns={historyColumns}
          dataSource={publishHistory}
          rowKey="historyId"
          pagination={{ pageSize: 10 }}
          loading={loading}
        />
      </Card>

      <Modal
        title="发布 API"
        open={publishModalVisible}
        onOk={handlePublish}
        onCancel={() => setPublishModalVisible(false)}
        confirmLoading={publishing}
        width={600}
      >
        <Alert
          message="发布说明"
          description="发布操作会将当前 API 定义同步到选定的网关。目前仅支持发布到单个网关。"
          type="info"
          showIcon
          className="mb-6"
        />
        
        <Form form={form} layout="vertical">
          <Form.Item
            name="gatewayId"
            label="选择网关"
            rules={[{ required: true, message: '请选择要发布的网关' }]}
          >
            <Select
              placeholder="请选择网关"
              loading={loading}
            >
              {gateways.map(gateway => (
                <Select.Option key={gateway.gatewayId} value={gateway.gatewayId}>
                  {gateway.gatewayName} ({gateway.gatewayType})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="comment"
            label="发布备注"
            rules={[{ max: 200, message: '备注不能超过200字' }]}
          >
            <TextArea rows={4} placeholder="请输入本次发布的备注信息（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="配置快照"
        open={snapshotModalVisible}
        onCancel={() => setSnapshotModalVisible(false)}
        footer={null}
        width={800}
      >
        {currentSnapshot ? (
          <div className="max-h-[600px] overflow-y-auto">
            <Descriptions title="基本信息" bordered column={2} size="small" className="mb-4">
              <Descriptions.Item label="名称">{currentSnapshot.name}</Descriptions.Item>
              <Descriptions.Item label="版本">{currentSnapshot.version}</Descriptions.Item>
              <Descriptions.Item label="类型">{currentSnapshot.type}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{currentSnapshot.description || '-'}</Descriptions.Item>
            </Descriptions>

            {currentSnapshot.endpoints && currentSnapshot.endpoints.length > 0 && (
              <div>
                <h3 className="text-base font-medium mb-2">Endpoints</h3>
                <div className="space-y-4">
                  {currentSnapshot.endpoints.map((endpoint: any, index: number) => (
                    <Card 
                      key={index} 
                      size="small" 
                      title={
                        <div className="flex items-center justify-between">
                          <span>{endpoint.name}</span>
                          <Tag>{endpoint.type}</Tag>
                        </div>
                      }
                    >
                      <div className="mb-2 text-gray-500">{endpoint.description}</div>
                      {endpoint.config && (
                        <div className="bg-gray-50 p-2 rounded border border-gray-200">
                          <pre className="text-xs overflow-x-auto whitespace-pre-wrap m-0">
                            {JSON.stringify(endpoint.config, null, 2)}
                          </pre>
                        </div>
                      )}
                    </Card>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="text-center text-gray-500 py-8">暂无快照信息</div>
        )}
      </Modal>
    </div>
  );
}
