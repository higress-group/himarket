import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Dropdown, MenuProps, Modal, message } from 'antd'
import {
  MoreOutlined,
  LeftOutlined,
  EyeOutlined,
  LinkOutlined,
  BookOutlined,
  GlobalOutlined
} from '@ant-design/icons'
import { ApiProductOverview } from '@/components/api-product/ApiProductOverview'
import { ApiProductLinkApi } from '@/components/api-product/ApiProductLinkApi'
import { ApiProductUsageGuide } from '@/components/api-product/ApiProductUsageGuide'
import { ApiProductPortal } from '@/components/api-product/ApiProductPortal'
// import { ApiProductDashboard } from '@/components/api-product/ApiProductDashboard'
import { apiProductApi } from '@/lib/api';
import type { ApiProduct, LinkedService } from '@/types/api-product';

import ApiProductFormModal from '@/components/api-product/ApiProductFormModal';

const menuItems = [
  {
    key: "overview",
    label: "Overview",
    description: "产品概览",
    icon: EyeOutlined
  },
  {
    key: "link-api",
    label: "Link API",
    description: "API关联",
    icon: LinkOutlined
  },
  {
    key: "usage-guide",
    label: "Usage Guide",
    description: "使用指南",
    icon: BookOutlined
  },
  {
    key: "portal",
    label: "Portal",
    description: "发布的门户",
    icon: GlobalOutlined
  },
  // {
  //   key: "dashboard",
  //   label: "Dashboard",
  //   description: "实时监控和统计",
  //   icon: DashboardOutlined
  // }
]

export default function ApiProductDetail() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [apiProduct, setApiProduct] = useState<ApiProduct | null>(null)
  const [linkedService, setLinkedService] = useState<LinkedService | null>(null)
  const [, setLoading] = useState(true) // 添加 loading 状态
  
  // 从URL query参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview'
  // 验证tab值是否有效，如果无效则使用默认值
  const validTab = menuItems.some(item => item.key === currentTab) ? currentTab : 'overview'
  const [activeTab, setActiveTab] = useState(validTab)

  const [editModalVisible, setEditModalVisible] = useState(false)

  
  const fetchApiProduct = async () => {
    const productId = searchParams.get('productId')
    if (productId) {
      setLoading(true)
      try {
        // 并行获取Product详情和关联信息
        const [productRes, refRes] = await Promise.all([
          apiProductApi.getApiProductDetail(productId),
          apiProductApi.getApiProductRef(productId).catch(() => ({ data: null })) // 关联信息获取失败不影响页面显示
        ])
        
        setApiProduct(productRes.data)
        setLinkedService(refRes.data || null)
      } catch (error) {
        console.error('获取Product详情失败:', error)
      } finally {
        setLoading(false)
      }
    }
  }

  // 更新关联信息的回调函数
  const handleLinkedServiceUpdate = (newLinkedService: LinkedService | null) => {
    setLinkedService(newLinkedService)
  }

  useEffect(() => {
    fetchApiProduct()
  }, [searchParams.get('productId')])

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
    if (!apiProduct) {
      return <div className="p-6">Loading...</div>
    }
    
    switch (activeTab) {
      case "overview":
        return <ApiProductOverview apiProduct={apiProduct} linkedService={linkedService} onEdit={handleEdit} />
      case "link-api":
        return <ApiProductLinkApi 
          apiProduct={apiProduct} 
          linkedService={linkedService}
          onLinkedServiceUpdate={handleLinkedServiceUpdate}
          handleRefresh={fetchApiProduct}
        />
      case "usage-guide":
        return <ApiProductUsageGuide apiProduct={apiProduct} handleRefresh={fetchApiProduct} />
      case "portal":
        return <ApiProductPortal apiProduct={apiProduct} />
      // case "dashboard":
      //   return <ApiProductDashboard apiProduct={apiProduct} />
      default:
        return <ApiProductOverview apiProduct={apiProduct} linkedService={linkedService} onEdit={handleEdit} />
    }
  }

  const dropdownItems: MenuProps['items'] = [
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
    if (!apiProduct) return;
    
    apiProductApi.deleteApiProduct(apiProduct.productId).then(() => {
      message.success('删除成功')
      navigate('/api-products')
    }).catch(() => {
      // message.error(error.response?.data?.message || '删除失败')
    })
  }

  const handleEdit = () => {
    setEditModalVisible(true)
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
            返回
          </Button>
        </div>

        {/* API Product 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-semibold">{apiProduct?.name || 'Loading...'}</h2>
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
              <Button type="text" icon={<MoreOutlined />} />
            </Dropdown>
          </div>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 space-y-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.key}
                onClick={() => handleTabChange(item.key)}
                className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors ${
                  activeTab === item.key
                    ? "bg-blue-500 text-white"
                    : "hover:bg-gray-100"
                }`}
              >
                <Icon className="h-4 w-4 flex-shrink-0" />
                <div>
                  <div className="font-medium">{item.label}</div>
                  <div className="text-xs opacity-70">{item.description}</div>
                </div>
              </button>
            );
          })}
        </nav>
      </div>

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto min-w-0">
        <div className="w-full max-w-full">
          {renderContent()}
        </div>
      </div>

      {apiProduct && (
        <ApiProductFormModal
          visible={editModalVisible}
          onCancel={handleEditCancel}
          onSuccess={handleEditSuccess}
          productId={apiProduct.productId}
          initialData={apiProduct}
        />
      )}
    </div>
  )
}
