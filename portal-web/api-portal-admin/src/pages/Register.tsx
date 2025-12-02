import React, { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import api from '../lib/api'
import { Form, Input, Button, Alert } from 'antd'

const Register: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const searchParams = new URLSearchParams(location.search)
  searchParams.get('portalId') || '';
  const handleRegister = async (values: { username: string; password: string; confirmPassword: string }) => {
    setError('')
    if (!values.username || !values.password || !values.confirmPassword) {
      setError('请填写所有字段')
      return
    }
    if (values.password !== values.confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }
    setLoading(true)
    try {
      await api.post('/admins/init', { username: values.username, password: values.password })
      navigate('/login')
    } catch {
      setError('注册失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-white">
      <div className="bg-white p-8 rounded-xl shadow-2xl w-full max-w-md flex flex-col items-center border border-gray-100">
        {/* Logo */}
        <div className="mb-4">
          <img src="/logo.png" alt="Logo" className="w-16 h-16 mx-auto mb-4" />
        </div>
        <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">注册 AI Portal</h2>
        <Form
          className="w-full flex flex-col gap-4"
          layout="vertical"
          onFinish={handleRegister}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input placeholder="账号" autoComplete="username" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder="密码" autoComplete="new-password" size="large" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            rules={[{ required: true, message: '请确认密码' }]}
          >
            <Input.Password placeholder="确认密码" autoComplete="new-password" size="large" />
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
              注册
            </Button>
          </Form.Item>
        </Form>
        <div className="mt-6 text-gray-400 text-sm text-center w-full">
          已有账号？<Link to="/login" className="text-indigo-500 hover:underline ml-1">登录</Link>
        </div>
      </div>
    </div>
  )
}

export default Register 