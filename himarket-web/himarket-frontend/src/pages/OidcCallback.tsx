import React, { useEffect, useState, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { message, Spin } from 'antd'
import { handleOidcCallback, type AuthResult } from '../lib/api'

const OidcCallback: React.FC = () => {
  const location = useLocation()
  const navigate = useNavigate()
  const [, setLoading] = useState(true)
  const processedRef = useRef(false)

  useEffect(() => {
    // 防止重复执行：使用ref标记是否已经处理过
    if (!processedRef.current) {
      processedRef.current = true
      handleOidcCallbackProcess()
    }
  }, [location.search]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleOidcCallbackProcess = async () => {
    try {
      setLoading(true)

      // 1. 从URL提取参数
      const searchParams = new URLSearchParams(location.search)
      const code = searchParams.get('code')
      const state = searchParams.get('state')
      const error = searchParams.get('error')
      const errorDescription = searchParams.get('error_description')

      // 处理授权错误
      if (error) {
        message.error(`登录失败: ${errorDescription || error}`)
        navigate('/login', { replace: true })
        return
      }

      // 检查必要参数
      if (!code || !state) {
        message.error('回调参数不完整，请重试')
        navigate('/login', { replace: true })
        return
      }

      // 2. 调用后端API
      const authResult: AuthResult = await handleOidcCallback(code, state)
      if (!authResult?.data?.access_token) {
        throw new Error('未获取到访问令牌')
      }

      // 3. 存储token
      localStorage.setItem('access_token', authResult.data.access_token)

      // 4. 用户反馈
      message.success('登录成功！')

      // 5. 页面跳转
      navigate('/', { replace: true })

    } catch (error: any) {

      let errorMessage = '登录失败，请重试'
      if (error.response?.status === 400) {
        errorMessage = '授权码无效或已过期'
      } else if (error.response?.status === 404) {
        errorMessage = 'OIDC配置不存在'
      } else if (error.message) {
        errorMessage = error.message
      }

      message.error(errorMessage)
      navigate('/login', { replace: true })

    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="text-center">
        <Spin size="large" />
        <div className="mt-4 text-gray-600">
          正在处理登录信息...
        </div>
      </div>
    </div>
  )
}

export default OidcCallback
