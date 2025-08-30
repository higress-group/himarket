import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Row, Col, Statistic, Progress, Tag } from 'antd'
import { 
  ApiOutlined, 
  UserOutlined, 
  SettingOutlined,
  EyeOutlined,
  ClockCircleOutlined
} from '@ant-design/icons'
import type { ApiProduct } from '@/types/api-product'
import { getStatusBadgeVariant, getServiceName, formatDateTime } from '@/lib/utils'
import { apiProductApi } from '@/lib/api'


interface ApiProductOverviewProps {
  apiProduct: ApiProduct
}

export function ApiProductOverview({ apiProduct }: ApiProductOverviewProps) {

  const [portalCount, setPortalCount] = useState(0)
  const [linkedService, setLinkedService] = useState(null)
  const [loading, setLoading] = useState(false)

  const navigate = useNavigate()

  useEffect(() => {
    if (apiProduct.productId) {
      Promise.all([
        fetchLinkedService(),
        fetchPublishedPortals()
      ]).finally(() => {
        setLoading(false)
      })
    }
  }, [apiProduct.productId])


  const fetchLinkedService = async () => {
    try {
      const res = await apiProductApi.getApiProductRef(apiProduct.productId)
      setLinkedService(res.data || null)
    } catch (error) {
      console.error('获取关联服务失败:', error)
      setLinkedService(null)
    }
  }

  const fetchPublishedPortals = async () => {
    try {
      const res = await apiProductApi.getApiProductPublications(apiProduct.productId)
      setPortalCount(res.data.content?.length || 0)
    } catch (error) {
    } finally {
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">API产品的基本信息和统计数据</p>
      </div>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={12}>
          <Card
            onClick={() => {
              navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=portal`)
            }}
          >
            <Statistic
              title="关联门户"
              value={portalCount}
              prefix={<EyeOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={12}>
          <Card
            onClick={() => {
              navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=link-api`)
            }}
          >
            <Statistic
              title="关联服务"
              value={getServiceName(linkedService)}
              prefix={<ApiOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        
      </Row>

      {/* 详细信息 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={24}>
          <Card title="基本信息" className="h-full">
            <div className="space-y-4">
              <div className="flex justify-between">
                <span className="text-gray-600">产品名称</span>
                <span className="font-medium">{apiProduct.name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">状态</span>
                <Tag color={getStatusBadgeVariant(apiProduct.status)}>
                  {apiProduct.status === "PENDING" ? "待配置" : apiProduct.status === "READY" ? "待发布" : "已发布"}
                </Tag>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">创建时间</span>
                <span>{formatDateTime(apiProduct.createAt)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">产品描述</span>
                <span>{apiProduct.description}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">自动审批订阅</span>
                <span>
                  {apiProduct.autoApprove === true ? (
                    <Tag color="green">启用</Tag>
                  ) : apiProduct.autoApprove === false ? (
                    <Tag color="red">禁用</Tag>
                  ) : (
                    <Tag color="default">使用平台设置</Tag>
                  )}
                </span>
              </div>
              
            </div>
          </Card>
        </Col>
        {/* <Col xs={24} lg={12}>
          <Card title="使用统计" className="h-full">
            <div className="space-y-6">
              <div>
                <div className="flex justify-between mb-2">
                  <span>API调用次数</span>
                  <span className="text-blue-600">0</span>
                </div>
                <Progress percent={0} strokeColor="#1890ff" />
              </div>
              <div>
                <div className="flex justify-between mb-2">
                  <span>错误率</span>
                  <span className="text-red-600">0%</span>
                </div>
                <Progress percent={0} strokeColor="#ff4d4f" />
              </div>
              <div>
                <div className="flex justify-between mb-2">
                  <span>平均响应时间</span>
                  <span className="text-green-600">0ms</span>
                </div>
                <Progress percent={0} strokeColor="#52c41a" />
              </div>
            </div>
          </Card>
        </Col> */}
      </Row>
    </div>
  )
} 