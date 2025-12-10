import React, { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Form, Input, Button, message, Divider } from "antd";
import { UserOutlined, LockOutlined } from "@ant-design/icons";
import api, { type IdpResult } from "../lib/api";
import { AxiosError } from "axios";
import { Layout } from "../components/Layout";
import APIs from "../lib/apis";

import aliyunIcon from "../assets/aliyun.png";
import githubIcon from "../assets/github.png";
import googleIcon from "../assets/google.png";


const oidcIcons: Record<string, React.ReactNode> = {
  google: <img src={googleIcon} alt="Google" className="w-5 h-5 mr-2" />,
  github: <img src={githubIcon} alt="GitHub" className="w-6 h-6 mr-2" />,
  aliyun: <img src={aliyunIcon} alt="Aliyun" className="w-6 h-6 mr-2" />,
};

const Login: React.FC = () => {
  const [providers, setProviders] = useState<IdpResult[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    // 使用OidcController的接口获取OIDC提供商
    APIs.getOidcProviders()
      .then(({data}) => {
        console.log('OIDC providers response:', data);
        setProviders(data);
      })
      .catch((error) => {
        console.error('Failed to fetch OIDC providers:', error);
        setProviders([]);
      });
  }, []);

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

        // 检查URL中是否有returnUrl参数
        const returnUrl = searchParams.get('returnUrl');
        if (returnUrl) {
          navigate(decodeURIComponent(returnUrl));
        } else {
          navigate('/');
        }
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

  // 跳转到 OIDC 授权 - 对接OidcController
  const handleOidcLogin = (provider: string) => {
    // 获取API前缀配置
    const apiPrefix = api.defaults.baseURL || '/api/v1';

    // 构建授权URL - 对接 /developers/oidc/authorize
    const authUrl = new URL(`${window.location.origin}${apiPrefix}/developers/oidc/authorize`);
    authUrl.searchParams.set('provider', provider);

    console.log('Redirecting to OIDC authorization:', authUrl.toString());

    // 跳转到OIDC授权服务器
    window.location.href = authUrl.toString();
  };

  return (
    <Layout>
      <div
        className="min-h-[calc(100vh-96px)] w-full flex items-center justify-center"
      >
        <div className="w-full max-w-md mx-4">
          {/* 登录卡片 */}
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">
                  嗨，
                </span>
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
                  className="rounded-lg"
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
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  className="w-full rounded-lg h-10"
                  size="large"
                >
                  {loading ? "登录中..." : "登录"}
                </Button>
              </Form.Item>
            </Form>
            {/* 分隔线 */}
            {
              providers.length > 0 && (
                <Divider plain className="text-subTitle"><span className="text-subTitle">或</span></Divider>
              )
            }
            {/* OIDC 登录按钮 */}
            <div className="flex flex-col gap-2 mb-2">
              {providers.length === 0 ? (
                null
              ) : (
                providers.map((provider) => (
                  <Button
                    key={provider.provider}
                    onClick={() => handleOidcLogin(provider.provider)}
                    className="w-full flex items-center justify-center"
                    size="large"
                    icon={oidcIcons[provider.provider.toLowerCase()] || <span></span>}
                  >
                    使用 {provider.name || provider.provider} 登录
                  </Button>
                ))
              )}
            </div>
            <div className="text-center text-subTitle">
              没有账号？<Link to="/register" className="text-colorPrimary hover:text-colorPrimary hover:underline">注册</Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
