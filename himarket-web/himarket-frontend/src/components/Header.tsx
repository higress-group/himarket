import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';

import { HiMarket, Logo } from './icon';
import { LanguageSwitcher } from './LanguageSwitcher';
import { UserInfo } from './UserInfo';
import { usePortalConfig } from '../context/usePortalConfig';

export function Header() {
  const location = useLocation();
  const [isScrolled, setIsScrolled] = useState(() => window.scrollY > 10);
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
      <div className="mx-auto w-full">
        <div className="flex items-center justify-between gap-3 px-4 py-1 sm:px-6 lg:px-8">
          <div className="flex min-w-0 flex-1 items-center overflow-hidden">
            <Link
              className="flex flex-shrink-0 items-center space-x-2 transition-all duration-300 hover:opacity-80"
              to="/"
            >
              <div className="w-8 h-8 rounded-full flex items-center justify-center">
                {/* LOGO区域 */}
                <Logo className="w-6 h-6" />
              </div>
              <HiMarket />
            </Link>
            <div className="mx-3 h-6 w-[1px] flex-shrink-0 bg-gray-200 sm:mx-5"></div>
            {/* Tab 区域 - loading 时显示占位骨架，避免突然出现 */}
            {loading ? (
              <div className="flex min-w-0 flex-1 items-center gap-1.5 overflow-x-auto">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div
                    className="h-8 flex-shrink-0 animate-pulse rounded-full bg-gray-200/60"
                    key={i}
                    style={{ width: `${56 + (i % 3) * 8}px` }}
                  />
                ))}
              </div>
            ) : (
              <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
                {visibleTabs.map((tab) => (
                  <Link className="flex-shrink-0" key={tab.path} to={tab.path}>
                    <div
                      className={`
                      px-3.5 py-1 rounded-[9px] text-[15px] font-medium
                      transition-all duration-300 ease-in-out
                      ${
                        isActiveTab(tab.path)
                          ? 'bg-colorPrimary text-white shadow-sm'
                          : 'text-gray-700 hover:bg-colorPrimaryBg hover:text-colorPrimary hover:shadow-sm'
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
          <div className="flex flex-shrink-0 items-center space-x-3 sm:space-x-4">
            <LanguageSwitcher />
            {location.pathname !== '/login' && location.pathname !== '/register' && <UserInfo />}
          </div>
        </div>
      </div>
    </nav>
  );
}
