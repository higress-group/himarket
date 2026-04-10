import React, { useState, useEffect } from "react";
import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import {
  HomeOutlined,
  ProductOutlined,
  DesktopOutlined,
  UserOutlined,
  MenuOutlined,
  SettingOutlined,
  TagsOutlined,
  BarChartOutlined,
  DashboardOutlined,
  MonitorOutlined,
  BulbOutlined,
  ThunderboltOutlined,
  RobotOutlined,
  ApiOutlined,
} from "@ant-design/icons";
import McpServerIcon from "@/components/icons/McpServerIcon";
import { Button, Tooltip } from "antd";
import { isAuthenticated, removeToken } from "../lib/utils";

interface NavigationItem {
  name: string;
  cn: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  children?: NavigationItem[];
}

const Layout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);

  useEffect(() => {
    // 检查 cookie 中的 token 来判断登录状态
    const checkAuthStatus = () => {
      const hasToken = isAuthenticated();
      setIsLoggedIn(hasToken);
    };

    checkAuthStatus();
    // 监听 storage 变化（当其他标签页登录/登出时）
    window.addEventListener("storage", checkAuthStatus);

    return () => {
      window.removeEventListener("storage", checkAuthStatus);
    };
  }, []);

  useEffect(() => {
    // 进入详情页自动折叠侧边栏（排除 API Products 子菜单路由）
    const apiProductSubRoutes = ['model-api', 'mcp-server', 'agent-skill', 'worker', 'agent-api', 'rest-api'];
    const isApiProductDetail = location.pathname.match(/^\/api-products\/([^/]+)$/);
    const isSubMenuRoute = isApiProductDetail && apiProductSubRoutes.includes(isApiProductDetail[1]);

    if (
      location.pathname.match(/^\/portals\/[^/]+$/) ||
      (isApiProductDetail && !isSubMenuRoute)
    ) {
      setSidebarCollapsed(true);
    } else {
      setSidebarCollapsed(false);
    }
  }, [location.pathname]);

  const navigation: NavigationItem[] = [
    { name: "Portal", cn: "门户", href: "/portals", icon: HomeOutlined },
    {
      name: "API Products",
      cn: "API产品",
      href: "/api-products",
      icon: ProductOutlined,
      children: [
        { name: "Model API", cn: "Model API", href: "/api-products/model-api", icon: BulbOutlined },
        { name: "MCP Server", cn: "MCP Server", href: "/api-products/mcp-server", icon: McpServerIcon },
        { name: "Agent Skill", cn: "Agent Skill", href: "/api-products/agent-skill", icon: ThunderboltOutlined },
        { name: "Worker", cn: "Worker", href: "/api-products/worker", icon: UserOutlined },
        { name: "Agent API", cn: "Agent API", href: "/api-products/agent-api", icon: RobotOutlined },
        { name: "REST API", cn: "REST API", href: "/api-products/rest-api", icon: ApiOutlined },
      ],
    },
    {
      name: "Categories",
      cn: "产品类别",
      href: "/product-categories",
      icon: TagsOutlined,
    },
    {
      name: "实例管理",
      cn: "实例管理",
      href: "/consoles",
      icon: SettingOutlined,
      children: [
        { name: 'Nacos实例', cn: 'Nacos实例', href: '/consoles/nacos', icon: DesktopOutlined },
        { name: '网关实例', cn: '网关实例', href: '/consoles/gateway', icon: DesktopOutlined },
        { name: 'Sandbox实例', cn: 'Sandbox实例', href: '/consoles/sandbox', icon: DesktopOutlined },
      ]
    },
    {
      name: "观测分析",
      cn: "观测分析",
      href: "/observability",
      icon: BarChartOutlined,
      children: [
        {
          name: "模型监控",
          cn: "模型监控",
          href: "/observability/model-dashboard",
          icon: DashboardOutlined,
        },
        {
          name: "MCP监控",
          cn: "MCP监控",
          href: "/observability/mcp-monitor",
          icon: MonitorOutlined,
        },
      ],
    },
  ];

  const toggleSidebar = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  const handleLogout = () => {
    removeToken();
    setIsLoggedIn(false);
    navigate("/login");
  };

  const isMenuActive = (item: NavigationItem): boolean => {
    if (item.children) {
      return item.children.some(child => location.pathname === child.href);
    }
    return location.pathname === item.href || location.pathname.startsWith(item.href + '/');
  };

  const renderMenuItem = (item: NavigationItem, level: number = 0) => {
    const Icon = item.icon;
    const isActive = isMenuActive(item);
    const hasChildren = item.children && item.children.length > 0;

    // 折叠状态：隐藏子菜单，图标居中，添加 Tooltip
    if (sidebarCollapsed) {
      if (level > 0) return null;
      return (
        <Tooltip key={item.name} title={item.cn || item.name} placement="right">
          <Link
            to={item.href}
            className={`flex items-center justify-center mt-2 p-3 rounded-lg transition-colors duration-150 ${
              isActive && !hasChildren
                ? "bg-gray-100 text-black"
                : "text-gray-500 hover:text-black hover:bg-gray-50"
            }`}
          >
            <Icon className="h-5 w-5" />
          </Link>
        </Tooltip>
      );
    }

    // 展开状态：保持现有逻辑
    return (
      <div key={item.name}>
        <Link
          to={item.href}
          className={`flex items-center mt-2 px-3 py-3 rounded-lg transition-colors duration-150 ${
            level > 0 ? "ml-4" : ""
          } ${
            isActive && !hasChildren
              ? "bg-gray-100 text-black font-semibold"
              : "text-gray-500 hover:text-black hover:bg-gray-50"
          }`}
        >
          <Icon className="mr-3 h-5 w-5 flex-shrink-0" />
          <div className="flex flex-col flex-1">
            <span className="text-base leading-none">{item.name}</span>
          </div>
        </Link>
        {hasChildren && (
          <div className="ml-2">
            {item.children!.map(child => renderMenuItem(child, level + 1))}
          </div>
        )}
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
          <button
            onClick={() => navigate("/login")}
            className="flex items-center px-4 py-2 rounded bg-black text-white hover:bg-gray-800"
          >
            <UserOutlined className="mr-2" /> 登录
          </button>
        )}
      </header>
      <div className="flex">
        {/* 侧边栏 */}
        <aside
          className={`bg-white border-r min-h-screen pt-8 transition-all duration-300 ${
            sidebarCollapsed ? "w-16" : "w-64"
          }`}
        >
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
  );
};

export default Layout;
