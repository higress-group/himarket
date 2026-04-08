import { ApiOutlined, ToolOutlined, RobotOutlined, BulbOutlined } from '@ant-design/icons';
import { Skeleton } from 'antd';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';

import { UserInfo } from './UserInfo';

interface NavigationProps {
  loading?: boolean;
}

export function Navigation({ loading = false }: NavigationProps) {
  const location = useLocation();
  const { t } = useTranslation('header');

  const isActive = (path: string) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  const navigationItems = [
    {
      icon: <ApiOutlined />,
      path: '/apis',
      subtitle: t('navigation.restApi'),
      title: 'APIs',
    },
    {
      icon: <ToolOutlined />,
      path: '/mcp',
      subtitle: t('navigation.toolIntegration'),
      title: 'MCP',
    },
    {
      icon: <BulbOutlined />,
      path: '/models',
      subtitle: t('navigation.aiModel'),
      title: 'Model',
    },
    {
      icon: <RobotOutlined />,
      path: '/agents',
      subtitle: t('navigation.intelligentAssistant'),
      title: 'Agent',
    },
  ];

  return (
    <nav className="sticky top-4 z-50">
      <div className="w-full mx-auto px-4 sm:px-6 lg:px-8">
        <div className="bg-[#f4f4f6]/95 backdrop-blur-sm rounded-xl border border-gray-200 flex justify-between items-center h-20 px-6">
          <div className="flex items-center">
            {loading ? (
              <div className="flex items-center space-x-2">
                <Skeleton.Avatar active size={32} />
                <Skeleton.Input active size="small" style={{ height: 24, width: 120 }} />
              </div>
            ) : (
              <Link
                className="flex items-center space-x-2 hover:opacity-80 transition-opacity"
                to="/"
              >
                <div className="w-8 h-8 rounded-full flex items-center justify-center">
                  {/* LOGO区域 */}
                  <img
                    alt="logo"
                    className="w-6 h-6"
                    src="/logo.png"
                    style={{ display: 'block' }}
                  />
                </div>
                <span className="text-xl font-bold text-gray-900">HiMarket</span>
              </Link>
            )}
          </div>

          <div className="hidden md:flex items-center absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
            {loading ? (
              <div className="flex space-x-3">
                <Skeleton.Input active size="small" style={{ height: 60, width: 100 }} />
                <Skeleton.Input active size="small" style={{ height: 60, width: 100 }} />
                <Skeleton.Input active size="small" style={{ height: 60, width: 100 }} />
                <Skeleton.Input active size="small" style={{ height: 60, width: 100 }} />
              </div>
            ) : (
              <div className="flex space-x-3">
                {navigationItems.map((item) => (
                  <Link
                    className={`px-4 py-3 rounded-lg font-medium transition-all duration-200 flex flex-col items-center justify-center min-w-[100px] ${
                      isActive(item.path)
                        ? 'bg-blue-50/80 text-blue-700 border border-blue-200/50 shadow-sm'
                        : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50/50 border border-transparent'
                    }`}
                    key={item.path}
                    to={item.path}
                  >
                    <div className="flex items-center space-x-1 mb-1">
                      <div
                        className={`text-lg ${isActive(item.path) ? 'text-blue-600' : 'text-gray-500'}`}
                      >
                        {item.icon}
                      </div>
                      <div className="text-sm font-semibold leading-tight">{item.title}</div>
                    </div>
                    <div className="text-xs text-gray-500 leading-tight">{item.subtitle}</div>
                  </Link>
                ))}
              </div>
            )}
          </div>

          <div className="flex items-center space-x-4">
            {loading ? <Skeleton.Avatar active size={32} /> : <UserInfo />}
          </div>
        </div>
      </div>
    </nav>
  );
}
