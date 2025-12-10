import { useState, useEffect, useRef } from "react";
import { Button, Avatar, Dropdown, Skeleton, message } from "antd";
import { useNavigate } from "react-router-dom";
import { LogOut, UserRoundCheck } from "./icon";
import APIs from "../lib/apis";
import "./UserInfo.css";

interface UserInfo {
  displayName: string;
  email?: string;
  avatar?: string;
}

// 全局缓存用户信息，避免重复请求
let globalUserInfo: UserInfo | null = null;
let globalLoading = false;

export function UserInfo() {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(globalUserInfo);
  const [loading, setLoading] = useState(globalUserInfo ? false : true);
  const navigate = useNavigate();
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;

    // 如果已有缓存数据，直接使用
    if (globalUserInfo) {
      setUserInfo(globalUserInfo);
      setLoading(false);
      return;
    }

    // 如果正在加载中，等待加载完成 - 优化轮询逻辑
    if (globalLoading) {
      const checkLoading = () => {
        if (!globalLoading && mounted.current) {
          setUserInfo(globalUserInfo);
          setLoading(false);
        } else if (globalLoading && mounted.current) {
          // 使用requestAnimationFrame替代setTimeout提升性能
          requestAnimationFrame(checkLoading);
        }
      };
      checkLoading();
      return;
    }

    // 开始加载用户信息
    globalLoading = true;
    setLoading(true);

    APIs.getDeveloperInfo()
      .then((response) => {
        const data = response.data;
        if (data) {
          const userData = {
            displayName: data.username || data.email || "未命名用户",
            email: data.email,
            avatar: data.avatarUrl || undefined,
          };
          globalUserInfo = userData;
          if (mounted.current) {
            setUserInfo(userData);
          }
        }
      })
      .catch((error) => {
        console.error('获取用户信息失败:', error);
      })
      .finally(() => {
        globalLoading = false;
        if (mounted.current) {
          setLoading(false);
        }
      });

    return () => {
      mounted.current = false;
    };
  }, []);

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
      globalUserInfo = null;
      globalLoading = false;
      setUserInfo(null);
      // 显示成功消息
      message.success('退出登录成功', 1);
      // 跳转到登录页
      navigate('/login');
    }
  };

  const menuItems = [
    {
      key: 'user-info',
      label: (
        <div>
          <div className="font-semibold text-gray-900 text-base">{userInfo?.displayName}</div>
          {userInfo?.email && (
            <div className="text-xs text-gray-500 mt-0.5">{userInfo.email}</div>
          )}
        </div>
      ),
      disabled: true,
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'my-applications',
      icon: <UserRoundCheck className="mr-1" />,
      label: '消费者管理',
      onClick: () => navigate('/consumers'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogOut className="mr-1" />,
      label: '退出登录',
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
        <Skeleton.Avatar size={32} active />
      </div>
    );
  }

  if (userInfo) {
    return (
      <Dropdown
        menu={{ items: menuItems }}
        placement="bottomRight"
        trigger={['hover']}
        overlayClassName="user-dropdown"
      >
        <div className="flex items-center space-x-2 cursor-pointer hover:opacity-80 transition-opacity px-2 py-1 rounded-full">
          {userInfo.avatar ? (
            <Avatar src={userInfo.avatar} size="default" />
          ) : (
            <Avatar size="default" className="bg-colorPrimarySecondary text-mainTitle font-medium">
              {getInitials(userInfo.displayName)}
            </Avatar>
          )}
        </div>
      </Dropdown>
    );
  }

  return (
    <Button
      onClick={() => {
        navigate(`/login`);
      }}
      type="default"
      className="rounded-full shadow-none bg-colorPrimary text-white border-none hover:opacity-90"
    >
      登录
    </Button>
  );
} 