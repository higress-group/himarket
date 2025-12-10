import React, { useEffect, useState } from 'react'
import { Card, Spin, Button, Space } from 'antd'
import { ReloadOutlined, DashboardOutlined } from '@ant-design/icons'
import { portalApi } from '@/lib/api'
import type { Portal } from '@/types'

interface PortalDashboardProps {
  portal: Portal
}

export const PortalDashboard: React.FC<PortalDashboardProps> = ({ portal }) => {
  const [dashboardUrl, setDashboardUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [fallback, setFallback] = useState(false)

  const fetchDashboardUrl = async () => {
    if (!portal.portalId) return
    setLoading(true)
    setError('')
    try {
      const res = await portalApi.getPortalDashboard(portal.portalId, 'Portal')
      if (!res?.data) {
        setFallback(true)
      } else {
        setDashboardUrl(res.data)
      }
    } catch (e: any) {
      setError(e?.response?.data?.message || '获取监控面板失败')
      setFallback(true)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDashboardUrl()
  }, [portal.portalId])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spin size="large" />
      </div>
    )
  }

  if (fallback || !dashboardUrl || error) {
    return (
      <div className="p-6">
        <div className="w-full h-[600px] flex items-center justify-center text-gray-500">
          Dashboard 发布中，敬请期待
        </div>
        <div className="mt-4 text-right">
          <Button onClick={fetchDashboardUrl} loading={loading}>刷新</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold flex items-center gap-2">
            <DashboardOutlined className="text-blue-500" />
            Dashboard 监控面板
          </h2>
          <p className="text-gray-500 mt-2">实时监控 {portal.name} 的访问与性能</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchDashboardUrl} loading={loading}>刷新</Button>
        </Space>
      </div>

      <Card title="监控面板" className="w-full">
        <div className="w-full h-[600px] border rounded-lg overflow-hidden">
          <iframe
            src={dashboardUrl}
            title={`${portal.name} Dashboard`}
            className="w-full h-full border-0"
            sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
            onError={() => setFallback(true)}
          />
        </div>
      </Card>
    </div>
  )
}


