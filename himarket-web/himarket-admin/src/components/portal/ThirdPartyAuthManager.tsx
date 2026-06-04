import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  MinusCircleOutlined,
  KeyOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
} from '@ant-design/icons';
import {
  Button,
  Form,
  Input,
  Select,
  Switch,
  Modal,
  Space,
  message,
  Divider,
  Steps,
  Card,
  Tabs,
  Collapse,
  Radio,
} from 'antd';
import { useState } from 'react';

import { DataTable } from '@/components/common/DataTable';
import { useLocale } from '@/contexts/LocaleContext';
import type { ThirdPartyAuthConfig, AuthCodeConfig, OAuth2Config, OidcConfig } from '@/types';
import { AuthenticationType, GrantType, PublicKeyFormat } from '@/types';

interface ThirdPartyAuthManagerProps {
  configs: ThirdPartyAuthConfig[];
  onSave: (configs: ThirdPartyAuthConfig[]) => Promise<void>;
}

export function ThirdPartyAuthManager({ configs, onSave }: ThirdPartyAuthManagerProps) {
  const { t } = useLocale();
  const [form] = Form.useForm();
  const [modalVisible, setModalVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editingConfig, setEditingConfig] = useState<ThirdPartyAuthConfig | null>(null);
  const [currentStep, setCurrentStep] = useState(0);
  const [selectedType, setSelectedType] = useState<AuthenticationType | null>(null);

  // 添加新配置
  const handleAdd = () => {
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    setModalVisible(true);
    form.resetFields();
  };

  // 编辑配置
  const handleEdit = (config: ThirdPartyAuthConfig) => {
    setEditingConfig(config);
    setSelectedType(config.type);
    setCurrentStep(1); // 直接进入配置步骤

    // 先重置表单，再设置新值
    form.resetFields();
    setModalVisible(true);

    // 根据类型设置表单值
    if (config.type === AuthenticationType.OIDC) {
      // OIDC配置：直接使用OidcConfig的字段
      const oidcConfig = config as OidcConfig & { type: AuthenticationType.OIDC };

      // 检查是否是手动配置模式（有具体的端点地址）
      const hasManualEndpoints = !!(
        oidcConfig.authCodeConfig?.authorizationEndpoint &&
        oidcConfig.authCodeConfig?.tokenEndpoint &&
        oidcConfig.authCodeConfig?.userInfoEndpoint
      );

      form.setFieldsValue({
        configMode: hasManualEndpoints ? 'manual' : 'auto',
        enabled: oidcConfig.enabled,
        name: oidcConfig.name,
        provider: oidcConfig.provider,
        type: oidcConfig.type,
        ...oidcConfig.authCodeConfig,
        emailField:
          oidcConfig.identityMapping?.emailField ||
          oidcConfig.authCodeConfig?.identityMapping?.emailField,
        // 设置OIDC专用的授权模式字段
        oidcGrantType: oidcConfig.grantType || 'AUTHORIZATION_CODE',
        // 身份映射字段可能在根级别或authCodeConfig中
        userIdField:
          oidcConfig.identityMapping?.userIdField ||
          oidcConfig.authCodeConfig?.identityMapping?.userIdField,
        userNameField:
          oidcConfig.identityMapping?.userNameField ||
          oidcConfig.authCodeConfig?.identityMapping?.userNameField,
      });
    } else if (config.type === AuthenticationType.OAUTH2) {
      // OAuth2配置：直接使用OAuth2Config的字段
      const oauth2Config = config as OAuth2Config & { type: AuthenticationType.OAUTH2 };
      form.setFieldsValue({
        emailField: oauth2Config.identityMapping?.emailField,
        enabled: oauth2Config.enabled,
        name: oauth2Config.name,
        oauth2GrantType: oauth2Config.grantType || GrantType.JWT_BEARER, // 使用oauth2GrantType字段
        provider: oauth2Config.provider,
        publicKeys: oauth2Config.jwtBearerConfig?.publicKeys || [],
        type: oauth2Config.type,
        userIdField: oauth2Config.identityMapping?.userIdField,
        userNameField: oauth2Config.identityMapping?.userNameField,
      });
    }
  };

  // 删除配置
  const handleDelete = async (provider: string, name: string) => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('portal.auth.deleteConfirm', { name }),
      icon: <ExclamationCircleOutlined />,
      okText: t('common.confirmDelete'),
      okType: 'danger',
      async onOk() {
        try {
          const updatedConfigs = configs.filter((config) => config.provider !== provider);
          await onSave(updatedConfigs);
          message.success(t('portal.auth.deleteSuccess'));
        } catch (_error) {
          message.error(t('portal.auth.deleteFailed'));
        }
      },
      title: t('common.confirmDelete'),
    });
  };

  // 下一步
  const handleNext = async () => {
    if (currentStep === 0) {
      try {
        const values = await form.validateFields(['type']);
        setSelectedType(values.type);
        setCurrentStep(1);

        // 为不同类型设置默认值
        if (values.type === AuthenticationType.OAUTH2) {
          form.setFieldsValue({
            enabled: true,
            oauth2GrantType: GrantType.JWT_BEARER,
          });
        } else if (values.type === AuthenticationType.OIDC) {
          form.setFieldsValue({
            configMode: 'auto',
            enabled: true,
          });
        }
      } catch (_error) {
        // 验证失败
      }
    }
  };

  // 上一步
  const handlePrevious = () => {
    setCurrentStep(0);
  };

  // 保存配置
  const handleSave = async () => {
    try {
      setLoading(true);

      const values = await form.validateFields();

      let newConfig: ThirdPartyAuthConfig;

      if (selectedType === AuthenticationType.OIDC) {
        // OIDC配置：根据配置模式创建不同的authCodeConfig
        let authCodeConfig: AuthCodeConfig;

        // 构建身份映射配置（放在OidcConfig根级别）
        const identityMapping =
          values.userIdField || values.userNameField || values.emailField
            ? {
                emailField: values.emailField || null,
                userIdField: values.userIdField || null,
                userNameField: values.userNameField || null,
              }
            : undefined;

        if (values.configMode === 'auto') {
          // 自动发现模式：只保存issuer，端点置空（后端会通过issuer自动发现）
          authCodeConfig = {
            authorizationEndpoint: '', // 自动发现模式下端点为空
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            issuer: values.issuer,
            jwkSetUri: '',
            // 可选的自定义回调地址
            redirectUri: values.redirectUri || undefined,
            scopes: values.scopes,
            tokenEndpoint: '',
            userInfoEndpoint: '',
          };
        } else {
          // 手动配置模式：保存具体的端点地址
          authCodeConfig = {
            authorizationEndpoint: values.authorizationEndpoint,
            clientId: values.clientId,
            clientSecret: values.clientSecret,
            issuer: values.issuer || '', // 手动配置模式下issuer可选
            jwkSetUri: values.jwkSetUri || '',
            // 可选的自定义回调地址
            redirectUri: values.redirectUri || undefined,
            scopes: values.scopes,
            tokenEndpoint: values.tokenEndpoint,
            userInfoEndpoint: values.userInfoEndpoint,
          };
        }

        newConfig = {
          authCodeConfig,
          enabled: values.enabled ?? true,
          grantType: values.oidcGrantType || ('AUTHORIZATION_CODE' as const), // 使用oidcGrantType字段
          // 根级别的身份映射
          identityMapping,
          logoUrl: null,
          name: values.name,
          provider: values.provider,
          type: AuthenticationType.OIDC,
        } as OidcConfig & { type: AuthenticationType.OIDC };
      } else {
        // OAuth2配置：直接创建OAuth2Config格式
        const grantType = values.oauth2GrantType || GrantType.JWT_BEARER; // 使用oauth2GrantType字段
        newConfig = {
          enabled: values.enabled ?? true,
          grantType: grantType,
          identityMapping: {
            emailField: values.emailField || null,
            userIdField: values.userIdField || null,
            userNameField: values.userNameField || null,
          },
          jwtBearerConfig:
            grantType === GrantType.JWT_BEARER
              ? {
                  publicKeys: values.publicKeys || [],
                }
              : undefined,
          name: values.name,
          provider: values.provider,
          type: AuthenticationType.OAUTH2,
        } as OAuth2Config & { type: AuthenticationType.OAUTH2 };
      }

      let updatedConfigs;
      if (editingConfig) {
        updatedConfigs = configs.map((config) =>
          config.provider === editingConfig.provider ? newConfig : config,
        );
      } else {
        updatedConfigs = [...configs, newConfig];
      }

      await onSave(updatedConfigs);

      message.success(editingConfig ? t('portal.auth.updateSuccess') : t('portal.auth.addSuccess'));
      setModalVisible(false);
    } catch (_error) {
      message.error(t('portal.auth.saveFailed'));
    } finally {
      setLoading(false);
    }
  };

  // 取消
  const handleCancel = () => {
    setModalVisible(false);
    setEditingConfig(null);
    setSelectedType(null);
    setCurrentStep(0);
    form.resetFields();
  };

  // OIDC表格列定义（不包含类型列）
  const oidcColumns = [
    {
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => <span className="font-medium text-gray-700">{provider}</span>,
      title: t('portal.auth.provider'),
      width: 120,
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: t('portal.auth.name'),
      width: 150,
    },
    {
      key: 'grantType',
      render: () => <span className="text-gray-600">{t('portal.auth.authCode')}</span>,
      title: t('portal.auth.grantType'),
      width: 120,
    },
    {
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '12px' }} />
          ) : (
            <MinusCircleFilled className="text-gray-500 mr-2" style={{ fontSize: '12px' }} />
          )}
          <span className="text-gray-700">
            {enabled ? t('portal.auth.enabled') : t('portal.auth.disabled')}
          </span>
        </div>
      ),
      title: t('common.status'),
      width: 80,
    },
    {
      key: 'action',
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)} type="link">
            {t('common.edit')}
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
            type="link"
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
      title: t('common.operation'),
      width: 150,
    },
  ];

  // OAuth2表格列定义（不包含类型列）
  const oauth2Columns = [
    {
      dataIndex: 'provider',
      key: 'provider',
      render: (provider: string) => <span className="font-medium text-gray-700">{provider}</span>,
      title: t('portal.auth.provider'),
      width: 120,
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: t('portal.auth.name'),
      width: 150,
    },
    {
      key: 'grantType',
      render: (record: ThirdPartyAuthConfig) => {
        if (record.type === AuthenticationType.OAUTH2) {
          const oauth2Config = record as OAuth2Config & { type: AuthenticationType.OAUTH2 };
          return (
            <span className="text-gray-600">
              {oauth2Config.grantType === GrantType.JWT_BEARER
                ? t('portal.auth.jwtBearer')
                : t('portal.auth.authCode')}
            </span>
          );
        }
        return <span className="text-gray-600">{t('portal.auth.authCode')}</span>;
      },
      title: t('portal.auth.grantType'),
      width: 120,
    },
    {
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (
        <div className="flex items-center">
          {enabled ? (
            <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: '12px' }} />
          ) : (
            <MinusCircleFilled className="text-gray-500 mr-2" style={{ fontSize: '12px' }} />
          )}
          <span className="text-gray-700">
            {enabled ? t('portal.auth.enabled') : t('portal.auth.disabled')}
          </span>
        </div>
      ),
      title: t('common.status'),
      width: 80,
    },
    {
      key: 'action',
      render: (_: unknown, record: ThirdPartyAuthConfig) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)} type="link">
            {t('common.edit')}
          </Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.provider, record.name)}
            type="link"
          >
            {t('common.delete')}
          </Button>
        </Space>
      ),
      title: t('common.operation'),
      width: 150,
    },
  ];

  // 渲染OIDC配置表单
  const renderOidcForm = () => (
    <div className="space-y-6">
      <Form.Item
        initialValue="AUTHORIZATION_CODE"
        label={t('portal.auth.grantType')}
        name="oidcGrantType"
      >
        <Select disabled>
          <Select.Option value="AUTHORIZATION_CODE">{t('portal.auth.authCode')}</Select.Option>
        </Select>
      </Form.Item>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          label="Client ID"
          name="clientId"
          rules={[{ message: t('portal.auth.clientIdRequired'), required: true }]}
        >
          <Input placeholder="Client ID" />
        </Form.Item>
        <Form.Item
          label="Client Secret"
          name="clientSecret"
          rules={[{ message: t('portal.auth.clientSecretRequired'), required: true }]}
        >
          <Input.Password placeholder="Client Secret" />
        </Form.Item>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Form.Item
          label={t('portal.auth.scopes')}
          name="scopes"
          rules={[{ message: t('portal.auth.scopesRequired'), required: true }]}
        >
          <Input placeholder={t('portal.auth.scopesPlaceholder')} />
        </Form.Item>
        <Form.Item
          label={t('portal.auth.redirectUri')}
          name="redirectUri"
          tooltip={t('portal.auth.redirectUriTooltip')}
        >
          <Input placeholder={t('portal.auth.redirectUriPlaceholder')} />
        </Form.Item>
      </div>

      <Divider />

      {/* 配置模式选择 */}
      <Form.Item initialValue="auto" label={t('portal.auth.endpointConfig')} name="configMode">
        <Radio.Group>
          <Radio value="auto">{t('portal.auth.autoDiscovery')}</Radio>
          <Radio value="manual">{t('portal.auth.manualConfig')}</Radio>
        </Radio.Group>
      </Form.Item>

      {/* 根据配置模式显示不同字段 */}
      <Form.Item
        noStyle
        shouldUpdate={(prevValues, curValues) => prevValues.configMode !== curValues.configMode}
      >
        {({ getFieldValue }) => {
          const configMode = getFieldValue('configMode') || 'auto';

          if (configMode === 'auto') {
            // 自动发现模式：只需要Issuer地址
            return (
              <Form.Item
                label="Issuer"
                name="issuer"
                rules={[
                  { message: t('portal.auth.issuerRequired'), required: true },
                  { message: t('portal.auth.invalidUrl'), type: 'url' },
                ]}
              >
                <Input placeholder={t('portal.auth.issuerPlaceholder')} />
              </Form.Item>
            );
          } else {
            // 手动配置模式：需要各个端点
            return (
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    label={t('portal.auth.authorizationEndpoint')}
                    name="authorizationEndpoint"
                    rules={[
                      { message: t('portal.auth.authorizationEndpointRequired'), required: true },
                    ]}
                  >
                    <Input placeholder={t('portal.auth.authorizationEndpointPlaceholder')} />
                  </Form.Item>
                  <Form.Item
                    label={t('portal.auth.tokenEndpoint')}
                    name="tokenEndpoint"
                    rules={[{ message: t('portal.auth.tokenEndpointRequired'), required: true }]}
                  >
                    <Input placeholder={t('portal.auth.tokenEndpointPlaceholder')} />
                  </Form.Item>
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <Form.Item
                    label={t('portal.auth.userInfoEndpoint')}
                    name="userInfoEndpoint"
                    rules={[{ message: t('portal.auth.userInfoEndpointRequired'), required: true }]}
                  >
                    <Input placeholder={t('portal.auth.userInfoEndpointPlaceholder')} />
                  </Form.Item>
                  <Form.Item label={t('portal.auth.jwkSetUri')} name="jwkSetUri">
                    <Input placeholder={t('portal.auth.optional')} />
                  </Form.Item>
                </div>
              </div>
            );
          }
        }}
      </Form.Item>

      <div className="-ml-3">
        <Collapse
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? 'rotate-90' : ''}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                clipRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                fillRule="evenodd"
              />
            </svg>
          )}
          ghost
          items={[
            {
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item label={t('portal.auth.developerId')} name="userIdField">
                      <Input placeholder={t('portal.auth.defaultSub')} />
                    </Form.Item>
                    <Form.Item label={t('portal.auth.developerName')} name="userNameField">
                      <Input placeholder={t('portal.auth.defaultName')} />
                    </Form.Item>
                    <Form.Item label={t('portal.auth.email')} name="emailField">
                      <Input placeholder={t('portal.auth.defaultEmail')} />
                    </Form.Item>
                  </div>

                  <div className="bg-blue-50 p-3 rounded-lg">
                    <div className="flex items-start space-x-2">
                      <div className="text-blue-600 mt-0.5">
                        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                          <path
                            clipRule="evenodd"
                            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                            fillRule="evenodd"
                          />
                        </svg>
                      </div>
                      <div>
                        <h4 className="text-blue-800 font-medium text-sm">
                          {t('portal.auth.configHelp')}
                        </h4>
                        <p className="text-blue-700 text-xs mt-1">
                          {t('portal.auth.oidcIdentityHelp')}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ),
              forceRender: true, // 确保折叠时表单字段仍然渲染，值能被收集
              key: 'advanced',
              label: (
                <div className="flex items-center text-gray-600">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      clipRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      fillRule="evenodd"
                    />
                    <path
                      clipRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      fillRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">{t('portal.auth.advancedConfig')}</span>
                  <span className="text-xs text-gray-400 ml-2">
                    {t('portal.auth.identityMapping')}
                  </span>
                </div>
              ),
            },
          ]}
          size="small"
        />
      </div>
    </div>
  );

  // 渲染OAuth2配置表单
  const renderOAuth2Form = () => (
    <div className="space-y-6">
      <Form.Item
        initialValue={GrantType.JWT_BEARER}
        label={t('portal.auth.grantType')}
        name="oauth2GrantType"
        rules={[{ required: true }]}
      >
        <Select disabled>
          <Select.Option value={GrantType.JWT_BEARER}>{t('portal.auth.jwtBearer')}</Select.Option>
        </Select>
      </Form.Item>

      <Form.List name="publicKeys">
        {(fields, { add, remove }) => (
          <div className="space-y-4">
            {fields.length > 0 && (
              <Collapse
                items={fields.map(({ key, name, ...restField }) => ({
                  children: (
                    <div className="space-y-4 px-4">
                      <div className="grid grid-cols-3 gap-4">
                        <Form.Item
                          {...restField}
                          label="Key ID"
                          name={[name, 'kid']}
                          rules={[
                            { message: t('portal.auth.publicKeyIdRequired'), required: true },
                          ]}
                        >
                          <Input
                            placeholder={t('portal.auth.publicKeyIdPlaceholder')}
                            size="small"
                          />
                        </Form.Item>
                        <Form.Item
                          {...restField}
                          label={t('portal.auth.signatureAlgorithm')}
                          name={[name, 'algorithm']}
                          rules={[
                            {
                              message: t('portal.auth.signatureAlgorithmRequired'),
                              required: true,
                            },
                          ]}
                        >
                          <Select
                            placeholder={t('portal.auth.selectSignatureAlgorithm')}
                            size="small"
                          >
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
                          label={t('portal.auth.publicKeyFormat')}
                          name={[name, 'format']}
                          rules={[
                            { message: t('portal.auth.publicKeyFormatRequired'), required: true },
                          ]}
                        >
                          <Select placeholder={t('portal.auth.selectPublicKeyFormat')} size="small">
                            <Select.Option value={PublicKeyFormat.PEM}>PEM</Select.Option>
                            <Select.Option value={PublicKeyFormat.JWK}>JWK</Select.Option>
                          </Select>
                        </Form.Item>
                      </div>

                      <Form.Item
                        noStyle
                        shouldUpdate={(prevValues, curValues) => {
                          const prevFormat = prevValues?.publicKeys?.[name]?.format;
                          const curFormat = curValues?.publicKeys?.[name]?.format;
                          return prevFormat !== curFormat;
                        }}
                      >
                        {({ getFieldValue }) => {
                          const format = getFieldValue(['publicKeys', name, 'format']);
                          return (
                            <Form.Item
                              {...restField}
                              label={t('portal.auth.publicKeyContent')}
                              name={[name, 'value']}
                              rules={[
                                {
                                  message: t('portal.auth.publicKeyContentRequired'),
                                  required: true,
                                },
                              ]}
                            >
                              <Input.TextArea
                                placeholder={
                                  format === PublicKeyFormat.JWK
                                    ? t('portal.auth.jwkPublicKeyPlaceholder')
                                    : t('portal.auth.pemPublicKeyPlaceholder')
                                }
                                rows={6}
                                style={{ fontFamily: 'monospace', fontSize: '12px' }}
                              />
                            </Form.Item>
                          );
                        }}
                      </Form.Item>
                    </div>
                  ),
                  extra: (
                    <Button
                      danger
                      icon={<MinusCircleOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        remove(name);
                      }}
                      size="small"
                      type="link"
                    >
                      {t('common.delete')}
                    </Button>
                  ),
                  key: key,
                  label: (
                    <div className="flex items-center">
                      <KeyOutlined className="mr-2" />
                      <span>{t('portal.auth.publicKeyNumber', { number: name + 1 })}</span>
                    </div>
                  ),
                }))}
                size="small"
              />
            )}
            <Button block icon={<PlusOutlined />} onClick={() => add()} size="small" type="dashed">
              {t('portal.auth.addPublicKey')}
            </Button>
          </div>
        )}
      </Form.List>

      <div className="-ml-3">
        <Collapse
          expandIcon={({ isActive }) => (
            <svg
              className={`w-4 h-4 transition-transform ${isActive ? 'rotate-90' : ''}`}
              fill="currentColor"
              viewBox="0 0 20 20"
            >
              <path
                clipRule="evenodd"
                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                fillRule="evenodd"
              />
            </svg>
          )}
          ghost
          items={[
            {
              children: (
                <div className="space-y-4 pt-2 ml-3">
                  <div className="grid grid-cols-3 gap-4">
                    <Form.Item label={t('portal.auth.developerId')} name="userIdField">
                      <Input placeholder={t('portal.auth.defaultUserId')} />
                    </Form.Item>
                    <Form.Item label={t('portal.auth.developerName')} name="userNameField">
                      <Input placeholder={t('portal.auth.defaultName')} />
                    </Form.Item>
                    <Form.Item label={t('portal.auth.email')} name="emailField">
                      <Input placeholder={t('portal.auth.defaultEmail')} />
                    </Form.Item>
                  </div>

                  <div className="bg-blue-50 p-3 rounded-lg">
                    <div className="flex items-start space-x-2">
                      <div className="text-blue-600 mt-0.5">
                        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                          <path
                            clipRule="evenodd"
                            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 0 100-2v-3a1 1 0 00-1-1H9z"
                            fillRule="evenodd"
                          />
                        </svg>
                      </div>
                      <div>
                        <h4 className="text-blue-800 font-medium text-sm">
                          {t('portal.auth.configHelp')}
                        </h4>
                        <p className="text-blue-700 text-xs mt-1">
                          {t('portal.auth.jwtIdentityHelp')}
                        </p>
                      </div>
                    </div>
                  </div>
                </div>
              ),
              forceRender: true, // 确保折叠时表单字段仍然渲染，值能被收集
              key: 'advanced',
              label: (
                <div className="flex items-center text-gray-600">
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path
                      clipRule="evenodd"
                      d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947z"
                      fillRule="evenodd"
                    />
                    <path
                      clipRule="evenodd"
                      d="M10 13a3 3 0 100-6 3 3 0 000 6z"
                      fillRule="evenodd"
                    />
                  </svg>
                  <span className="ml-2">{t('portal.auth.advancedConfig')}</span>
                  <span className="text-xs text-gray-400 ml-2">
                    {t('portal.auth.identityMapping')}
                  </span>
                </div>
              ),
            },
          ]}
          size="small"
        />
      </div>
    </div>
  );

  // 按类型分组配置
  const oidcConfigs = configs.filter((config) => config.type === AuthenticationType.OIDC);
  const oauth2Configs = configs.filter((config) => config.type === AuthenticationType.OAUTH2);

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h3 className="text-lg font-medium">{t('portal.auth.title')}</h3>
          <p className="text-sm text-gray-500">{t('portal.auth.description')}</p>
        </div>
        <Button icon={<PlusOutlined />} onClick={handleAdd} type="primary">
          {t('portal.auth.add')}
        </Button>
      </div>

      <Tabs
        defaultActiveKey="oidc"
        items={[
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4">
                  <h4 className="text-lg font-medium text-gray-900">
                    {t('portal.auth.oidcTitle')}
                  </h4>
                  <p className="text-sm text-gray-500 mt-1">{t('portal.auth.oidcDescription')}</p>
                </div>
                <DataTable<ThirdPartyAuthConfig>
                  columns={oidcColumns}
                  dataSource={oidcConfigs}
                  locale={{
                    emptyText: t('portal.auth.noOidc'),
                  }}
                  rowKey="provider"
                />
              </div>
            ),
            key: 'oidc',
            label: t('portal.auth.oidcTitle'),
          },
          {
            children: (
              <div className="bg-white rounded-lg">
                <div className="py-4">
                  <h4 className="text-lg font-medium text-gray-900">
                    {t('portal.auth.oauth2Title')}
                  </h4>
                  <p className="text-sm text-gray-500 mt-1">{t('portal.auth.oauth2Description')}</p>
                </div>
                <DataTable<ThirdPartyAuthConfig>
                  columns={oauth2Columns}
                  dataSource={oauth2Configs}
                  locale={{
                    emptyText: t('portal.auth.noOAuth2'),
                  }}
                  rowKey="provider"
                />
              </div>
            ),
            key: 'oauth2',
            label: t('portal.auth.oauth2Title'),
          },
        ]}
      />

      {/* 添加/编辑配置模态框 */}
      <Modal
        footer={null}
        onCancel={handleCancel}
        open={modalVisible}
        title={editingConfig ? t('portal.auth.editTitle') : t('portal.auth.addTitle')}
        width={800}
      >
        <Steps
          className="mb-6"
          current={currentStep}
          items={[
            {
              description: t('portal.auth.stepSelectTypeDescription'),
              title: t('portal.auth.stepSelectTypeTitle'),
            },
            {
              description: t('portal.auth.stepConfigureDescription'),
              title: t('portal.auth.stepConfigureTitle'),
            },
          ]}
        />

        <Form form={form} layout="vertical">
          {currentStep === 0 ? (
            // 第一步：选择类型
            <Card>
              <Form.Item
                label={t('portal.auth.authType')}
                name="type"
                rules={[{ message: t('portal.auth.authTypeRequired'), required: true }]}
              >
                <Select placeholder={t('portal.auth.selectAuthType')} size="large">
                  <Select.Option value={AuthenticationType.OIDC}>
                    <div className="py-2">
                      <div className="font-medium">{t('portal.auth.oidcOption')}</div>
                    </div>
                  </Select.Option>
                  <Select.Option value={AuthenticationType.OAUTH2}>
                    <div className="py-2">
                      <div className="font-medium">{t('portal.auth.oauth2Option')}</div>
                    </div>
                  </Select.Option>
                </Select>
              </Form.Item>

              <div className="flex justify-end">
                <Button onClick={handleNext} type="primary">
                  {t('common.next')}
                </Button>
              </div>
            </Card>
          ) : (
            // 第二步：配置详情
            <div>
              <div className="grid grid-cols-2 gap-4">
                <Form.Item
                  label={t('portal.auth.providerId')}
                  name="provider"
                  rules={[
                    { message: t('portal.auth.providerIdRequired'), required: true },
                    {
                      validator: (_, value) => {
                        if (!value) return Promise.resolve();

                        // 检查provider唯一性
                        const isDuplicate = configs.some(
                          (config) =>
                            config.provider === value &&
                            (!editingConfig || editingConfig.provider !== value),
                        );

                        if (isDuplicate) {
                          return Promise.reject(new Error(t('portal.auth.providerIdDuplicate')));
                        }

                        return Promise.resolve();
                      },
                    },
                  ]}
                >
                  <Input
                    disabled={editingConfig !== null}
                    placeholder={t('portal.auth.providerIdPlaceholder')}
                  />
                </Form.Item>
                <Form.Item
                  label={t('portal.auth.displayName')}
                  name="name"
                  rules={[{ message: t('portal.auth.displayNameRequired'), required: true }]}
                >
                  <Input placeholder={t('portal.auth.displayNamePlaceholder')} />
                </Form.Item>
              </div>

              <Form.Item
                label={t('portal.auth.enabledStatus')}
                name="enabled"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>

              <Divider />

              {/* 根据类型显示不同的配置表单 */}
              {selectedType === AuthenticationType.OIDC ? renderOidcForm() : renderOAuth2Form()}

              <div className="flex justify-between mt-6">
                <Button onClick={handlePrevious}>{t('common.previous')}</Button>
                <Space>
                  <Button onClick={handleCancel}>{t('common.cancel')}</Button>
                  <Button loading={loading} onClick={handleSave} type="primary">
                    {editingConfig ? t('action.update') : t('common.add')}
                  </Button>
                </Space>
              </div>
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
}
