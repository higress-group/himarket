import { useState } from 'react'
import { Button, Table, Modal, Form, Input, message, Select } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { gatewayApi } from '@/lib/api'
import { Gateway, ApigConfig } from '@/types'
import { getGatewayTypeLabel } from '@/lib/constant'

interface ImportGatewayModalProps {
  visible: boolean
  gatewayType: 'APIG_API' | 'APIG_AI' | 'ADP_AI_GATEWAY'
  onCancel: () => void
  onSuccess: () => void
}

export default function ImportGatewayModal({ visible, gatewayType, onCancel, onSuccess }: ImportGatewayModalProps) {
  const [importForm] = Form.useForm()

  const [gatewayLoading, setGatewayLoading] = useState(false)
  const [gatewayList, setGatewayList] = useState<Gateway[]>([])
  const [selectedGateway, setSelectedGateway] = useState<Gateway | null>(null)

  const [apigConfig, setApigConfig] = useState<ApigConfig>({
    region: '',
    accessKey: '',
    secretKey: '',
  })

  const [gatewayPagination, setGatewayPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  })

  // 监听表单中的认证方式，确保切换时联动渲染
  const authType = Form.useWatch('authType', importForm)

  // 获取网关列表
  const fetchGateways = async (values: any, page = 1, size = 10) => {
    setGatewayLoading(true)
    try {
      const res = await gatewayApi.getApigGateway({
        ...values,
        page,
        size,
      })
      
      setGatewayList(res.data?.content || [])
      setGatewayPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      })
    } catch {
      // message.error('获取网关列表失败')
    } finally {
      setGatewayLoading(false)
    }
  }

  const fetchAdpGateways = async (values: any, page = 1, size = 50) => {
    setGatewayLoading(true)
    try {
      const res = await gatewayApi.getAdpGateways({...values, page, size})
      setGatewayList(res.data?.content || [])
      setGatewayPagination({
        current: page,
        pageSize: size,
        total: res.data?.totalElements || 0,
      })
    } catch {
      // message.error('获取网关列表失败')
    } finally {
      setGatewayLoading(false)
    }
  }

  // 处理网关选择
  const handleGatewaySelect = (gateway: Gateway) => {
    setSelectedGateway(gateway)
  }

  // 处理网关分页变化
  const handleGatewayPaginationChange = (page: number, pageSize: number) => {
    const values = importForm.getFieldsValue()
    const data = JSON.parse(sessionStorage.getItem('importFormConfig') || '');
    if (JSON.stringify(values) === '{}') {
      fetchGateways({...data, gatewayType: gatewayType}, page, pageSize)
    } else {
      fetchGateways({...values, gatewayType: gatewayType}, page, pageSize)
    }
  }

  // 处理导入
  const handleImport = () => {
    if (!selectedGateway) {
      message.warning('请选择一个Gateway')
      return
    }
    const payload: any = {
      ...selectedGateway,
      gatewayType: gatewayType,
    }
    if (gatewayType === 'ADP_AI_GATEWAY') {
      payload.adpAIGatewayConfig = apigConfig
    } else {
      payload.apigConfig = apigConfig
    }
    gatewayApi.importGateway(payload).then(() => {
      message.success('导入成功！')
      handleCancel()
      onSuccess()
    }).catch(() => {
      // message.error(error.response?.data?.message || '导入失败！')
    })
  }

  // 重置弹窗状态
  const handleCancel = () => {
    setSelectedGateway(null)
    setGatewayList([])
    setGatewayPagination({ current: 1, pageSize: 10, total: 0 })
    importForm.resetFields()
    onCancel()
  }

  return (
    <Modal
      title="导入网关实例"
      open={visible}
      onCancel={handleCancel}
      footer={null}
      width={800}
    >
      <Form form={importForm} layout="vertical" preserve={false}>
        {gatewayList.length === 0 && ['APIG_API', 'APIG_AI'].includes(gatewayType) && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item label="Region" name="region" rules={[{ required: true, message: '请输入region' }]}>
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
                  setApigConfig(values)
                  sessionStorage.setItem('importFormConfig', JSON.stringify(values))
                  fetchGateways({...values, gatewayType: gatewayType})
                })
              }}
              loading={gatewayLoading}
            >
              获取网关列表
            </Button>
          </div>
        )}

        {['ADP_AI_GATEWAY'].includes(gatewayType) && gatewayList.length === 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">认证信息</h3>
            <Form.Item label="服务地址" name="baseUrl" rules={[{ required: true, message: '请输入服务地址' }, { pattern: /^https?:\/\//i, message: '必须以 http:// 或 https:// 开头' }]}> 
              <Input placeholder="如：http://apigateway.example.com 或者 http://10.236.6.144" />
            </Form.Item>
            <Form.Item 
              label="端口" 
              name="port" 
              initialValue={80} 
              rules={[
                { required: true, message: '请输入端口号' }, 
                { 
                  validator: (_, value) => {
                    if (value === undefined || value === null || value === '') return Promise.resolve()
                    const n = Number(value)
                    return n >= 1 && n <= 65535 ? Promise.resolve() : Promise.reject(new Error('端口范围需在 1-65535'))
                  }
                }
              ]}
            > 
              <Input type="text" placeholder="如：8080" />
            </Form.Item>
            <Form.Item
              label="认证方式"
              name="authType"
              initialValue="Seed"
              rules={[{ required: true, message: '请选择认证方式' }]}
            >
              <Select>
                <Select.Option value="Seed">Seed</Select.Option>
                <Select.Option value="Header">固定Header</Select.Option>
              </Select>
            </Form.Item>
            {authType === 'Seed' && (
              <Form.Item label="Seed" name="authSeed" rules={[{ required: true, message: '请输入Seed' }]}>
                <Input placeholder="通过configmap获取" />
              </Form.Item>
            )}
            {authType === 'Header' && (
              <Form.Item label="Headers">
                <Form.List name="authHeaders" initialValue={[{ key: '', value: '' }]}>
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map(({ key, name, ...restField }) => (
                        <div key={key} style={{ display: 'flex', marginBottom: 8, alignItems: 'center' }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'key']}
                            rules={[{ required: true, message: '请输入Header名称' }]}
                            style={{ flex: 1, marginRight: 8, marginBottom: 0 }}
                          >
                            <Input placeholder="Header名称，如：X-Auth-Token" />
                          </Form.Item>
                          <Form.Item
                            {...restField}
                            name={[name, 'value']}
                            rules={[{ required: true, message: '请输入Header值' }]}
                            style={{ flex: 1, marginRight: 8, marginBottom: 0 }}
                          >
                            <Input placeholder="Header值" />
                          </Form.Item>
                          {fields.length > 1 && (
                            <Button 
                              type="text" 
                              danger 
                              onClick={() => remove(name)}
                              style={{ marginBottom: 0 }}
                            >
                              删除
                            </Button>
                          )}
                        </div>
                      ))}
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Button 
                          type="dashed" 
                          onClick={() => add({ key: '', value: '' })} 
                          block 
                          icon={<PlusOutlined />}
                        >
                          添加Header
                        </Button>
                      </Form.Item>
                    </>
                  )}
                </Form.List>
              </Form.Item>
            )}
            <Button 
              type="primary" 
              onClick={() => {
                importForm.validateFields().then((values) => {
                  // 处理认证参数
                  const processedValues = { ...values };
                  
                  // 根据认证方式设置相应的参数
                  if (values.authType === 'Seed') {
                    processedValues.authSeed = values.authSeed;
                    delete processedValues.authHeaders;
                  } else if (values.authType === 'Header') {
                    processedValues.authHeaders = values.authHeaders;
                    delete processedValues.authSeed;
                  }
                  
                  setApigConfig(processedValues)
                  sessionStorage.setItem('importFormConfig', JSON.stringify(processedValues))
                  fetchAdpGateways({...processedValues, gatewayType: gatewayType})
                })
              }}
              loading={gatewayLoading}
            >
              获取网关列表
            </Button>
          </div>
        )}

        {gatewayList.length > 0 && (
          <div className="mb-4">
            <h3 className="text-lg font-medium mb-3">选择网关实例</h3>
            <Table
              rowKey="gatewayId"
              columns={[
                { title: 'ID', dataIndex: 'gatewayId' },
                { 
                  title: '类型', 
                  dataIndex: 'gatewayType',
                  render: (gatewayType: string) => getGatewayTypeLabel(gatewayType as any)
                },
                { title: '名称', dataIndex: 'gatewayName' },
              ]}
              dataSource={gatewayList}
              rowSelection={{
                type: 'radio',
                selectedRowKeys: selectedGateway ? [selectedGateway.gatewayId] : [],
                onChange: (_selectedRowKeys, selectedRows) => {
                  handleGatewaySelect(selectedRows[0])
                },
              }}
              pagination={{
                current: gatewayPagination.current,
                pageSize: gatewayPagination.pageSize,
                total: gatewayPagination.total,
                onChange: handleGatewayPaginationChange,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`,
              }}
              size="small"
            />
          </div>
        )}

        {selectedGateway && (
          <div className="text-right">
            <Button type="primary" onClick={handleImport}>
              完成导入
            </Button>
          </div>
        )}
      </Form>
    </Modal>
  )
} 