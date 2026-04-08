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
  // 认证配置相关状态
  const [currentSource, setCurrentSource] = useState<string>('Default');
  const [currentKey, setCurrentKey] = useState<string>('Authorization');
  const [currentConfig, setCurrentConfig] = useState<ConsumerCredentialResult | null>(null);

  // 凭证管理相关状态
  const [credentialType, setCredentialType] = useState<'API_KEY' | 'HMAC'>('API_KEY');
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);
  const [credentialLoading, setCredentialLoading] = useState(false);

  // 编辑来源相关状态
  const [sourceModalVisible, setSourceModalVisible] = useState(false);
  const [editingSource, setEditingSource] = useState<string>('Default');
  const [editingKey, setEditingKey] = useState<string>('Authorization');

  // 表单
  const [sourceForm] = Form.useForm();
  const [credentialForm] = Form.useForm();

  const [activeTab, setActiveTab] = useState<string>('API_KEY');

  // 获取当前配置
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
      console.error('获取当前配置失败:', error);
    }
  }, [consumerId]);

  useEffect(() => {
    fetchCurrentConfig();
  }, [consumerId, fetchCurrentConfig]);

  // 凭证管理功能函数
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
        message.success('凭证添加成功');
        setCredentialModalVisible(false);
        resetCredentialForm();
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('创建凭证失败:', error);
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
        message.success('凭证删除成功');
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('删除凭证失败:', error);
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
        message.success('已复制到剪贴板');
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
        message.success('凭证来源更新成功');
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
      console.error('更新凭证来源失败:', error);
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
          console.error('设置API Key失败:', error);
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
          console.error('设置AK/SK失败:', error);
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

  // API Key 列
  const apiKeyColumns = [
    {
      dataIndex: 'apiKey',
      key: 'apiKey',
      render: (apiKey: string) => (
        <div className="flex items-center space-x-2">
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">{apiKey}</code>
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
          title="确定要删除该API Key凭证吗？"
        >
          <Button className="rounded-lg" icon={<DeleteOutlined className="text-red-500" />} />
        </Popconfirm>
      ),
      title: <span className="text-[#737373]">操作</span>,
    },
  ];

  // HMAC 列
  const hmacColumns = [
    {
      dataIndex: 'ak',
      key: 'ak',
      render: (ak: string) => (
        <div className="flex items-center space-x-2">
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">{ak}</code>
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
          <code className="text-sm px-2 py-1 border border-[#e5e5e5] rounded-lg">
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
          title="确定要删除该AK/SK凭证吗？"
        >
          <Button
            className="rounded-lg"
            icon={<DeleteOutlined className="text-red-500" />}
            size="small"
          />
        </Popconfirm>
      ),
      title: '操作',
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
    <div className="bg-white backdrop-blur-sm rounded-xl border border-white/60 shadow-sm overflow-hidden">
      <div className="p-6">
        <h3 className="text-base font-semibold text-gray-900 mb-4 ">认证方式</h3>
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
                {/* 凭证来源配置 */}
                <div className="mb-6 p-4 backdrop-blur-sm rounded-lg border border-[#e5e5e5]">
                  <div className="flex items-center">
                    <div className="flex items-center gap-4">
                      <span className="font-medium">凭证来源：</span>
                      <Text type="secondary">
                        {currentSource === 'Default'
                          ? 'Authorization: Bearer <token>'
                          : `${currentSource}：${currentKey}`}
                      </Text>
                    </div>
                    <Button
                      className="text-colorPrimary hover:text-colorPrimarySecondary ml-2"
                      icon={<EditOutlined />}
                      onClick={openSourceModal}
                      size="small"
                      type="text"
                    >
                      编辑
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
                  新增凭证
                </Button>
              </div>
              <div className="p-1 border border-[#e5e5e5] rounded-lg overflow-hidden">
                <Table
                  columns={apiKeyColumns}
                  dataSource={currentConfig?.apiKeyConfig?.credentials || []}
                  locale={{ emptyText: '暂无API Key凭证，请点击上方按钮创建' }}
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
                  添加AK/SK
                </Button>
              </div>
              <div className="border border-[#e5e5e5] rounded-lg overflow-hidden">
                <Table
                  columns={hmacColumns}
                  dataSource={currentConfig?.hmacConfig?.credentials || []}
                  locale={{ emptyText: '暂无AK/SK凭证，请点击上方按钮创建' }}
                  pagination={false}
                  rowKey={(record) => record.ak || record.sk || Math.random().toString()}
                  size="small"
                />
              </div>
            </div>
          )}
          {activeTab === 'JWT' && (
            <div className="text-center py-8 text-gray-500">JWT功能暂未开放</div>
          )}
        </div>
      </div>

      {/* 创建凭证模态框 */}
      <Modal
        cancelText="取消"
        confirmLoading={credentialLoading}
        okText="添加"
        onCancel={() => {
          setCredentialModalVisible(false);
          resetCredentialForm();
        }}
        onOk={handleCreateCredential}
        open={credentialModalVisible}
        styles={modelStyles}
        title={`添加 ${credentialType === 'API_KEY' ? 'API Key' : 'AK/SK'}`}
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
              <span>生成方式</span>
            </div>
            <Form.Item
              className="mb-0"
              name="generationMethod"
              rules={[{ message: '请选择生成方式', required: true }]}
            >
              <Radio.Group>
                <Radio value="SYSTEM">系统生成</Radio>
                <Radio value="CUSTOM">自定义</Radio>
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
                          <span>凭证</span>
                        </div>
                        <Form.Item
                          className="mb-2"
                          name="customApiKey"
                          rules={[
                            { message: '请输入自定义API Key', required: true },
                            {
                              message: '支持英文、数字、下划线(_)和短横线(-)',
                              pattern: /^[A-Za-z0-9_-]+$/,
                            },
                            { message: 'API Key长度至少8个字符', min: 8 },
                            { max: 128, message: 'API Key长度不能超过128个字符' },
                          ]}
                        >
                          <Input maxLength={128} placeholder="请输入凭证" />
                        </Form.Item>
                        <div className="text-xs text-gray-500">
                          长度为8-128个字符，可包含英文、数字、下划线（_）和短横线（-）
                        </div>
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
                              { message: '请输入自定义Access Key', required: true },
                              {
                                message: '支持英文、数字、下划线(_)和短横线(-)',
                                pattern: /^[A-Za-z0-9_-]+$/,
                              },
                              { message: 'Access Key长度至少8个字符', min: 8 },
                              { max: 128, message: 'Access Key长度不能超过128个字符' },
                            ]}
                          >
                            <Input maxLength={128} placeholder="请输入Access Key" />
                          </Form.Item>
                          <div className="text-xs text-gray-500">
                            长度为8-128个字符，可包含英文、数字、下划线（_）和短横线（-）
                          </div>
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
                              { message: '请输入自定义Secret Key', required: true },
                              {
                                message: '支持英文、数字、下划线(_)和短横线(-)',
                                pattern: /^[A-Za-z0-9_-]+$/,
                              },
                              { message: 'Secret Key长度至少8个字符', min: 8 },
                              { max: 128, message: 'Secret Key长度不能超过128个字符' },
                            ]}
                          >
                            <Input maxLength={128} placeholder="请输入 Secret Key" />
                          </Form.Item>
                          <div className="text-xs text-gray-500">
                            长度为8-128个字符，可包含英文、数字、下划线（_）和短横线（-）
                          </div>
                        </div>
                      </>
                    )}
                  </>
                );
              } else if (method === 'SYSTEM') {
                return (
                  <div>
                    <div className="text-sm text-gray-500">
                      <span>系统将自动生成符合规范的凭证</span>
                    </div>
                  </div>
                );
              }
              return null;
            }}
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑凭证来源模态框 */}
      <Modal
        cancelText="取消"
        okText="保存"
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
            // 校验失败，不提交
          }
        }}
        open={sourceModalVisible}
        styles={modelStyles}
        title="编辑凭证来源"
      >
        <Form
          form={sourceForm}
          initialValues={{ key: editingKey, source: editingSource }}
          layout="vertical"
        >
          <Form.Item
            label="凭证来源"
            name="source"
            rules={[{ message: '请选择凭证来源', required: true }]}
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
              <Select.Option value="Default">默认</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.source !== curr.source}>
            {({ getFieldValue }) =>
              getFieldValue('source') !== 'Default' ? (
                <Form.Item
                  label="键名"
                  name="key"
                  rules={[
                    {
                      message: '请输入键名',
                      required: true,
                    },
                    {
                      message: '仅支持字母/数字/-/_',
                      pattern: /^[A-Za-z0-9-_]+$/,
                    },
                  ]}
                >
                  <Input className="rounded-lg" placeholder="请输入键名" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
