import { Form, Input, Button, Alert } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { authApi } from '@/lib/api';

import api from '../lib/api';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [isRegister, setIsRegister] = useState<boolean | null>(null); // null 表示正在加载
  const navigate = useNavigate();

  // 页面加载时检查权限
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await authApi.getNeedInit(); // 替换为你的权限接口
        setIsRegister(response.data === true); // 根据接口返回值决定是否显示注册表单
      } catch (_err) {
        setIsRegister(false); // 默认显示登录表单
      }
    };

    checkAuth();
  }, []);

  // 登录表单提交
  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    setError('');
    try {
      const response = await api.post('/admins/login', {
        password: values.password,
        username: values.username,
      });
      const accessToken = response.data.access_token;
      localStorage.setItem('access_token', accessToken);
      localStorage.setItem('userInfo', JSON.stringify(response.data));
      navigate('/portals');
    } catch {
      setError('账号或密码错误');
    } finally {
      setLoading(false);
    }
  };

  // 注册表单提交
  const handleRegister = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) => {
    setLoading(true);
    setError('');
    if (values.password !== values.confirmPassword) {
      setError('两次输入的密码不一致');
      setLoading(false);
      return;
    }
    try {
      const response = await api.post('/admins/init', {
        password: values.password,
        username: values.username,
      });
      if (response.data.adminId) {
        setIsRegister(false); // 初始化成功后切换到登录状态
      }
    } catch {
      setError('初始化失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      {/* 左侧品牌区域 - 移动端隐藏 */}
      <div
        className="hidden md:flex w-1/2 items-center justify-center relative overflow-hidden"
        style={{ background: 'linear-gradient(135deg, #6366F1 0%, #4F46E5 50%, #818CF8 100%)' }}
      >
        {/* 装饰性背景元素 */}
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-10 w-32 h-32 rounded-full bg-white" />
          <div className="absolute bottom-32 right-16 w-48 h-48 rounded-full bg-white" />
          <div className="absolute top-1/2 left-1/3 w-24 h-24 rounded-full bg-white" />
        </div>
        <div className="text-center text-white relative z-10">
          <img alt="Logo" className="w-20 h-20 mx-auto mb-6 drop-shadow-lg" src="/logo.png" />
          <h1 className="text-3xl font-bold mb-3">HiMarket</h1>
          <p className="text-lg opacity-80">企业级 AI 开放平台管理后台</p>
        </div>
      </div>

      {/* 右侧表单区域 */}
      <div className="w-full md:w-1/2 flex items-center justify-center bg-gradient-to-br from-indigo-50 to-white md:bg-white">
        <div className="w-full max-w-md px-8">
          {/* 移动端 Logo */}
          <div className="md:hidden mb-6 text-center">
            <img alt="Logo" className="w-16 h-16 mx-auto mb-4" src="/logo.png" />
          </div>

          <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">
            {isRegister ? '注册Admin账号' : '登录HiMarket-后台'}
          </h2>

          {/* 登录表单 */}
          {!isRegister && (
            <Form className="w-full flex flex-col gap-4" layout="vertical" onFinish={handleLogin}>
              <Form.Item name="username" rules={[{ message: '请输入账号', required: true }]}>
                <Input placeholder="账号" size="large" />
              </Form.Item>
              <Form.Item name="password" rules={[{ message: '请输入密码', required: true }]}>
                <Input.Password placeholder="密码" size="large" />
              </Form.Item>
              {error && <Alert className="mb-2" message={error} showIcon type="error" />}
              <Form.Item>
                <Button
                  className="w-full"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  type="primary"
                >
                  登录
                </Button>
              </Form.Item>
            </Form>
          )}

          {/* 注册表单 */}
          {isRegister && (
            <Form
              className="w-full flex flex-col gap-4"
              layout="vertical"
              onFinish={handleRegister}
            >
              <Form.Item name="username" rules={[{ message: '请输入账号', required: true }]}>
                <Input placeholder="账号" size="large" />
              </Form.Item>
              <Form.Item name="password" rules={[{ message: '请输入密码', required: true }]}>
                <Input.Password placeholder="密码" size="large" />
              </Form.Item>
              <Form.Item name="confirmPassword" rules={[{ message: '请确认密码', required: true }]}>
                <Input.Password placeholder="确认密码" size="large" />
              </Form.Item>
              {error && <Alert className="mb-2" message={error} showIcon type="error" />}
              <Form.Item>
                <Button
                  className="w-full"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  type="primary"
                >
                  初始化
                </Button>
              </Form.Item>
            </Form>
          )}
        </div>
      </div>
    </div>
  );
};

export default Login;
