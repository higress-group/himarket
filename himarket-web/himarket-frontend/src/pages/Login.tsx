import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { Form, Input, Button, message, Divider } from 'antd';
import { AxiosError } from 'axios';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import aliyunIcon from '../assets/aliyun.png';
import githubIcon from '../assets/github.png';
import googleIcon from '../assets/google.png';
import { Layout } from '../components/Layout';
import APIs from '../lib/apis';
import request from '../lib/request';

import type { IIdpProvider } from '../lib/apis';

const oidcIcons: Record<string, React.ReactNode> = {
  aliyun: <img alt="Aliyun" className="w-6 h-6 mr-2" src={aliyunIcon} />,
  github: <img alt="GitHub" className="w-6 h-6 mr-2" src={githubIcon} />,
  google: <img alt="Google" className="w-5 h-5 mr-2" src={googleIcon} />,
};

const Login: React.FC = () => {
  const { t } = useTranslation('login');
  const [providers, setProviders] = useState<IIdpProvider[]>([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  useEffect(() => {
    // 使用OidcController的接口获取OIDC提供商
    APIs.getOidcProviders()
      .then(({ data }) => {
        console.warn('OIDC providers response:', data);
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
      const res = await request.post('/developers/login', {
        password: values.password,
        username: values.username,
      });
      // 登录成功后跳转到首页并携带access_token
      if (res && res.data && res.data.access_token) {
        message.success(t('loginSuccess'), 1);
        localStorage.setItem('access_token', res.data.access_token);

        // 检查URL中是否有returnUrl参数
        const returnUrl = searchParams.get('returnUrl');
        if (returnUrl) {
          navigate(decodeURIComponent(returnUrl));
        } else {
          navigate('/');
        }
      } else {
        message.error(t('loginFailedNoToken'));
      }
    } catch (error) {
      if (error instanceof AxiosError) {
        message.error(error.response?.data.message || t('loginFailedCheckCredentials'));
      } else {
        message.error(t('loginFailed'));
      }
    } finally {
      setLoading(false);
    }
  };

  // 跳转到 OIDC 授权 - 对接OidcController
  const handleOidcLogin = (provider: string) => {
    // 获取API前缀配置
    const apiPrefix = request.defaults.baseURL || '/api/v1';

    // 构建授权URL - 对接 /developers/oidc/authorize
    const authUrl = new URL(`${window.location.origin}${apiPrefix}/developers/oidc/authorize`);
    authUrl.searchParams.set('provider', provider);

    console.warn('Redirecting to OIDC authorization:', authUrl.toString());

    // 跳转到OIDC授权服务器
    window.location.href = authUrl.toString();
  };

  return (
    <Layout>
      <div className="min-h-[calc(100vh-96px)] w-full flex items-center justify-center">
        <div className="w-full max-w-md mx-4">
          {/* 登录卡片 */}
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">{t('greeting')}</span>
                {t('hello')}
              </h2>
              <p className="text-sm text-[#85888D]">{t('welcomeMessage')}</p>
            </div>

            {/* 账号密码登录表单 */}
            <Form
              autoComplete="off"
              layout="vertical"
              name="login"
              onFinish={handlePasswordLogin}
              size="large"
            >
              <Form.Item
                name="username"
                rules={[{ message: t('usernameRequired'), required: true }]}
              >
                <Input
                  autoComplete="username"
                  className="rounded-lg"
                  placeholder={t('usernamePlaceholder')}
                  prefix={<UserOutlined className="text-gray-400" />}
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[{ message: t('passwordRequired'), required: true }]}
              >
                <Input.Password
                  autoComplete="current-password"
                  className="rounded-lg"
                  placeholder={t('passwordPlaceholder')}
                  prefix={<LockOutlined className="text-gray-400" />}
                />
              </Form.Item>

              <Form.Item>
                <Button
                  className="w-full rounded-lg h-10"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  type="primary"
                >
                  {loading ? t('loggingIn') : t('login')}
                </Button>
              </Form.Item>
            </Form>
            {/* 分隔线 */}
            {providers.length > 0 && (
              <Divider className="text-subTitle" plain>
                <span className="text-subTitle">{t('or')}</span>
              </Divider>
            )}
            {/* OIDC 登录按钮 */}
            <div className="flex flex-col gap-2 mb-2">
              {providers.length === 0
                ? null
                : providers.map((provider) => (
                    <Button
                      className="w-full flex items-center justify-center"
                      icon={oidcIcons[provider.provider.toLowerCase()] || <span></span>}
                      key={provider.provider}
                      onClick={() => handleOidcLogin(provider.provider)}
                      size="large"
                    >
                      {t('loginWithProvider', { provider: provider.name || provider.provider })}
                    </Button>
                  ))}
            </div>
            <div className="text-center text-subTitle">
              {t('noAccount')}
              <Link
                className="text-colorPrimary hover:text-colorPrimary hover:underline"
                to="/register"
              >
                {t('registerLink')}
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Login;
