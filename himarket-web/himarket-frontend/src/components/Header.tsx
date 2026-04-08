import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';

import { HiMarket, Logo } from './icon';
import { LanguageSwitcher } from './LanguageSwitcher';
import { UserInfo } from './UserInfo';
import { usePortalConfig } from '../context/usePortalConfig';

export function Header() {
  const location = useLocation();
  const [isScrolled, setIsScrolled] = useState(false);
  const { loading, visibleTabs } = usePortalConfig();
  const { t } = useTranslation('header');

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 10);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);
  const isActiveTab = (path: string) => {
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  return (
    <nav
      className={`
        sticky top-0 z-50 transition-all duration-1000 ease-in-out h-auto
        ${isScrolled ? 'bg-gray-100/90 shadow-sm' : 'backdrop-blur-md bg-transparent'}
      `}
    >
      <div className="w-full mx-auto">
        <div className="flex justify-between items-center px-8 py-1">
          <div className="flex items-center">
            <Link
              className="flex items-center space-x-2 hover:opacity-80 transition-all duration-300"
              to="/"
            >
              <div className="w-8 h-8 rounded-full flex items-center justify-center">
                {/* LOGO区域 */}
                <Logo className="w-6 h-6" />
              </div>
              <HiMarket />
            </Link>
            <div className="h-6 w-[1px] bg-gray-200 mx-5"></div>
            {/* Tab 区域 - loading 时显示占位骨架，避免突然出现 */}
            {loading ? (
              <div className="flex items-center gap-1.5">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div
                    className="h-8 rounded-full bg-gray-200/60 animate-pulse"
                    key={i}
                    style={{ width: `${56 + (i % 3) * 8}px` }}
                  />
                ))}
              </div>
            ) : (
              <div className="flex items-center gap-1.5 animate-in fade-in duration-300">
                {visibleTabs.map((tab) => (
                  <Link key={tab.path} to={tab.path}>
                    <div
                      className={`
                      px-4 py-1.5 rounded-full
                      transition-all duration-300 ease-in-out
                      ${
                        isActiveTab(tab.path)
                          ? 'bg-white text-gray-900 font-medium shadow-sm scale-[1.02]'
                          : 'text-gray-600 hover:bg-white/60 hover:text-gray-900 hover:shadow-sm hover:scale-[1.02]'
                      }
                    `}
                    >
                      {t(tab.label)}
                    </div>
                  </Link>
                ))}
              </div>
            )}
          </div>
          <div className="flex items-center space-x-4">
            <LanguageSwitcher />
            {location.pathname !== '/login' && location.pathname !== '/register' && <UserInfo />}
          </div>
        </div>
      </div>
    </nav>
  );
}
