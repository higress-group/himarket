import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Form, Input, Button, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import request from '../lib/request'
import { Layout } from '../components/Layout'

const Register: React.FC = () => {
  const { t } = useTranslation('register');
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  // const location = useLocation()
  // const searchParams = new URLSearchParams(location.search)
  // const portalId = searchParams.get('portalId') || ''

  const handleRegister = async (values: { username: string; password: string; confirmPassword: string }) => {
    setLoading(true)
    try {
      // 这里需要根据实际API调整
      await request.post('/developers', {
        username: values.username,
        password: values.password,
      })
      message.success(t('registerSuccess'))
      // 注册成功后跳转到登录页
      navigate('/login')
    } catch {
      message.error(t('registerFailed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Layout>
      <div className="min-h-[calc(100vh-96px)] flex items-center justify-center " style={{
        backdropFilter: 'blur(204px)',
        WebkitBackdropFilter: 'blur(204px)',
      }}>
        <div className="w-full max-w-md mx-4">
          <div className='bg-white backdrop-blur-sm rounded-2xl p-8 shadow-lg'>
            <div className="mb-8">
              <h2 className="text-[32px] flex text-gray-900">
                <span className="text-colorPrimary">{t('greeting')}</span>
                {t('hello')}
              </h2>
              <p className="text-sm text-[#85888D]">{t('welcomeMessage')}</p>
            </div>

            <Form
              name="register"
              onFinish={handleRegister}
              autoComplete="off"
              layout="vertical"
              size="large"
            >
              <Form.Item
                name="username"
                rules={[
                  { required: true, message: t('usernameRequired') },
                  { min: 3, message: t('usernameMinLength') }
                ]}
              >
                <Input
                  prefix={<UserOutlined className='text-gray-400' />}
                  placeholder={t('usernamePlaceholder')}
                  autoComplete="username"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[
                  { required: true, message: t('passwordRequired') },
                  { min: 6, message: t('passwordMinLength') }
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className='text-gray-400' />}
                  placeholder={t('passwordPlaceholder')}
                  autoComplete="new-password"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item
                name="confirmPassword"
                dependencies={['password']}
                rules={[
                  { required: true, message: t('confirmPasswordRequired') },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error(t('passwordMismatch')))
                    },
                  }),
                ]}
              >
                <Input.Password
                  prefix={<LockOutlined className='text-gray-400' />}
                  placeholder={t('confirmPasswordPlaceholder')}
                  autoComplete="new-password"
                  className="rounded-lg"
                />
              </Form.Item>

              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  className="rounded-lg w-full"
                  size="large"
                >
                  {loading ? t('registering') : t('register')}
                </Button>
              </Form.Item>
            </Form>

            <div className="text-center text-subTitle">
              {t('hasAccount')}<Link to="/login" className="text-colorPrimary hover:text-colorPrimary hover:underline">{t('loginLink')}</Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  )
}

export default Register
