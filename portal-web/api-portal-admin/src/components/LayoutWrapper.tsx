import React, { useEffect } from 'react';
import { Outlet, useLocation, Navigate } from 'react-router-dom';
import Layout from './Layout';
import { useLoading } from '@/contexts/LoadingContext';

const LayoutWrapper: React.FC = () => {
  const { loading, setLoading } = useLoading();
  const location = useLocation();

  // 权限验证
  const isAuthenticated = () => {
    return localStorage.getItem('token') !== null;
  };

  // 当前是否是登录页面
  const isLoginPage = location.pathname === '/login';

  // 路由切换时设置 loading 状态
  useEffect(() => {
    if (!isLoginPage) {
      setLoading(true);
      const timer = setTimeout(() => {
        setLoading(false);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [location.pathname, setLoading, isLoginPage]);

  // 未登录且不是登录页面 → 跳转到 /login
  if (!isAuthenticated() && !isLoginPage) {
    return <Navigate to="/login" replace />;
  }

  // 如果是登录页面，直接渲染
  if (isLoginPage) {
    return <Outlet />;
  }

  return (
    <Layout loading={loading}>
      <Outlet />
    </Layout>
  );
};

export default LayoutWrapper;
