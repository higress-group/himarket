import { useState, useEffect, useCallback } from 'react'
import { Button, Table, message, Modal, Tabs } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { gatewayApi } from '@/lib/api'
import ImportGatewayModal from '@/components/console/ImportGatewayModal'
import ImportHigressModal from '@/components/console/ImportHigressModal'
import GatewayTypeSelector from '@/components/console/GatewayTypeSelector'
import { formatDateTime } from '@/lib/utils'
import { Gateway, GatewayType } from '@/types'

export default function Consoles() {
  const [gateways, setGateways] = useState<Gateway[]>([])
  const [typeSelectorVisible, setTypeSelectorVisible] = useState(false)
  const [importVisible, setImportVisible] = useState(false)
  const [higressImportVisible, setHigressImportVisible] = useState(false)
  const [selectedGatewayType, setSelectedGatewayType] = useState<GatewayType>('APIG_API')
  const [loading, setLoading] = useState(false)
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })

  // 按类型分组的网关列表
  const apigApiGateways = gateways.filter(g => g.gatewayType === 'APIG_API')
  const apigAiGateways = gateways.filter(g => g.gatewayType === 'APIG_AI')
  const higressGateways = gateways.filter(g => g.gatewayType === 'HIGRESS')
  const adpAiGateways = gateways.filter(g => g.gatewayType === 'ADP_AI_GATEWAY')
  const fetchGatewaysConsoles = useCallback(async (page = 1, size = 10) => {
    setLoading(true)
    try {
      const res = await gatewayApi.getGateways({ page, size })
      setGateways(res.data?.content || [])
      setPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      })
    } catch (error) {
      // message.error('获取网关列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchGatewaysConsoles(1, 10)
  }, [fetchGatewaysConsoles])

  // 处理导入成功
  const handleImportSuccess = () => {
    fetchGatewaysConsoles(pagination.current, pagination.pageSize)
  }

  // 处理网关类型选择
  const handleGatewayTypeSelect = (type: GatewayType) => {
    setSelectedGatewayType(type)
    setTypeSelectorVisible(false)
    if (type === 'HIGRESS') {
      setHigressImportVisible(true)
    } else {
      setImportVisible(true)
    }
  }

  // 处理分页变化
  const handlePaginationChange = (page: number, pageSize: number) => {
    fetchGatewaysConsoles(page, pageSize)
  }

  const handleDeleteGateway = async (gatewayId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该网关吗？',
      onOk: async () => {
        try {
          await gatewayApi.deleteGateway(gatewayId)
          message.success('删除成功')
          fetchGatewaysConsoles(pagination.current, pagination.pageSize)
        } catch (error) {
          // message.error('删除失败')
        }
      },
    })
  }

  // APIG 网关的列定义
  const apigColumns = [
    {
      title: '网关ID',
      dataIndex: 'gatewayId',
      key: 'gatewayId',
    },
    {
      title: '网关名称',
      dataIndex: 'gatewayName',
      key: 'gatewayName',
    },
    {
      title: '区域',
      dataIndex: 'region',
      key: 'region',
      render: (_: any, record: Gateway) => {
        return record.apigConfig?.region || '-'
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Gateway) => (
        <Button type="link" danger onClick={() => handleDeleteGateway(record.gatewayId)}>删除</Button>
      ),
    },
  ]

  // 专有云 AI 网关的列定义
  const adpAiColumns = [
    {
      title: '网关ID',
      dataIndex: 'gatewayId',
      key: 'gatewayId',
    },
    {
      title: '网关名称',
      dataIndex: 'gatewayName',
      key: 'gatewayName',
    },
    
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Gateway) => (
        <Button type="link" danger onClick={() => handleDeleteGateway(record.gatewayId)}>删除</Button>
      ),
    }
  ]

  // Higress 网关的列定义
  const higressColumns = [
    {
      title: '网关ID',
      dataIndex: 'gatewayId',
      key: 'gatewayId',
    },
    {
      title: '网关名称',
      dataIndex: 'gatewayName',
      key: 'gatewayName',
    },
    {
      title: '服务地址',
      dataIndex: 'address',
      key: 'address',
      render: (_: any, record: Gateway) => {
        return record.higressConfig?.address || '-'
      }
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (_: any, record: Gateway) => {
        return record.higressConfig?.username || '-'
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (date: string) => formatDateTime(date)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Gateway) => (
        <Button type="link" danger onClick={() => handleDeleteGateway(record.gatewayId)}>删除</Button>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">网关实例</h1>
          <p className="text-gray-500 mt-2">
            管理和配置您的网关实例
          </p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setTypeSelectorVisible(true)}>
          导入网关实例
        </Button>
      </div>

      <Tabs
        defaultActiveKey="APIG_API"
        items={[
          {
            key: 'HIGRESS',
            label: 'Higress 网关',
            children: (
              <div className="bg-white rounded-lg">
                <div className="px-6 py-4 border-b border-gray-200">
                  <h3 className="text-lg font-medium text-gray-900">Higress 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">Higress 云原生网关</p>
                </div>
                <Table
                  columns={higressColumns}
                  dataSource={higressGateways}
                  rowKey="gatewayId"
                  loading={loading}
                  pagination={false}
                  locale={{
                    emptyText: '暂无 Higress 网关实例'
                  }}
                />
              </div>
            ),
          },
          {
            key: 'APIG_API',
            label: 'API 网关',
            children: (
              <div className="bg-white rounded-lg">
                <div className="px-6 py-4 border-b border-gray-200">
                  <h3 className="text-lg font-medium text-gray-900">API 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">阿里云 API 网关服务</p>
                </div>
                <Table
                  columns={apigColumns}
                  dataSource={apigApiGateways}
                  rowKey="gatewayId"
                  loading={loading}
                  pagination={false}
                  locale={{
                    emptyText: '暂无 API 网关实例'
                  }}
                />
              </div>
            ),
          },
          {
            key: 'APIG_AI',
            label: 'AI 网关',
            children: (
              <div className="bg-white rounded-lg">
                <div className="px-6 py-4 border-b border-gray-200">
                  <h3 className="text-lg font-medium text-gray-900">AI 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">阿里云 AI 网关服务</p>
                </div>
                <Table
                  columns={apigColumns}
                  dataSource={apigAiGateways}
                  rowKey="gatewayId"
                  loading={loading}
                  pagination={false}
                  locale={{
                    emptyText: '暂无 AI 网关实例'
                  }}
                />
              </div>
            ),
          },
          {
            key: 'ADP_AI_GATEWAY',
            label: '专有云 AI 网关',
            children: (
              <div className="bg-white rounded-lg">
                <div className="px-6 py-4 border-b border-gray-200">
                  <h3 className="text-lg font-medium text-gray-900">AI 网关</h3>
                  <p className="text-sm text-gray-500 mt-1">专有云 AI 网关服务</p>
                </div>
                <Table
                  columns={adpAiColumns}
                  dataSource={adpAiGateways}
                  rowKey="gatewayId"
                  loading={loading}
                  pagination={false}
                  locale={{
                    emptyText: '暂无 专有云 AI 网关实例'
                  }}
                />
              </div>
            ),
          },
          
        ]}
      />

      <ImportGatewayModal
        visible={importVisible}
        gatewayType={selectedGatewayType as 'APIG_API' | 'APIG_AI' | 'ADP_AI_GATEWAY'}
        onCancel={() => setImportVisible(false)}
        onSuccess={handleImportSuccess}
      />

      <ImportHigressModal
        visible={higressImportVisible}
        onCancel={() => setHigressImportVisible(false)}
        onSuccess={handleImportSuccess}
      />

      <GatewayTypeSelector
        visible={typeSelectorVisible}
        onCancel={() => setTypeSelectorVisible(false)}
        onSelect={handleGatewayTypeSelect}
      />
    </div>
  )
}
