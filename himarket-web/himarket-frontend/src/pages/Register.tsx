import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { Form, Input, Button, message } from 'antd';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';

import { Layout } from '../components/Layout';
import request from '../lib/request';

const Register: React.FC = () => {
  const { t } = useTranslation('register');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  // const location = useLocation()
  // const searchParams = new URLSearchParams(location.search)
  // const portalId = searchParams.get('portalId') || ''

  const handleRegister = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) => {
    setLoading(true);
    try {
      // 这里需要根据实际API调整
      await request.post('/developers', {
        password: values.password,
        username: values.username,
      });
      message.success(t('registerSuccess'));
      // 注册成功后跳转到登录页
      navigate('/login');
    } catch {
      message.error(t('registerFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <div
        className="min-h-[calc(100vh-96px)] flex items-center justify-center "
        style={{
          backdropFilter: 'blur(204px)',
          WebkitBackdropFilter: 'blur(204px)',
        }}
      >
        <div className="w-full max-w-md mx-4">
          <div className="bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg">
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">{t('greeting')}</span>
                {t('hello')}
              </h2>
              <p className="text-sm text-[#85888D]">{t('welcomeMessage')}</p>
            </div>

            <Form
              autoComplete="off"
              layout="vertical"
              name="register"
              onFinish={handleRegister}
              size="large"
            >
              <Form.Item
                name="username"
                rules={[
                  { message: t('usernameRequired'), required: true },
                  { message: t('usernameMinLength'), min: 3 },
                ]}
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
                rules={[
                  { message: t('passwordRequired'), required: true },
                  { message: t('passwordMinLength'), min: 6 },
                ]}
              >
                <Input.Password
                  autoComplete="new-password"
                  className="rounded-lg"
                  placeholder={t('passwordPlaceholder')}
                  prefix={<LockOutlined className="text-gray-400" />}
                />
              </Form.Item>

              <Form.Item
                dependencies={['password']}
                name="confirmPassword"
                rules={[
                  { message: t('confirmPasswordRequired'), required: true },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error(t('passwordMismatch')));
                    },
                  }),
                ]}
              >
                <Input.Password
                  autoComplete="new-password"
                  className="rounded-lg"
                  placeholder={t('confirmPasswordPlaceholder')}
                  prefix={<LockOutlined className="text-gray-400" />}
                />
              </Form.Item>

              <Form.Item>
                <Button
                  className="rounded-lg w-full"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  type="primary"
                >
                  {loading ? t('registering') : t('register')}
                </Button>
              </Form.Item>
            </Form>

            <div className="text-center text-subTitle">
              {t('hasAccount')}
              <Link
                className="text-colorPrimary hover:text-colorPrimary hover:underline"
                to="/login"
              >
                {t('loginLink')}
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
};

export default Register;
