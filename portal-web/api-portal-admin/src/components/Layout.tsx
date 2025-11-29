import React, { useState, useEffect } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { GlobalOutlined, AppstoreOutlined, DesktopOutlined, UserOutlined, MenuOutlined, SettingOutlined, FolderOutlined, BarChartOutlined, DashboardOutlined, MonitorOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { isAuthenticated, removeToken } from '../lib/utils'

interface NavigationItem {
  name: string
  cn: string
  href: string
  icon: React.ComponentType<any>
  children?: NavigationItem[]
}

const Layout: React.FC = () => {
  const location = useLocation()
  const navigate = useNavigate()
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false)
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false)

  useEffect(() => {
    // 检查 cookie 中的 token 来判断登录状态
    const checkAuthStatus = () => {
      const hasToken = isAuthenticated()
      setIsLoggedIn(hasToken)
    }
    
    checkAuthStatus()
    // 监听 storage 变化（当其他标签页登录/登出时）
    window.addEventListener('storage', checkAuthStatus)
    
    return () => {
      window.removeEventListener('storage', checkAuthStatus)
    }
  }, [])

  useEffect(() => {
    // 进入详情页自动折叠侧边栏
    if (location.pathname.startsWith('/portals/detail') || location.pathname.startsWith('/api-products/detail')) {
      setSidebarCollapsed(true)
    } else {
      setSidebarCollapsed(false)
    }
  }, [location.pathname])

  const navigation: NavigationItem[] = [
    { name: 'Portal', cn: '门户', href: '/portals', icon: GlobalOutlined },
    { name: 'API Products', cn: 'API产品', href: '/api-products', icon: AppstoreOutlined },
    { name: 'Categories', cn: '产品类别', href: '/product-categories', icon: FolderOutlined },
    { 
      name: '实例管理', 
      cn: '实例管理', 
      href: '/consoles', 
      icon: SettingOutlined,
      children: [
        { name: 'Nacos实例', cn: 'Nacos实例', href: '/consoles/nacos', icon: DesktopOutlined },
        { name: '网关实例', cn: '网关实例', href: '/consoles/gateway', icon: DesktopOutlined },
      ]
    },
    {
      name: '观测分析',
      cn: '观测分析',
      href: '/observability',
      icon: BarChartOutlined,
      children: [
        { name: '模型监控', cn: '模型监控', href: '/observability/model-dashboard', icon: DashboardOutlined },
        { name: 'MCP监控', cn: 'MCP监控', href: '/observability/mcp-monitor', icon: MonitorOutlined },
      ]
    },
  ]

  const toggleSidebar = () => {
    setSidebarCollapsed(!sidebarCollapsed)
  }

  const handleLogout = () => {
    removeToken()
    setIsLoggedIn(false)
    navigate('/login')
  }

  const isMenuActive = (item: NavigationItem): boolean => {
    if (location.pathname === item.href) return true
    if (item.children) {
      return item.children.some(child => location.pathname === child.href)
    }
    return false
  }

  const renderMenuItem = (item: NavigationItem, level: number = 0) => {
    const Icon = item.icon
    const isActive = isMenuActive(item)
    const hasChildren = item.children && item.children.length > 0

    return (
      <div key={item.name}>
          <Link
            to={item.href}
            className={`flex items-center mt-2 px-3 py-3 rounded-lg transition-colors duration-150 ${
              level > 0 ? 'ml-4' : ''
            } ${
              isActive && !hasChildren
                ? 'bg-gray-100 text-black font-semibold'
                : 'text-gray-500 hover:text-black hover:bg-gray-50'
            }`}
            title={sidebarCollapsed ? item.name : ''}
          >
            <Icon className="mr-3 h-5 w-5 flex-shrink-0" />
            {!sidebarCollapsed && (
              <div className="flex flex-col flex-1">
                <span className="text-base leading-none">{item.name}</span>
              </div>
            )}
          </Link>
        {!sidebarCollapsed && hasChildren && (
          <div className="ml-2">
            {item.children!.map(child => renderMenuItem(child, level + 1))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background">
      {/* 顶部导航栏 */}
      <header className="w-full h-16 flex items-center justify-between px-8 bg-white border-b shadow-sm">
        <div className="flex items-center space-x-2">
        <div className="bg-white">
              <Button
                type="text"
                icon={<MenuOutlined />}
                onClick={toggleSidebar}
                className="hover:bg-gray-100"
              />
            </div>
          <span className="text-2xl font-bold">HiMarket</span>
        </div>
        {/* 顶部右侧用户信息或登录按钮 */}
        {isLoggedIn ? (
          <div className="flex items-center space-x-2">
            <UserOutlined className="mr-2 text-lg" />
            <span>admin</span>
            <button
              onClick={handleLogout}
              className="ml-2 px-2 py-1 rounded bg-gray-200 hover:bg-gray-300"
            >
              退出
            </button>
          </div>
        ) : (
          <button onClick={() => navigate('/login')} className="flex items-center px-4 py-2 rounded bg-black text-white hover:bg-gray-800">
            <UserOutlined className="mr-2" /> 登录
          </button>
        )}
      </header>
      <div className="flex">
        {/* 侧边栏 */}
        <aside className={`bg-white border-r min-h-screen pt-8 transition-all duration-300 ${
          sidebarCollapsed ? 'w-16' : 'w-64'
        }`}>
          <nav className="flex flex-col space-y-2 px-4">
            {navigation.map(item => renderMenuItem(item))}
          </nav>
        </aside>

        {/* 主内容区域 */}
        <div className="flex-1 min-h-screen overflow-hidden">
          <main className="p-8 w-full max-w-full overflow-x-hidden">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  )
}

export default Layout