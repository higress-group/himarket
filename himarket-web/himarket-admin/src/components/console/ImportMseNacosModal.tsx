import { useState } from 'react'
import { Button, Table, Modal, Form, Input, message } from 'antd'
import { nacosApi } from '@/lib/api'

interface MseNacosItem {
  instanceId: string
  name: string
  serverIntranetEndpoint?: string
  serverInternetEndpoint?: string
  version?: string
}

interface ImportMseNacosModalProps {
  visible: boolean
  onCancel: () => void
  // 将选中的 MSE Nacos 信息带入创建表单
  onPrefill: (values: {
    nacosName?: string
    serverUrl?: string
  serverInternetEndpoint?: string
  serverIntranetEndpoint?: string
    username?: string
    password?: string
    accessKey?: string
    secretKey?: string
    description?: string
  nacosId?: string
  }) => void
}

export default function ImportMseNacosModal({ visible, onCancel, onPrefill }: ImportMseNacosModalProps) {
  const [importForm] = Form.useForm()

  const [loading, setLoading] = useState(false)
  const [list, setList] = useState<MseNacosItem[]>([])
  const [selected, setSelected] = useState<MseNacosItem | null>(null)
  const [auth, setAuth] = useState({
    regionId: '',
    accessKey: '',
    secretKey: ''
  })
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })

  const fetchMseNacos = async (values: any, page = 0, size = 10) => {
    setLoading(true)
    try {
      const res = await nacosApi.getMseNacos({ ...values, page, size })
      setList(res.data?.content || [])
      setPagination({ current: page + 1, pageSize: size, total: res.data?.totalElements || 0 })
    } catch (e: any) {
      // message.error(e?.response?.data?.message || '获取 MSE Nacos 列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleImport = async () => {
    if (!selected) {
      message.warning('请选择一个 Nacos 实例')
      return
    }
    // 将关键信息带出到创建表单，供用户补充
    onPrefill({
      nacosName: selected.name,
      serverUrl: selected.serverInternetEndpoint || selected.serverIntranetEndpoint,
  serverInternetEndpoint: selected.serverInternetEndpoint,
  serverIntranetEndpoint: selected.serverIntranetEndpoint,
  accessKey: auth.accessKey,
  secretKey: auth.secretKey,
  nacosId: selected.instanceId,
    })
    handleCancel()
  }

  const handleCancel = () => {
    setSelected(null)
    setList([])
    setPagination({ current: 1, pageSize: 10, total: 0 })
    importForm.resetFields()
    onCancel()
  }

  return (
    <Modal title="导入 MSE Nacos 实例" open={visible} onCancel={handleCancel} footer={null} width={800}>
      <Form form={importForm} layout="vertical" preserve={false}>
        {list.length === 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item label="Region" name="regionId" rules={[{ required: true, message: '请输入region' }]}>
              <Input />
            </Form.Item>
            <Form.Item label="Access Key" name="accessKey" rules={[{ required: true, message: '请输入accessKey' }]}>
              <Input />
            </Form.Item>
            <Form.Item label="Secret Key" name="secretKey" rules={[{ required: true, message: '请输入secretKey' }]}>
              <Input.Password />
            </Form.Item>
            <Button 
              type="primary" 
              onClick={() => {
                importForm.validateFields().then((values) => {
                  setAuth(values)
                  fetchMseNacos(values)
                })
              }}
              loading={loading}
            >
              获取实例列表
            </Button>
          </div>
        )}

        {list.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">选择 Nacos 实例</h3>
            <Table
              rowKey="instanceId"
              columns={[
                { title: '实例ID', dataIndex: 'instanceId' },
                { title: '名称', dataIndex: 'name' },
                { title: '版本', dataIndex: 'version' },
              ]}
              dataSource={list}
              rowSelection={{
                type: 'radio',
                selectedRowKeys: selected ? [selected.instanceId] : [],
                onChange: (_selectedRowKeys, selectedRows) => setSelected(selectedRows[0]),
              }}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                onChange: (page, pageSize) => fetchMseNacos(auth, page - 1, pageSize),
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 条`,
              }}
              size="small"
            />
          </div>
        )}

        {selected && (
          <div className="text-right">
            <Button type="primary" onClick={handleImport}>
              导入
            </Button>
          </div>
        )}
      </Form>
    </Modal>
  )
}
