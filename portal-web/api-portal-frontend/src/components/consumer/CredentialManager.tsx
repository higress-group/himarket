import { useState, useEffect } from "react";
import {
  Card,
  Button,
  message,
  Tabs,
  Modal,
  Radio,
  Input,
  Table,
  Popconfirm,
  Select,
    Form,
} from "antd";
import {
  PlusOutlined,
  InfoCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined
} from "@ant-design/icons";
import api from "../../lib/api";
import type { ConsumerCredentialResult, CreateCredentialParam, ConsumerCredential, HMACCredential, APIKeyCredential } from "../../types/consumer";
import type { ApiResponse } from "../../types";

interface CredentialManagerProps {
  consumerId: string;
}

export function CredentialManager({ consumerId }: CredentialManagerProps) {
  const [credentialType, setCredentialType] = useState<'API_KEY' | 'HMAC'>('API_KEY');
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);
  const [credentialLoading, setCredentialLoading] = useState(false);

  const [sourceModalVisible, setSourceModalVisible] = useState(false);
  const [editingSource, setEditingSource] = useState<string>('Default');
  const [editingKey, setEditingKey] = useState<string>('Authorization');
  // 已保存（展示用）与编辑中的两套状态，取消时回滚到已保存值
  const [currentSource, setCurrentSource] = useState<string>('Default');
  const [currentKey, setCurrentKey] = useState<string>('Authorization');
  // 表单（编辑凭证来源）
  const [sourceForm] = Form.useForm();
  // 表单（创建凭证）
  const [credentialForm] = Form.useForm();
  // 当前完整配置（驱动表格数据源）
  const [currentConfig, setCurrentConfig] = useState<ConsumerCredentialResult | null>(null);

  // 初始化时获取当前配置
  const fetchCurrentConfig = async () => {
    try {
      const response: ApiResponse<ConsumerCredentialResult> = await api.get(`/consumers/${consumerId}/credentials`);
      if (response.code === "SUCCESS" && response.data) {
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
  };

  // 组件挂载时获取配置
  useEffect(() => {
    fetchCurrentConfig();
  }, [consumerId]);

  const handleCreateCredential = async () => {
    try {
      const values = await credentialForm.validateFields();
      setCredentialLoading(true);
      
      // 先获取当前的凭证配置
      const currentResponse: ApiResponse<ConsumerCredentialResult> = await api.get(`/consumers/${consumerId}/credentials`);
      let currentConfig: ConsumerCredentialResult = {};
      
      if (currentResponse.code === "SUCCESS" && currentResponse.data) {
        currentConfig = currentResponse.data;
      }

      // 构建新的凭证配置
      const param: CreateCredentialParam = {
        ...currentConfig,
      };
      
      if (credentialType === 'API_KEY') {
        const newCredential: ConsumerCredential = {
          apiKey: values.generationMethod === 'CUSTOM' ? values.customApiKey : generateRandomCredential('apiKey'),
          mode: values.generationMethod
        };
        param.apiKeyConfig = {
          ...currentConfig.apiKeyConfig,
          credentials: [...(currentConfig.apiKeyConfig?.credentials || []), newCredential]
        };
      } else if (credentialType === 'HMAC') {
        const newCredential: ConsumerCredential = {
          ak: values.generationMethod === 'CUSTOM' ? values.customAccessKey : generateRandomCredential('accessKey'),
          sk: values.generationMethod === 'CUSTOM' ? values.customSecretKey : generateRandomCredential('secretKey'),
          mode: values.generationMethod
        };
        param.hmacConfig = {
          ...currentConfig.hmacConfig,
          credentials: [...(currentConfig.hmacConfig?.credentials || []), newCredential]
        };
      }

      const response: ApiResponse<ConsumerCredentialResult> = await api.put(`/consumers/${consumerId}/credentials`, param);
      if (response?.code === "SUCCESS") {
        message.success('凭证添加成功');
        setCredentialModalVisible(false);
        resetCredentialForm();
        // 刷新当前配置以驱动表格
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('创建凭证失败:', error);
      // message.error('创建凭证失败');
    } finally {
      setCredentialLoading(false);
    }
  };

  const handleDeleteCredential = async (credentialType: string, credential: ConsumerCredential) => {
    try {
      // 先获取当前的凭证配置
      const currentResponse: ApiResponse<ConsumerCredentialResult> = await api.get(`/consumers/${consumerId}/credentials`);
      let currentConfig: ConsumerCredentialResult = {};
      
      if (currentResponse.code === "SUCCESS" && currentResponse.data) {
        currentConfig = currentResponse.data;
      }

      // 构建删除后的凭证配置，清空对应类型的凭证
      const param: CreateCredentialParam = {
        ...currentConfig,
      };
      
      if (credentialType === 'API_KEY') {
        param.apiKeyConfig = {
          credentials: currentConfig.apiKeyConfig?.credentials?.filter(cred => cred.apiKey !== (credential as APIKeyCredential).apiKey),
          source: currentConfig.apiKeyConfig?.source || 'Default',
          key: currentConfig.apiKeyConfig?.key || 'Authorization'
        };
      } else if (credentialType === 'HMAC') {
        param.hmacConfig = {
          credentials: currentConfig.hmacConfig?.credentials?.filter(cred => cred.ak !== (credential as HMACCredential).ak),
        };
      }

      const response: ApiResponse<ConsumerCredentialResult> = await api.put(`/consumers/${consumerId}/credentials`, param);
      if (response?.code === "SUCCESS") {
        message.success('凭证删除成功');
        await fetchCurrentConfig();
      }
    } catch (error) {
      console.error('删除凭证失败:', error);
      // message.error('删除凭证失败');
    }
  };
  const handleCopyCredential = (text: string) => {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-9999px'; // 避免影响页面布局
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
  
    try {
      const success = document.execCommand('copy');
      if (success) {
        message.success('已复制到剪贴板');
      } else {
        // message.error('复制失败，请手动复制内容');
      }
    } catch (err) {
      // message.error('复制失败，请手动复制内容');
    } finally {
      document.body.removeChild(textArea); // 清理 DOM
    }
  };
  

  const resetCredentialForm = () => {
    credentialForm.resetFields();
  };

  const handleEditSource = async (source: string, key: string) => {
    try {
      // 先获取当前的凭证配置
      const currentResponse: ApiResponse<ConsumerCredentialResult> = await api.get(`/consumers/${consumerId}/credentials`);
      let currentConfig: ConsumerCredentialResult = {};
      
      if (currentResponse.code === "SUCCESS" && currentResponse.data) {
        currentConfig = currentResponse.data as ConsumerCredentialResult;
      }

      // 构建新的凭证配置
      const param: CreateCredentialParam = {};
      
      // 更新API Key配置的source和key
      if (currentConfig.apiKeyConfig) {
        param.apiKeyConfig = {
          source: source,
          key: source === 'Default' ? 'Authorization' : key,
          credentials: currentConfig.apiKeyConfig.credentials
        };
      } else {
        param.apiKeyConfig = {
          source: source,
          key: source === 'Default' ? 'Authorization' : key,
          credentials: []
        };
      }

      

      // 提交配置到后端
      const response: ApiResponse<ConsumerCredentialResult> = await api.put(`/consumers/${consumerId}/credentials`, param);
      if (response?.code === "SUCCESS") {
        message.success('凭证来源更新成功');
        
        // 重新查询接口获取最新配置，确保数据落盘
        const updatedResponse: ApiResponse<ConsumerCredentialResult> = await api.get(`/consumers/${consumerId}/credentials`);
        if (updatedResponse.code === "SUCCESS" && updatedResponse.data) {
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
      // message.error('更新凭证来源失败');
    }
  };

  const openSourceModal = () => {
    // 打开弹窗前将已保存值拷贝到编辑态和表单
    const initSource = currentSource;
    const initKey = initSource === 'Default' ? 'Authorization' : currentKey;
    setEditingSource(initSource);
    setEditingKey(initKey);
    sourceForm.setFieldsValue({ source: initSource, key: initKey });
    setSourceModalVisible(true);
  };

  const openCredentialModal = () => {
    // 打开弹窗前重置表单并设置初始值
    credentialForm.resetFields();
    credentialForm.setFieldsValue({ 
      generationMethod: 'SYSTEM',
      customApiKey: '',
      customAccessKey: '',
      customSecretKey: ''
    });
    setCredentialModalVisible(true);
  };

  // 生成随机凭证
  const generateRandomCredential = (type: 'apiKey' | 'accessKey' | 'secretKey'): string => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    
    if (type === 'apiKey') {
      const apiKey = Array.from({ length: 16 }, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
      console.log('生成API Key:', apiKey);
      
      // 确保表单字段已经渲染并设置值
      const setValue = () => {
        try {
          credentialForm.setFieldsValue({ customApiKey: apiKey });
          console.log('API Key已设置到表单');
        } catch (error) {
          console.error('设置API Key失败:', error);
        }
      };
      
      // 如果表单已经渲染，立即设置；否则延迟设置
      if (credentialForm.getFieldValue('customApiKey') !== undefined) {
        setValue();
      } else {
        setTimeout(setValue, 100);
      }
      
      return apiKey;
    } else {
      const ak = Array.from({ length: 16 }, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
      const sk = Array.from({ length: 32 }, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
      console.log('生成AK:', ak, 'SK:', sk);
      
      // 确保表单字段已经渲染并设置值
      const setValue = () => {
        try {
          credentialForm.setFieldsValue({ 
            customAccessKey: ak, 
            customSecretKey: sk 
          });
          console.log('AK/SK已设置到表单');
        } catch (error) {
          console.error('设置AK/SK失败:', error);
        }
      };
      
      // 如果表单已经渲染，立即设置；否则延迟设置
      if (credentialForm.getFieldValue('customAccessKey') !== undefined) {
        setValue();
      } else {
        setTimeout(setValue, 100);
      }
      
      // 根据类型返回对应的值
      return type === 'accessKey' ? ak : sk;
    }
  };

  // API Key 列
  const apiKeyColumns = [
    {
      title: 'API Key',
      dataIndex: 'apiKey',
      key: 'apiKey',
      render: (apiKey: string) => (
        <div className="flex items-center space-x-2">
          <code className="text-sm bg-gray-100 px-2 py-1 rounded">{apiKey}</code>
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopyCredential(apiKey)} />
        </div>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (record: ConsumerCredential) => (
        <Popconfirm title="确定要删除该API Key凭证吗？" onConfirm={() => handleDeleteCredential('API_KEY', record)}>
          <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ];

  // 脱敏函数
  const maskSecretKey = (secretKey: string): string => {
    if (!secretKey || secretKey.length < 8) return secretKey;
    return secretKey.substring(0, 4) + '*'.repeat(secretKey.length - 8) + secretKey.substring(secretKey.length - 4);
  };

  // HMAC 列
  const hmacColumns = [
    {
      title: 'Access Key',
      dataIndex: 'ak',
      key: 'ak',
      render: (ak: string) => (
        <div className="flex items-center space-x-2">
          <code className="text-sm bg-gray-100 px-2 py-1 rounded">{ak}</code>
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopyCredential(ak)} />
        </div>
      ),
    },
    {
      title: 'Secret Key',
      dataIndex: 'sk',
      key: 'sk',
      render: (sk: string) => (
        <div className="flex items-center space-x-2">
          <code className="text-sm bg-gray-100 px-2 py-1 rounded">{maskSecretKey(sk)}</code>
          <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => handleCopyCredential(sk)} />
        </div>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (record: ConsumerCredential) => (
        <Popconfirm title="确定要删除该AK/SK凭证吗？" onConfirm={() => handleDeleteCredential('HMAC', record)}>
          <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <Card title="认证方式">
        <Tabs defaultActiveKey="API_KEY">
          <Tabs.TabPane tab="API Key" key="API_KEY">
            <div className="mb-4">
              <div className="flex items-start space-x-2 mb-4">
                <InfoCircleOutlined className="text-blue-500 mt-1" />
                <div className="text-sm text-gray-600">
                  API Key是一种简单的认证方式，客户端需要在请求中添加凭证，网关会验证API Key的合法性和权限。
                  API Key常用于简单场景，不涉及敏感操作，安全性相对较低，请注意凭证的管理与保护。
                </div>
              </div>
              
              {/* 凭证来源配置（展示已保存值）*/}
              <div className="mb-4 p-3 bg-gray-50 rounded border">
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-sm font-medium text-gray-700">凭证来源</span>
                  <Button type="link" size="small" icon={<EditOutlined />} onClick={openSourceModal}>
                    编辑
                  </Button>
                </div>
                {/* <div className="text-sm text-gray-600">
                  {currentSource === 'Default' ? '' : `${currentSource}`}
                </div> */}
                <div className="text-sm text-gray-600">
                  {currentSource === 'Default' ? 'Authorization: Bearer <token>' : `${currentSource}：${currentKey}`}
                </div>
              </div>
              
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setCredentialType('API_KEY');
                  openCredentialModal();
                }}
              >
                添加凭证
              </Button>
            </div>
            <Table
              columns={apiKeyColumns}
              dataSource={currentConfig?.apiKeyConfig?.credentials || []}
              rowKey={(record) => record.apiKey || Math.random().toString()}
              pagination={false}
              size="small"
              locale={{ emptyText: '暂无API Key凭证，请点击上方按钮创建' }}
            />
          </Tabs.TabPane>

          <Tabs.TabPane tab="HMAC" key="HMAC">
            <div className="mb-4">
              <div className="flex items-start space-x-2 mb-4">
                <InfoCircleOutlined className="text-blue-500 mt-1" />
                <div className="text-sm text-gray-600">
                  一种基于HMAC算法的AK/SK签名认证方式。客户端在调用API时，需要使用签名密钥对请求内容进行签名计算，
                  并将签名同步传输给服务器端进行签名验证。
                </div>
              </div>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setCredentialType('HMAC');
                  openCredentialModal();
                }}
              >
                添加AK/SK
              </Button>
            </div>
            <Table
              columns={hmacColumns}
              dataSource={currentConfig?.hmacConfig?.credentials || []}
              rowKey={(record) => record.ak || record.sk || Math.random().toString()}
              pagination={false}
              size="small"
              locale={{ emptyText: '暂无AK/SK凭证，请点击上方按钮创建' }}
            />
          </Tabs.TabPane>

          <Tabs.TabPane tab="JWT" key="JWT" disabled>
            <div className="text-center py-8 text-gray-500">
              JWT功能暂未开放
            </div>
          </Tabs.TabPane>
        </Tabs>
      </Card>

      {/* 创建凭证模态框 */}
      <Modal
        title={`添加 ${credentialType === 'API_KEY' ? 'API Key' : 'AK/SK'}`}
        open={credentialModalVisible}
        onCancel={() => {
          setCredentialModalVisible(false);
          resetCredentialForm();
        }}
        onOk={handleCreateCredential}
        confirmLoading={credentialLoading}
        okText="添加"
        cancelText="取消"
      >
        <Form form={credentialForm} layout="vertical" initialValues={{ 
          generationMethod: 'SYSTEM',
          customApiKey: '',
          customAccessKey: '',
          customSecretKey: ''
        }}>
          <Form.Item
            label="生成方式"
            name="generationMethod"
            rules={[{ required: true, message: '请选择生成方式' }]}
          >
            <Radio.Group>
              <Radio value="SYSTEM">系统生成</Radio>
              <Radio value="CUSTOM">自定义</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item noStyle shouldUpdate={(prev, curr) => prev.generationMethod !== curr.generationMethod}>
            {({ getFieldValue }) => {
              const method = getFieldValue('generationMethod');
              if (method === 'CUSTOM') {
                return (
                  <>
                                         {credentialType === 'API_KEY' && (
                       <Form.Item
                         label="API Key"
                         name="customApiKey"
                         rules={[
                           { required: true, message: '请输入自定义API Key' },
                           { pattern: /^[A-Za-z0-9-_]+$/, message: '仅支持字母/数字/-/_' },
                           { len: 16, message: 'API Key长度必须为16位' }
                         ]}
                       >
                           <Input placeholder="请输入自定义API Key（16位）" maxLength={16} />
                          
                       </Form.Item>
                     )}
                    {credentialType === 'HMAC' && (
                      <>
                                               <Form.Item
                         label="Access Key"
                         name="customAccessKey"
                         rules={[
                           { required: true, message: '请输入自定义Access Key' },
                           { pattern: /^[A-Za-z0-9-_]+$/, message: '仅支持字母/数字/-/_' },
                           { len: 16, message: 'Access Key长度必须为16位' }
                         ]}
                       >
                           <Input placeholder="请输入自定义Access Key（16位）" maxLength={16} />
                       </Form.Item>
                       <Form.Item
                         label="Secret Key"
                         name="customSecretKey"
                         rules={[
                           { required: true, message: '请输入自定义Secret Key' },
                           { pattern: /^[A-Za-z0-9-_]+$/, message: '仅支持字母/数字/-/_' },
                           { len: 32, message: 'Secret Key长度必须为32位' }
                         ]}
                       >
                         <Input placeholder="请输入自定义Secret Key（32位）" maxLength={32} />
                       </Form.Item>
                      </>
                    )}
                  </>
                );
              } else if (method === 'SYSTEM') {
                return (
                  <div className="text-sm text-gray-500 bg-gray-50 p-3 rounded border">
                    <div className="flex items-center gap-2 mb-2">
                      <InfoCircleOutlined />
                      <span>系统将自动生成符合规范的凭证</span>
                    </div>
                    <div className="text-xs">
                      {credentialType === 'API_KEY' ? (
                        <>• API Key: 16位字母数字组合</>
                      ) : (
                        <>
                          • Access Key: 16位字母数字组合<br/>
                          • Secret Key: 32位字母数字组合
                        </>
                      )}
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
        title="编辑凭证来源"
        open={sourceModalVisible}
        onCancel={() => {
          // 取消不落盘，回退到已保存值并重置表单
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
        okText="保存"
        cancelText="取消"
      >
        <Form form={sourceForm} layout="vertical" initialValues={{ source: editingSource, key: editingKey }}>
          <Form.Item
            label="凭证来源"
            name="source"
            rules={[{ required: true, message: '请选择凭证来源' }]}
          >
            <Select
              onChange={(value) => {
                const nextKey = value === 'Default' ? 'Authorization' : '';
                sourceForm.setFieldsValue({ key: nextKey });
              }}
              style={{ width: '100%' }}
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
                      required: true,
                      message: '请输入键名',
                    },
                    {
                      pattern: /^[A-Za-z0-9-_]+$/,
                      message: '仅支持字母/数字/-/_',
                    },
                  ]}
                >
                  <Input placeholder="请输入键名" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
{/* 
          <div className="text-sm text-gray-500">
            <div>说明：</div>
            <div>• Header: 凭证放在HTTP请求头中</div>
            <div>• QueryString: 凭证放在URL查询参数中</div>
            <div>• Default: 使用标准的Authorization头</div>
          </div> */}
        </Form>
      </Modal>
    </>
  );
} 