import { useState, useEffect, forwardRef, useImperativeHandle } from 'react';
import {
  Button,
  Modal,
  Form,
  Select,
  Input,
  message,
  Space,
  Tag,
  Popconfirm,
  Card,
  Descriptions,
  Empty,
  Alert
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  CloudServerOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined
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
  const [record, setRecord] = useState<PublishRecord | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [loadingGateways, setLoadingGateways] = useState(false);
  const [form] = Form.useForm();

  // 获取当前活跃的发布记录
  const activeRecord = record?.status === 'ACTIVE' ? record : null;

  useEffect(() => {
    fetchRecords();
  }, [apiDefinitionId]);

  const fetchRecords = async () => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getPublishRecords(apiDefinitionId);
      const data = response?.data?.content || response?.content || response?.data || response || [];
      const recordList = Array.isArray(data) ? data : [];
      // 只取第一条活跃记录（当前限制只能发布到一个网关）
      const activeRecord = recordList.find(r => r.status === 'ACTIVE');
      setRecord(activeRecord || recordList[0] || null);
    } catch (error) {
      message.error('获取发布记录失败');
      setRecord(null);
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

  // 渲染发布状态标签
  const renderStatusTag = (status: string) => {
    const statusConfig = {
      ACTIVE: { color: 'green', icon: <CheckCircleOutlined />, text: '已发布' },
      INACTIVE: { color: 'default', icon: <CloseCircleOutlined />, text: '已下线' },
      FAILED: { color: 'red', icon: <ExclamationCircleOutlined />, text: '发布失败' }
    };
    const config = statusConfig[status as keyof typeof statusConfig] || statusConfig.INACTIVE;
    return (
      <Tag color={config.color} icon={config.icon}>
        {config.text}
      </Tag>
    );
  };

  // 渲染发布记录详情
  const renderPublishRecord = () => {
    if (!record) {
      return (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span className="text-gray-500">
              暂无发布记录
              <br />
              <span className="text-sm">当前版本限制一个 API 只能发布到一个网关</span>
            </span>
          }
        >
          {status === 'DRAFT' && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handlePublish}
            >
              发布到网关
            </Button>
          )}
        </Empty>
      );
    }

    return (
      <div className="space-y-4">
        {/* 状态提示 */}
        {record.status === 'ACTIVE' && (
          <Alert
            message="API 已成功发布到网关"
            description="当前 API 正在网关上运行，可通过访问端点进行调用"
            type="success"
            showIcon
          />
        )}
        {record.status === 'INACTIVE' && (
          <Alert
            message="API 已下线"
            description="该 API 已从网关取消发布，当前不可访问"
            type="warning"
            showIcon
          />
        )}
        {record.status === 'FAILED' && (
          <Alert
            message="发布失败"
            description={record.errorMessage || '发布过程中出现错误'}
            type="error"
            showIcon
          />
        )}

        {/* 发布详情 */}
        <Card
          title={
            <Space>
              <CloudServerOutlined />
              <span>发布详情</span>
            </Space>
          }
          extra={
            record.status === 'ACTIVE' && (
              <Popconfirm
                title="确认取消发布"
                description="确定要取消发布到此网关吗？取消后 API 将无法访问。"
                onConfirm={() => handleUnpublish(record.recordId)}
                okText="确认"
                cancelText="取消"
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  取消发布
                </Button>
              </Popconfirm>
            )
          }
        >
          <Descriptions column={2} bordered>
            <Descriptions.Item label="网关名称">
              {record.gatewayName}
            </Descriptions.Item>
            <Descriptions.Item label="网关类型">
              <Tag>{record.gatewayType}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="发布状态">
              {renderStatusTag(record.status)}
            </Descriptions.Item>
            <Descriptions.Item label="发布时间">
              {record.publishedAt ? new Date(record.publishedAt).toLocaleString('zh-CN') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="访问端点" span={2}>
              {record.accessEndpoint ? (
                <code className="px-2 py-1 bg-gray-100 rounded text-sm">
                  {record.accessEndpoint}
                </code>
              ) : (
                <span className="text-gray-400">-</span>
              )}
            </Descriptions.Item>
            {record.gatewayResourceId && (
              <Descriptions.Item label="网关资源 ID" span={2}>
                <code className="px-2 py-1 bg-gray-100 rounded text-xs">
                  {record.gatewayResourceId}
                </code>
              </Descriptions.Item>
            )}
            {record.publishConfig && (
              <>
                <Descriptions.Item label="基础路径">
                  {record.publishConfig.basePath || '/'}
                </Descriptions.Item>
                <Descriptions.Item label="域名列表">
                  {record.publishConfig.domains?.length > 0 ? (
                    <Space wrap>
                      {record.publishConfig.domains.map((domain: string, idx: number) => (
                        <Tag key={idx}>{domain}</Tag>
                      ))}
                    </Space>
                  ) : (
                    <span className="text-gray-400">-</span>
                  )}
                </Descriptions.Item>
              </>
            )}
          </Descriptions>
        </Card>

        {/* 如果已下线，显示重新发布按钮 */}
        {record.status === 'INACTIVE' && status === 'DRAFT' && (
          <div className="text-center pt-4">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handlePublish}
            >
              重新发布到网关
            </Button>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">发布状态</h1>
        <p className="text-gray-600">查看 API 在网关的发布状态和配置信息</p>
      </div>

      <Card
        loading={loading}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchRecords}>
              刷新
            </Button>
            {status === 'DRAFT' && activeRecord && (
              <Button
                type="default"
                disabled
                title="当前版本限制只能发布到一个网关，请先取消现有发布后再发布到其他网关"
              >
                已发布（如需更换网关请先取消发布）
              </Button>
            )}
          </Space>
        }
      >
        {renderPublishRecord()}
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

          <Form.Item label="备注" name="comment">
            <TextArea rows={3} placeholder="发布说明或备注信息" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
});

export default PublishRecordsTab;
