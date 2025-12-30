import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Card,
  Button,
  Table,
  Tag,
  Space,
  Modal,
  Form,
  Select,
  Input,
  InputNumber,
  message,
  Tabs,
  Descriptions,
  Badge,
  Alert,
  Radio,
  Checkbox,
  Collapse
} from 'antd';
import {
  ArrowLeftOutlined,
  CloudUploadOutlined,
  HistoryOutlined,
  GlobalOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  StopOutlined,
  EyeOutlined
} from '@ant-design/icons';
import { apiDefinitionApi, gatewayApi, nacosApi } from '@/lib/api';
import type { Gateway } from '@/types/gateway';
import dayjs from 'dayjs';
import { MonacoDiffEditor } from 'react-monaco-editor';

const { TextArea } = Input;

export default function ApiPublishManagement() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const apiDefinitionId = searchParams.get('id');
  const { productName, productId, productType } = location.state || {};

  const [apiDefinition, setApiDefinition] = useState<any>(null);
  const [publishRecords, setPublishRecords] = useState<any[]>([]);
  const [publishHistory, setPublishHistory] = useState<any[]>([]);
  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [ingressDomains, setIngressDomains] = useState<string[]>([]);
  const [serviceTypes, setServiceTypes] = useState<string[]>(['NACOS', 'FIXED_ADDRESS', 'DNS']);
  const [nacosInstances, setNacosInstances] = useState<any[]>([]);
  const [namespaces, setNamespaces] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [publishModalVisible, setPublishModalVisible] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [snapshotModalVisible, setSnapshotModalVisible] = useState(false);
  const [currentSnapshot, setCurrentSnapshot] = useState<any>(null);
  const [diffModalVisible, setDiffModalVisible] = useState(false);
  const [diffOriginal, setDiffOriginal] = useState('');
  const [diffModified, setDiffModified] = useState('');
  const [latestSnapshot, setLatestSnapshot] = useState<string>('');
  const [latestSnapshotRecordId, setLatestSnapshotRecordId] = useState<string>('');
  const [activeGatewayId, setActiveGatewayId] = useState<string | null>(null);
  const [form] = Form.useForm();
  const serviceType = Form.useWatch('serviceType', form);
  const provider = Form.useWatch('provider', form);
  const nacosId = Form.useWatch('nacosId', form);
  const namespace = Form.useWatch('namespace', form);

  useEffect(() => {
    if (apiDefinitionId) {
      fetchData();
    }
  }, [apiDefinitionId]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [apiRes, recordsRes, gatewaysRes, nacosRes] = await Promise.all([
        apiDefinitionApi.getApiDefinitionDetail(apiDefinitionId!),
        apiDefinitionApi.getPublishRecords(apiDefinitionId!, { page: 1, size: 100 }),
        gatewayApi.getGateways({ page: 1, size: 100 }),
        nacosApi.getNacosInstances({ page: 1, size: 100 })
      ]);

      setApiDefinition(apiRes.data || apiRes);
      
      const allRecords = recordsRes.data?.content || recordsRes.content || [];
      // Filter active records for the status view (if needed, or just use the latest per gateway)
      // For now, we can just use the list as is, or filter for ACTIVE ones if the UI expects only active ones in publishRecords
      // But the UI seems to just take the first one [0] to show status.
      // If we want to show "Current Status", we should probably find the latest record for each gateway.
      // However, to minimize changes, let's just set both to allRecords.
      // But wait, publishRecords was likely used for "Active Deployments".
      // If I set it to all history, publishRecords[0] will be the latest history item.
      // If the latest item is UNPUBLISH, status is INACTIVE. This is correct.
      
      setPublishRecords(allRecords);
      setPublishHistory(allRecords);

      // 获取当前生效的快照
      const activeRecord = allRecords.find((r: any) => r.status === 'ACTIVE');
      if (activeRecord) {
        setActiveGatewayId(activeRecord.gatewayId);
        if (activeRecord.snapshot) {
          setLatestSnapshot(JSON.stringify(activeRecord.snapshot, null, 2));
          setLatestSnapshotRecordId(activeRecord.recordId);
        }
      } else {
        setActiveGatewayId(null);
        // 如果没有 ACTIVE 状态的，尝试找最近一次成功的 PUBLISH
        const latestSuccess = allRecords.find((r: any) => r.action === 'PUBLISH' && r.status === 'SUCCESS');
        if (latestSuccess && latestSuccess.snapshot) {
          setLatestSnapshot(JSON.stringify(latestSuccess.snapshot, null, 2));
          setLatestSnapshotRecordId(latestSuccess.recordId);
        } else {
          setLatestSnapshot('');
          setLatestSnapshotRecordId('');
        }
      }

      setNacosInstances(nacosRes.data?.content || nacosRes.content || []);
      
      // Filter gateways based on API type if needed
      const allGateways = gatewaysRes.data?.content || gatewaysRes.content || [];
      setGateways(allGateways);
    } catch (error) {
      console.error('Failed to fetch data:', error);
      message.error('获取数据失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGatewayChange = async (gatewayId: string) => {
    try {
      const [domainsRes, serviceTypesRes] = await Promise.all([
        gatewayApi.getGatewayDomains(gatewayId),
        gatewayApi.getGatewayServiceTypes(gatewayId)
      ]);
      
      const domains = domainsRes.data || domainsRes;
      setIngressDomains(Array.isArray(domains) ? domains : []);
      
      const types = serviceTypesRes.data || serviceTypesRes;
      let availableTypes = Array.isArray(types) ? types : [];
      
      // 如果是 Model API，只保留 AI_SERVICE 选项
      if (apiDefinition?.type === 'MODEL_API') {
        availableTypes = ['AI_SERVICE'];
        form.setFieldValue('serviceType', 'AI_SERVICE');
      }

      if (availableTypes.length > 0) {
        setServiceTypes(availableTypes);
      }
      
      form.setFieldValue('ingressDomain', undefined); // Reset domain selection
      // Don't reset serviceType if it's still valid, or maybe we should? 
      // User request implies they want to see options early. 
      // If we reset, they lose selection. Let's keep it if valid, or reset if not in new list.
      const currentServiceType = form.getFieldValue('serviceType');
      if (currentServiceType && !availableTypes.includes(currentServiceType)) {
          form.setFieldValue('serviceType', undefined);
      }
    } catch (error) {
      console.error('Failed to fetch gateway info:', error);
      message.error('获取网关信息失败');
      setIngressDomains([]);
      // Don't clear serviceTypes on error, keep defaults
    }
  };

  const handleNacosChange = async (nacosId: string) => {
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 100 });
      setNamespaces(res.data?.content || res.content || []);
      form.setFieldValue('namespace', undefined); // Reset namespace selection
    } catch (error) {
      console.error('Failed to fetch namespaces:', error);
      message.error('获取命名空间失败');
    }
  };

  const handleOpenPublishModal = () => {
    if (activeGatewayId) {
      form.setFieldsValue({ gatewayId: activeGatewayId });
      handleGatewayChange(activeGatewayId);
    }
    setPublishModalVisible(true);
  };

  const handlePublish = async () => {
    try {
      const values = await form.validateFields();
      setPublishing(true);

      let serviceConfig: any = {
        serviceType: values.serviceType,
        tlsEnabled: values.tlsEnabled || false,
      };

      if (values.serviceType === 'NACOS') {
        serviceConfig = {
          ...serviceConfig,
          nacosId: values.nacosId,
          namespace: values.namespace,
          group: values.group,
          serviceName: values.serviceName,
        };
      } else if (values.serviceType === 'FIXED_ADDRESS') {
        serviceConfig = {
          ...serviceConfig,
          address: values.address,
        };
      } else if (values.serviceType === 'DNS') {
        serviceConfig = {
          ...serviceConfig,
          domain: values.domain,
        };
      } else if (values.serviceType === 'AI_SERVICE') {
        serviceConfig = {
          ...serviceConfig,
          provider: values.provider,
          protocol: values.protocol,
          apiKey: values.apiKey,
          // Provider specific configs
          openaiCustomUrl: values.openaiCustomUrl,
          responseJsonSchema: values.responseJsonSchema ? JSON.parse(values.responseJsonSchema) : undefined,
          azureServiceUrl: values.azureServiceUrl,
          moonshotFileId: values.moonshotFileId,
          qwenEnableSearch: values.qwenEnableSearch,
          qwenFileIds: values.qwenFileIds,
          qwenEnableCompatible: values.qwenEnableCompatible,
          reasoningContentMode: values.reasoningContentMode,
          minimaxApiType: values.minimaxApiType,
          minimaxGroupId: values.minimaxGroupId,
          claudeVersion: values.claudeVersion,
          ollamaServerHost: values.ollamaServerHost,
          ollamaServerPort: values.ollamaServerPort,
          hunyuanAuthId: values.hunyuanAuthId,
          hunyuanAuthKey: values.hunyuanAuthKey,
          cloudflareAccountId: values.cloudflareAccountId,
          geminiSafetySetting: values.geminiSafetySetting,
          apiVersion: values.apiVersion,
          geminiThinkingBudget: values.geminiThinkingBudget,
          targetLang: values.targetLang,
          difyApiUrl: values.difyApiUrl,
          botType: values.botType,
          inputVariable: values.inputVariable,
          outputVariable: values.outputVariable,
          vertexAuthKey: values.vertexAuthKey,
          vertexRegion: values.vertexRegion,
          vertexProjectId: values.vertexProjectId,
          vertexAuthServiceName: values.vertexAuthServiceName,
          vertexTokenRefreshAhead: values.vertexTokenRefreshAhead,
          tritonModelVersion: values.tritonModelVersion,
          tritonDomain: values.tritonDomain,
          awsAccessKey: values.awsAccessKey,
          awsSecretKey: values.awsSecretKey,
          awsRegion: values.awsRegion,
          bedrockAdditionalFields: values.bedrockAdditionalFields ? JSON.parse(values.bedrockAdditionalFields) : undefined,
          genericHost: values.genericHost,
          timeout: values.timeout,
          subPath: values.subPath,
          modelMapping: values.modelMapping ? JSON.parse(values.modelMapping) : undefined,
          context: values.context ? JSON.parse(values.context) : undefined,
        };
        // Remove undefined keys
        Object.keys(serviceConfig).forEach(key => serviceConfig[key] === undefined && delete serviceConfig[key]);
      }

      const publishData = {
        apiDefinitionId,
        gatewayId: values.gatewayId,
        comment: values.comment,
        publishConfig: {
          serviceConfig,
          domains: Array.isArray(values.ingressDomain) ? values.ingressDomain : (values.ingressDomain ? [values.ingressDomain] : []),
          basePath: '/'
        }
      };

      await apiDefinitionApi.publishApi(apiDefinitionId!, publishData);
      message.success('发布成功');
      setPublishModalVisible(false);
      form.resetFields();
      fetchData(); // Refresh data
    } catch (error: any) {
      message.error(error.message || '发布失败');
    } finally {
      setPublishing(false);
    }
  };

  const handleUnpublish = async (recordId: string) => {
    Modal.confirm({
      title: '确认下线',
      content: '确定要下线该 API 吗？下线后将无法访问。',
      okText: '确认下线',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await apiDefinitionApi.unpublishApi(apiDefinitionId!, recordId);
          message.success('下线成功');
          fetchData();
        } catch (error: any) {
          message.error(error.message || '下线失败');
        }
      }
    });
  };

  const handleViewSnapshot = (snapshot: any) => {
    setCurrentSnapshot(snapshot);
    setSnapshotModalVisible(true);
  };

  const handleDiffSnapshot = (record: any) => {
    if (!record.snapshot) return;
    const recordSnapshot = JSON.stringify(record.snapshot, null, 2);
    
    setDiffOriginal(recordSnapshot);
    setDiffModified(latestSnapshot);
    setDiffModalVisible(true);
  };

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Tag icon={<CheckCircleOutlined />} color="success">已发布</Tag>;
      case 'INACTIVE':
        return <Tag icon={<StopOutlined />} color="default">已下线</Tag>;
      case 'FAILED':
        return <Tag icon={<CloseCircleOutlined />} color="error">发布失败</Tag>;
      default:
        return <Tag icon={<SyncOutlined spin />} color="processing">{status}</Tag>;
    }
  };

  const renderServiceConfig = (config: any) => {
    if (!config) return '-';
    
    const itemStyle = { marginBottom: '4px', display: 'flex', alignItems: 'center' };
    const labelStyle = { color: '#8c8c8c', marginRight: '8px', minWidth: '60px' };

    switch (config.serviceType) {
      case 'NACOS':
        return (
          <div className="text-sm">
            <div style={{ marginBottom: '8px' }}>
              <Tag color="blue">Nacos 服务</Tag>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>服务名:</span>
              <span className="font-medium">{config.serviceName}</span>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>分组:</span>
              <span>{config.group}</span>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>命名空间:</span>
              <span>{config.namespace}</span>
            </div>
          </div>
        );
      case 'FIXED_ADDRESS':
        return (
          <div className="text-sm flex items-center">
            <Tag color="cyan">固定地址</Tag>
            <span className="font-mono bg-gray-50 px-1 rounded ml-2">{config.address}</span>
          </div>
        );
      case 'DNS':
        return (
          <div className="text-sm flex items-center">
            <Tag color="purple">DNS 域名</Tag>
            <span className="font-mono bg-gray-50 px-1 rounded ml-2">{config.domain}</span>
          </div>
        );
      case 'AI_SERVICE':
        return (
          <div className="text-sm">
            <div style={{ marginBottom: '8px' }}>
              <Tag color="magenta">AI 服务</Tag>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>协议:</span>
              <span>{config.protocol || 'openai/v1'}</span>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>提供商:</span>
              <span>{config.provider}</span>
            </div>
          </div>
        );
      default:
        return config.serviceType || '-';
    }
  };

  const historyColumns = [
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      render: (action: string) => {
        return action === 'PUBLISH' ? <Tag color="blue">发布</Tag> : <Tag color="orange">下线</Tag>;
      }
    },
    {
      title: '备注',
      dataIndex: 'publishNote',
      key: 'publishNote',
    },
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (text: string) => dayjs(text).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '结果',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      render: (errorMessage: string) => errorMessage ? <span className="text-red-500">{errorMessage}</span> : <span className="text-green-500">成功</span>
    },
    {
      title: '快照',
      key: 'snapshot',
      render: (_: any, record: any) => (
        record.snapshot ? (
          <Space>
            <Button 
              type="link" 
              icon={<EyeOutlined />} 
              onClick={() => handleViewSnapshot(record.snapshot)}
            >
              查看
            </Button>
            {record.recordId !== latestSnapshotRecordId && (
              <Button 
                type="link" 
                onClick={() => handleDiffSnapshot(record)}
              >
                对比
              </Button>
            )}
          </Space>
        ) : '-'
      )
    }
  ];

  return (
    <div className="p-6">
      <div className="mb-6 flex justify-between items-center">
        <div className="flex items-center">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(-1)}
            className="mr-4"
          >
            返回
          </Button>
          <div>
            <h1 className="text-2xl font-bold">API 发布管理</h1>
          </div>
        </div>
        <Button
          type="primary"
          icon={<CloudUploadOutlined />}
          onClick={handleOpenPublishModal}
        >
          发布 API
        </Button>
      </div>

      {apiDefinition && (
        <Card className="mb-6" title="基本信息">
          <Descriptions column={3}>
            <Descriptions.Item label="API 名称">{apiDefinition.name}</Descriptions.Item>
            <Descriptions.Item label="API ID">{apiDefinition.apiDefinitionId}</Descriptions.Item>
            <Descriptions.Item label="类型">{apiDefinition.type}</Descriptions.Item>
            <Descriptions.Item label="更新时间" span={3}>
              {dayjs(apiDefinition.updatedAt).format('YYYY-MM-DD HH:mm:ss')}
            </Descriptions.Item>
            <Descriptions.Item label="描述" span={3}>
              {apiDefinition.description || '-'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Card 
        className="mb-6" 
        title={
          <div className="flex items-center">
            <GlobalOutlined className="mr-2" />
            <span>发布状态</span>
          </div>
        }
      >
        {publishRecords.length > 0 ? (
          (() => {
            const record = publishRecords[0];
            const gateway = gateways.find(g => g.gatewayId === record.gatewayId);
            return (
              <Descriptions column={2}>
                <Descriptions.Item label="发布网关">
                  {gateway ? gateway.gatewayName : record.gatewayId}
                </Descriptions.Item>
                <Descriptions.Item label="发布版本">
                  {record.version}
                </Descriptions.Item>
                <Descriptions.Item label="发布域名">
                  {record.publishConfig?.domains?.map((d: string) => <Tag key={d}>{d}</Tag>) || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="后端服务">
                  {renderServiceConfig(record.publishConfig?.serviceConfig)}
                </Descriptions.Item>
                <Descriptions.Item label="当前状态" span={2}>
                  <Space>
                    {getStatusTag(record.status)}
                    {record.status === 'ACTIVE' && (
                      <Button 
                        type="link" 
                        danger 
                        size="small"
                        onClick={() => handleUnpublish(record.recordId)}
                        style={{ padding: 0 }}
                      >
                        下线
                      </Button>
                    )}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="发布时间">
                  {dayjs(record.publishTime).format('YYYY-MM-DD HH:mm:ss')}
                </Descriptions.Item>
              </Descriptions>
            );
          })()
        ) : (
          <div className="text-center text-gray-500 py-8">暂无发布信息</div>
        )}
      </Card>

      <Card 
        title={
          <div className="flex items-center">
            <HistoryOutlined className="mr-2" />
            <span>操作历史</span>
          </div>
        }
      >
        <Table
          columns={historyColumns}
          dataSource={publishHistory}
          rowKey="recordId"
          pagination={{ pageSize: 10 }}
          loading={loading}
        />
      </Card>

      <Modal
        title="发布 API"
        open={publishModalVisible}
        onOk={handlePublish}
        onCancel={() => setPublishModalVisible(false)}
        confirmLoading={publishing}
        width={600}
      >
        
        <Form form={form} layout="vertical" initialValues={{ serviceType: 'NACOS', tlsEnabled: false }}>
          <Form.Item
            name="gatewayId"
            label="选择网关"
            rules={[{ required: true, message: '请选择要发布的网关' }]}
          >
            <Select
              placeholder="请选择网关"
              loading={loading}
              onChange={handleGatewayChange}
              disabled={!!activeGatewayId}
            >
              {gateways.map(gateway => (
                <Select.Option key={gateway.gatewayId} value={gateway.gatewayId}>
                  {gateway.gatewayName} ({gateway.gatewayType})
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="ingressDomain"
            label="域名"
            rules={[{ required: true, message: '请选择域名' }]}
          >
            <Select placeholder="请选择域名" mode="multiple">
              {ingressDomains.map(domain => (
                <Select.Option key={domain} value={domain}>
                  {domain}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item name="serviceType" label="服务类型" rules={[{ required: true }]}>
            <Radio.Group>
              {serviceTypes.map(type => (
                <Radio key={type} value={type}>
                  {type === 'NACOS' ? 'Nacos' : 
                   type === 'FIXED_ADDRESS' ? '固定地址' : 
                   type === 'DNS' ? 'DNS' : 
                   type === 'AI_SERVICE' ? 'AI 服务' : type}
                </Radio>
              ))}
            </Radio.Group>
          </Form.Item>

          {serviceType === 'AI_SERVICE' && (
            <>
              <Form.Item name="protocol" label="模型协议" rules={[{ required: true }]} initialValue="openai/v1">
                 <Select placeholder="请选择模型协议">
                    <Select.Option value="openai/v1">openai/v1</Select.Option>
                    <Select.Option value="original">原生协议</Select.Option>
                 </Select>
              </Form.Item>
              <Form.Item name="provider" label="模型提供商" rules={[{ required: true }]}>
                 <Select placeholder="请选择模型提供商">
                    <Select.Option value="openai">OpenAI</Select.Option>
                    <Select.Option value="azure">Azure OpenAI</Select.Option>
                    <Select.Option value="qwen">DashScope (通义千问)</Select.Option>
                    <Select.Option value="moonshot">Moonshot (月之暗面)</Select.Option>
                    <Select.Option value="baichuan">Baichuan (百川)</Select.Option>
                    <Select.Option value="yi">Yi (零一万物)</Select.Option>
                    <Select.Option value="zhipuai">Zhipu AI (智谱)</Select.Option>
                    <Select.Option value="deepseek">DeepSeek</Select.Option>
                    <Select.Option value="groq">Groq</Select.Option>
                    <Select.Option value="grok">Grok</Select.Option>
                    <Select.Option value="openrouter">OpenRouter</Select.Option>
                    <Select.Option value="fireworks">Fireworks AI</Select.Option>
                    <Select.Option value="baidu">Baidu (文心一言)</Select.Option>
                    <Select.Option value="ai360">360 (智脑)</Select.Option>
                    <Select.Option value="github">GitHub</Select.Option>
                    <Select.Option value="mistral">Mistral</Select.Option>
                    <Select.Option value="minimax">MiniMax</Select.Option>
                    <Select.Option value="claude">Claude</Select.Option>
                    <Select.Option value="ollama">Ollama</Select.Option>
                    <Select.Option value="hunyuan">Hunyuan (混元)</Select.Option>
                    <Select.Option value="stepfun">Stepfun (阶跃星辰)</Select.Option>
                    <Select.Option value="cloudflare">Cloudflare</Select.Option>
                    <Select.Option value="spark">Spark (星火)</Select.Option>
                    <Select.Option value="gemini">Gemini</Select.Option>
                    <Select.Option value="deepl">DeepL</Select.Option>
                    <Select.Option value="cohere">Cohere</Select.Option>
                    <Select.Option value="together-ai">Together-AI</Select.Option>
                    <Select.Option value="dify">Dify</Select.Option>
                    <Select.Option value="vertex">Google Vertex AI</Select.Option>
                    <Select.Option value="bedrock">AWS Bedrock</Select.Option>
                    <Select.Option value="triton">NVIDIA Triton</Select.Option>
                    <Select.Option value="doubao">Doubao (豆包)</Select.Option>
                    <Select.Option value="coze">Coze (扣子)</Select.Option>
                    <Select.Option value="generic">Generic (通用代理)</Select.Option>
                 </Select>
              </Form.Item>
              
              {/* Provider Specific Fields */}
              {provider === 'openai' && (
                <>
                  <Form.Item name="openaiCustomUrl" label="自定义后端 URL">
                    <Input placeholder="例如: www.example.com/myai/v1/chat/completions" />
                  </Form.Item>
                  <Form.Item name="responseJsonSchema" label="响应 JSON Schema">
                    <TextArea rows={4} placeholder="JSON Schema" />
                  </Form.Item>
                </>
              )}

              {provider === 'azure' && (
                <Form.Item name="azureServiceUrl" label="Azure 服务 URL" rules={[{ required: true }]}>
                  <Input placeholder="https://YOUR_RESOURCE_NAME.openai.azure.com/..." />
                </Form.Item>
              )}

              {provider === 'moonshot' && (
                <Form.Item name="moonshotFileId" label="文件 ID">
                  <Input placeholder="上传至月之暗面的文件 ID" />
                </Form.Item>
              )}

              {provider === 'qwen' && (
                <>
                  <Form.Item name="qwenEnableSearch" valuePropName="checked">
                    <Checkbox>启用互联网搜索</Checkbox>
                  </Form.Item>
                  <Form.Item name="qwenEnableCompatible" valuePropName="checked">
                    <Checkbox>启用兼容模式</Checkbox>
                  </Form.Item>
                  <Form.Item name="reasoningContentMode" label="推理内容模式">
                    <Select placeholder="请选择推理内容模式">
                      <Select.Option value="passthrough">passthrough (正常输出)</Select.Option>
                      <Select.Option value="ignore">ignore (不输出)</Select.Option>
                      <Select.Option value="concat">concat (拼接在常规输出前)</Select.Option>
                    </Select>
                  </Form.Item>
                  <Form.Item name="qwenFileIds" label="文件 ID 列表">
                    <Select mode="tags" placeholder="输入文件 ID 后回车" />
                  </Form.Item>
                </>
              )}

              {provider === 'minimax' && (
                <>
                  <Form.Item 
                    noStyle 
                    shouldUpdate={(prev, curr) => prev.minimaxApiType !== curr.minimaxApiType}
                  >
                    {({ getFieldValue }) => 
                      getFieldValue('minimaxApiType') === 'pro' ? (
                        <Form.Item name="minimaxGroupId" label="Group ID" rules={[{ required: true }]}>
                          <Input />
                        </Form.Item>
                      ) : null
                    }
                  </Form.Item>
                  <Form.Item name="minimaxApiType" label="API 类型" initialValue="v2">
                    <Select>
                      <Select.Option value="v2">v2</Select.Option>
                      <Select.Option value="pro">pro</Select.Option>
                    </Select>
                  </Form.Item>
                </>
              )}

              {provider === 'claude' && (
                <Form.Item name="claudeVersion" label="Claude 版本" initialValue="2023-06-01">
                  <Input />
                </Form.Item>
              )}

              {provider === 'ollama' && (
                <>
                  <Form.Item name="ollamaServerHost" label="服务器主机" rules={[{ required: true }]}>
                    <Input placeholder="例如: 127.0.0.1" />
                  </Form.Item>
                  <Form.Item name="ollamaServerPort" label="服务器端口" rules={[{ required: true }]} initialValue={11434}>
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                </>
              )}

              {provider === 'hunyuan' && (
                <>
                  <Form.Item name="hunyuanAuthId" label="Auth ID" rules={[{ required: true }]}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="hunyuanAuthKey" label="Auth Key" rules={[{ required: true }]}>
                    <Input.Password />
                  </Form.Item>
                </>
              )}

              {provider === 'cloudflare' && (
                <Form.Item name="cloudflareAccountId" label="Account ID" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              )}

              {provider === 'gemini' && (
                <>
                  <Form.Item name="apiVersion" label="API 版本" initialValue="v1beta">
                    <Select>
                      <Select.Option value="v1">v1</Select.Option>
                      <Select.Option value="v1beta">v1beta</Select.Option>
                    </Select>
                  </Form.Item>
                  <Form.Item name="geminiThinkingBudget" label="思考预算">
                    <InputNumber style={{ width: '100%' }} placeholder="0: 不开启, -1: 动态调整" />
                  </Form.Item>
                </>
              )}

              {provider === 'deepl' && (
                <Form.Item name="targetLang" label="目标语言" rules={[{ required: true }]}>
                  <Input placeholder="例如: ZH" />
                </Form.Item>
              )}

              {provider === 'dify' && (
                <>
                  <Form.Item name="difyApiUrl" label="API URL">
                    <Input placeholder="私有化部署 URL" />
                  </Form.Item>
                  <Form.Item name="botType" label="应用类型">
                    <Select>
                      <Select.Option value="Chat">Chat</Select.Option>
                      <Select.Option value="Completion">Completion</Select.Option>
                      <Select.Option value="Agent">Agent</Select.Option>
                      <Select.Option value="Workflow">Workflow</Select.Option>
                    </Select>
                  </Form.Item>
                  <Form.Item 
                    noStyle 
                    shouldUpdate={(prev, curr) => prev.botType !== curr.botType}
                  >
                    {({ getFieldValue }) => 
                      getFieldValue('botType') === 'Workflow' ? (
                        <>
                          <Form.Item name="inputVariable" label="输入变量">
                            <Input />
                          </Form.Item>
                          <Form.Item name="outputVariable" label="输出变量">
                            <Input />
                          </Form.Item>
                        </>
                      ) : null
                    }
                  </Form.Item>
                </>
              )}

              {provider === 'vertex' && (
                <>
                  <Form.Item name="vertexRegion" label="区域" rules={[{ required: true }]}>
                    <Input placeholder="例如: us-central1" />
                  </Form.Item>
                  <Form.Item name="vertexProjectId" label="项目 ID" rules={[{ required: true }]}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="vertexAuthServiceName" label="Auth 服务名称" rules={[{ required: true }]}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="vertexAuthKey" label="Service Account Key" rules={[{ required: true }]}>
                    <TextArea rows={4} placeholder="JSON Key Content" />
                  </Form.Item>
                  <Form.Item name="vertexTokenRefreshAhead" label="Token 刷新提前时间(秒)">
                    <InputNumber style={{ width: '100%' }} />
                  </Form.Item>
                </>
              )}

              {provider === 'bedrock' && (
                <>
                  <Form.Item name="awsRegion" label="AWS 区域" rules={[{ required: true }]}>
                    <Input placeholder="例如: us-east-1" />
                  </Form.Item>
                  <Form.Item name="awsAccessKey" label="Access Key" rules={[{ required: true }]}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="awsSecretKey" label="Secret Key" rules={[{ required: true }]}>
                    <Input.Password />
                  </Form.Item>
                  <Form.Item name="bedrockAdditionalFields" label="额外模型请求参数">
                    <TextArea rows={4} placeholder="JSON 格式" />
                  </Form.Item>
                </>
              )}

              {provider === 'triton' && (
                <>
                  <Form.Item name="tritonDomain" label="Triton Domain">
                    <Input />
                  </Form.Item>
                  <Form.Item name="tritonModelVersion" label="Model Version">
                    <Input />
                  </Form.Item>
                </>
              )}

              {provider === 'generic' && (
                <Form.Item name="genericHost" label="目标 Host">
                  <Input placeholder="例如: api.example.com" />
                </Form.Item>
              )}

              <Form.Item name="apiKey" label="API Key" rules={[{ required: !['ollama', 'vertex', 'bedrock'].includes(provider) }]}>
                <Input.Password placeholder="请输入 API Key" />
              </Form.Item>

              <Collapse ghost>
                <Collapse.Panel header="高级设置" key="1">
                  <Form.Item name="timeout" label="超时时间 (ms)">
                    <InputNumber style={{ width: '100%' }} placeholder="默认 120000" />
                  </Form.Item>

                  <Form.Item name="subPath" label="Sub Path">
                    <Input placeholder="移除请求路径前缀" />
                  </Form.Item>

                  <Form.Item name="modelMapping" label="模型映射">
                    <TextArea rows={4} placeholder='JSON 格式, 例如: {"*": "my-model"}' />
                  </Form.Item>

                  <Form.Item name="context" label="上下文配置">
                    <TextArea rows={4} placeholder='JSON 格式, 例如: {"fileUrl": "...", "serviceName": "..."}' />
                  </Form.Item>
                </Collapse.Panel>
              </Collapse>
            </>
          )}

          {serviceType === 'NACOS' && (
            <>
              <Form.Item name="nacosId" label="Nacos 实例" rules={[{ required: true }]}>
                <Select 
                  placeholder="请选择 Nacos 实例"
                  onChange={handleNacosChange}
                >
                  {nacosInstances.map(nacos => (
                    <Select.Option key={nacos.nacosId} value={nacos.nacosId}>
                      {nacos.nacosName} ({nacos.serverUrl})
                    </Select.Option>
                  ))}
                </Select>
              </Form.Item>
              
              {nacosId && (
                <Form.Item name="namespace" label="命名空间" rules={[{ required: true }]}>
                  <Select placeholder="请选择命名空间">
                    {namespaces.map(ns => (
                      <Select.Option key={ns.namespaceId} value={ns.namespaceId}>
                        {ns.namespaceName} ({ns.namespaceId})
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>
              )}

              {nacosId && namespace && (
                <>
                  <Form.Item name="group" label="分组" rules={[{ required: true }]}>
                    <Input placeholder="请输入分组" />
                  </Form.Item>
                  <Form.Item name="serviceName" label="服务名称" rules={[{ required: true }]}>
                    <Input placeholder="请输入服务名称" />
                  </Form.Item>
                </>
              )}
            </>
          )}

          {serviceType === 'FIXED_ADDRESS' && (
            <Form.Item name="address" label="服务地址" rules={[{ required: true }]} help="多个地址用逗号分隔，例如: 127.0.0.1:8080,127.0.0.2:8080">
              <Input placeholder="请输入服务地址" />
            </Form.Item>
          )}

          {serviceType === 'DNS' && (
            <Form.Item name="domain" label="域名" rules={[{ required: true }]}>
              <Input placeholder="请输入域名" />
            </Form.Item>
          )}

          {apiDefinition?.type !== 'MODEL_API' && (
            <Form.Item name="tlsEnabled" valuePropName="checked">
              <Checkbox>开启 TLS</Checkbox>
            </Form.Item>
          )}

          <Form.Item
            name="comment"
            label="发布备注"
            rules={[{ max: 200, message: '备注不能超过200字' }]}
          >
            <TextArea rows={4} placeholder="请输入本次发布的备注信息（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="配置快照"
        open={snapshotModalVisible}
        onCancel={() => setSnapshotModalVisible(false)}
        footer={null}
        width={800}
      >
        {currentSnapshot ? (
          <div className="max-h-[600px] overflow-y-auto">
            <Descriptions title="基本信息" bordered column={2} size="small" className="mb-4">
              <Descriptions.Item label="名称">{currentSnapshot.name}</Descriptions.Item>
              <Descriptions.Item label="版本">{currentSnapshot.version}</Descriptions.Item>
              <Descriptions.Item label="类型" span={2}>{currentSnapshot.type}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>{currentSnapshot.description || '-'}</Descriptions.Item>
            </Descriptions>

            {currentSnapshot.endpoints && currentSnapshot.endpoints.length > 0 && (
              <div>
                <h3 className="text-base font-medium mb-2">Endpoints</h3>
                <div className="space-y-4">
                  {currentSnapshot.endpoints.map((endpoint: any, index: number) => (
                    <Card 
                      key={index} 
                      size="small" 
                      title={
                        <div className="flex items-center justify-between">
                          <span>{endpoint.name}</span>
                          <Tag>{endpoint.type}</Tag>
                        </div>
                      }
                    >
                      <div className="mb-2 text-gray-500">{endpoint.description}</div>
                      {endpoint.config && (
                        <div className="bg-gray-50 p-2 rounded border border-gray-200">
                          <pre className="text-xs overflow-x-auto whitespace-pre-wrap m-0">
                            {JSON.stringify(endpoint.config, null, 2)}
                          </pre>
                        </div>
                      )}
                    </Card>
                  ))}
                </div>
              </div>
            )}

            {currentSnapshot.properties && currentSnapshot.properties.length > 0 && (
              <div className="mt-4">
                <h3 className="text-base font-medium mb-2">API 属性</h3>
                <div className="space-y-4">
                  {currentSnapshot.properties.map((property: any, index: number) => (
                    <Card 
                      key={index} 
                      size="small" 
                      title={
                        <div className="flex items-center justify-between">
                          <span>{property.name || property.type}</span>
                          <Space>
                            <Tag>{property.type}</Tag>
                            {property.enabled !== undefined && (
                              <Tag color={property.enabled ? 'success' : 'default'}>
                                {property.enabled ? '已启用' : '已禁用'}
                              </Tag>
                            )}
                          </Space>
                        </div>
                      }
                    >
                      <div className="bg-gray-50 p-2 rounded border border-gray-200">
                        <pre className="text-xs overflow-x-auto whitespace-pre-wrap m-0">
                          {JSON.stringify(
                            Object.fromEntries(
                              Object.entries(property).filter(([key]) => 
                                !['type', 'name', 'enabled', 'priority', 'phase'].includes(key)
                              )
                            ), 
                            null, 
                            2
                          )}
                        </pre>
                      </div>
                    </Card>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="text-center text-gray-500 py-8">暂无快照信息</div>
        )}
      </Modal>

      <Modal
        title="配置对比 (左: 选定记录配置, 右: 当前发布配置)"
        open={diffModalVisible}
        onCancel={() => setDiffModalVisible(false)}
        footer={null}
        width={1000}
      >
        <div className="h-[600px]">
          <MonacoDiffEditor
            language="json"
            theme="vs-light"
            original={diffOriginal}
            value={diffModified}
            options={{
              readOnly: true,
              minimap: { enabled: false },
              scrollBeyondLastLine: false
            }}
          />
        </div>
      </Modal>
    </div>
  );
}
