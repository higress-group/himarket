import { useState, useEffect } from 'react'
import { Card, Table, Modal, Button, Space, message, Empty } from 'antd'
import { EyeOutlined, DeleteOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { Portal, ApiProduct } from '@/types'
import { apiProductApi } from '@/lib/api'
import { useNavigate } from 'react-router-dom'
import { ProductTypeMap } from '@/lib/utils'

interface PortalApiProductsProps {
  portal: Portal
}

export function PortalPublishedApis({ portal }: PortalApiProductsProps) {
  const navigate = useNavigate()
  const [apiProducts, setApiProducts] = useState<ApiProduct[]>([])
  const [apiProductsOptions, setApiProductsOptions] = useState<ApiProduct[]>([])
  const [isModalVisible, setIsModalVisible] = useState(false)
  const [selectedApiIds, setSelectedApiIds] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [modalLoading, setModalLoading] = useState(false)
  
  // 分页状态
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)
  
  useEffect(() => {
    if (portal.portalId) {
      fetchApiProducts()
    }
  }, [portal.portalId, currentPage, pageSize])

  const fetchApiProducts = () => {
    setLoading(true)
    apiProductApi.getApiProducts({
      portalId: portal.portalId,
      page: currentPage,
      size: pageSize
    }).then((res) => {
      setApiProducts(res.data.content)
      setTotal(res.data.totalElements || 0)
    }).finally(() => {
      setLoading(false)
    })
  }

  useEffect(() => {
    if (isModalVisible) {
      setModalLoading(true)
      apiProductApi.getApiProducts({
        page: 1,
        size: 500, // 获取所有可用的API
        status: 'READY'
      }).then((res) => {
        // 过滤掉已发布在该门户里的api
        setApiProductsOptions(res.data.content.filter((api: ApiProduct) => 
          !apiProducts.some((a: ApiProduct) => a.productId === api.productId)
        ))
      }).finally(() => {
        setModalLoading(false)
      })
    }
  }, [isModalVisible]) // 移除apiProducts依赖，避免重复请求

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page)
    if (size) {
      setPageSize(size)
    }
  }

  const columns = [
    {
      title: '名称/ID',
      key: 'nameAndId',
      width: 280,
      render: (_: any, record: ApiProduct) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">{record.name}</div>
          <div className="text-xs text-gray-500 truncate">{record.productId}</div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (text: string) => ProductTypeMap[text] || text
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 400,
    },
    // {
    //   title: '分类',
    //   dataIndex: 'category',
    //   key: 'category',
    // },
    {
      title: '操作',
      key: 'action',
      width: 180,
      render: (_: any, record: ApiProduct) => (
        <Space size="middle">
          <Button
            onClick={() => {
              navigate(`/api-products/detail?productId=${record.productId}`)
            }}
            type="link" icon={<EyeOutlined />}>
            查看
          </Button>
          
          <Button type="link" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.productId, record.name)}>
            移除
          </Button>
        </Space>
      ),
    },
  ]

  const modalColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      render: (_: any, record: ApiProduct) => (
        <div>
          <div className="text-sm font-medium text-gray-900 truncate">
            {record.name}
          </div>
          <div className="text-xs text-gray-500 truncate">
            {record.productId}
          </div>
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => ProductTypeMap[type] || type,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 300,
    },
  ]

  const handleDelete = (productId: string, productName: string) => {
    Modal.confirm({
      title: '确认移除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要从门户中移除API产品 "${productName}" 吗？此操作不可恢复。`,
      okText: '确认移除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        apiProductApi.cancelPublishToPortal(productId, portal.portalId).then(() => {
          message.success('移除成功')
          fetchApiProducts()
          setIsModalVisible(false)
        }).catch(() => {
          // message.error('移除失败')
        })
      },
    })
  }

  const handlePublish = async () => {
    if (selectedApiIds.length === 0) {
      message.warning('请至少选择一个API')
      return
    }

    setModalLoading(true)
    try {
      // 批量发布选中的API
      for (const productId of selectedApiIds) {
        await apiProductApi.publishToPortal(productId, portal.portalId)
      }
      message.success(`成功发布 ${selectedApiIds.length} 个API`)
      setSelectedApiIds([])
      fetchApiProducts()
      setIsModalVisible(false)
    } catch (error) {
      // message.error('发布失败')
    } finally {
      setModalLoading(false)
    }
  }

  const handleModalCancel = () => {
    setIsModalVisible(false)
    setSelectedApiIds([])
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold mb-2">API Product</h1>
          <p className="text-gray-600">管理在此Portal中发布的API产品</p>
        </div>
        <Button type="primary" onClick={() => setIsModalVisible(true)}>
          发布API产品
        </Button>
      </div>

      <Card>
        <Table 
          columns={columns} 
          dataSource={apiProducts}
          rowKey="productId"
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
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无已发布的API产品"
              />
            )
          }}
        />
      </Card>

      <Modal
        title="发布API产品"
        open={isModalVisible}
        onOk={handlePublish}
        onCancel={handleModalCancel}
        okText="发布"
        cancelText="取消"
        width={800}
        confirmLoading={modalLoading}
        okButtonProps={{
          disabled: selectedApiIds.length === 0
        }}
      >
        <Table
          columns={modalColumns}
          dataSource={apiProductsOptions}
          rowKey="productId"
          loading={modalLoading}
          pagination={false}
          scroll={{ y: 400 }}
          rowSelection={{
            type: 'checkbox',
            selectedRowKeys: selectedApiIds,
            onChange: (selectedRowKeys) => {
              setSelectedApiIds(selectedRowKeys as string[]);
            },
            columnWidth: 60,
          }}
        />
      </Modal>
    </div>
  )
} 