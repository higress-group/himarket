import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Button, Dropdown, MenuProps, Typography, Spin, Modal, message } from 'antd'
import {
  MoreOutlined,
  LeftOutlined,
  EyeOutlined,
  ApiOutlined,
  TeamOutlined,
  SafetyOutlined,
  CloudOutlined,
  DashboardOutlined
} from '@ant-design/icons'
import { PortalOverview } from '@/components/portal/PortalOverview'
import { PortalPublishedApis } from '@/components/portal/PortalPublishedApis'
import { PortalDevelopers } from '@/components/portal/PortalDevelopers'
import { PortalConsumers } from '@/components/portal/PortalConsumers'
import { PortalDashboard } from '@/components/portal/PortalDashboard'
import { PortalSecurity } from '@/components/portal/PortalSecurity'
import { PortalDomain } from '@/components/portal/PortalDomain'
import PortalFormModal from '@/components/portal/PortalFormModal'
import { portalApi } from '@/lib/api'
import { Portal } from '@/types'

const { Title } = Typography

// 移除mockPortal，使用真实API数据

const menuItems = [
  {
    key: "overview",
    label: "Overview",
    icon: EyeOutlined,
    description: "Portal概览"
  },
  {
    key: "published-apis",
    label: "Products",
    icon: ApiOutlined,
    description: "已发布的API产品"
  },
  {
    key: "developers",
    label: "Developers",
    icon: TeamOutlined,
    description: "开发者管理"
  },
  {
    key: "security",
    label: "Security",
    icon: SafetyOutlined,
    description: "安全设置"
  },
  {
    key: "domain",
    label: "Domain",
    icon: CloudOutlined,
    description: "域名管理"
  },
  // {
  //   key: "consumers",
  //   label: "Consumers",
  //   icon: UserOutlined,
  //   description: "消费者管理"
  // },
  // {
  //   key: "dashboard",
  //   label: "Dashboard",
  //   icon: DashboardOutlined,
  //   description: "监控面板"
  // }
]

export default function PortalDetail() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [portal, setPortal] = useState<Portal | null>(null)
  const [loading, setLoading] = useState(true) // 初始状态为 loading
  const [error, setError] = useState<string | null>(null)
  const [editModalVisible, setEditModalVisible] = useState(false)

  // 从URL查询参数获取当前tab，默认为overview
  const currentTab = searchParams.get('tab') || 'overview'
  const [activeTab, setActiveTab] = useState(currentTab)

  const fetchPortalData = async () => {
    try {
      setLoading(true)
      const portalId = searchParams.get('id') || 'portal-6882e06f4fd0c963020e3485'
      const response = await portalApi.getPortalDetail(portalId) as any
      if (response && response.code === 'SUCCESS') {
        setPortal(response.data)
      } else {
        setError(response?.message || '获取Portal信息失败')
      }
    } catch (err) {
      console.error('获取Portal信息失败:', err)
      setError('获取Portal信息失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchPortalData()
  }, [])

  // 当URL中的tab参数变化时，更新activeTab
  useEffect(() => {
    setActiveTab(currentTab)
  }, [currentTab])

  const handleBackToPortals = () => {
    navigate('/portals')
  }

  // 处理tab切换，同时更新URL查询参数
  const handleTabChange = (tabKey: string) => {
    setActiveTab(tabKey)
    const newSearchParams = new URLSearchParams(searchParams)
    newSearchParams.set('tab', tabKey)
    setSearchParams(newSearchParams)
  }

  const handleEdit = () => {
    setEditModalVisible(true)
  }

  const handleEditSuccess = () => {
    setEditModalVisible(false)
    fetchPortalData()
  }

  const handleEditCancel = () => {
    setEditModalVisible(false)
  }

  const renderContent = () => {
    if (!portal) return null
    
    switch (activeTab) {
      case "overview":
        return <PortalOverview portal={portal} onEdit={handleEdit} />
      case "published-apis":
        return <PortalPublishedApis portal={portal} />
      case "developers":
        return <PortalDevelopers portal={portal} />
      case "security":
        return <PortalSecurity portal={portal} onRefresh={fetchPortalData} />
      case "domain":
        return <PortalDomain portal={portal} onRefresh={fetchPortalData} />
      case "consumers":
        return <PortalConsumers portal={portal} />
      case "dashboard":
        return <PortalDashboard portal={portal} />
      default:
        return <PortalOverview portal={portal} onEdit={handleEdit} />
    }
  }

  const dropdownItems: MenuProps['items'] = [
    {
      key: "delete",
      label: "删除",
      danger: true,
      onClick: () => {
        Modal.confirm({
          title: "删除Portal",
          content: "确定要删除该Portal吗？",
          onOk: () => {
            return handleDeletePortal();
          },
        });
      },
    },
  ]
  const handleDeletePortal = () => {
    return portalApi.deletePortal(searchParams.get('id') || '').then(() => {
      message.success('删除成功')
      navigate('/portals')
    }).catch((error) => {
      message.error(error?.response?.data?.message || '删除失败，请稍后重试')
      throw error; // 重新抛出错误，让Modal保持loading状态
    })
  }

  if (error || !portal) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="text-center">
          {error && <><p className=" mb-4">{error || 'Portal信息不存在'}</p>
          <Button onClick={() => navigate('/portals')}>返回Portal列表</Button></>}
          {!error && <Spin fullscreen spinning={loading} />}
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full">
      <Spin fullscreen spinning={loading} />
      {/* Portal详情侧边栏 */}
      <div className="w-64 border-r bg-white flex flex-col">
        {/* 返回按钮 */}
        <div className="pb-4 border-b">
          <Button 
            type="text"
            // className="w-full justify-start text-gray-600 hover:text-gray-900"
            onClick={handleBackToPortals}
            icon={<LeftOutlined />}
          >
            返回
          </Button>
        </div>

        {/* Portal 信息 */}
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-2">
            <Title level={5} className="mb-0">{portal.name}</Title>
            <Dropdown menu={{ items: dropdownItems }} trigger={['click']}>
              <Button type="text" icon={<MoreOutlined />} size="small" />
            </Dropdown>
          </div>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon
            return (
              <button
                key={item.key}
                onClick={() => handleTabChange(item.key)}
                className={`w-full flex items-center gap-3 px-3 py-3 rounded-lg text-left transition-colors ${
                  activeTab === item.key
                    ? "bg-blue-50 text-blue-600 border border-blue-200"
                    : "hover:bg-gray-50 text-gray-700"
                }`}
              >
                <Icon className="h-4 w-4 flex-shrink-0" />
                <div className="flex-1">
                  <div className="font-medium">{item.label}</div>
                  <div className="text-xs text-gray-500 mt-1">{item.description}</div>
                </div>
              </button>
            )
          })}
        </nav>
      </div>

      {/* 主内容区域 */}
      <div className="flex-1 overflow-auto">
      {renderContent()}
      </div>

      {portal && (
        <PortalFormModal
          visible={editModalVisible}
          onCancel={handleEditCancel}
          onSuccess={handleEditSuccess}
          portal={portal}
        />
      )}
    </div>
  )
} 