import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Badge, Dropdown, MenuProps, Modal, message } from 'antd'
import {
  MoreOutlined,
  LeftOutlined
} from '@ant-design/icons'
import { ApiProductOverview } from '@/components/api-product/ApiProductOverview'
import { ApiProductLinkApi } from '@/components/api-product/ApiProductLinkApi'
import { ApiProductApiDocs } from '@/components/api-product/ApiProductApiDocs'
import { ApiProductUsageGuide } from '@/components/api-product/ApiProductUsageGuide'
import { ApiProductPortal } from '@/components/api-product/ApiProductPortal'
import { ApiProductDashboard } from '@/components/api-product/ApiProductDashboard'
import { apiProductApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';
import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';


const menuItems = [
  {
    key: "overview",
    label: "Overview",
    description: "基本信息和统计"
  },
  {
    key: "link-api",
    label: "Link API",
    description: "关联的网关服务"
  },
  {
    key: "api-docs",
    label: "API Config",
    description: "API配置"
  },
  {
    key: "usage-guide",
    label: "Usage Guide",
    description: "使用指南编辑"
  },
  {
    key: "portal",
    label: "Portal",
    description: "发布的门户"
  },
  {
    key: "dashboard",
    label: "Dashboard",
    description: "实时监控和统计"
  }
]

export default function ApiProductDetail() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [apiProduct, setApiProduct] = useState<ApiProduct>({} as ApiProduct)
  const [loading, setLoading] = useState(true) // 添加 loading 状态
  
  // 从URL query参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview'
  // 验证tab值是否有效，如果无效则使用默认值
  const validTab = menuItems.some(item => item.key === currentTab) ? currentTab : 'overview'
  const [activeTab, setActiveTab] = useState(validTab)

  const [editModalVisible, setEditModalVisible] = useState(false)

  
  const fetchApiProduct = () => {
    const productId = searchParams.get('productId')
    if (productId) {
      setLoading(true)
      apiProductApi.getApiProductDetail(productId).then((res: any) => {
        setApiProduct(res.data)
        setLoading(false)
      }).catch(() => {
        setLoading(false)
      })
    }
  }

  useEffect(() => {
    fetchApiProduct()
  }, [validTab])

  // 同步URL参数和activeTab状态
  useEffect(() => {
    setActiveTab(validTab)
  }, [validTab, searchParams])

  const handleBackToApiProducts = () => {
    navigate('/api-products')
  }

  const handleTabChange = (tabKey: string) => {
    setActiveTab(tabKey)
    // 更新URL query参数
    const newSearchParams = new URLSearchParams(searchParams)
    newSearchParams.set('tab', tabKey)
    setSearchParams(newSearchParams)
  }

  const renderContent = () => {
    switch (activeTab) {
      case "overview":
        return <ApiProductOverview apiProduct={apiProduct} />
      case "link-api":
        return <ApiProductLinkApi apiProduct={apiProduct} handleRefresh={fetchApiProduct} />
      case "api-docs":
        return <ApiProductApiDocs apiProduct={apiProduct} handleRefresh={fetchApiProduct} />
      case "usage-guide":
        return <ApiProductUsageGuide apiProduct={apiProduct} handleRefresh={fetchApiProduct} />
      case "portal":
        return <ApiProductPortal apiProduct={apiProduct} />
      case "dashboard":
        return <ApiProductDashboard apiProduct={apiProduct} />
      default:
        return <ApiProductOverview apiProduct={apiProduct} />
    }
  }

  const dropdownItems: MenuProps['items'] = [
    {
      key: 'edit',
      label: '编辑产品',
      onClick: () => {
        setEditModalVisible(true)
      },
    },
    {
      key: 'delete',
      label: '删除',
      onClick: () => {
        Modal.confirm({
          title: '确认删除',
          content: '确定要删除该产品吗？',
          onOk: () => {
            handleDeleteApiProduct()
          },
        })
      },
      danger: true,
    },
  ]

  const handleDeleteApiProduct = () => {
    apiProductApi.deleteApiProduct(apiProduct.productId).then(() => {
      message.success('删除成功')
      navigate('/api-products')
    }).catch((error) => {
      // message.error(error.response?.data?.message || '删除失败')
    })
  }

  const handleEditSuccess = () => {
    setEditModalVisible(false)
    fetchApiProduct()
  }

  const handleEditCancel = () => {
    setEditModalVisible(false)
  }

  return (
    <div className="flex h-full w-full overflow-hidden">
      {/* API Product 详情侧边栏 */}
      <div className="w-64 border-r bg-white flex flex-col flex-shrink-0">
        {/* 返回按钮 */}
        <div className="pb-4 border-b">
          <Button
            type="text"
            // className="w-full justify-start"
            onClick={handleBackToApiProducts}
            icon={<LeftOutlined />}
          >
            返回 API Products
          </Button>
        </div>

        {/* API Product 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-semibold">{apiProduct.name}</h2>
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
              <Button type="text" icon={<MoreOutlined />} />
            </Dropdown>
          </div>
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Badge>{apiProduct.type}</Badge>
              
            </div>
            <p className="text-sm text-gray-500">{apiProduct.description}</p>
          </div>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 space-y-1">
          {menuItems.map((item) => (
            <button
              key={item.key}
              onClick={() => handleTabChange(item.key)}
              className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors ${
                activeTab === item.key
                  ? "bg-blue-500 text-white"
                  : "hover:bg-gray-100"
              }`}
            >
              {/*<item.icon className="h-4 w-4" />*/}
              <div>
                <div className="font-medium">{item.label}</div>
                <div className="text-xs opacity-70">{item.description}</div>
              </div>
            </button>
          ))}
        </nav>
      </div>

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto min-w-0">
        <div className="w-full max-w-full">
          {renderContent()}
        </div>
      </div>

      <ApiProductFormModal
        visible={editModalVisible}
        onCancel={handleEditCancel}
        onSuccess={handleEditSuccess}
        productId={apiProduct.productId}
        initialData={apiProduct}
      />
    </div>
  )
}
