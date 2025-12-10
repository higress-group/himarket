import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Row, Col, Statistic, Button, message } from 'antd'
import { 
  ApiOutlined, 
  GlobalOutlined,
  TeamOutlined,
  EditOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
  CopyOutlined,
  ExclamationCircleFilled,
  ClockCircleFilled
} from '@ant-design/icons'
import type { ApiProduct } from '@/types/api-product'
import { getServiceName, formatDateTime, copyToClipboard } from '@/lib/utils'
import { apiProductApi } from '@/lib/api'
import { getProductCategories } from '@/lib/productCategoryApi'
import type { ProductCategory } from '@/types/product-category'


interface ApiProductOverviewProps {
  apiProduct: ApiProduct
  linkedService: any | null
  onEdit: () => void
}

export function ApiProductOverview({ apiProduct, linkedService, onEdit }: ApiProductOverviewProps) {

  const [portalCount, setPortalCount] = useState(0)
  const [subscriberCount] = useState(0)
  const [productCategories, setProductCategories] = useState<ProductCategory[]>([])

  const navigate = useNavigate()

  useEffect(() => {
    if (apiProduct.productId) {
      fetchPublishedPortals()
      fetchProductCategories()
    }
  }, [apiProduct.productId, apiProduct])

  const fetchPublishedPortals = async () => {
    try {
      const res = await apiProductApi.getApiProductPublications(apiProduct.productId)
      setPortalCount(res.data.content?.length || 0)
    } catch (error) {
    } finally {
    }
  }

  const fetchProductCategories = async () => {
    try {
      // 获取产品关联的类别信息
      const res = await apiProductApi.getProductCategories(apiProduct.productId)
      
      // 检查返回的数据结构，确保是数组
      let categoriesData = res.data;
      
      // 如果没有关联类别，直接设置空数组
      if (!categoriesData || categoriesData.length === 0) {
        setProductCategories([])
        return
      }
      
      // 获取所有类别信息以显示类别名称
      const allCategoriesRes = await getProductCategories()
      const allCategories = allCategoriesRes.data.content || allCategoriesRes.data || []
      
      // 将产品关联的类别ID映射为类别名称
      const categoriesWithNames = categoriesData.map((category: any) => {
        // 处理不同的数据格式
        const categoryId = category.categoryId || category.id;
        const fullCategoryInfo = allCategories.find((c: ProductCategory) => 
          (c.categoryId === categoryId) || (c.id === categoryId)
        )
        return fullCategoryInfo || category
      })
      
      setProductCategories(categoriesWithNames)
    } catch (error) {
      console.error('获取产品类别失败:', error)
      setProductCategories([])
    }
  }


  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">API产品概览</p>
      </div>

      {/* 基本信息 */}
      <Card 
        title="基本信息"
        extra={
          <Button 
            type="primary" 
            icon={<EditOutlined />} 
            onClick={onEdit}
          >
            编辑
          </Button>
        }
      >
        <div>
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
             <span className="text-xs text-gray-600">产品名称:</span>
             <span className="col-span-2 text-xs text-gray-900">{apiProduct.name}</span>
             <span className="text-xs text-gray-600">产品ID:</span>
              <div className="col-span-2 flex items-center gap-2">
                <span className="text-xs text-gray-700">{apiProduct.productId}</span>
                <CopyOutlined 
                  className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors ml-1" 
                  style={{ fontSize: '12px' }}
                  onClick={async () => {
                    try {
                      await copyToClipboard(apiProduct.productId);
                      message.success('产品ID已复制');
                    } catch {
                      message.error('复制失败，请手动复制');
                    }
                  }}
                />
              </div>
            </div>
            
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
             <span className="text-xs text-gray-600">类型:</span>
              <span className="col-span-2 text-xs text-gray-900">
                {apiProduct.type === 'REST_API' ? 'REST API' : 
                 apiProduct.type === 'AGENT_API' ? 'Agent API' : 
                 apiProduct.type === 'MODEL_API' ? 'Model API' : 'MCP Server'}
              </span>
             <span className="text-xs text-gray-600">状态:</span>
              <div className="col-span-2 flex items-center">
                {apiProduct.status === "PENDING" ? (
                  <ExclamationCircleFilled className="text-yellow-500 mr-2" style={{fontSize: '10px'}} />
                ) : apiProduct.status === "READY" ? (
                  <ClockCircleFilled className="text-blue-500 mr-2" style={{fontSize: '10px'}} />
                ) : (
                  <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '10px'}} />
                )}
                <span className="text-xs text-gray-900">
                  {apiProduct.status === "PENDING" ? "待配置" : apiProduct.status === "READY" ? "待发布" : "已发布"}
                </span>
              </div>
            </div>
            
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
              <span className="text-xs text-gray-600">自动审批订阅:</span>
              <div className="col-span-2 flex items-center">
                {apiProduct.autoApprove === true ? (
                  <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '10px'}} />
                ) : (
                  <MinusCircleFilled className="text-gray-400 mr-2" style={{fontSize: '10px'}} />
                )}
                <span className="text-xs text-gray-900">
                 {apiProduct.autoApprove === true ? '已开启' : '已关闭'}
                </span>
              </div>
              <span className="text-xs text-gray-600">创建时间:</span>
              <span className="col-span-2 text-xs text-gray-700">{formatDateTime(apiProduct.createAt)}</span>
            </div>
            
            <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
              <span className="text-xs text-gray-600">产品类别:</span>
              <div className="col-span-2 text-xs text-gray-900">
                {productCategories && productCategories.length > 0 ? (
                  <span>
                    {productCategories.map((category, index) => (
                      <span key={category.categoryId}>
                        <span 
                          className="text-gray-900 hover:text-blue-600 cursor-pointer hover:underline transition-colors"
                          onClick={() => navigate(`/product-categories/${category.categoryId}`)}
                        >
                          {category.name}
                        </span>
                        {index < productCategories.length - 1 && (
                          <span className="text-gray-400 mx-2">|</span>
                        )}
                      </span>
                    ))}
                  </span>
                ) : (
                  <span className="text-gray-400">-</span>
                )}
              </div>
              {/* Feature 配置 - 仅 MODEL_API 类型显示 */}
              {apiProduct.type === 'MODEL_API' && (
                <>
                  <span className="text-xs text-gray-600">模型参数:</span>
                  <div className="col-span-2 text-xs text-gray-700">
                    {apiProduct.feature?.modelFeature ? (
                      <span>
                        {[
                          apiProduct.feature.modelFeature.model,
                          apiProduct.feature.modelFeature.maxTokens && `${apiProduct.feature.modelFeature.maxTokens} tokens`,
                          apiProduct.feature.modelFeature.temperature !== null && apiProduct.feature.modelFeature.temperature !== undefined && `temperature ${apiProduct.feature.modelFeature.temperature}`,
                          apiProduct.feature.modelFeature.webSearch && '联网搜索'
                        ].filter(Boolean).map((param, index, array) => (
                          <span key={index}>
                            <span className="text-gray-900">{param}</span>
                            {index < array.length - 1 && (
                              <span className="text-gray-400 mx-2">|</span>
                            )}
                          </span>
                        ))}
                      </span>
                    ) : (
                      <span className="text-gray-400">-</span>
                    )}
                  </div>
                </>
              )}
            </div>

            {apiProduct.description && (
              <div className="grid grid-cols-6 gap-8 pt-2 pb-2">
                <span className="text-xs text-gray-600">描述:</span>
                <span className="col-span-5 text-xs text-gray-700 leading-relaxed">
                  {apiProduct.description}
                </span>
              </div>
            )}

        </div>
      </Card>

      {/* 统计数据 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={8}>
          <Card 
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => {
              navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=portal`)
            }}
          >
            <Statistic
              title="发布的门户"
              value={portalCount}
              prefix={<GlobalOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card 
            className="cursor-pointer hover:shadow-md transition-shadow"
            onClick={() => {
              navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=link-api`)
            }}
          >
            <Statistic
              title="关联API"
              value={getServiceName(linkedService) || '未关联'}
              prefix={<ApiOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={8}>
          <Card className="hover:shadow-md transition-shadow">
            <Statistic
              title="订阅用户"
              value={subscriberCount}
              prefix={<TeamOutlined className="text-blue-500" />}
              valueStyle={{ color: '#1677ff', fontSize: '24px' }}
            />
          </Card>
        </Col>
      </Row>

    </div>
  )
} 