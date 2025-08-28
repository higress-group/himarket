import React, { useState, useEffect } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { HomeOutlined, GlobalOutlined, AppstoreOutlined, DesktopOutlined, UserOutlined, MenuOutlined, SettingOutlined } from '@ant-design/icons'
import { Button, Skeleton } from 'antd'
import { isAuthenticated, removeToken } from '../lib/utils'

interface NavigationItem {
  name: string
  cn: string
  href: string
  icon: React.ComponentType<any>
  children?: NavigationItem[]
}

interface LayoutProps {
  loading?: boolean;
}

const Layout: React.FC<LayoutProps> = ({ loading = false }) => {
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
    { 
      name: '实例管理', 
      cn: '实例管理', 
      href: '/consoles', 
      icon: SettingOutlined,
      children: [
        { name: '网关实例', cn: '网关实例', href: '/consoles/gateway', icon: DesktopOutlined },
        { name: 'Nacos实例', cn: 'Nacos实例', href: '/consoles/nacos', icon: DesktopOutlined },
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
          <span className="text-2xl font-bold">AI开放平台</span>
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
            {loading ? (
              <div className="space-y-8">
                {/* 页面标题骨架屏 */}
                <div className="mb-8">
                  <Skeleton.Input active size="large" style={{ width: 300, height: 48, marginBottom: 16 }} />
                  <Skeleton.Input active size="small" style={{ width: '60%', height: 24 }} />
                </div>
                
                {/* 操作按钮骨架屏 */}
                <div className="flex justify-end mb-6">
                  <Skeleton.Button active size="large" style={{ width: 120, height: 40 }} />
                </div>
                
                {/* 搜索/过滤器骨架屏 */}
                <div className="mb-6">
                  <Skeleton.Input active size="large" style={{ width: '100%', height: 40 }} />
                </div>
                
                {/* 内容区域骨架屏 */}
                <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                  {Array.from({ length: 6 }).map((_, index) => (
                    <div key={index} className="h-full rounded-lg shadow-lg bg-white p-4">
                      <div className="flex items-start space-x-4">
                        <Skeleton.Avatar size={48} active />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-2">
                            <Skeleton.Input active size="small" style={{ width: 120 }} />
                            <Skeleton.Input active size="small" style={{ width: 60 }} />
                          </div>
                          <Skeleton.Input active size="small" style={{ width: '100%', marginBottom: 12 }} />
                          <Skeleton.Input active size="small" style={{ width: '80%', marginBottom: 8 }} />
                          <div className="flex items-center justify-between">
                            <Skeleton.Input active size="small" style={{ width: 60 }} />
                            <Skeleton.Input active size="small" style={{ width: 80 }} />
                          </div>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <Outlet />
            )}
          </main>
        </div>
      </div>
    </div>
  )
}

export default Layout
