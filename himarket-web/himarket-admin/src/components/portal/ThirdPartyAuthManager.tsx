import {useState} from 'react'
import {Button, Form, Input, Select, Switch, Table, Modal, Space, message, Divider, Steps, Card, Tabs, Collapse, Radio} from 'antd'
import {PlusOutlined, EditOutlined, DeleteOutlined, ExclamationCircleOutlined, MinusCircleOutlined, KeyOutlined, CheckCircleFilled, MinusCircleFilled} from '@ant-design/icons'
import {ThirdPartyAuthConfig, AuthenticationType, GrantType, AuthCodeConfig, OAuth2Config, OidcConfig, PublicKeyFormat} from '@/types'

interface ThirdPartyAuthManagerProps {
  configs: ThirdPartyAuthConfig[]
  onSave: (configs: ThirdPartyAuthConfig[]) => Promise<void>
}

export function ThirdPartyAuthManager({configs, onSave}: ThirdPartyAuthManagerProps) {
  const [form] = Form.useForm()
  const [modalVisible, setModalVisible] = useState(false)
  const [loading, setLoading] = useState(false)
  const [editingConfig, setEditingConfig] = useState<ThirdPartyAuthConfig | null>(null)
  const [currentStep, setCurrentStep] = useState(0)
  const [selectedType, setSelectedType] = useState<AuthenticationType | null>(null)


  // 添加新配置
  const handleAdd = () => {
    setEditingConfig(null)
    setSelectedType(null)
    setCurrentStep(0)
    setModalVisible(true)
    form.resetFields()
  }

  // 编辑配置
  const handleEdit = (config: ThirdPartyAuthConfig) => {
    setEditingConfig(config)
    setSelectedType(config.type)
    setCurrentStep(1) // 直接进入配置步骤
    setModalVisible(true)
    
    // 根据类型设置表单值
    if (config.type === AuthenticationType.OIDC) {
      // OIDC配置：直接使用OidcConfig的字段
      const oidcConfig = config as (OidcConfig & { type: AuthenticationType.OIDC })
      
      // 检查是否是手动配置模式（有具体的端点地址）
      const hasManualEndpoints = !!(oidcConfig.authCodeConfig?.authorizationEndpoint && 
                                   oidcConfig.authCodeConfig?.tokenEndpoint && 
                                   oidcConfig.authCodeConfig?.userInfoEndpoint)
      
      form.setFieldsValue({
        provider: oidcConfig.provider,
        name: oidcConfig.name,
        enabled: oidcConfig.enabled,
        type: oidcConfig.type,
        configMode: hasManualEndpoints ? 'manual' : 'auto',
        ...oidcConfig.authCodeConfig,
        // 设置OIDC专用的授权模式字段
        oidcGrantType: oidcConfig.grantType || 'AUTHORIZATION_CODE',
        // 身份映射字段可能在根级别或authCodeConfig中
        userIdField: oidcConfig.identityMapping?.userIdField || oidcConfig.authCodeConfig?.identityMapping?.userIdField,
        userNameField: oidcConfig.identityMapping?.userNameField || oidcConfig.authCodeConfig?.identityMapping?.userNameField,
        emailField: oidcConfig.identityMapping?.emailField || oidcConfig.authCodeConfig?.identityMapping?.emailField
      })
    } else if (config.type === AuthenticationType.OAUTH2) {
      // OAuth2配置：直接使用OAuth2Config的字段
      const oauth2Config = config as (OAuth2Config & { type: AuthenticationType.OAUTH2 })
      form.setFieldsValue({
        provider: oauth2Config.provider,
        name: oauth2Config.name,
        enabled: oauth2Config.enabled,
        type: oauth2Config.type,
        oauth2GrantType: oauth2Config.grantType || GrantType.JWT_BEARER, // 使用oauth2GrantType字段
        userIdField: oauth2Config.identityMapping?.userIdField,
        userNameField: oauth2Config.identityMapping?.userNameField,
        emailField: oauth2Config.identityMapping?.emailField,
        publicKeys: oauth2Config.jwtBearerConfig?.publicKeys || []
      })
    }
  }

  // 删除配置
  const handleDelete = async (provider: string, name: string) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined/>,
      content: `确定要删除第三方认证配置 "${name}" 吗？此操作不可恢复。`,
      okText: '确认删除',
      okType: 'danger',
      cancelText: '取消',
      async onOk() {
        try {
          const updatedConfigs = configs.filter(config => config.provider !== provider)
          await onSave(updatedConfigs)
          message.success('第三方认证配置删除成功')
        } catch (error) {
          message.error('删除第三方认证配置失败')
        }
      },
    })
  }


  // 下一步
  const handleNext = async () => {
    if (currentStep === 0) {
      try {
        const values = await form.validateFields(['type'])
        setSelectedType(values.type)
        setCurrentStep(1)
        
        // 为不同类型设置默认值
        if (values.type === AuthenticationType.OAUTH2) {
          form.setFieldsValue({
            oauth2GrantType: GrantType.JWT_BEARER,
            enabled: true
          })
        } else if (values.type === AuthenticationType.OIDC) {
          form.setFieldsValue({
            enabled: true,
            configMode: 'auto'
          })
        }
      } catch (error) {
        // 验证失败
      }
    }
  }

  // 上一步
  const handlePrevious = () => {
    setCurrentStep(0)
  }

  // 保存配置
  const handleSave = async () => {
    try {
      setLoading(true)
      
      const values = await form.validateFields()

      let newConfig: ThirdPartyAuthConfig

      if (selectedType === AuthenticationType.OIDC) {
        // OIDC配置：根据配置模式创建不同的authCodeConfig
        let authCodeConfig: AuthCodeConfig
        
        if (values.configMode === 'auto') {
          // 自动发现模式：只保存issuer，端点置空（后端会通过issuer自动发现）
          authCodeConfig = {
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            scopes: values.scopes,
            issuer: values.issuer,
            authorizationEndpoint: '',  // 自动发现模式下端点为空
            tokenEndpoint: '',
            userInfoEndpoint: '',
            jwkSetUri: '',
            // 可选的身份映射配置
            identityMapping: (values.userIdField || values.userNameField || values.emailField) ? {
              userIdField: values.userIdField || null,
              userNameField: values.userNameField || null,
              emailField: values.emailField || null
            } : undefined
          }
        } else {
          // 手动配置模式：保存具体的端点地址
          authCodeConfig = {
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            scopes: values.scopes,
            issuer: values.issuer || '',  // 手动配置模式下issuer可选
            authorizationEndpoint: values.authorizationEndpoint,
            tokenEndpoint: values.tokenEndpoint,
            userInfoEndpoint: values.userInfoEndpoint,
            jwkSetUri: values.jwkSetUri || '',
            // 可选的身份映射配置
            identityMapping: (values.userIdField || values.userNameField || values.emailField) ? {
              userIdField: values.userIdField || null,
              userNameField: values.userNameField || null,
              emailField: values.emailField || null
            } : undefined
          }
        }

        newConfig = {
          provider: values.provider,
          name: values.name,
          logoUrl: null,
          enabled: values.enabled ?? true,
          grantType: values.oidcGrantType || 'AUTHORIZATION_CODE' as const, // 使用oidcGrantType字段
          authCodeConfig,
          // 根级别的身份映射（为兼容后端格式）
          identityMapping: authCodeConfig.identityMapping,
          type: AuthenticationType.OIDC
        } as (OidcConfig & { type: AuthenticationType.OIDC })
      } else {
        // OAuth2配置：直接创建OAuth2Config格式
        const grantType = values.oauth2GrantType || GrantType.JWT_BEARER // 使用oauth2GrantType字段
        newConfig = {
          provider: values.provider,
          name: values.name,
          enabled: values.enabled ?? true,
          grantType: grantType,
          jwtBearerConfig: grantType === GrantType.JWT_BEARER ? {
            publicKeys: values.publicKeys || []
          } : undefined,
          identityMapping: {
            userIdField: values.userIdField || null,
            userNameField: values.userNameField || null,
            emailField: values.emailField || null
          },
          type: AuthenticationType.OAUTH2
        } as (OAuth2Config & { type: AuthenticationType.OAUTH2 })
      }

      let updatedConfigs
      if (editingConfig) {
        updatedConfigs = configs.map(config => 
          config.provider === editingConfig.provider ? newConfig : config
        )
      } else {
        updatedConfigs = [...configs, newConfig]
      }

      await onSave(updatedConfigs)
      
      message.success(editingConfig ? '第三方认证配置更新成功' : '第三方认证配置添加成功')
      setModalVisible(false)
    } catch (error) {
      message.error('保存第三方认证配置失败')
    } finally {
      setLoading(false)
    }
  }

  // 取消
  const handleCancel = () => {
    setModalVisible(false)
    setEditingConfig(null)
    setSelectedType(null)
    setCurrentStep(0)
    form.resetFields()
  }

  // OIDC表格列定义（不包含类型列）
  const oidcColumns = [
    {
      title: '提供商',
      dataIndex: 'provider',
      key: 'provider',
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      )
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 150,
    },
    {
      title: '授权模式',
      key: 'grantType',
      width: 120,
      render: () => <span className="text-gray-600">授权码模式</span>
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '12px'}} />
          ) : (
            <MinusCircleFilled className="text-gray-500 mr-2" style={{fontSize: '12px'}} />
          )}
          <span className="text-gray-700">
            {enabled ? '已启用' : '已停用'}
          </span>
        </div>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined/>}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined/>}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      )
    }
  ]

  // OAuth2表格列定义（不包含类型列）
  const oauth2Columns = [
    {
      title: '提供商',
      dataIndex: 'provider',
      key: 'provider',
      width: 120,
      render: (provider: string) => (
        <span className="font-medium text-gray-700">{provider}</span>
      )
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 150,
    },
    {
      title: '授权模式',
      key: 'grantType',
      width: 120,
      render: (record: ThirdPartyAuthConfig) => {
        if (record.type === AuthenticationType.OAUTH2) {
          const oauth2Config = record as (OAuth2Config & { type: AuthenticationType.OAUTH2 })
          return (
            <span className="text-gray-600">
              {oauth2Config.grantType === GrantType.JWT_BEARER ? 'JWT断言' : '授权码模式'}
            </span>
          )
        }
        return <span className="text-gray-600">授权码模式</span>
      }
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled className="text-green-500 mr-2" style={{fontSize: '12px'}} />
          ) : (
            <MinusCircleFilled className="text-gray-500 mr-2" style={{fontSize: '12px'}} />
          )}
          <span className="text-gray-700">
            {enabled ? '已启用' : '已停用'}
          </span>
        </div>
      )
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: any, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined/>}
            onClick={() => handleEdit(record)}
          >
            编辑
          </Button>
          <Button
            type="link"
            danger
            icon={<DeleteOutlined/>}
            onClick={() => handleDelete(record.provider, record.name)}
          >
            删除
          </Button>
        </Space>
      )
    }
  ]

  // 渲染OIDC配置表单
  const renderOidcForm = () => (
    <div className="space-y-6">
      <Form.Item
        name="oidcGrantType"
        label="授权模式"
        initialValue="AUTHORIZATION_CODE"
      >
        <Select disabled>
          <Select.Option value="AUTHORIZATION_CODE">授权码模式</Select.Option>
        </Select>
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="clientId"
          label="Client ID"
          rules={[{required: true, message: '请输入 Client ID'}]}
        >
          <Input placeholder="Client ID"/>
        </Form.Item>
        <Form.Item
          name="clientSecret"
          label="Client Secret"
          rules={[{required: true, message: '请输入 Client Secret'}]}
        >
          <Input.Password placeholder="Client Secret"/>
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          name="scopes"
          label="授权范围"
          rules={[{required: true, message: '请输入授权范围'}]}
        >
          <Input placeholder="如: openid profile email"/>
        </Form.Item>
        <div></div>
      </div>

      <Divider />

      {/* 配置模式选择 */}
      <Form.Item
        name="configMode"
        label="端点配置"
        initialValue="auto"
      >
        <Radio.Group>
          <Radio value="auto">自动发现</Radio>
          <Radio value="manual">手动配置</Radio>
        </Radio.Group>
      </Form.Item>

      {/* 根据配置模式显示不同字段 */}
      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) => prevValues.configMode !== curValues.configMode}
      >
        {({ getFieldValue }) => {
          const configMode = getFieldValue('configMode') || 'auto'
          
          if (configMode === 'auto') {
            // 自动发现模式：只需要Issuer地址
            return (
              <Form.Item
                name="issuer"
                label="Issuer"
                rules={[
                  { required: true, message: '请输入Issuer地址' },
                  { type: 'url', message: '请输入有效的URL' }
                ]}
              >
                <Input placeholder="如: https://accounts.google.com" />
              </Form.Item>
            )
          } else {
            // 手动配置模式：需要各个端点
            return (
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    name="authorizationEndpoint"
                    label="授权端点"
                    rules={[{ required: true, message: '请输入授权端点' }]}
                  >
                    <Input placeholder="Authorization 授权端点"/>
                  </Form.Item>
                  <Form.Item
                    name="tokenEndpoint"
                    label="令牌端点"
                    rules={[{ required: true, message: '请输入令牌端点' }]}
                  >
                    <Input placeholder="Token 令牌端点"/>
                  </Form.Item>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    name="userInfoEndpoint"
                    label="用户信息端点"
                    rules={[{ required: true, message: '请输入用户信息端点' }]}
                  >
                    <Input placeholder="UserInfo 端点"/>
                  </Form.Item>
                  <Form.Item
                    name="jwkSetUri"
                    label="公钥端点"
                  >
                    <Input placeholder="可选"/>
                  </Form.Item>
                </div>
              </div>
            )
          }
        }}
      </Form.Item>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg 
              className={`w-4 h-4 transition-transform ${isActive ? 'rotate-90' : ''}`}
              fill="currentColor" 
              viewBox="0 0 20 20"
            >
              <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
            </svg>
          )}
          items={[
            {
              key: 'advanced',
              label: (
                <div className="flex items-center text-gray-600">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z" clipRule="evenodd" />
                    <path fillRule="evenodd" d="M10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item
                      name="userIdField"
                      label="开发者ID"
                    >
                      <Input placeholder="默认: sub"/>
                    </Form.Item>
                    <Form.Item
                      name="userNameField"
                      label="开发者名称"
                    >
                      <Input placeholder="默认: name"/>
                    </Form.Item>
                    <Form.Item
                      name="emailField"
                      label="邮箱"
                    >
                      <Input placeholder="默认: email"/>
                    </Form.Item>
                  </div>

                <div className="bg-blue-50 p-3 rounded-lg">
                  <div className="flex items-start space-x-2">
                    <div className="text-blue-600 mt-0.5">
                      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <div>
                      <h4 className="text-blue-800 font-medium text-sm">配置说明</h4>
                      <p className="text-blue-700 text-xs mt-1">
                        身份映射用于从OIDC令牌中提取用户信息。如果不填写，系统将使用OIDC标准字段。
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            )
          }
        ]}
      />
      </div>
    </div>
  )

  // 渲染OAuth2配置表单
  const renderOAuth2Form = () => (
    <div className="space-y-6">
      <Form.Item
        name="oauth2GrantType"
        label="授权模式"
        initialValue={GrantType.JWT_BEARER}
        rules={[{required: true}]}
      >
        <Select disabled>
          <Select.Option value={GrantType.JWT_BEARER}>JWT断言</Select.Option>
        </Select>
      </Form.Item>

      <Form.List name="publicKeys">
        {(fields, { add, remove }) => (
          <div className="space-y-4">
            {fields.length > 0 && (
              <Collapse
                size="small"
                items={fields.map(({ key, name, ...restField }) => ({
                  key: key,
                  label: (
                    <div className="flex items-center">
                      <KeyOutlined className="mr-2" />
                      <span>公钥 {name + 1}</span>
                    </div>
                  ),
                  extra: (
                    <Button
                      type="link"
                      danger
                      size="small"
                      icon={<MinusCircleOutlined />}
                      onClick={(e) => {
                        e.stopPropagation()
                        remove(name)
                      }}
                    >
                      删除
                    </Button>
                  ),
                  children: (
                    <div className="space-y-4 px-4">
                      <div className="grid grid-cols-3 gap-4">
                        <Form.Item
                          {...restField}
                          name={[name, 'kid']}
                          label="Key ID"
                          rules={[{ required: true, message: '请输入Key ID' }]}
                        >
                          <Input placeholder="公钥标识符" size="small" />
                        </Form.Item>
                        <Form.Item
                          {...restField}
                          name={[name, 'algorithm']}
                          label="签名算法"
                          rules={[{ required: true, message: '请选择签名算法' }]}
                        >
                          <Select placeholder="选择签名算法" size="small">
                            <Select.Option value="RS256">RS256</Select.Option>
                            <Select.Option value="RS384">RS384</Select.Option>
                            <Select.Option value="RS512">RS512</Select.Option>
                            <Select.Option value="ES256">ES256</Select.Option>
                            <Select.Option value="ES384">ES384</Select.Option>
                            <Select.Option value="ES512">ES512</Select.Option>
                          </Select>
                        </Form.Item>
                        <Form.Item
                          {...restField}
                          name={[name, 'format']}
                          label="公钥格式"
                          rules={[{ required: true, message: '请选择公钥格式' }]}
                        >
                          <Select placeholder="选择公钥格式" size="small">
                            <Select.Option value={PublicKeyFormat.PEM}>PEM</Select.Option>
                            <Select.Option value={PublicKeyFormat.JWK}>JWK</Select.Option>
                          </Select>
                        </Form.Item>
                      </div>

                      <Form.Item
                        noStyle
                        shouldUpdate={(prevValues, curValues) => {
                          const prevFormat = prevValues?.publicKeys?.[name]?.format
                          const curFormat = curValues?.publicKeys?.[name]?.format
                          return prevFormat !== curFormat
                        }}
                      >
                        {({ getFieldValue }) => {
                          const format = getFieldValue(['publicKeys', name, 'format'])
                          return (
                            <Form.Item
                              {...restField}
                              name={[name, 'value']}
                              label="公钥内容"
                              rules={[{ required: true, message: '请输入公钥内容' }]}
                            >
                              <Input.TextArea
                                rows={6}
                                placeholder={
                                  format === PublicKeyFormat.JWK
                                    ? 'JWK格式公钥，例如:\n{\n  "kty": "RSA",\n  "kid": "key1",\n  "n": "...",\n  "e": "AQAB"\n}'
                                    : 'PEM格式公钥，例如:\n-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\n-----END PUBLIC KEY-----'
                                }
                                style={{ fontFamily: 'monospace', fontSize: '12px' }}
                              />
                            </Form.Item>
                          )
                        }}
                      </Form.Item>
                    </div>
                  )
                }))}
              />
            )}
            <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />} size="small">
              添加公钥
            </Button>
          </div>
        )}
      </Form.List>

      <div className="-ml-3">
        <Collapse
          size="small"
          ghost
          expandIcon={({ isActive }) => (
            <svg 
              className={`w-4 h-4 transition-transform ${isActive ? 'rotate-90' : ''}`}
              fill="currentColor" 
              viewBox="0 0 20 20"
            >
              <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
            </svg>
          )}
          items={[
            {
              key: 'advanced',
              label: (
                <div className="flex items-center text-gray-600">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z" clipRule="evenodd" />
                    <path fillRule="evenodd" d="M10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" />
                  </svg>
                  <span className="ml-2">高级配置</span>
                  <span className="text-xs text-gray-400 ml-2">身份映射</span>
                </div>
              ),
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item
                      name="userIdField"
                      label="开发者ID"
                    >
                      <Input placeholder="默认: userId"/>
                    </Form.Item>
                    <Form.Item
                      name="userNameField"
                      label="开发者名称"
                    >
                      <Input placeholder="默认: name"/>
                    </Form.Item>
                    <Form.Item
                      name="emailField"
                      label="邮箱"
                    >
                      <Input placeholder="默认: email"/>
                    </Form.Item>
                  </div>

                <div className="bg-blue-50 p-3 rounded-lg">
                  <div className="flex items-start space-x-2">
                    <div className="text-blue-600 mt-0.5">
                      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <div>
                      <h4 className="text-blue-800 font-medium text-sm">配置说明</h4>
                      <p className="text-blue-700 text-xs mt-1">
                        身份映射用于从JWT载荷中提取用户信息。如果不填写，系统将使用默认字段名。
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            )
          }
        ]}
      />
      </div>
    </div>
  )

  // 按类型分组配置
  const oidcConfigs = configs.filter(config => config.type === AuthenticationType.OIDC)
  const oauth2Configs = configs.filter(config => config.type === AuthenticationType.OAUTH2)

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-medium">第三方认证</h3>
          <p className="text-sm text-gray-500">管理外部身份认证配置</p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined/>}
          onClick={handleAdd}
        >
          添加配置
        </Button>
      </div>

      <Tabs
        defaultActiveKey="oidc"
        items={[
          {
            key: 'oidc',
            label: 'OIDC配置',
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">OIDC配置</h4>
                  <p className="text-sm text-gray-500 mt-1">支持OpenID Connect标准协议的身份提供商</p>
                </div>
                <Table
                  columns={oidcColumns}
                  dataSource={oidcConfigs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: '暂无OIDC配置'
                  }}
                />
              </div>
            ),
          },
          {
            key: 'oauth2',
            label: 'OAuth2配置',
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4 border-b border-gray-200">
                  <h4 className="text-lg font-medium text-gray-900">OAuth2配置</h4>
                  <p className="text-sm text-gray-500 mt-1">支持OAuth 2.0标准协议的身份提供商</p>
                </div>
                <Table
                  columns={oauth2Columns}
                  dataSource={oauth2Configs}
                  rowKey="provider"
                  pagination={false}
                  size="small"
                  locale={{
                    emptyText: '暂无OAuth2配置'
                  }}
                />
              </div>
            ),
          },
        ]}
      />

      {/* 添加/编辑配置模态框 */}
      <Modal
        title={editingConfig ? '编辑第三方认证配置' : '添加第三方认证配置'}
        open={modalVisible}
        onCancel={handleCancel}
        width={800}
        footer={null}
      >
        <Steps
          current={currentStep}
          className="mb-6"
          items={[
            {
              title: '选择类型',
              description: '选择认证协议类型'
            },
            {
              title: '配置认证',
              description: '填写认证参数'
            }
          ]}
        />

        <Form
          form={form}
          layout="vertical"
        >
          {currentStep === 0 ? (
            // 第一步：选择类型
            <Card>
              <Form.Item
                name="type"
                label="认证类型"
                rules={[{required: true, message: '请选择认证类型'}]}
              >
                <Select placeholder="请选择认证方式" size="large">
                  <Select.Option value={AuthenticationType.OIDC}>
                    <div className="py-2">
                      <div className="font-medium">OIDC（适用于支持OpenID Connect的身份提供商认证）</div>
                    </div>
                  </Select.Option>
                  <Select.Option value={AuthenticationType.OAUTH2}>
                    <div className="py-2">
                      <div className="font-medium">OAuth2（适用于服务间集成）</div>
                    </div>
                  </Select.Option>
                </Select>
              </Form.Item>
              
              <div className="flex justify-end">
                <Button type="primary" onClick={handleNext}>
                  下一步
                </Button>
              </div>
            </Card>
          ) : (
            // 第二步：配置详情
            <div>
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  name="provider"
                  label="提供商标识"
                  rules={[
                    {required: true, message: '请输入提供商标识'},
                    {
                      validator: (_, value) => {
                        if (!value) return Promise.resolve()
                        
                        // 检查provider唯一性
                        const isDuplicate = configs.some(config => 
                          config.provider === value && 
                          (!editingConfig || editingConfig.provider !== value)
                        )
                        
                        if (isDuplicate) {
                          return Promise.reject(new Error('该提供商标识已存在，请使用不同的标识'))
                        }
                        
                        return Promise.resolve()
                      }
                    }
                  ]}
                >
                  <Input
                    placeholder="如: google, company-sso"
                    disabled={editingConfig !== null}
                  />
                </Form.Item>
                <Form.Item
                  name="name"
                  label="显示名称"
                  rules={[{required: true, message: '请输入显示名称'}]}
                >
                  <Input placeholder="如: Google登录、公司SSO"/>
                </Form.Item>
              </div>

              <Form.Item
                name="enabled"
                label="启用状态"
                valuePropName="checked"
              >
                <Switch/>
              </Form.Item>

              <Divider />

              {/* 根据类型显示不同的配置表单 */}
              {selectedType === AuthenticationType.OIDC ? renderOidcForm() : renderOAuth2Form()}

              <div className="flex justify-between mt-6">
                <Button onClick={handlePrevious}>
                  上一步
                </Button>
                <Space>
                  <Button onClick={handleCancel}>
                    取消
                  </Button>
                  <Button type="primary" loading={loading} onClick={handleSave}>
                    {editingConfig ? '更新' : '添加'}
                  </Button>
                </Space>
              </div>
            </div>
          )}
        </Form>
      </Modal>

    </div>
  )
}
