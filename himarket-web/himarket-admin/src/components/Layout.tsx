import {
  HomeOutlined,
  ProductOutlined,
  UserOutlined,
  MenuOutlined,
  CloudServerOutlined,
  CodeSandboxOutlined,
  TagsOutlined,
  BarChartOutlined,
  DashboardOutlined,
  MonitorOutlined,
  RightOutlined,
  LockOutlined,
  LogoutOutlined,
  GlobalOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, message, Tooltip } from 'antd';
import React, { useState, useEffect } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';

import { useLocale } from '@/contexts/LocaleContext';

import { AdminBrand } from './AdminBrand';
import { ChangePasswordModal } from './ChangePasswordModal';
import GatewayIcon from './icons/GatewayIcon';
import NacosIcon from './icons/NacosIcon';
import { authApi } from '../lib/api';
import { isAuthenticated, removeToken } from '../lib/utils';

interface NavigationItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  key: string;
  children?: NavigationItem[];
}

function SidebarIcon({ icon: Icon, level = 0 }: { icon: NavigationItem['icon']; level?: number }) {
  return (
    <span
      className={`flex shrink-0 items-center justify-center overflow-visible ${
        level > 0 ? 'h-5 w-5 text-[14px]' : 'h-5 w-5 text-[16px]'
      }`}
    >
      <Icon />
    </span>
  );
}

