import { Form, Input, Button, Alert } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { authApi } from '@/lib/api';

import { AdminBrandMark } from '../components/AdminBrand';
import api from '../lib/api';

const inputClassName = 'h-[42px] rounded-lg text-sm';
const buttonClassName = 'h-[42px] w-full rounded-lg text-sm font-medium active:scale-[0.985]';
const formClassName = '[&_.ant-form-item]:mb-0 [&_.ant-form-item-label]:pb-1.5';

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

  const pageTitle = isRegister ? '初始化管理员账号' : '登录管理后台';
  const pageCaption = isRegister ? '首次使用请创建管理员账号。' : '使用管理员账号继续~';

  return (
    <main
      className="flex min-h-[100dvh] items-center justify-center px-6 py-10"
      style={{
        background:
          'radial-gradient(circle at 50% -18%, rgba(99, 102, 241, 0.18) 0, rgba(99, 102, 241, 0) 38%), radial-gradient(circle at 92% 84%, rgba(199, 210, 254, 0.38) 0, rgba(199, 210, 254, 0) 28%), linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%)',
      }}
    >
      <section className="w-full max-w-[460px] rounded-[10px] border border-white/80 bg-white/[0.94] px-8 py-9 shadow-[0_24px_70px_-38px_rgba(79,70,229,0.45)] backdrop-blur-sm sm:px-9">
        <div className="mb-10">
          <AdminBrandMark />
        </div>

        <div className="mb-7">
          <h1 className="m-0 text-[25px] font-semibold leading-snug tracking-normal text-[#18181b]">
            {pageTitle}
          </h1>
          <p className="mt-2 text-[13px] leading-6 text-gray-500">{pageCaption}</p>
        </div>

        {!isRegister && (
          <Form
            autoComplete="off"
            className={`${formClassName} grid gap-[15px]`}
            colon={false}
            layout="vertical"
            onFinish={handleLogin}
            requiredMark={false}
          >
            <Form.Item
              label="账号"
              name="username"
              rules={[{ message: '请输入账号', required: true }]}
            >
              <Input autoComplete="username" className={inputClassName} placeholder="请输入账号" />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ message: '请输入密码', required: true }]}
            >
              <Input.Password
                autoComplete="current-password"
                className={inputClassName}
                placeholder="请输入密码"
              />
            </Form.Item>
            {error && <Alert className="my-1" message={error} showIcon type="error" />}
            <Form.Item>
              <Button
                className={buttonClassName}
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

        {isRegister && (
          <Form
            autoComplete="off"
            className={`${formClassName} grid gap-[15px]`}
            colon={false}
            layout="vertical"
            onFinish={handleRegister}
            requiredMark={false}
          >
            <Form.Item
              label="账号"
              name="username"
              rules={[{ message: '请输入账号', required: true }]}
            >
              <Input autoComplete="username" className={inputClassName} placeholder="请输入账号" />
            </Form.Item>
            <Form.Item
              label="密码"
              name="password"
              rules={[{ message: '请输入密码', required: true }]}
            >
              <Input.Password
                autoComplete="new-password"
                className={inputClassName}
                placeholder="请输入密码"
              />
            </Form.Item>
            <Form.Item
              label="确认密码"
              name="confirmPassword"
              rules={[{ message: '请确认密码', required: true }]}
            >
              <Input.Password
                autoComplete="new-password"
                className={inputClassName}
                placeholder="请再次输入密码"
              />
            </Form.Item>
            {error && <Alert className="my-1" message={error} showIcon type="error" />}
            <Form.Item>
              <Button
                className={buttonClassName}
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
      </section>
    </main>
  );
};

export default Login;
