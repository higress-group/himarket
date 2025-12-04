import { Link, useLocation } from "react-router-dom";
import { Skeleton } from "antd";
import { ApiOutlined, ToolOutlined, RobotOutlined, BulbOutlined } from "@ant-design/icons";
import { UserInfo } from "./UserInfo";

interface NavigationProps {
  loading?: boolean;
}

export function Navigation({ loading = false }: NavigationProps) {
  const location = useLocation();
  
  const isActive = (path: string) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  const navigationItems = [
    { 
      path: '/apis', 
      icon: <ApiOutlined />, 
      title: 'APIs', 
      subtitle: 'REST接口' 
    },
    { 
      path: '/mcp', 
      icon: <ToolOutlined />, 
      title: 'MCP', 
      subtitle: '工具集成' 
    },
    { 
      path: '/models', 
      icon: <BulbOutlined />, 
      title: 'Model', 
      subtitle: 'AI模型' 
    },
    { 
      path: '/agents', 
      icon: <RobotOutlined />, 
      title: 'Agent', 
      subtitle: '智能助手' 
    }
  ];

  return (
    <nav className="sticky top-4 z-50">
      <div className="w-full mx-auto px-4 sm:px-6 lg:px-8">
        <div className="bg-[#f4f4f6]/95 backdrop-blur-sm rounded-xl border border-gray-200 flex justify-between items-center h-20 px-6">
          <div className="flex items-center">
            {loading ? (
              <div className="flex items-center space-x-2">
                <Skeleton.Avatar size={32} active />
                <Skeleton.Input active size="small" style={{ width: 120, height: 24 }} />
              </div>
            ) : (
              <Link to="/" className="flex items-center space-x-2 hover:opacity-80 transition-opacity">
                <div className="w-8 h-8 rounded-full flex items-center justify-center">
                {/* LOGO区域 */}
                <img
                  src="/logo.png"
                  alt="logo"
                  className="w-6 h-6"
                  style={{ display: "block" }}
                />
                </div>
                <span className="text-xl font-bold text-gray-900">Himarket</span>
              </Link>
            )}
          </div>
          
          <div className="hidden md:flex items-center absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
            {loading ? (
              <div className="flex space-x-3">
                <Skeleton.Input active size="small" style={{ width: 100, height: 60 }} />
                <Skeleton.Input active size="small" style={{ width: 100, height: 60 }} />
                <Skeleton.Input active size="small" style={{ width: 100, height: 60 }} />
                <Skeleton.Input active size="small" style={{ width: 100, height: 60 }} />
              </div>
            ) : (
              <div className="flex space-x-3">
                {navigationItems.map((item) => (
                  <Link 
                    key={item.path}
                    to={item.path} 
                    className={`px-4 py-3 rounded-lg font-medium transition-all duration-200 flex flex-col items-center justify-center min-w-[100px] ${
                      isActive(item.path) 
                        ? 'bg-blue-50/80 text-blue-700 border border-blue-200/50 shadow-sm' 
                        : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50/50 border border-transparent'
                    }`}
                  >
                    <div className="flex items-center space-x-1 mb-1">
                      <div className={`text-lg ${isActive(item.path) ? 'text-blue-600' : 'text-gray-500'}`}>
                        {item.icon}
                      </div>
                      <div className="text-sm font-semibold leading-tight">
                        {item.title}
                      </div>
                    </div>
                    <div className="text-xs text-gray-500 leading-tight">
                      {item.subtitle}
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </div>
          
          <div className="flex items-center space-x-4">
            {loading ? (
              <Skeleton.Avatar size={32} active />
            ) : (
              <UserInfo />
            )}
          </div>
        </div>
      </div>
    </nav>
  );
} 