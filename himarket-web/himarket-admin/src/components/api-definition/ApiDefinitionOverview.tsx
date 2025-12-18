import { Card, Row, Col, Statistic, Button, Space, Tag } from 'antd';
import {
  ApiOutlined,
  CloudServerOutlined,
  ClockCircleOutlined,
  EditOutlined,
  CloudUploadOutlined
} from '@ant-design/icons';
import { formatDateTime } from '@/lib/utils';

interface ApiDefinition {
  apiDefinitionId: string;
  name: string;
  description: string;
  type: string;
  status: string;
  version: string;
  properties?: Array<Record<string, any>>;
  createdAt: string;
  updatedAt: string;
}

interface ApiDefinitionOverviewProps {
  apiDefinition: ApiDefinition;
  endpointCount: number;
  publishedTargetCount: number;
  onEdit: () => void;
  onPublish: () => void;
}

export function ApiDefinitionOverview({
  apiDefinition,
  endpointCount,
  publishedTargetCount,
  onEdit,
  onPublish
}: ApiDefinitionOverviewProps) {
  const getTypeLabel = (type: string) => {
    const labels: Record<string, string> = {
      'MCP_SERVER': 'MCP Server',
      'REST_API': 'REST API',
      'AGENT_API': 'Agent API',
      'MODEL_API': 'Model API'
    };
    return labels[type] || type;
  };

  const getStatusLabel = (status: string) => {
    const labels: Record<string, string> = {
      'DRAFT': '草稿',
      'PUBLISHING': '发布中',
      'PUBLISHED': '已发布',
      'DEPRECATED': '已废弃',
      'ARCHIVED': '已归档'
    };
    return labels[status] || status;
  };

  const getStatusColor = (status: string) => {
    const colors: Record<string, string> = {
      'DRAFT': 'default',
      'PUBLISHING': 'processing',
      'PUBLISHED': 'success',
      'DEPRECATED': 'warning',
      'ARCHIVED': 'error'
    };
    return colors[status] || 'default';
  };

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">API Definition 概览</p>
      </div>

      {/* 基本信息 */}
      <Card
        title="基本信息"
        extra={
          <Space>
            {apiDefinition.status === 'DRAFT' && (
              <Button
                type="primary"
                icon={<CloudUploadOutlined />}
                onClick={onPublish}
              >
                发布到网关
              </Button>
            )}
            <Button
              type="default"
              icon={<EditOutlined />}
              onClick={onEdit}
            >
              编辑
            </Button>
          </Space>
        }
      >
        <div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">API 名称:</span>
            <span className="col-span-2 text-xs text-gray-900">{apiDefinition.name}</span>
            <span className="text-xs text-gray-600">API ID:</span>
            <span className="col-span-2 text-xs text-gray-700">{apiDefinition.apiDefinitionId}</span>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">{getTypeLabel(apiDefinition.type)}</span>
            <span className="text-xs text-gray-600">状态:</span>
            <div className="col-span-2 flex items-center">
              <Tag color={getStatusColor(apiDefinition.status)}>
                {getStatusLabel(apiDefinition.status)}
              </Tag>
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">版本:</span>
            <span className="col-span-2 text-xs text-gray-900">{apiDefinition.version}</span>
            <span className="text-xs text-gray-600">创建时间:</span>
            <span className="col-span-2 text-xs text-gray-700">{formatDateTime(apiDefinition.createdAt)}</span>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">更新时间:</span>
            <span className="col-span-2 text-xs text-gray-700">{formatDateTime(apiDefinition.updatedAt)}</span>
          </div>

          {apiDefinition.description && (
            <div className="grid grid-cols-6 gap-8 pt-2 pb-2">
              <span className="text-xs text-gray-600">描述:</span>
              <span className="col-span-5 text-xs text-gray-700 leading-relaxed">
                {apiDefinition.description}
              </span>
            </div>
          )}
        </div>
      </Card>

      {/* 统计数据 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={8}>
          <Card className="hover:shadow-md transition-shadow">
            <Statistic
              title="端点数量"
              value={endpointCount}
              prefix={<ApiOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card className="hover:shadow-md transition-shadow">
            <Statistic
              title="已发布目标"
              value={publishedTargetCount}
              prefix={<CloudServerOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card className="hover:shadow-md transition-shadow">
            <Statistic
              title="最后更新"
              value={formatDateTime(apiDefinition.updatedAt).split(' ')[0]}
              prefix={<ClockCircleOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
