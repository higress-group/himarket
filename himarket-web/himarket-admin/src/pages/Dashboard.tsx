import { Card, Row, Col, Statistic, Progress, Table } from 'antd'
import { 
  EyeOutlined, 
  UserOutlined, 
  ApiOutlined,
  GlobalOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined
} from '@ant-design/icons'

const mockRecentActivity = [
  {
    key: '1',
    action: 'Portal访问',
    description: 'Company Portal被访问了1250次',
    time: '2小时前',
    user: 'developer@example.com'
  },
  {
    key: '2',
    action: 'API调用',
    description: 'Payment API被调用了8765次',
    time: '4小时前',
    user: 'api@company.com'
  },
  {
    key: '3',
    action: '新用户注册',
    description: '新开发者注册了账户',
    time: '6小时前',
    user: 'newuser@example.com'
  }
]

export default function Dashboard() {
  const activityColumns = [
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '用户',
      dataIndex: 'user',
      key: 'user',
    },
    {
      title: '时间',
      dataIndex: 'time',
      key: 'time',
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">仪表板</h1>
        <p className="text-gray-500 mt-2">
          欢迎使用Himarket管理系统
        </p>
      </div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Portal访问量"
              value={1250}
              prefix={<EyeOutlined />}
              valueStyle={{ color: '#3f8600' }}
              suffix={<ArrowUpOutlined style={{ fontSize: '14px' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="注册用户"
              value={45}
              prefix={<UserOutlined />}
              valueStyle={{ color: '#1890ff' }}
              suffix={<ArrowUpOutlined style={{ fontSize: '14px' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="API调用"
              value={8765}
              prefix={<ApiOutlined />}
              valueStyle={{ color: '#722ed1' }}
              suffix={<ArrowUpOutlined style={{ fontSize: '14px' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="活跃Portal"
              value={3}
              prefix={<GlobalOutlined />}
              valueStyle={{ color: '#fa8c16' }}
              suffix={<ArrowDownOutlined style={{ fontSize: '14px' }} />}
            />
          </Card>
        </Col>
      </Row>

      {/* 详细信息 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="系统状态" className="h-full">
            <div className="space-y-4">
              <div>
                <div className="flex justify-between mb-2">
                  <span>系统负载</span>
                  <span className="text-blue-600">75%</span>
                </div>
                <Progress percent={75} strokeColor="#1890ff" />
              </div>
              <div>
                <div className="flex justify-between mb-2">
                  <span>API响应时间</span>
                  <span className="text-green-600">245ms</span>
                </div>
                <Progress percent={85} strokeColor="#52c41a" />
              </div>
              <div>
                <div className="flex justify-between mb-2">
                  <span>错误率</span>
                  <span className="text-red-600">0.12%</span>
                </div>
                <Progress percent={1.2} strokeColor="#ff4d4f" />
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="快速操作" className="h-full">
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="text-center p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
                  <GlobalOutlined className="text-2xl text-blue-500 mb-2" />
                  <div className="font-medium">创建Portal</div>
                  <div className="text-sm text-gray-500">新建开发者门户</div>
                </div>
                <div className="text-center p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
                  <ApiOutlined className="text-2xl text-green-500 mb-2" />
                  <div className="font-medium">发布API</div>
                  <div className="text-sm text-gray-500">发布新的API产品</div>
                </div>
                <div className="text-center p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
                  <UserOutlined className="text-2xl text-purple-500 mb-2" />
                  <div className="font-medium">管理用户</div>
                  <div className="text-sm text-gray-500">管理开发者账户</div>
                </div>
                <div className="text-center p-4 border rounded-lg hover:bg-gray-50 cursor-pointer">
                  <EyeOutlined className="text-2xl text-orange-500 mb-2" />
                  <div className="font-medium">查看统计</div>
                  <div className="text-sm text-gray-500">查看使用统计</div>
                </div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 最近活动 */}
      <Card title="最近活动">
        <Table 
          columns={activityColumns} 
          dataSource={mockRecentActivity}
          rowKey="key"
          pagination={false}
          size="small"
        />
      </Card>
    </div>
  )
} 