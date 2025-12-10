import { useState, useEffect } from 'react'
import { Button, Table, Modal, Form, Input, message, Select } from 'antd'
import dayjs from 'dayjs'
import { PlusOutlined } from '@ant-design/icons'
import { nacosApi } from '@/lib/api'
import NacosTypeSelector, { NacosImportType } from '@/components/console/NacosTypeSelector'
import ImportMseNacosModal from '@/components/console/ImportMseNacosModal'
import type { NacosInstance } from '@/types/gateway'

// 开源创建表单数据由 antd 表单直接管理，无需额外类型声明

export default function NacosConsoles() {
  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([])
  const [loading, setLoading] = useState(false)
  // 开源 Nacos 创建/编辑弹窗
  const [modalVisible, setModalVisible] = useState(false)
  const [editingNacos, setEditingNacos] = useState<NacosInstance | null>(null)
  const [form] = Form.useForm()
  // 导入类型选择与 MSE 导入
  const [typeSelectorVisible, setTypeSelectorVisible] = useState(false)
  const [mseImportVisible, setMseImportVisible] = useState(false)
  // 由 MSE 导入时可能带入的两个地址
  const [importEndpoints, setImportEndpoints] = useState<{ internet?: string; intranet?: string }>({})
  // 当从 MSE 导入时，保存 MSE 返回的 instanceId 以作为 nacosId 提交
  const [importNacosId, setImportNacosId] = useState<string | null>(null)
  // 创建来源：OPEN_SOURCE 或 MSE（用于控制是否展示 AK/SK）
  const [creationMode, setCreationMode] = useState<'OPEN_SOURCE' | 'MSE' | null>(null)
  // 命名空间字段已移除
  
  // 分页状态
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)

  useEffect(() => {
    fetchNacosInstances()
  }, [currentPage, pageSize])

  const fetchNacosInstances = async () => {
    setLoading(true)
    try {
      const response = await nacosApi.getNacos({
        page: currentPage,
        size: pageSize
      })
      setNacosInstances(response.data.content || [])
      setTotal(response.data.totalElements || 0)
    } catch (error) {
      console.error('获取Nacos实例列表失败:', error)
      // message.error('获取Nacos实例列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handlePageChange = (page: number, size?: number) => {
    setCurrentPage(page)
    if (size) {
      setPageSize(size)
    }
  }


  const handleEdit = (record: NacosInstance) => {
    setEditingNacos(record)
    form.setFieldsValue({
      nacosName: record.nacosName,
  serverUrl: record.serverUrl,
      username: record.username,
  // 密码/AK/SK 可能不返回，这里仅在存在时回填
  password: record.password,
  accessKey: record.accessKey,
  secretKey: record.secretKey,
  description: record.description
    })
    setModalVisible(true)
  }

  const handleDelete = async (nacosId: string, nacosName: string) => {
    try {
      await nacosApi.deleteNacos(nacosId)
      message.success(`成功删除Nacos实例: ${nacosName}`)
      fetchNacosInstances()
    } catch (error) {
      console.error('删除失败:', error)
      // message.error('删除失败')
    }
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      // 避免将空的敏感字段覆盖后端，移除空值
      const payload: any = { ...values }
      ;['password', 'accessKey', 'secretKey'].forEach((k) => {
        if (payload[k] === undefined || payload[k] === null || payload[k] === '') {
          delete payload[k]
        }
      })
      
      if (editingNacos) {
        // 编辑模式
        await nacosApi.updateNacos(editingNacos.nacosId, payload)
        message.success('更新成功')
      } else {
        // 创建模式
        // 若是 MSE 导入来源并带有 importNacosId，则将其作为 nacosId 一并提交
        if (creationMode === 'MSE' && importNacosId) {
          payload.nacosId = importNacosId
        }
        await nacosApi.createNacos(payload)
        message.success('创建成功')
      }
      
      setModalVisible(false)
      form.resetFields()
      fetchNacosInstances()
      setImportNacosId(null)
    } catch (error) {
      console.error('操作失败:', error)
      // message.error('操作失败')
    }
  }

  const handleModalCancel = () => {
    setModalVisible(false)
    setEditingNacos(null)
  setCreationMode(null)
  setImportEndpoints({})
    form.resetFields()
  }

  // 命名空间动态加载逻辑已移除

  const columns = [
    {
      title: '实例名称',
      dataIndex: 'nacosName',
      key: 'nacosName',
    },
    {
      title: '服务器地址',
      dataIndex: 'serverUrl',
      key: 'serverUrl',
    },
  // 命名空间列已移除
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      // render: (description: string) => {
      //   return <Tooltip title={description}>{description || '-'}</Tooltip>
      // },
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createAt',
      key: 'createAt',
      render: (val: any, record: NacosInstance) => {
        const t = val ?? record.createAt ?? (record as any).createTime ?? (record as any).gmtCreate
        return t ? dayjs(t).format('YYYY/MM/DD HH:mm:ss') : '-'
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: NacosInstance) => (
        <div className='flex items-center'>
          <Button type="link" onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button className='ml-2' type="link" danger onClick={() => handleDelete(record.nacosId, record.nacosName)}>
            删除
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Nacos实例管理</h1>
          <p className="text-gray-500 mt-2">
          管理Nacos配置中心实例
          </p>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setTypeSelectorVisible(true)}>
          导入/创建实例
        </Button>
      </div>

     

      <div className="bg-white rounded-lg">
        <Table
          columns={columns}
          dataSource={nacosInstances}
          rowKey="nacosId"
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
      </div>

  {/* 开源 Nacos 创建/编辑弹窗（保持原有） */}
  <Modal
        title={editingNacos ? '编辑Nacos实例' : '创建Nacos实例'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okText={editingNacos ? '更新' : '创建'}
        cancelText="取消"
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{}}
        >
          <Form.Item
            name="nacosName"
            label="实例名称"
            rules={[{ required: true, message: '请输入实例名称' }]}
          >
            <Input placeholder="请输入Nacos实例名称" />
          </Form.Item>

      <Form.Item name="serverUrl" label="服务器地址" rules={[{ required: true, message: '请选择或输入服务器地址' }]}> 
            {importEndpoints.internet || importEndpoints.intranet ? (
              <Select
                placeholder="请选择地址"
                options={[
                  ...(importEndpoints.internet
                    ? [{ label: `公网地址：${importEndpoints.internet}`, value: importEndpoints.internet }]
                    : []),
                  ...(importEndpoints.intranet
                    ? [{ label: `内网地址：${importEndpoints.intranet}`, value: importEndpoints.intranet }]
                    : []),
                ]}
        onChange={() => { /* 地址变更无需处理命名空间 */ }}
              />
            ) : (
              <Input placeholder="例如: http://localhost:8848" onChange={() => {
        // 已移除 namespace 重置
              }} />
            )}
          </Form.Item>
      {/* 命名空间字段已移除 */}

          {/* 用户名/密码改为非必填 */}
          <Form.Item name="username" label="用户名" rules={[]}>
            <Input placeholder="请输入Nacos用户名（可选）" />
          </Form.Item>

          {/* 编辑和创建都允许填写密码（可选） */}
          <Form.Item name="password" label="密码" rules={[]}>
            <Input.Password placeholder="请输入Nacos密码（可选）" />
          </Form.Item>

          {/* AK/SK：编辑时允许修改；创建时仅在 MSE 导入展示 */}
          {(editingNacos || creationMode === 'MSE') && (
            <>
              <Form.Item name="accessKey" label="Access Key" rules={[]}>
                <Input placeholder="可选：用于记录 AK" />
              </Form.Item>
              <Form.Item name="secretKey" label="Secret Key" rules={[]}>
                <Input.Password placeholder="可选：用于记录 SK" />
              </Form.Item>
            </>
          )}

          <Form.Item
            name="description"
            label="描述"
          >
            <Input.TextArea
              rows={3}
              placeholder="请输入实例描述（可选）"
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 导入类型选择器 */}
      <NacosTypeSelector
        visible={typeSelectorVisible}
        onCancel={() => setTypeSelectorVisible(false)}
        onSelect={(type: NacosImportType) => {
          setTypeSelectorVisible(false)
          if (type === 'MSE') {
            setMseImportVisible(true)
          } else {
            setEditingNacos(null)
            setCreationMode('OPEN_SOURCE')
            setImportEndpoints({})
            setModalVisible(true)
          }
        }}
      />

      {/* MSE 导入弹窗 */}
      <ImportMseNacosModal
        visible={mseImportVisible}
        onCancel={() => setMseImportVisible(false)}
        onPrefill={(values) => {
          // 打开创建弹窗并回填数据，等待用户补充后提交
          setMseImportVisible(false)
          setEditingNacos(null)
          setModalVisible(true)
          setCreationMode('MSE')
          setImportEndpoints({ internet: values.serverInternetEndpoint, intranet: values.serverIntranetEndpoint })
          form.setFieldsValue({
            nacosName: values.nacosName,
            serverUrl: values.serverUrl,
            accessKey: values.accessKey,
            secretKey: values.secretKey,
          })
              // 保存导入来源的 nacosId
              setImportNacosId(values.nacosId || null)
        }}
      />
    </div>
  )
}
