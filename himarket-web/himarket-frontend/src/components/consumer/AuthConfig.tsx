import { EditOutlined, PlusOutlined, CopyOutlined, DeleteOutlined } from '@ant-design/icons';
import {
  Button,
  Typography,
  Table,
  Popconfirm,
  Modal,
  Radio,
  Input,
  Select,
  Form,
  message,
} from 'antd';
import React, { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import request from '../../lib/request';
import { modelStyles } from '../../lib/styles.ts';
import MultiSwitchButton from '../switch-button.tsx';

import type { ApiResponse } from '../../types';
import type {
  ConsumerCredentialResult,
  CreateCredentialParam,
  ConsumerCredential,
  HMACCredential,
  APIKeyCredential,
} from '../../types/consumer';

const { Text } = Typography;

interface AuthConfigProps {
  consumerId: string;
}

export function AuthConfig({ consumerId }: AuthConfigProps) {
  const { t } = useTranslation(['consumer', 'common']);
  const [currentSource, setCurrentSource] = useState<string>('Default');
  const [currentKey, setCurrentKey] = useState<string>('Authorization');
  const [currentConfig, setCurrentConfig] = useState<ConsumerCredentialResult | null>(null);

  const [credentialType, setCredentialType] = useState<'API_KEY' | 'HMAC'>('API_KEY');
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);
  const [credentialLoading, setCredentialLoading] = useState(false);

  const [sourceModalVisible, setSourceModalVisible] = useState(false);
  const [editingSource, setEditingSource] = useState<string>('Default');
  const [editingKey, setEditingKey] = useState<string>('Authorization');

  const [sourceForm] = Form.useForm();
  const [credentialForm] = Form.useForm();

  const [activeTab, setActiveTab] = useState<string>('API_KEY');

  const fetchCurrentConfig = React.useCallback(async () => {
    try {
      const response: ApiResponse<ConsumerCredentialResult> = await request.get(
        `/consumers/${consumerId}/credentials`,
      );
      if (response.code === 'SUCCESS' && response.data) {
        const config = response.data;
        setCurrentConfig(config);
        if (config.apiKeyConfig) {
          setCurrentSource(config.apiKeyConfig.source || 'Default');
          setCurrentKey(config.apiKeyConfig.key || 'Authorization');
        }
      }
    } catch (error) {
      console.error('Failed to fetch credential config:', error);
    }
  }, [consumerId]);

  useEffect(() => {
    fetchCurrentConfig();
  }, [consumerId, fetchCurrentConfig]);

  const handleCreateCredential = async () => {
    try {
      const values = await credentialForm.validateFields();
      setCredentialLoading(true);

      const currentResponse: ApiResponse<ConsumerCredentialResult> = await request.get(
        `/consumers/${consumerId}/credentials`,
      );
      let currentConfig: ConsumerCredentialResult = {};

      if (currentResponse.code === 'SUCCESS' && currentResponse.data) {
        currentConfig = currentResponse.data;
      }

      const param: CreateCredentialParam = {
        ...currentConfig,
      };

      if (credentialType === 'API_KEY') {
        const newCredential: ConsumerCredential = {
          apiKey:
            values.generationMethod === 'CUSTOM'
              ? values.customApiKey
              : generateRandomCredential('apiKey'),
          mode: values.generationMethod,
        };
        param.apiKeyConfig = {
          ...currentConfig.apiKeyConfig,
          credentials: [...(currentConfig.apiKeyConfig?.credentials || []), newCredential],
        };
      } else if (credentialType === 'HMAC') {
        const newCredential: ConsumerCredential = {
          ak:
            values.generationMethod === 'CUSTOM'
              ? values.customAccessKey
              : generateRandomCredential('accessKey'),
          mode: values.generationMethod,
          sk:
            values.generationMethod === 'CUSTOM'
              ? values.customSecretKey
              : generateRandomCredential('secretKey'),
        };
        param.hmacConfig = {
          ...currentConfig.hmacConfig,
          credentials: [...(currentConfig.hmacConfig?.credentials || []), newCredential],
        };
      }

      const response: ApiResponse<ConsumerCredentialResult> = await request.put(
        `/consumers/${consumerId}/credentials`,
        param,
      );
      if (response?.code === 'SUCCESS') {
        message.success(t('auth.credentialAdded'));
        setCredentialModalVisible(false);
        resetCredentialForm();
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('Failed to create credential:', error);
    } finally {
      setCredentialLoading(false);
    }
  };

  const handleDeleteCredential = async (credentialType: string, credential: ConsumerCredential) => {
    try {
      const currentResponse: ApiResponse<ConsumerCredentialResult> = await request.get(
        `/consumers/${consumerId}/credentials`,
      );
      let currentConfig: ConsumerCredentialResult = {};

      if (currentResponse.code === 'SUCCESS' && currentResponse.data) {
        currentConfig = currentResponse.data;
      }

      const param: CreateCredentialParam = {
        ...currentConfig,
      };

      if (credentialType === 'API_KEY') {
        param.apiKeyConfig = {
          credentials: currentConfig.apiKeyConfig?.credentials?.filter(
            (cred) => cred.apiKey !== (credential as APIKeyCredential).apiKey,
          ),
          key: currentConfig.apiKeyConfig?.key || 'Authorization',
          source: currentConfig.apiKeyConfig?.source || 'Default',
        };
      } else if (credentialType === 'HMAC') {
        param.hmacConfig = {
          credentials: currentConfig.hmacConfig?.credentials?.filter(
            (cred) => cred.ak !== (credential as HMACCredential).ak,
          ),
        };
      }

      const response: ApiResponse<ConsumerCredentialResult> = await request.put(
        `/consumers/${consumerId}/credentials`,
        param,
      );
      if (response?.code === 'SUCCESS') {
        message.success(t('auth.credentialDeleted'));
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('Failed to delete credential:', error);
    }
  };

  const handleCopyCredential = (text: string) => {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();

    try {
      const success = document.execCommand('copy');
      if (success) {
        message.success(t('auth.copied'));
      }
    } catch (err) {
      console.warn(err);
      // Ignore
    } finally {
      document.body.removeChild(textArea);
    }
  };

  const resetCredentialForm = () => {
    credentialForm.resetFields();
  };

  const handleEditSource = async (source: string, key: string) => {
    try {
      const currentResponse: ApiResponse<ConsumerCredentialResult> = await request.get(
        `/consumers/${consumerId}/credentials`,
      );
      let currentConfig: ConsumerCredentialResult = {};

      if (currentResponse.code === 'SUCCESS' && currentResponse.data) {
        currentConfig = currentResponse.data as ConsumerCredentialResult;
      }

      const param: CreateCredentialParam = {};

      if (currentConfig.apiKeyConfig) {
        param.apiKeyConfig = {
          credentials: currentConfig.apiKeyConfig.credentials,
          key: source === 'Default' ? 'Authorization' : key,
          source: source,
        };
      } else {
        param.apiKeyConfig = {
          credentials: [],
          key: source === 'Default' ? 'Authorization' : key,
          source: source,
        };
      }

      const response: ApiResponse<ConsumerCredentialResult> = await request.put(
        `/consumers/${consumerId}/credentials`,
        param,
      );
      if (response?.code === 'SUCCESS') {
        message.success(t('auth.sourceUpdated'));
        const updatedResponse: ApiResponse<ConsumerCredentialResult> = await request.get(
          `/consumers/${consumerId}/credentials`,
        );
        if (updatedResponse.code === 'SUCCESS' && updatedResponse.data) {
          const updatedConfig = updatedResponse.data;
          if (updatedConfig.apiKeyConfig) {
            setCurrentSource(updatedConfig.apiKeyConfig.source || 'Default');
            setCurrentKey(updatedConfig.apiKeyConfig.key || 'Authorization');
          }
        }
        setSourceModalVisible(false);
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('Failed to update credential source:', error);
    }
  };

  const openSourceModal = () => {
    const initSource = currentSource;
    const initKey = initSource === 'Default' ? 'Authorization' : currentKey;
    setEditingSource(initSource);
    setEditingKey(initKey);
    sourceForm.setFieldsValue({ key: initKey, source: initSource });
    setSourceModalVisible(true);
  };

  const openCredentialModal = () => {
    credentialForm.resetFields();
    credentialForm.setFieldsValue({
      customAccessKey: '',
      customApiKey: '',
      customSecretKey: '',
      generationMethod: 'SYSTEM',
    });
    setCredentialModalVisible(true);
  };

  const generateRandomCredential = (type: 'apiKey' | 'accessKey' | 'secretKey'): string => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-';

    if (type === 'apiKey') {
      const apiKey = Array.from({ length: 32 }, () =>
        chars.charAt(Math.floor(Math.random() * chars.length)),
      ).join('');
      const setValue = () => {
        try {
          credentialForm.setFieldsValue({ customApiKey: apiKey });
        } catch (error) {
          console.error('Failed to set API Key:', error);
        }
      };
      if (credentialForm.getFieldValue('customApiKey') !== undefined) {
        setValue();
      } else {
        setTimeout(setValue, 100);
      }
      return apiKey;
    } else {
      const ak = Array.from({ length: 32 }, () =>
        chars.charAt(Math.floor(Math.random() * chars.length)),
      ).join('');
      const sk = Array.from({ length: 64 }, () =>
        chars.charAt(Math.floor(Math.random() * chars.length)),
      ).join('');
      const setValue = () => {
        try {
          credentialForm.setFieldsValue({
            customAccessKey: ak,
            customSecretKey: sk,
          });
        } catch (error) {
          console.error('Failed to set AK/SK:', error);
        }
      };
      if (credentialForm.getFieldValue('customAccessKey') !== undefined) {
        setValue();
      } else {
        setTimeout(setValue, 100);
      }
      return type === 'accessKey' ? ak : sk;
    }
  };

  const maskSecretKey = (secretKey: string): string => {
    if (!secretKey || secretKey.length < 8) return secretKey;
    return (
      secretKey.substring(0, 4) +
      '*'.repeat(secretKey.length - 8) +
      secretKey.substring(secretKey.length - 4)
    );
  };

  const apiKeyColumns = [
    {
      dataIndex: 'apiKey',
      key: 'apiKey',
      render: (apiKey: string) => (
        <div className="flex items-center space-x-2">
          <code
            className="block max-w-[450px] truncate rounded-lg border border-[#e5e5e5] px-2 py-1 text-sm"
            title={apiKey}
          >
            {apiKey}
          </code>
          <Button
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(apiKey)}
            size="small"
            type="text"
          />
        </div>
      ),
      title: <span className="text-[#737373]">API Key</span>,
    },
    {
      key: 'action',
      render: (record: ConsumerCredential) => (
        <Popconfirm
          onConfirm={() => handleDeleteCredential('API_KEY', record)}
          title={t('auth.deleteApiKeyConfirm')}
        >
          <Button className="rounded-lg" icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
      title: <span className="text-[#737373]">{t('auth.action')}</span>,
      width: 80,
    },
  ];

  const hmacColumns = [
    {
      dataIndex: 'ak',
      key: 'ak',
      render: (ak: string) => (
        <div className="flex items-center space-x-2">
          <code
            className="block max-w-[300px] truncate rounded-lg border border-[#e5e5e5] px-2 py-1 text-sm"
            title={ak}
          >
            {ak}
          </code>
          <Button
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(ak)}
            size="small"
            type="text"
          />
        </div>
      ),
      title: 'Access Key',
    },
    {
      dataIndex: 'sk',
      key: 'sk',
      render: (sk: string) => (
        <div className="flex items-center space-x-2">
          <code
            className="block max-w-[380px] truncate rounded-lg border border-[#e5e5e5] px-2 py-1 text-sm"
            title={maskSecretKey(sk)}
          >
            {maskSecretKey(sk)}
          </code>
          <Button
            icon={<CopyOutlined className="text-colorPrimary" />}
            onClick={() => handleCopyCredential(sk)}
            size="small"
            type="text"
          />
        </div>
      ),
      title: 'Secret Key',
    },
    {
      key: 'action',
      render: (record: ConsumerCredential) => (
        <Popconfirm
          onConfirm={() => handleDeleteCredential('HMAC', record)}
          title={t('auth.deleteHmacConfirm')}
        >
          <Button
            className="rounded-lg"
            icon={<DeleteOutlined className="text-red-500" />}
            size="small"
          />
        </Popconfirm>
      ),
      title: t('auth.action'),
      width: 80,
    },
  ];

  const switchBtnOptions = useMemo(() => {
    return [
      { label: 'API Key', value: 'API_KEY' },
      { label: 'HMAC', value: 'HMAC' },
      { label: 'JWT', value: 'JWT' },
    ];
  }, []);

  return (
    <div className="bg-white backdrop-blur-sm rounded-[10px] border border-white/60 shadow-sm overflow-hidden">
      <div className="p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-4 ">{t('auth.title')}</h3>
        <MultiSwitchButton
          initialValue={activeTab}
          onChange={(val) => {
            setActiveTab(val);
          }}
          options={switchBtnOptions}
        />
        <div className="mt-6">
          {activeTab === 'API_KEY' && (
            <div>
              <div className="mb-4">
                <div className="mb-6 p-4 backdrop-blur-sm rounded-lg border border-[#e5e5e5]">
                  <div className="flex items-center">
                    <div className="flex items-center gap-4">
                      <span className="font-medium">{t('auth.credentialSource')}</span>
                      <Text type="secondary">
                        {currentSource === 'Default'
                          ? 'Authorization: Bearer <token>'
                          : `${currentSource}: ${currentKey}`}
                      </Text>
                    </div>
                    <Button
                      className="text-colorPrimary hover:text-colorPrimarySecondary ml-2"
                      icon={<EditOutlined />}
                      onClick={openSourceModal}
                      size="small"
                      type="text"
                    >
                      {t('auth.edit')}
                    </Button>
                  </div>
                </div>

                <Button
                  className="rounded-lg"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setCredentialType('API_KEY');
                    openCredentialModal();
                  }}
                  type="primary"
                >
                  {t('auth.addCredential')}
                </Button>
              </div>
              <div className="overflow-hidden rounded-lg border border-[#e5e5e5] p-1">
                <Table
                  columns={apiKeyColumns}
                  dataSource={currentConfig?.apiKeyConfig?.credentials || []}
                  locale={{ emptyText: t('auth.apiKeyEmpty') }}
                  pagination={false}
                  rowKey={(record) => record.apiKey || Math.random().toString()}
                />
              </div>
            </div>
          )}
          {activeTab === 'HMAC' && (
            <div>
              <div className="mb-4">
                <Button
                  className="rounded-lg"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setCredentialType('HMAC');
                    openCredentialModal();
                  }}
                  type="primary"
                >
                  {t('auth.addAkSk')}
                </Button>
              </div>
              <div className="overflow-hidden rounded-lg border border-[#e5e5e5]">
                <Table
                  columns={hmacColumns}
                  dataSource={currentConfig?.hmacConfig?.credentials || []}
                  locale={{ emptyText: t('auth.hmacEmpty') }}
                  pagination={false}
                  rowKey={(record) => record.ak || record.sk || Math.random().toString()}
                  size="small"
                />
              </div>
            </div>
          )}
          {activeTab === 'JWT' && (
            <div className="text-center py-8 text-gray-500">{t('auth.jwtComingSoon')}</div>
          )}
        </div>
      </div>

      <Modal
        cancelText={t('common:cancel')}
        confirmLoading={credentialLoading}
        okText={t('auth.add')}
        onCancel={() => {
          setCredentialModalVisible(false);
          resetCredentialForm();
        }}
        onOk={handleCreateCredential}
        open={credentialModalVisible}
        styles={modelStyles}
        title={t('auth.createCredentialTitle', {
          type: credentialType === 'API_KEY' ? 'API Key' : 'AK/SK',
        })}
      >
        <Form
          form={credentialForm}
          initialValues={{
            customAccessKey: '',
            customApiKey: '',
            customSecretKey: '',
            generationMethod: 'SYSTEM',
          }}
        >
          <div className="mb-4">
            <div className="mb-2">
              <span className="text-red-500 mr-1">*</span>
              <span>{t('auth.generationMethod')}</span>
            </div>
            <Form.Item
              className="mb-0"
              name="generationMethod"
              rules={[{ message: t('auth.generationMethodRequired'), required: true }]}
            >
              <Radio.Group>
                <Radio value="SYSTEM">{t('auth.systemGenerated')}</Radio>
                <Radio value="CUSTOM">{t('auth.custom')}</Radio>
              </Radio.Group>
            </Form.Item>
          </div>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.generationMethod !== curr.generationMethod}
          >
            {({ getFieldValue }) => {
              const method = getFieldValue('generationMethod');
              if (method === 'CUSTOM') {
                return (
                  <>
                    {credentialType === 'API_KEY' && (
                      <div className="mb-4">
                        <div className="mb-2">
                          <span className="text-red-500 mr-1">*</span>
                          <span>{t('auth.credential')}</span>
                        </div>
                        <Form.Item
                          className="mb-2"
                          name="customApiKey"
                          rules={[
                            { message: t('auth.customApiKeyRequired'), required: true },
                            {
                              message: t('auth.allowedCredentialChars'),
                              pattern: /^[A-Za-z0-9_-]+$/,
                            },
                            { message: t('auth.apiKeyMin'), min: 8 },
                            { max: 128, message: t('auth.apiKeyMax') },
                          ]}
                        >
                          <Input maxLength={128} placeholder={t('auth.credentialPlaceholder')} />
                        </Form.Item>
                        <div className="text-xs text-gray-500">{t('auth.credentialHelp')}</div>
                      </div>
                    )}
                    {credentialType === 'HMAC' && (
                      <>
                        <div className="mb-4">
                          <div className="mb-2">
                            <span className="text-red-500 mr-1">*</span>
                            <span>Access Key</span>
                          </div>
                          <Form.Item
                            className="mb-2"
                            name="customAccessKey"
                            rules={[
                              { message: t('auth.customAccessKeyRequired'), required: true },
                              {
                                message: t('auth.allowedCredentialChars'),
                                pattern: /^[A-Za-z0-9_-]+$/,
                              },
                              { message: t('auth.accessKeyMin'), min: 8 },
                              { max: 128, message: t('auth.accessKeyMax') },
                            ]}
                          >
                            <Input maxLength={128} placeholder={t('auth.accessKeyPlaceholder')} />
                          </Form.Item>
                          <div className="text-xs text-gray-500">{t('auth.credentialHelp')}</div>
                        </div>
                        <div className="mb-4">
                          <div className="mb-2">
                            <span className="text-red-500 mr-1">*</span>
                            <span>Secret Key</span>
                          </div>
                          <Form.Item
                            className="mb-2"
                            name="customSecretKey"
                            rules={[
                              { message: t('auth.customSecretKeyRequired'), required: true },
                              {
                                message: t('auth.allowedCredentialChars'),
                                pattern: /^[A-Za-z0-9_-]+$/,
                              },
                              { message: t('auth.secretKeyMin'), min: 8 },
                              { max: 128, message: t('auth.secretKeyMax') },
                            ]}
                          >
                            <Input maxLength={128} placeholder={t('auth.secretKeyPlaceholder')} />
                          </Form.Item>
                          <div className="text-xs text-gray-500">{t('auth.credentialHelp')}</div>
                        </div>
                      </>
                    )}
                  </>
                );
              } else if (method === 'SYSTEM') {
                return (
                  <div>
                    <div className="text-sm text-gray-500">
                      <span>{t('auth.systemGenerationHint')}</span>
                    </div>
                  </div>
                );
              }
              return null;
            }}
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        cancelText={t('common:cancel')}
        okText={t('auth.save')}
        onCancel={() => {
          const initSource = currentSource;
          const initKey = initSource === 'Default' ? 'Authorization' : currentKey;
          setEditingSource(initSource);
          setEditingKey(initKey);
          sourceForm.resetFields();
          setSourceModalVisible(false);
        }}
        onOk={async () => {
          try {
            const values = await sourceForm.validateFields();
            setEditingSource(values.source);
            setEditingKey(values.key);
            await handleEditSource(values.source, values.key);
          } catch {
            // Ignore validation errors.
          }
        }}
        open={sourceModalVisible}
        styles={modelStyles}
        title={t('auth.editSourceTitle')}
      >
        <Form
          form={sourceForm}
          initialValues={{ key: editingKey, source: editingSource }}
          layout="vertical"
        >
          <Form.Item
            label={t('auth.source')}
            name="source"
            rules={[{ message: t('auth.sourceRequired'), required: true }]}
          >
            <Select
              className="w-full rounded-lg"
              onChange={(value) => {
                const nextKey = value === 'Default' ? 'Authorization' : '';
                sourceForm.setFieldsValue({ key: nextKey });
              }}
            >
              <Select.Option value="Header">Header</Select.Option>
              <Select.Option value="QueryString">QueryString</Select.Option>
              <Select.Option value="Default">{t('auth.defaultSource')}</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.source !== curr.source}>
            {({ getFieldValue }) =>
              getFieldValue('source') !== 'Default' ? (
                <Form.Item
                  label={t('auth.keyName')}
                  name="key"
                  rules={[
                    {
                      message: t('auth.keyRequired'),
                      required: true,
                    },
                    {
                      message: t('auth.keyPattern'),
                      pattern: /^[A-Za-z0-9-_]+$/,
                    },
                  ]}
                >
                  <Input className="rounded-lg" placeholder={t('auth.keyPlaceholder')} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
