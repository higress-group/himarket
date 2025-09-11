import { Link, useLocation } from "react-router-dom";
import { Skeleton } from "antd";
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

  const getNavLinkClass = (path: string) => {
    const baseClass = "font-medium transition-colors";
    return isActive(path) 
      ? `${baseClass} text-blue-600 border-b-2 border-blue-600 pb-1` 
      : `${baseClass} text-gray-700 hover:text-gray-900`;
  };

  return (
    <nav className="bg-[#f4f4f6] sticky top-0 z-50">
      <div className="w-full mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
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
                <span className="text-xl font-bold text-gray-900">AI 开放平台</span>
              </Link>
            )}
          </div>
          
          <div className="hidden md:flex items-center space-x-8 absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
            {loading ? (
              <>
                <Skeleton.Input active size="small" style={{ width: 100, height: 20 }} />
                <Skeleton.Input active size="small" style={{ width: 60, height: 20 }} />
                <Skeleton.Input active size="small" style={{ width: 60, height: 20 }} />
                <Skeleton.Input active size="small" style={{ width: 60, height: 20 }} />
              </>
            ) : (
              <>
                <Link 
                  to="/getting-started" 
                  className={getNavLinkClass('/getting-started')}
                >
                  Getting Started
                </Link>
                <Link 
                  to="/apis" 
                  className={getNavLinkClass('/apis')}
                >
                  APIs
                </Link>
                <Link 
                  to="/models" 
                  className={getNavLinkClass('/models')}
                >
                  Model
                </Link>
                <Link 
                  to="/mcp" 
                  className={getNavLinkClass('/mcp')}
                >
                  MCP
                </Link>
              </>
            )}
          </div>
          
          <div className="flex items-center space-x-4">
            {/* <div className="hidden sm:block">
              <Input
                placeholder="Search"
                prefix={<SearchOutlined className="text-gray-400" />}
                className="w-48 lg:w-64"
                size="middle"
              />
            </div> */}
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