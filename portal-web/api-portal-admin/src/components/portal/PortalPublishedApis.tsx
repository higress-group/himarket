import { useState, useEffect } from 'react'
import { Card, Table, Modal, Form, Button, Space, Select, message, Checkbox } from 'antd'
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

  const [form] = Form.useForm()
  
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
  }, [isModalVisible, apiProducts])

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
      render: (_: any, record: ApiProduct) => (
        <div>
          <div>{record.name}</div>
          <div style={{ fontSize: '12px', color: '#666' }}>ID: {record.productId}</div>
        </div>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      render: (text: string) => ProductTypeMap[text] || text
    },
    // {
    //   title: '分类',
    //   dataIndex: 'category',
    //   key: 'category',
    // },
    {
      title: '操作',
      key: 'action',
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
      title: '选择',
      dataIndex: 'select',
      key: 'select',
      width: 60,
      render: (_: any, record: ApiProduct) => (
        <Checkbox
          checked={selectedApiIds.includes(record.productId)}
          onChange={(e) => {
            if (e.target.checked) {
              setSelectedApiIds([...selectedApiIds, record.productId])
            } else {
              setSelectedApiIds(selectedApiIds.filter(id => id !== record.productId))
            }
          }}
        />
      ),
    },
    {
      title: '名称/ID',
      key: 'nameAndId',
      render: (_: any, record: ApiProduct) => (
        <div>
          <div>{record.name}</div>
          <div style={{ fontSize: '12px', color: '#666' }}>ID: {record.productId}</div>
        </div>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
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
        apiProductApi.cancelPublishToPortal(productId, portal.portalId).then((res) => {
          message.success('移除成功')
          fetchApiProducts()
          setIsModalVisible(false)
        }).catch((error) => {
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
          <p className="text-gray-600">管理在此Portal中发布的API</p>
        </div>
        <Button type="primary" onClick={() => setIsModalVisible(true)}>
          发布新API
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
            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
            onChange: handlePageChange,
            onShowSizeChange: handlePageChange,
          }}
        />
      </Card>

      <Modal
        title="发布新API"
        open={isModalVisible}
        onOk={handlePublish}
        onCancel={handleModalCancel}
        okText="发布"
        cancelText="取消"
        width={800}
        confirmLoading={modalLoading}
      >
        <div className="mb-4">
          <p className="text-gray-600">请选择要发布的API产品：</p>
        </div>
        <Table
          columns={modalColumns}
          dataSource={apiProductsOptions}
          rowKey="productId"
          loading={modalLoading}
          pagination={false}
          scroll={{ y: 400 }}
        />
      </Modal>
    </div>
  )
} 