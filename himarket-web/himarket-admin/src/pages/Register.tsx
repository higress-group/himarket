import { Form, Input, Button, Alert } from 'antd';
import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import api from '../lib/api';

const Register: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const handleRegister = async (values: {
    username: string;
    password: string;
    confirmPassword: string;
  }) => {
    setError('');
    if (!values.username || !values.password || !values.confirmPassword) {
      setError('请填写所有字段');
      return;
    }
    if (values.password !== values.confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }
    setLoading(true);
    try {
      await api.post('/admins/init', { password: values.password, username: values.username });
      navigate('/login');
    } catch {
      setError('注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-white">
      <div className="bg-white p-8 rounded-xl shadow-2xl w-full max-w-md flex flex-col items-center border border-gray-100">
        {/* Logo */}
        <div className="mb-4">
          <img alt="Logo" className="w-16 h-16 mx-auto mb-4" src="/logo.png" />
        </div>
        <h2 className="text-2xl font-bold mb-6 text-gray-900 text-center">注册 AI Portal</h2>
        <Form className="w-full flex flex-col gap-4" layout="vertical" onFinish={handleRegister}>
          <Form.Item name="username" rules={[{ message: '请输入账号', required: true }]}>
            <Input autoComplete="username" placeholder="账号" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ message: '请输入密码', required: true }]}>
            <Input.Password autoComplete="new-password" placeholder="密码" size="large" />
          </Form.Item>
          <Form.Item name="confirmPassword" rules={[{ message: '请确认密码', required: true }]}>
            <Input.Password autoComplete="new-password" placeholder="确认密码" size="large" />
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
              注册
            </Button>
          </Form.Item>
        </Form>
        <div className="mt-6 text-gray-400 text-sm text-center w-full">
          已有账号？
          <Link className="text-indigo-500 hover:underline ml-1" to="/login">
            登录
          </Link>
        </div>
      </div>
    </div>
  );
};

export default Register;
