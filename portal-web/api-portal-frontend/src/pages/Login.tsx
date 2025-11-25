import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Form, Input, Button, message } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import api from "../lib/api";
import bgImage from "../assets/bg.png";
import { AxiosError } from "axios";
import { Layout } from "../components/Layout";

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  // 账号密码登录
  const handlePasswordLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await api.post("/developers/login", {
        username: values.username,
        password: values.password,
      });
      // 登录成功后跳转到首页并携带access_token
      if (res && res.data && res.data.access_token) {
        message.success('登录成功！', 1);
        localStorage.setItem('access_token', res.data.access_token)
        navigate('/')
      } else {
        message.error("登录失败，未获取到access_token");
      }
    } catch (error) {
      if (error instanceof AxiosError) {
        message.error(error.response?.data.message || "登录失败，请检查账号密码是否正确");
      } else {
        message.error("登录失败");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <div
        className="min-h-[calc(100vh-96px)] w-full flex items-center justify-center"
        style={{
          backdropFilter: 'blur(204px)',
          WebkitBackdropFilter: 'blur(204px)',
        }}
      >
        <div className="w-full max-w-md mx-4">
          {/* 登录卡片 */}
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <h2 className="text-colorPrimary">嗨，</h2>
                您好
              </h2>
              <p className="text-sm text-[#85888D]">欢迎来到 Himarket，登录以继续</p>
            </div>

            {/* 账号密码登录表单 */}
            <Form
              name="login"
              onFinish={handlePasswordLogin}
              autoComplete="off"
              layout="vertical"
              size="large"
            >
              <Form.Item
                name="username"
                rules={[
                  { required: true, message: '请输入账号' }
                ]}
              >
                <Input
                  prefix={<UserOutlined className="text-gray-400" />}
                  placeholder="账号"
                  autoComplete="username"
                  className="rounded-xl"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[
                  { required: true, message: '请输入密码' }
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className="text-gray-400" />}
                  placeholder="密码"
                  autoComplete="current-password"
                  className="rounded-xl"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  className="w-full rounded-xl h-11"
                  size="large"
                >
                  {loading ? "登录中..." : "登录"}
                </Button>
              </Form.Item>
            </Form>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
