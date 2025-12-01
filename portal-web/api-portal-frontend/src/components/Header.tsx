import { Link, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import { UserInfo } from "./UserInfo";
import { Himarket, Logo } from "./icon";


export function Header() {
  const location = useLocation();
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 10);
    };

    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const tabs = [
    { path: "/models", label: "模型" },
    { path: "/mcp", label: "MCP" },
    { path: "/agents", label: "智能体" },
    { path: "/apis", label: "API" },
    { path: "/chat", label: "体验中心" },
  ];

  const isActiveTab = (path: string) => {
    return location.pathname === path || location.pathname.startsWith(path + "/");
  };

  return (
    <nav
      className={`
        sticky top-0 z-50 transition-all duration-1000 ease-in-out
        ${isScrolled
          ? "bg-gray-100/90 shadow-sm"
          : "backdrop-blur-md bg-transparent"
        }
      `}
    >
      <div className="w-full mx-auto">
        <div className="flex justify-between items-center px-8 py-1">
          <div className="flex items-center">
            <Link to="/" className="flex items-center space-x-2 hover:opacity-80 transition-all duration-300">
              <div className="w-8 h-8 rounded-full flex items-center justify-center">
                {/* LOGO区域 */}
                <Logo className="w-6 h-6" />
              </div>
              <Himarket />
            </Link>
            <div className="h-6 w-[1px] bg-gray-200 mx-5"></div>
            {/* Tab 区域 */}
            <div className="flex items-center gap-1.5">
              {tabs.map((tab) => (
                <Link key={tab.path} to={tab.path}>
                  <div
                    className={`
                      px-4 py-1.5 rounded-full
                      transition-all duration-300 ease-in-out
                      ${isActiveTab(tab.path)
                        ? "bg-white text-gray-900 font-medium shadow-sm scale-[1.02]"
                        : "text-gray-600 hover:bg-white/60 hover:text-gray-900 hover:shadow-sm hover:scale-[1.02]"
                      }
                    `}
                  >
                    {tab.label}
                  </div>
                </Link>
              ))}
            </div>
          </div>
          <div className="flex items-center space-x-4">
            {
              location.pathname !== "/login" && location.pathname !== "/register" && (
                <UserInfo />
              )
            }
          </div>
        </div>
      </div>
    </nav>
  )
}
