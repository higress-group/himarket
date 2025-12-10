import {useState} from 'react'
import {Button, Form, Input, Select, Table, Modal, Space, Tag, message, Card, Row, Col} from 'antd'
import {PlusOutlined, EditOutlined, DeleteOutlined, ExclamationCircleOutlined} from '@ant-design/icons'
import {PublicKeyConfig, PublicKeyFormat} from '@/types'

interface PublicKeyManagerProps {
  provider?: string | null
  publicKeys: PublicKeyConfig[]
  onSave: (publicKeys: PublicKeyConfig[]) => void
}

interface PublicKeyFormData {
  kid: string
  format: PublicKeyFormat
  algorithm: string
  value: string
}

export function PublicKeyManager({provider, publicKeys, onSave}: PublicKeyManagerProps) {
  const [form] = Form.useForm<PublicKeyFormData>()
  const [modalVisible, setModalVisible] = useState(false)
  const [editingIndex, setEditingIndex] = useState<number | null>(null)
  const [localPublicKeys, setLocalPublicKeys] = useState<PublicKeyConfig[]>(publicKeys)
  const [selectedFormat, setSelectedFormat] = useState<PublicKeyFormat>(PublicKeyFormat.PEM)

  const handleAdd = () => {
    setEditingIndex(null)
    setModalVisible(true)
    form.resetFields()
    setSelectedFormat(PublicKeyFormat.PEM)
    form.setFieldsValue({
      format: PublicKeyFormat.PEM,
      algorithm: 'RS256'
    })
  }

  const handleEdit = (index: number) => {
    setEditingIndex(index)
    setModalVisible(true)
    const publicKey = localPublicKeys[index]
    setSelectedFormat(publicKey.format)
    form.setFieldsValue(publicKey)
  }

  const handleDelete = (index: number) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined/>,
      content: '确定要删除这个公钥配置吗？',
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      onOk() {
        const updatedKeys = localPublicKeys.filter((_, i) => i !== index)
        setLocalPublicKeys(updatedKeys)
        onSave(updatedKeys)
        message.success('公钥删除成功')
      },
    })
  }

  const handleModalOk = async () => {
    try {
      const values = await form.validateFields()
      
      // 验证Kid的唯一性
      const isKidExists = localPublicKeys.some((key, index) => 
        key.kid === values.kid && index !== editingIndex
      )
      
      if (isKidExists) {
        message.error('公钥ID已存在，请使用不同的ID')
        return
      }

      let updatedKeys
      if (editingIndex !== null) {
        // 编辑模式
        updatedKeys = localPublicKeys.map((key, index) => 
          index === editingIndex ? values as PublicKeyConfig : key
        )
      } else {
        // 新增模式
        updatedKeys = [...localPublicKeys, values as PublicKeyConfig]
      }

      setLocalPublicKeys(updatedKeys)
      onSave(updatedKeys)
      setModalVisible(false)
      message.success(editingIndex !== null ? '公钥更新成功' : '公钥添加成功')
    } catch (error) {
      message.error('保存公钥失败')
    }
  }

  const handleModalCancel = () => {
    setModalVisible(false)
    setEditingIndex(null)
    setSelectedFormat(PublicKeyFormat.PEM)
    form.resetFields()
  }

  // 验证公钥内容格式
  const validatePublicKey = (_: any, value: string) => {
    if (!value) {
      return Promise.reject(new Error('请输入公钥内容'))
    }
    
    if (selectedFormat === PublicKeyFormat.PEM) {
      // 简单的PEM格式验证
      if (!value.includes('-----BEGIN') || !value.includes('-----END')) {
        return Promise.reject(new Error('PEM格式公钥应包含BEGIN和END标记'))
      }
    } else if (selectedFormat === PublicKeyFormat.JWK) {
      // 简单的JWK格式验证
      try {
        const jwk = JSON.parse(value)
        if (!jwk.kty || !jwk.n || !jwk.e) {
          return Promise.reject(new Error('JWK格式应包含kty、n、e字段'))
        }
      } catch {
        return Promise.reject(new Error('JWK格式应为有效的JSON'))
      }
    }
    
    return Promise.resolve()
  }

  const columns = [
    {
      title: '公钥ID (kid)',
      dataIndex: 'kid',
      key: 'kid',
      render: (kid: string) => (
        <Tag color="blue">{kid}</Tag>
      )
    },
    {
      title: '格式',
      dataIndex: 'format',
      key: 'format',
      render: (format: PublicKeyFormat) => (
        <Tag color={format === PublicKeyFormat.PEM ? 'green' : 'orange'}>
          {format}
        </Tag>
      )
    },
    {
      title: '算法',
      dataIndex: 'algorithm',
      key: 'algorithm',
      render: (algorithm: string) => (
        <Tag color="purple">{algorithm}</Tag>
      )
    },
    {
      title: '公钥内容',
      key: 'value',
      render: (record: PublicKeyConfig) => (
        <span className="font-mono text-xs text-gray-600">
          {record.format === PublicKeyFormat.PEM 
            ? record.value.substring(0, 50) + '...'
            : JSON.stringify(JSON.parse(record.value || '{}')).substring(0, 50) + '...'
          }
        </span>
      )
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, _record: PublicKeyConfig, index: number) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined/>}
            onClick={() => handleEdit(index)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            size="small"
            icon={<DeleteOutlined/>}
            onClick={() => handleDelete(index)}
          >
            删除
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h4 className="text-lg font-medium">
            {provider && `${provider} - `}JWT签名公钥管理
          </h4>
          <p className="text-sm text-gray-500">
            管理用于验证JWT签名的公钥，支持PEM和JWK格式
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined/>}
          onClick={handleAdd}
        >
          添加公钥
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={localPublicKeys}
        rowKey="kid"
        pagination={false}
        size="small"
        locale={{
          emptyText: '暂无公钥配置'
        }}
      />

      {/* 公钥配置说明 */}
      <Card size="small" className="mt-4 bg-blue-50">
        <Row gutter={16}>
          <Col span={12}>
            <div className="text-sm">
              <h5 className="font-medium mb-2 text-blue-800">PEM格式示例：</h5>
              <div className="bg-white p-2 rounded font-mono text-xs border">
                -----BEGIN PUBLIC KEY-----<br/>
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...<br/>
                -----END PUBLIC KEY-----
              </div>
            </div>
          </Col>
          <Col span={12}>
            <div className="text-sm">
              <h5 className="font-medium mb-2 text-blue-800">JWK格式示例：</h5>
              <div className="bg-white p-2 rounded font-mono text-xs border">
                {`{
  "kty": "RSA",
  "kid": "key1",
  "n": "...",
  "e": "AQAB"
}`}
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* 公钥配置模态框 */}
      <Modal
        title={editingIndex !== null ? '编辑公钥' : '添加公钥'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={700}
        okText={editingIndex !== null ? '更新' : '添加'}
        cancelText="取消"
      >
        <Form
          form={form}
          layout="vertical"
        >
          <div className="grid grid-cols-2 gap-4">
            <Form.Item
              name="kid"
              label="公钥ID (kid)"
              rules={[
                {required: true, message: '请输入公钥ID'},
                {pattern: /^[a-zA-Z0-9_-]+$/, message: '公钥ID只能包含字母、数字、下划线和连字符'}
              ]}
            >
              <Input placeholder="如: key1, auth-key-2024"/>
            </Form.Item>
            <Form.Item
              name="algorithm"
              label="签名算法"
              rules={[{required: true, message: '请选择签名算法'}]}
            >
              <Select placeholder="选择签名算法">
                <Select.Option value="RS256">RS256</Select.Option>
                <Select.Option value="RS384">RS384</Select.Option>
                <Select.Option value="RS512">RS512</Select.Option>
                <Select.Option value="ES256">ES256</Select.Option>
                <Select.Option value="ES384">ES384</Select.Option>
                <Select.Option value="ES512">ES512</Select.Option>
              </Select>
            </Form.Item>
          </div>

          <Form.Item
            name="format"
            label="公钥格式"
            rules={[{required: true, message: '请选择公钥格式'}]}
          >
            <Select 
              placeholder="选择公钥格式"
              onChange={(value) => setSelectedFormat(value as PublicKeyFormat)}
            >
              <Select.Option value={PublicKeyFormat.PEM}>PEM格式</Select.Option>
              <Select.Option value={PublicKeyFormat.JWK}>JWK格式</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name="value"
            label="公钥内容"
            rules={[
              {required: true, message: '请输入公钥内容'},
              {validator: validatePublicKey}
            ]}
          >
            <Input.TextArea
              rows={8}
              placeholder={
                selectedFormat === PublicKeyFormat.JWK
                  ? '请输入JWK格式的公钥，例如:\n{\n  "kty": "RSA",\n  "kid": "key1",\n  "n": "...",\n  "e": "AQAB"\n}'
                  : '请输入PEM格式的公钥，例如:\n-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----'
              }
              style={{fontFamily: 'monospace'}}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
