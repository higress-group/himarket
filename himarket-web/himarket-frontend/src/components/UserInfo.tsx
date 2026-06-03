import { Button, Avatar, Dropdown, Skeleton, message } from 'antd';
import { AppWindow, UserRoundCog } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

import { LogOut } from './icon';
import APIs from '../lib/apis';
import {
  clearCachedUserInfo,
  getCachedUserInfo,
  isUserInfoLoading,
  setCachedUserInfo,
  setUserInfoLoading,
} from '../lib/userInfoCache';
import './UserInfo.css';

function hasAccessToken() {
  return Boolean(localStorage.getItem('access_token'));
}

export function UserInfo() {
  const { t } = useTranslation('userInfo');
  const [userInfo, setUserInfo] = useState(() => (hasAccessToken() ? getCachedUserInfo() : null));
  const [loading, setLoading] = useState(() => hasAccessToken() && !getCachedUserInfo());
  const navigate = useNavigate();
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;

    if (!hasAccessToken()) {
      clearCachedUserInfo();
      setUserInfo(null);
      setLoading(false);
      return () => {
        mounted.current = false;
      };
    }

    // 如果已有缓存数据，直接使用
    const cachedUserInfo = getCachedUserInfo();
    if (cachedUserInfo) {
      setUserInfo(cachedUserInfo);
      setLoading(false);
      return () => {
        mounted.current = false;
      };
    }

    // 如果正在加载中，等待加载完成 - 优化轮询逻辑
    if (isUserInfoLoading()) {
      const checkLoading = () => {
        if (!isUserInfoLoading() && mounted.current) {
          setUserInfo(getCachedUserInfo());
          setLoading(false);
        } else if (isUserInfoLoading() && mounted.current) {
          // 使用requestAnimationFrame替代setTimeout提升性能
          requestAnimationFrame(checkLoading);
        }
      };
      checkLoading();
      return () => {
        mounted.current = false;
      };
    }

    // 开始加载用户信息
    setUserInfoLoading(true);
    setLoading(true);

    APIs.getDeveloperInfo()
      .then((response) => {
        const data = response.data;
        if (data && data.username) {
          const userData = {
            avatar: data.avatarUrl || undefined,
            displayName: data.username || data.email || t('unnamedUser'),
            email: data.email,
          };
          setCachedUserInfo(userData);
          if (mounted.current) {
            setUserInfo(userData);
          }
        }
      })
      .finally(() => {
        setUserInfoLoading(false);
        if (mounted.current) {
          setLoading(false);
        }
      });

    return () => {
      mounted.current = false;
    };
  }, [t]);

  const handleLogout = async () => {
    try {
      // 调用后端logout接口，使token失效
      await APIs.developerLogout();
    } catch (error) {
      // 即使接口调用失败，也要清除本地token，避免用户被卡住
      console.error('退出登录接口调用失败:', error);
    } finally {
      // 清除localStorage中的token
      localStorage.removeItem('access_token');
      // 清除全局用户信息
      clearCachedUserInfo();
      setUserInfo(null);
      // 显示成功消息
      message.success(t('logoutSuccess'), 1);
      // 跳转到登录页
      navigate('/login');
    }
  };

  const menuItems = [
    {
      disabled: true,
      key: 'user-info',
      label: (
        <div>
          <div className="font-semibold text-gray-900 text-base">{userInfo?.displayName}</div>
          {userInfo?.email && <div className="text-xs text-gray-500 mt-0.5">{userInfo.email}</div>}
        </div>
      ),
    },
    {
      type: 'divider' as const,
    },
    {
      icon: <UserRoundCog className="mr-1 h-4 w-4" />,
      key: 'profile',
      label: t('profile'),
      onClick: () => navigate('/profile'),
    },
    {
      icon: <AppWindow className="mr-1 h-4 w-4" />,
      key: 'my-applications',
      label: t('consumerManagement'),
      onClick: () => navigate('/consumers'),
    },
    {
      type: 'divider' as const,
    },
    {
      icon: <LogOut className="mr-1" />,
      key: 'logout',
      label: t('logout'),
      onClick: handleLogout,
    },
  ];

  // 获取用户名首字母
  const getInitials = (name: string) => {
    if (!name) return 'U';
    // 如果是中文名，取第一个字
    if (/[\u4e00-\u9fa5]/.test(name)) {
      return name.charAt(0);
    }
    // 如果是英文名，取第一个字母
    return name.charAt(0).toUpperCase();
  };

  if (loading) {
    return (
      <div className="flex items-center space-x-2">
        <Skeleton.Avatar active size={32} />
      </div>
    );
  }

  if (userInfo) {
    return (
      <Dropdown
        classNames={{
          root: 'user-dropdown',
        }}
        menu={{ items: menuItems }}
        placement="bottomRight"
        trigger={['hover']}
      >
        <div className="flex items-center space-x-2 cursor-pointer hover:opacity-80 transition-opacity px-2 py-1 rounded-full">
          {userInfo.avatar ? (
            <Avatar size="default" src={userInfo.avatar} />
          ) : (
            <Avatar className="bg-colorPrimarySecondary text-mainTitle font-medium" size="default">
              {getInitials(userInfo.displayName)}
            </Avatar>
          )}
        </div>
      </Dropdown>
    );
  }

  return (
    <Button
      className="rounded-full bg-colorPrimary text-white border-none hover:opacity-90 hover:bg-colorPrimary"
      onClick={() => {
        navigate(`/login`);
      }}
      type="text"
    >
      {t('login')}
    </Button>
  );
}
