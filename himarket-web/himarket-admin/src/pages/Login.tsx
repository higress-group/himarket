import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../lib/api";
import { authApi } from '@/lib/api'
import { Form, Input, Button, Alert } from "antd";

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [isRegister, setIsRegister] = useState<boolean | null>(null); // null 表示正在加载
  const navigate = useNavigate();

  // 页面加载时检查权限
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await authApi.getNeedInit(); // 替换为你的权限接口
        setIsRegister(response.data === true); // 根据接口返回值决定是否显示注册表单
      } catch (err) {
        setIsRegister(false); // 默认显示登录表单
      }
    };

    checkAuth();
  }, []);

  // 登录表单提交
  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    setError("");
    try {
      const response = await api.post("/admins/login", {
        username: values.username,
        password: values.password,
      });
      const accessToken = response.data.access_token;
      localStorage.setItem('access_token', accessToken);
      localStorage.setItem('userInfo', JSON.stringify(response.data));
      navigate('/portals');
    } catch {
      setError("账号或密码错误");
    } finally {
      setLoading(false);
    }
  };

  // 注册表单提交
  const handleRegister = async (values: { username: string; password: string; confirmPassword: string }) => {
    setLoading(true);
    setError("");
    if (values.password !== values.confirmPassword) {
      setError("两次输入的密码不一致");
      setLoading(false);
      return;
    }
    try {
      const response = await api.post("/admins/init", {
        username: values.username,
        password: values.password,
      });
      if (response.data.adminId) {
        setIsRegister(false); // 初始化成功后切换到登录状态
      }
    } catch {
      setError("初始化失败，请重试");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-white">
      <div className="bg-white p-8 rounded-xl shadow-2xl w-full max-w-md flex flex-col items-center border border-gray-100">
        {/* Logo */}
        <div className="mb-4">
          <img src="/logo.png" alt="Logo" className="w-16 h-16 mx-auto mb-4" />
        </div>
        <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">
          {isRegister ? "注册Admin账号" : "登录Himarket-后台"}
        </h2>

        {/* 登录表单 */}
        {!isRegister && (
          <Form
            className="w-full flex flex-col gap-4"
            layout="vertical"
            onFinish={handleLogin}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: "请输入账号" }]}
            >
              <Input placeholder="账号" size="large" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password placeholder="密码" size="large" />
            </Form.Item>
            {error && <Alert message={error} type="error" showIcon className="mb-2" />}
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                className="w-full"
                loading={loading}
                size="large"
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
            <Form.Item
              name="username"
              rules={[{ required: true, message: "请输入账号" }]}
            >
              <Input placeholder="账号" size="large" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: "请输入密码" }]}
            >
              <Input.Password placeholder="密码" size="large" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              rules={[{ required: true, message: "请确认密码" }]}
            >
              <Input.Password placeholder="确认密码" size="large" />
            </Form.Item>
            {error && <Alert message={error} type="error" showIcon className="mb-2" />}
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                className="w-full"
                loading={loading}
                size="large"
              >
                初始化
              </Button>
            </Form.Item>
          </Form>
        )}
      </div>
    </div>
  );
};

export default Login;