const Layout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { locale, setLocale, t } = useLocale();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);
  const [changePasswordLoading, setChangePasswordLoading] = useState(false);

  useEffect(() => {
    // 检查 cookie 中的 token 来判断登录状态
    const checkAuthStatus = () => {
      const hasToken = isAuthenticated();
      setIsLoggedIn(hasToken);
    };

    checkAuthStatus();
    // 监听 storage 变化（当其他标签页登录/登出时）
    window.addEventListener('storage', checkAuthStatus);

    return () => {
      window.removeEventListener('storage', checkAuthStatus);
    };
  }, []);

  useEffect(() => {
    // 进入对象详情页自动折叠全局侧边栏，避免和详情页内部导航抢层级。
    const apiProductTypeRoutes = [
      'model-api',
      'mcp-server',
      'agent-skill',
      'worker',
      'agent-api',
      'rest-api',
    ];
    const apiProductMatch = location.pathname.match(/^\/api-products\/([^/]+)$/);
    const isApiProductDetail =
      apiProductMatch &&
      apiProductMatch[1] !== undefined &&
      !apiProductTypeRoutes.includes(apiProductMatch[1]);
    const isPortalDetail = location.pathname.match(/^\/portals\/[^/]+$/);
    const isCategoryDetail = location.pathname.match(/^\/product-categories\/[^/]+$/);

    if (isPortalDetail || isApiProductDetail || isCategoryDetail) {
      setSidebarCollapsed(true);
    } else {
      setSidebarCollapsed(false);
    }
  }, [location.pathname]);

  const navigation: NavigationItem[] = [
    { href: '/portals', icon: HomeOutlined, key: 'portal', name: t('nav.portal') },
    {
      href: '/api-products/model-api',
      icon: ProductOutlined,
      key: 'api-products',
      name: t('nav.apiProducts'),
    },
    {
      href: '/product-categories',
      icon: TagsOutlined,
      key: 'categories',
      name: t('nav.categories'),
    },
    {
      children: [
        {
          href: '/consoles/gateway',
          icon: GatewayIcon,
          key: 'gateway-instances',
          name: t('nav.gatewayInstances'),
        },
        {
          href: '/consoles/nacos',
          icon: NacosIcon,
          key: 'nacos-instances',
          name: t('nav.nacosInstances'),
        },
        {
          href: '/consoles/airegistry',
          icon: GlobalOutlined,
          key: 'airegistry-instances',
          name: t('nav.airegistryInstances'),
        },
        {
          href: '/consoles/sandbox',
          icon: CodeSandboxOutlined,
          key: 'sandbox-instances',
          name: t('nav.sandboxInstances'),
        },
      ],
      href: '/consoles',
      icon: CloudServerOutlined,
      key: 'instances',
      name: t('nav.instanceManagement'),
    },
    {
      children: [
        {
          href: '/observability/model-dashboard',
          icon: DashboardOutlined,
          key: 'model-monitor',
          name: t('nav.modelMonitor'),
        },
        {
          href: '/observability/mcp-monitor',
          icon: MonitorOutlined,
          key: 'mcp-monitor',
          name: t('nav.mcpMonitor'),
        },
      ],
      href: '/observability',
      icon: BarChartOutlined,
      key: 'observability',
      name: t('nav.observability'),
    },
  ];

  const toggleSidebar = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  const handleLogout = () => {
    removeToken();
    setIsLoggedIn(false);
    navigate('/login');
  };

  const handleChangePassword = async (values: { newPassword: string; oldPassword: string }) => {
    setChangePasswordLoading(true);
    try {
      await authApi.changePassword(values);
      message.success(t('layout.passwordChanged'), 1);
      setChangePasswordOpen(false);
      handleLogout();
    } finally {
      setChangePasswordLoading(false);
    }
  };

  const localeMenuItems = [
    {
      icon: locale === 'zh-CN' ? <CheckOutlined /> : <span className="w-[14px]" />,
      key: 'locale-zh-CN',
      label: t('language.zhCN'),
      onClick: () => setLocale('zh-CN'),
    },
    {
      icon: locale === 'en-US' ? <CheckOutlined /> : <span className="w-[14px]" />,
      key: 'locale-en-US',
      label: t('language.enUS'),
      onClick: () => setLocale('en-US'),
    },
  ];

  const toggleGroup = (key: string) => {
    setCollapsedGroups((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const isMenuActive = (item: NavigationItem): boolean => {
    if (item.children) {
      return item.children.some((child) => location.pathname === child.href);
    }
    if (location.pathname === item.href || location.pathname.startsWith(item.href + '/')) {
      return true;
    }
    // API Products 菜单在所有 /api-products/* 路由下保持高亮
    if (item.href === '/api-products/model-api' && location.pathname.startsWith('/api-products/')) {
      return true;
    }
    return false;
  };

  const renderMenuItem = (item: NavigationItem, level: number = 0) => {
    const isActive = isMenuActive(item);
    const hasChildren = item.children && item.children.length > 0;
    const isGroupCollapsed = collapsedGroups[item.key] ?? false;
    const itemTone = isActive
      ? hasChildren
        ? 'bg-gray-50 text-gray-900 font-semibold'
        : 'bg-gray-100 text-gray-950 font-semibold shadow-[inset_0_0_0_1px_rgba(17,24,39,0.04)]'
      : 'text-gray-500 hover:bg-gray-50 hover:text-gray-950';

    // 折叠状态：隐藏子菜单，图标居中，添加 Tooltip
    if (sidebarCollapsed) {
      if (level > 0) return null;
      if (hasChildren) {
        return (
          <Tooltip
            key={item.key}
            placement="right"
            title={
              <div className="flex flex-col">
                {(item.children || []).map((child) => (
                  <Link
                    className={`block px-2 py-1 rounded ${
                      location.pathname === child.href
                        ? 'text-white font-semibold'
                        : 'text-gray-300 hover:text-white'
                    }`}
                    key={child.key}
                    to={child.href}
                  >
                    {child.name}
                  </Link>
                ))}
              </div>
            }
          >
            <div
              className={`flex h-11 cursor-pointer items-center justify-center rounded-lg transition-colors duration-150 ${itemTone}`}
            >
              <SidebarIcon icon={item.icon} />
            </div>
          </Tooltip>
        );
      }
      return (
        <Tooltip key={item.key} placement="right" title={item.name}>
          <Link
            className={`flex h-11 items-center justify-center rounded-lg transition-colors duration-150 ${itemTone}`}
            to={item.href}
          >
            <SidebarIcon icon={item.icon} />
          </Link>
        </Tooltip>
      );
    }

    // 展开状态
    // 分组项：可折叠
    if (hasChildren) {
      return (
        <div className={level === 0 ? 'py-px' : undefined} key={item.key}>
          <div
            className={`flex h-11 cursor-pointer items-center rounded-lg px-3.5 transition-colors duration-150 ${itemTone}`}
            onClick={() => toggleGroup(item.key)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                toggleGroup(item.key);
              }
            }}
            role="button"
            tabIndex={0}
          >
            <SidebarIcon icon={item.icon} />
            <div className="ml-3 flex flex-1 flex-col">
              <span className="text-[15px] leading-none">{item.name}</span>
            </div>
            <RightOutlined
              className={`text-[11px] text-gray-400 transition-transform duration-200 ${
                isGroupCollapsed ? '' : 'rotate-90'
              }`}
            />
          </div>
          <div
            className={`mb-1.5 ml-[18px] mt-1 overflow-hidden border-l border-gray-100/80 pl-3 transition-all duration-200 ${
              isGroupCollapsed ? 'max-h-0 opacity-0' : 'max-h-96 opacity-1'
            }`}
          >
            <div className="space-y-1 py-0.5">
              {(item.children || []).map((child) => renderMenuItem(child, level + 1))}
            </div>
          </div>
        </div>
      );
    }

    // 普通菜单项 / 子菜单项
    return (
      <div className={level === 0 ? 'py-px' : undefined} key={item.key}>
        <Link
          className={`flex items-center rounded-lg transition-colors duration-150 ${
            level > 0 ? 'h-9 px-3 text-[14px]' : 'h-11 px-3.5 text-[15px]'
          } ${itemTone}`}
          to={item.href}
        >
          <SidebarIcon icon={item.icon} level={level} />
          <div className="ml-3 flex flex-1 flex-col">
            <span className="leading-none">{item.name}</span>
          </div>
        </Link>
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-background">
      {/* 顶部导航栏 */}
      <header className="w-full h-16 flex items-center justify-between px-8 bg-white border-b shadow-sm">
        <div className="flex items-center space-x-2">
          <div className="bg-white">
            <Button
              className="hover:bg-gray-100"
              icon={<MenuOutlined />}
              onClick={toggleSidebar}
              type="text"
            />
          </div>
          <AdminBrand />
        </div>
        {/* 顶部右侧用户信息或登录按钮 */}
        {isLoggedIn ? (
          <div className="flex items-center gap-2">
            <Dropdown menu={{ items: localeMenuItems }} placement="bottomRight" trigger={['click']}>
              <Button
                className="rounded-full border-gray-200 text-gray-700 hover:border-gray-300 hover:bg-gray-50"
                icon={<GlobalOutlined />}
              >
                {t('layout.languageShort')}
              </Button>
            </Dropdown>
            <Dropdown
              menu={{
                items: [
                  {
                    icon: <LockOutlined />,
                    key: 'change-password',
                    label: t('layout.changePassword'),
                    onClick: () => setChangePasswordOpen(true),
                  },
                  {
                    type: 'divider' as const,
                  },
                  {
                    icon: <LogoutOutlined />,
                    key: 'logout',
                    label: t('layout.logout'),
                    onClick: handleLogout,
                  },
                ],
              }}
              placement="bottomRight"
              trigger={['click']}
            >
              <button className="flex items-center gap-2 rounded-full border border-gray-200 bg-white px-2 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:border-gray-300 hover:bg-gray-50">
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100 text-gray-600">
                  <UserOutlined />
                </span>
                admin
              </button>
            </Dropdown>
            <ChangePasswordModal
              loading={changePasswordLoading}
              onCancel={() => setChangePasswordOpen(false)}
              onSubmit={handleChangePassword}
              open={changePasswordOpen}
            />
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Dropdown menu={{ items: localeMenuItems }} placement="bottomRight" trigger={['click']}>
              <Button icon={<GlobalOutlined />}>{t('layout.languageShort')}</Button>
            </Dropdown>
            <button
              className="flex items-center px-4 py-2 rounded bg-black text-white hover:bg-gray-800"
              onClick={() => navigate('/login')}
            >
              <UserOutlined className="mr-2" /> {t('layout.login')}
            </button>
          </div>
        )}
      </header>
      <div className="flex">
        {/* 侧边栏 */}
        <aside
          className={`min-h-screen border-r bg-white pt-8 transition-all duration-300 ${
            sidebarCollapsed ? 'w-[72px]' : 'w-64'
          }`}
        >
          <nav className={`flex flex-col gap-1 ${sidebarCollapsed ? 'px-3' : 'px-4'}`}>
            {navigation.map((item) => renderMenuItem(item))}
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
  );
};

export default Layout;
