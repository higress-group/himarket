import { useNavigate } from 'react-router-dom'
import { Card, Button, Table, Tag, Space, Switch, Modal, Form, Input, Select, message } from 'antd'
import { PlusOutlined, EyeOutlined, EditOutlined, DeleteOutlined, ExclamationCircleOutlined, GlobalOutlined, CheckCircleFilled, MinusCircleFilled } from '@ant-design/icons'
import { useState, useEffect } from 'react'
import type { ApiProduct } from '@/types/api-product';
import { apiProductApi, portalApi } from '@/lib/api';

interface ApiProductPortalProps {
  apiProduct: ApiProduct
}

interface Portal {
  portalId: string
  portalName: string
  autoApproveSubscription: boolean
  createdAt: string
}

export function ApiProductPortal({ apiProduct }: ApiProductPortalProps) {
  const [publishedPortals, setPublishedPortals] = useState<Portal[]>([])
  const [allPortals, setAllPortals] = useState<Portal[]>([])
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [selectedPortalIds, setSelectedPortalIds] = useState<string[]>([])
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [portalLoading, setPortalLoading] = useState(false)
  const [modalLoading, setModalLoading] = useState(false)

  // 分页状态
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)

  const navigate = useNavigate()

  // 获取已发布的门户列表
  useEffect(() => {
    if (apiProduct.productId) {
      fetchPublishedPortals()
    }
  }, [apiProduct.productId, currentPage, pageSize])

  // 获取所有门户列表
  useEffect(() => {
    fetchAllPortals()
  }, [])

  const fetchPublishedPortals = async () => {
    setLoading(true)
    try {
      const res = await apiProductApi.getApiProductPublications(apiProduct.productId, {
        page: currentPage,
        size: pageSize
      })
      setPublishedPortals(res.data.content?.map((item: any) => ({
        ...item,
        autoApproveSubscription: item.autoApproveSubscriptions || false,
      })) || [])
      setTotal(res.data.totalElements || 0)
    } catch (error) {
      console.error('获取已发布门户失败:', error)
      // message.error('获取已发布门户失败')
    } finally {
      setLoading(false)
    }
  }

  const fetchAllPortals = async () => {
    setPortalLoading(true)
    try {
      const res = await portalApi.getPortals({
        page: 1,
        size: 500 // 获取所有门户
      })
      setAllPortals(res.data.content?.map((item: any) => ({
        ...item,
        portalName: item.name,
        autoApproveSubscription: item.portalSettingConfig?.autoApproveSubscriptions || false,
      })) || [])
    } catch (error) {
      console.error('获取门户列表失败:', error)
      // message.error('获取门户列表失败')
    } finally {
      setPortalLoading(false)
    }
  }

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page)
    if (size) {
      setPageSize(size)
    }
  }

  const columns = [
    {
      title: '门户信息',
      key: 'portalInfo',
      width: 400,
      render: (_: any, record: Portal) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">
            {record.portalName}
          </div>
          <div className="text-xs text-gray-500 truncate">
            {record.portalId}
          </div>
        </div>
      ),
    },
    {
      title: '订阅自动审批',
      key: 'autoApprove',
      width: 160,
      render: (_: any, record: Portal) => (
        <div className="flex items-center">
          {record.autoApproveSubscription ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-1" style={{fontSize: '10px'}} />
              <span className="text-xs text-gray-900">已开启</span>
            </>
          ) : (
            <>
              <MinusCircleFilled className="text-gray-400 mr-1" style={{fontSize: '10px'}} />
              <span className="text-xs text-gray-900">已关闭</span>
            </>
          )}
        </div>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_: any, record: Portal) => (
        <Space size="middle">
          <Button onClick={() => {
            navigate(`/portals/detail?id=${record.portalId}`)
          }} type="link" icon={<EyeOutlined />}>
            查看
          </Button>
         
          <Button 
            type="link" 
            danger 
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.portalId, record.portalName)}
          >
            移除
          </Button>
        </Space>
      ),
    },
  ]

  const modalColumns = [
    {
      title: '门户信息',
      key: 'portalInfo',
      render: (_: any, record: Portal) => (
        <div>
          <div className="text-xs font-normal text-gray-900 truncate">
            {record.portalName}
          </div>
          <div className="text-xs text-gray-500">
            {record.portalId}
          </div>
        </div>
      ),
    },
    {
      title: '订阅自动审批',
      key: 'autoApprove',
      width: 140,
      render: (_: any, record: Portal) => (
        <div className="flex items-center">
          {record.autoApproveSubscription ? (
            <>
              <CheckCircleFilled className="text-green-500 mr-1" style={{fontSize: '10px'}} />
              <span className="text-xs text-gray-900">已开启</span>
            </>
          ) : (
            <>
              <MinusCircleFilled className="text-gray-400 mr-1" style={{fontSize: '10px'}} />
              <span className="text-xs text-gray-900">已关闭</span>
            </>
          )}
        </div>
      ),
    },
  ]

  const handleAdd = () => {
    setIsModalVisible(true)
  }

  const handleDelete = (portalId: string, portalName: string) => {
    Modal.confirm({
      title: '确认移除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要从API产品中移除门户 "${portalName}" 吗？此操作不可恢复。`,
      okText: '确认移除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        apiProductApi.cancelPublishToPortal(apiProduct.productId, portalId).then((res) => {
          message.success('移除成功')
          fetchPublishedPortals()
        }).catch((error) => {
          console.error('移除失败:', error)
          // message.error('移除失败')
        })
      },
    })
  }

  const handleModalOk = async () => {
    if (selectedPortalIds.length === 0) {
      message.warning('请至少选择一个门户')
      return
    }

    setModalLoading(true)
    try {
      // 批量发布到选中的门户
      for (const portalId of selectedPortalIds) {
        await apiProductApi.publishToPortal(apiProduct.productId, portalId)
      }
      message.success(`成功发布到 ${selectedPortalIds.length} 个门户`)
      setSelectedPortalIds([])
      setIsModalVisible(false)
      // 重新获取已发布的门户列表
      fetchPublishedPortals()
    } catch (error) {
      console.error('发布失败:', error)
      // message.error('发布失败')
    } finally {
      setModalLoading(false)
    }
  }

  const handleModalCancel = () => {
    setIsModalVisible(false)
    setSelectedPortalIds([])
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">发布门户</h1>
          <p className="text-gray-600">管理API产品发布的门户</p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          发布到门户
        </Button>
      </div>

      <Card>
        {publishedPortals.length === 0 && !loading ? (
          <div className="text-center py-8 text-gray-500">
            <p>暂未发布到任何门户</p>
          </div>
        ) : (
          <Table 
            columns={columns} 
            dataSource={publishedPortals}
            rowKey="portalId"
            loading={loading}
            pagination={{
              current: currentPage,
              pageSize: pageSize,
              total: total,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total) => `共 ${total} 条`,
              onChange: handlePageChange,
              onShowSizeChange: handlePageChange,
            }}
          />
        )}
      </Card>

      <Modal
        title="发布到门户"
        open={isModalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okText="发布"
        cancelText="取消"
        width={700}
        confirmLoading={modalLoading}
        destroyOnClose
      >
        <div className="border border-gray-200 rounded-lg overflow-hidden">
          <Table
            columns={modalColumns}
            dataSource={allPortals.filter(portal => 
              !publishedPortals.some(published => published.portalId === portal.portalId)
            )}
            rowKey="portalId"
            loading={portalLoading}
            pagination={false}
            scroll={{ y: 350 }}
            size="middle"
            className="portal-selection-table"
            rowSelection={{
              type: 'checkbox',
              selectedRowKeys: selectedPortalIds,
              onChange: (selectedRowKeys) => {
                setSelectedPortalIds(selectedRowKeys as string[])
              },
              columnWidth: 50,
            }}
            rowClassName={(record) => 
              selectedPortalIds.includes(record.portalId) 
                ? 'bg-blue-50 hover:bg-blue-100' 
                : 'hover:bg-gray-50'
            }
            locale={{
              emptyText: (
                <div className="py-8">
                  <div className="text-gray-400 mb-2">
                    <GlobalOutlined style={{ fontSize: '24px' }} />
                  </div>
                  <div className="text-gray-500 text-sm">暂无可发布的门户</div>
                </div>
              )
            }}
          />
        </div>
      </Modal>
    </div>
  )
} 