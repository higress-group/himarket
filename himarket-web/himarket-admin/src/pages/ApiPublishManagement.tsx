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
  Collapse,
  Divider
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
  EyeOutlined,
  DownOutlined,
  UpOutlined
} from '@ant-design/icons';
import { apiDefinitionApi, gatewayApi, nacosApi } from '@/lib/api';
import type { Gateway, DomainResult } from '@/types/gateway';
import dayjs from 'dayjs';
import { MonacoDiffEditor } from 'react-monaco-editor';

const { TextArea } = Input;

// Provider to address mapping for AI services
const PROVIDER_ADDRESS_MAP: Record<string, string> = {
  'openai': 'https://api.openai.com/v1',
  'deepseek': 'https://api.deepseek.com/v1',
  'doubao': 'https://ark.cn-beijing.volces.com/api/v3',
  'gemini': 'https://generativelanguage.googleapis.com/v1beta/openai',
  'minimax': 'https://api.minimaxi.com/v1',
  'moonshot': 'https://api.moonshot.cn/v1',
  'zhipuai': 'https://open.bigmodel.cn/api/paas/v4',
  'claude': 'https://api.anthropic.com',
  'baichuan': 'https://api.baichuan-ai.com/v1',
  'yi': 'https://api.lingyiwanwu.com/v1',
  'hunyuan': 'https://hunyuan.tencentcloudapi.com',
  'stepfun': 'https://api.stepfun.com',
  'spark': 'https://spark-api-open.xf-yun.com',
  'qwen': 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  // Bedrock uses dynamic address based on region
  // 'bedrock': 'https://bedrock-runtime.{awsRegion}.amazonaws.com',
  // Azure uses azureServiceUrl instead
};

// 可展开文本组件
interface ExpandableTextProps {
  text: string;
  maxLength?: number;
  isError?: boolean;
}

const ExpandableText = ({ text, maxLength = 100, isError = false }: ExpandableTextProps) => {
  const [expanded, setExpanded] = useState(false);

  if (!text || text.length <= maxLength) {
    return (
      <span className={isError ? 'text-red-500' : 'text-green-500'}>
        {text || (isError ? '' : '成功')}
      </span>
    );
  }

  return (
    <div style={{ maxWidth: '100%' }}>
      <div className={isError ? 'text-red-500' : 'text-green-500'} style={{ wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
        {expanded ? text : `${text.substring(0, maxLength)}...`}
      </div>
      <Button
        type="link"
        size="small"
        icon={expanded ? <UpOutlined /> : <DownOutlined />}
        onClick={() => setExpanded(!expanded)}
        style={{ padding: 0, height: 'auto', marginTop: 4 }}
      >
        {expanded ? '收起' : '展开'}
      </Button>
    </div>
  );
};

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
  const [ingressDomains, setIngressDomains] = useState<DomainResult[]>([]);
  const [serviceTypes, setServiceTypes] = useState<string[]>(['NACOS', 'FIXED_ADDRESS', 'DNS', 'GATEWAY']);
  const [nacosInstances, setNacosInstances] = useState<any[]>([]);
  const [namespaces, setNamespaces] = useState<any[]>([]);
  const [gatewayServices, setGatewayServices] = useState<any[]>([]);
  const [loadingGatewayServices, setLoadingGatewayServices] = useState(false);
  const [loadingDomains, setLoadingDomains] = useState(false);
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
  const [pollingRecordId, setPollingRecordId] = useState<string | null>(null);
  const [pollingInterval, setPollingInterval] = useState<NodeJS.Timeout | null>(null);
  const [form] = Form.useForm();
  const serviceType = Form.useWatch('serviceType', form);
  const provider = Form.useWatch('provider', form);
  const nacosId = Form.useWatch('nacosId', form);
  const namespace = Form.useWatch('namespace', form);
  const selectedGatewayId = Form.useWatch('gatewayId', form);
  const selectedServiceId = Form.useWatch('serviceId', form);

  useEffect(() => {
    if (apiDefinitionId) {
      fetchData();
    }
  }, [apiDefinitionId]);

  // Cleanup polling interval on unmount
  useEffect(() => {
    return () => {
      if (pollingInterval) {
        clearInterval(pollingInterval);
      }
    };
  }, [pollingInterval]);

  // Start polling for record status
  const startPolling = (recordId: string) => {
    setPollingRecordId(recordId);

    const interval = setInterval(async () => {
      try {
        const response = await apiDefinitionApi.getPublishRecordStatus(apiDefinitionId!, recordId);
        const record = response.data || response;

        // Check if operation completed (not in progress states)
        if (record.status !== 'PUBLISHING' && record.status !== 'UNPUBLISHING') {
          // Stop polling
          clearInterval(interval);
          setPollingInterval(null);
          setPollingRecordId(null);

          // Refresh data to show updated status
          await fetchData();

          // Show success or error message
          if (record.status === 'ACTIVE') {
            message.success('发布成功');
          } else if (record.status === 'INACTIVE') {
            message.success('下线成功');
          } else if (record.status === 'FAILED') {
            message.error(`操作失败: ${record.errorMessage || '未知错误'}`);
          }
        }
      } catch (error) {
        console.error('Failed to poll record status:', error);
        // Continue polling even on error
      }
    }, 2000); // Poll every 2 seconds

    setPollingInterval(interval);
  };

  // Stop polling
  const stopPolling = () => {
    if (pollingInterval) {
      clearInterval(pollingInterval);
      setPollingInterval(null);
      setPollingRecordId(null);
    }
  };

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

      // Initialize service types based on API definition type
      const apiDefData = apiRes.data || apiRes;
      if (apiDefData?.type === 'MODEL_API') {
        setServiceTypes(['AI_SERVICE', 'GATEWAY']);
      }
    } catch (error) {
      console.error('Failed to fetch data:', error);
      message.error('获取数据失败');
    } finally {
      setLoading(false);
    }
  };

  const handleGatewayChange = async (gatewayId: string) => {
    setLoadingDomains(true);
    setLoadingGatewayServices(true);
    setGatewayServices([]); // Reset services when gateway changes
    try {
      const [domainsRes, serviceTypesRes, servicesRes] = await Promise.all([
        gatewayApi.getGatewayDomains(gatewayId),
        gatewayApi.getGatewayServiceTypes(gatewayId),
        gatewayApi.getGatewayServices(gatewayId)
      ]);

      const domains = domainsRes.data || domainsRes;
      setIngressDomains(Array.isArray(domains) ? domains : []);

      const services = servicesRes.data || servicesRes;
      setGatewayServices(Array.isArray(services) ? services : []);

      const types = serviceTypesRes.data || serviceTypesRes;
      let availableTypes = Array.isArray(types) ? types : [];

      // 如果是 Model API，只保留 AI_SERVICE 和 GATEWAY 选项
      if (apiDefinition?.type === 'MODEL_API') {
        availableTypes = ['AI_SERVICE'];
        form.setFieldValue('serviceType', 'AI_SERVICE');
      }

      // Update service types only if backend returns them, otherwise keep defaults
      if (availableTypes.length > 0) {
        setServiceTypes(availableTypes);
      }

      // Clear gateway services - will be fetched on dropdown open
      setGatewayServices([]);

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
      setGatewayServices([]);
      // Keep default service types on error
      setServiceTypes(['NACOS', 'FIXED_ADDRESS', 'DNS', 'GATEWAY']);
    } finally {
      setLoadingDomains(false);
      setLoadingGatewayServices(false);
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

  const handleGatewayServiceDropdownOpen = async () => {
    // Fetch gateway services when dropdown is opened
    const gatewayId = form.getFieldValue('gatewayId');
    if (!gatewayId) return;

    // Only fetch if not already loaded
    if (gatewayServices.length > 0) return;

    setLoadingGatewayServices(true);
    try {
      const servicesRes = await gatewayApi.getGatewayServices(gatewayId);
      const services = servicesRes.data || servicesRes;
      setGatewayServices(Array.isArray(services) ? services : []);
    } catch (error) {
      console.error('Failed to fetch gateway services:', error);
      message.error('获取网关服务列表失败');
      setGatewayServices([]);
    } finally {
      setLoadingGatewayServices(false);
    }
  };

  const handleOpenPublishModal = () => {
    // Set initial values based on API type
    const initialServiceType = apiDefinition?.type === 'MODEL_API' ? 'AI_SERVICE' : 'NACOS';
    form.setFieldsValue({ serviceType: initialServiceType });
    
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
      } else if (values.serviceType === 'GATEWAY') {
        serviceConfig = {
          ...serviceConfig,
          gatewayId: values.gatewayId,
          serviceId: values.serviceId,
          serviceName: values.serviceName,
        };
      } else if (values.serviceType === 'AI_SERVICE') {
        // Transform protocol value: openai/v1 -> OpenAI/v1
        const transformedProtocol = values.protocol === 'openai/v1' ? 'OpenAI/v1' : values.protocol;
        
        // Determine address based on provider
        let address = values.address;
        if (!address && values.provider && PROVIDER_ADDRESS_MAP[values.provider]) {
          address = PROVIDER_ADDRESS_MAP[values.provider];
        }
        
        // For Bedrock, construct the address dynamically based on awsRegion
        if (values.provider === 'bedrock' && values.awsRegion) {
          address = `https://bedrock-runtime.${values.awsRegion}.amazonaws.com`;
        }
        
        serviceConfig = {
          ...serviceConfig,
          provider: values.provider,
          protocol: transformedProtocol,
          address: address,
          apiKey: values.apiKey,
          // Provider specific configs - Only for Azure, Bedrock, and Vertex AI
          azureServiceUrl: values.azureServiceUrl,
          vertexAuthKey: values.vertexAuthKey,
          vertexRegion: values.vertexRegion,
          vertexProjectId: values.vertexProjectId,
          vertexAuthServiceName: values.vertexAuthServiceName,
          awsAccessKey: values.awsAccessKey,
          awsSecretKey: values.awsSecretKey,
          awsRegion: values.awsRegion,
          bedrockAuthType: values.bedrockAuthType,
        };
        // Remove undefined keys
        Object.keys(serviceConfig).forEach(key => serviceConfig[key] === undefined && delete serviceConfig[key]);
      }

      const selectedDomains = Array.isArray(values.ingressDomain) ? values.ingressDomain : (values.ingressDomain ? [values.ingressDomain] : []);
      const domainObjects = selectedDomains.map((domainStr: string) => {
        const found = ingressDomains.find(d => d.domain === domainStr);
        return found ? {
          domain: found.domain,
          port: found.port,
          protocol: found.protocol,
          networkType: found.networkType
        } : { domain: domainStr };
      });

      const publishData = {
        apiDefinitionId,
        gatewayId: values.gatewayId,
        comment: values.comment,
        publishConfig: {
          serviceConfig: {
            ...serviceConfig,
            meta: {
              ...(serviceConfig.meta || {}),
              ...(values.mcpProtocol ? { mcpProtocol: values.mcpProtocol } : {}),
              ...(values.mcpPath ? { mcpPath: values.mcpPath } : {})
            }
          },
          domains: domainObjects,
          basePath: values.basePath || '/'
        }
      };

      const response = await apiDefinitionApi.publishApi(apiDefinitionId!, publishData);
      const publishRecord = response.data || response;

      message.success('发布请求已提交，正在处理中...');
      setPublishModalVisible(false);
      form.resetFields();

      // Start polling for status
      if (publishRecord.recordId) {
        startPolling(publishRecord.recordId);
      }

      // Immediately refresh data to show PUBLISHING status
      await fetchData();
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
          const response = await apiDefinitionApi.unpublishApi(apiDefinitionId!, recordId);
          message.success('下线请求已提交，正在处理中...');

          // Immediately refresh data to show UNPUBLISHING status
          await fetchData();

          // The unpublish API returns the new unpublish record
          // We need to poll for that record's status
          // However, the backend doesn't return the new recordId in unpublish response
          // So we'll fetch data and find the UNPUBLISHING record
          const recordsRes = await apiDefinitionApi.getPublishRecords(apiDefinitionId!, { page: 1, size: 100 });
          const allRecords = recordsRes.data?.content || recordsRes.content || [];
          const unpublishingRecord = allRecords.find((r: any) => r.status === 'UNPUBLISHING');

          if (unpublishingRecord?.recordId) {
            startPolling(unpublishingRecord.recordId);
          }
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
      case 'PUBLISHING':
        return <Tag icon={<SyncOutlined spin />} color="processing">发布中</Tag>;
      case 'UNPUBLISHING':
        return <Tag icon={<SyncOutlined spin />} color="processing">下线中</Tag>;
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
      case 'GATEWAY':
        return (
          <div className="text-sm">
            <div style={{ marginBottom: '8px' }}>
              <Tag color="green">网关服务</Tag>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>服务名:</span>
              <span className="font-medium">{config.serviceName}</span>
            </div>
            <div style={itemStyle}>
              <span style={labelStyle}>服务ID:</span>
              <span>{config.serviceId}</span>
            </div>
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
      render: (note: string) => note ? <ExpandableText text={note} maxLength={50} /> : '-'
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
      width: 400,
      render: (errorMessage: string) => <ExpandableText text={errorMessage} maxLength={50} isError={!!errorMessage} />
    },
    {
      title: '操作',
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
                  {record.publishConfig?.domains?.map((d: any) => {
                    const domainStr = typeof d === 'string' ? d : d.domain;
                    return <Tag key={domainStr}>{domainStr}</Tag>;
                  }) || '-'}
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

        <Form form={form} layout="vertical" initialValues={{ 
          serviceType: apiDefinition?.type === 'MODEL_API' ? 'AI_SERVICE' : 'NACOS', 
          tlsEnabled: false 
        }}>
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
          >
            <Select placeholder="请选择域名" mode="multiple" loading={loadingDomains} notFoundContent={loadingDomains ? '加载中...' : '暂无数据'}>
              {ingressDomains.map(item => (
                <Select.Option key={item.domain} value={item.domain}>
                  {item.protocol ? `${item.protocol}://${item.domain}` : item.domain}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          {/* Base Path for Model API */}
          {apiDefinition?.type === 'MODEL_API' && (
            <Form.Item
              name="basePath"
              label="Base Path"
              rules={[
                { required: true, message: '请输入 Base Path' },
                { pattern: /^\/.*/, message: 'Base Path 必须以 / 开头' }
              ]}
              initialValue="/"
              tooltip="Model API 的基础路径，必须以 / 开头"
            >
              <Input placeholder="例如: /v1 or /api/v1" />
            </Form.Item>
          )}

          <Divider style={{ fontSize: '16px', fontWeight: 600, marginTop: 24, marginBottom: 16 }}>
            服务配置
          </Divider>

          <Form.Item name="serviceSource" label="服务来源" rules={[{ required: true }]} initialValue="NEW">
            <Radio.Group
              onChange={(e) => {
                // Reset serviceType when switching source
                if (e.target.value === 'NEW') {
                  // For Model API, default to AI_SERVICE; otherwise default to NACOS
                  const defaultServiceType = apiDefinition?.type === 'MODEL_API' ? 'AI_SERVICE' : 'NACOS';
                  form.setFieldValue('serviceType', defaultServiceType);
                } else {
                  form.setFieldValue('serviceType', 'GATEWAY');
                }
              }}
            >
              <Radio value="NEW">新建服务</Radio>
              <Radio value="EXISTING">使用已有服务</Radio>
            </Radio.Group>
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.serviceSource !== curr.serviceSource}
          >
            {({ getFieldValue }) => {
              const source = getFieldValue('serviceSource');
              const newServiceTypes = serviceTypes.filter(t => t !== 'GATEWAY');

              // When using existing service (GATEWAY), render a hidden input
              // to ensure the value is included in form submission
              if (source === 'EXISTING') {
                return (
                  <Form.Item name="serviceType" hidden>
                    <Input />
                  </Form.Item>
                );
              }

              return (
                <Form.Item name="serviceType" label="服务类型" rules={[{ required: true }]}>
                  <Radio.Group>
                    {newServiceTypes.map(type => (
                      <Radio key={type} value={type}>
                        {type === 'NACOS' ? 'Nacos' :
                          type === 'FIXED_ADDRESS' ? '固定地址' :
                            type === 'DNS' ? 'DNS' :
                              type === 'AI_SERVICE' ? 'AI 服务' : type}
                      </Radio>
                    ))}
                  </Radio.Group>
                </Form.Item>
              );
            }}
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
                <Select 
                  placeholder="请选择模型提供商"
                  onChange={(value) => {
                    // Auto-fill address based on provider
                    if (PROVIDER_ADDRESS_MAP[value]) {
                      form.setFieldValue('address', PROVIDER_ADDRESS_MAP[value]);
                    }
                  }}
                >
                  <Select.Option value="qwen">阿里云百炼</Select.Option>
                  <Select.Option value="bedrock">Bedrock</Select.Option>
                  <Select.Option value="vertex">Vertex AI</Select.Option>
                  <Select.Option value="azure">Azure</Select.Option>
                  <Select.Option value="openai">OpenAI</Select.Option>
                  <Select.Option value="generic">OpenAI兼容（OpenAI Compatible）</Select.Option>
                  <Select.Option value="deepseek">DeepSeek</Select.Option>
                  <Select.Option value="doubao">豆包</Select.Option>
                  <Select.Option value="gemini">Gemini</Select.Option>
                  <Select.Option value="minimax">MiniMax</Select.Option>
                  <Select.Option value="moonshot">月之暗面</Select.Option>
                  <Select.Option value="zhipuai">智谱AI</Select.Option>
                  <Select.Option value="claude">Claude</Select.Option>
                  <Select.Option value="baichuan">百川智能</Select.Option>
                  <Select.Option value="yi">零一万物</Select.Option>
                  <Select.Option value="hunyuan">混元</Select.Option>
                  <Select.Option value="stepfun">阶跃星辰</Select.Option>
                  <Select.Option value="spark">星火</Select.Option>
                </Select>
              </Form.Item>

              {/* Provider Specific Fields - Only Azure, Bedrock, Vertex AI, and PAI-EAS */}
              
              {/* Address field - required for most providers, auto-filled based on provider selection */}
              <Form.Item
                noStyle
                shouldUpdate={(prev, curr) => prev.provider !== curr.provider}
              >
                {({ getFieldValue }) => {
                  const currentProvider = getFieldValue('provider');
                  // Hide address field for: vertex (uses separate configuration), azure (uses azureServiceUrl instead)
                  if (currentProvider === 'vertex' || currentProvider === 'azure') {
                    return null;
                  }
                  
                  return (
                    <Form.Item 
                      name="address" 
                      label="服务地址" 
                      rules={[{ required: true, message: '请输入服务地址' }]}
                      tooltip="模型服务的 API 地址"
                    >
                      <Input placeholder="请输入服务地址" />
                    </Form.Item>
                  );
                }}
              </Form.Item>

              {provider === 'azure' && (
                <Form.Item name="azureServiceUrl" label="Azure 服务 URL" rules={[{ required: true }]}>
                  <Input placeholder="https://YOUR_RESOURCE_NAME.openai.azure.com/..." />
                </Form.Item>
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
                </>
              )}

              {provider === 'bedrock' && (
                <>
                  <Form.Item name="awsRegion" label="awsRegion" rules={[{ required: true, message: '请输入 AWS 区域' }]}>
                    <Input placeholder="例如: us-east-1" />
                  </Form.Item>
                  
                  <Form.Item name="bedrockAuthType" label="认证方式" initialValue="AK_SK">
                    <Radio.Group>
                      <Radio value="API_KEY">API Key</Radio>
                      <Radio value="AK_SK">AK/SK</Radio>
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item
                    noStyle
                    shouldUpdate={(prev, curr) => prev.bedrockAuthType !== curr.bedrockAuthType}
                  >
                    {({ getFieldValue }) => {
                      const authType = getFieldValue('bedrockAuthType');
                      
                      if (authType === 'AK_SK') {
                        return (
                          <>
                            <Form.Item name="awsAccessKey" label="awsAccessKey" rules={[{ required: true, message: '请输入 AWS Access Key' }]}>
                              <Input placeholder="请输入 AWS Access Key" />
                            </Form.Item>
                            <Form.Item name="awsSecretKey" label="awsSecretKey" rules={[{ required: true, message: '请输入 AWS Secret Key' }]}>
                              <Input.Password placeholder="请输入 AWS Secret Key" />
                            </Form.Item>
                          </>
                        );
                      }
                      
                      // For API_KEY auth type, the common apiKey field below will be used
                      return null;
                    }}
                  </Form.Item>
                </>
              )}

              <Form.Item
                noStyle
                shouldUpdate={(prev, curr) => prev.provider !== curr.provider || prev.bedrockAuthType !== curr.bedrockAuthType}
              >
                {({ getFieldValue }) => {
                  const currentProvider = getFieldValue('provider');
                  const bedrockAuth = getFieldValue('bedrockAuthType');
                  
                  // Hide API Key field for: vertex, bedrock with AK_SK auth
                  const providersWithoutApiKey = ['vertex'];
                  const isBedrockWithAKSK = currentProvider === 'bedrock' && bedrockAuth === 'AK_SK';
                  
                  if (providersWithoutApiKey.includes(currentProvider) || isBedrockWithAKSK) {
                    return null;
                  }
                  
                  // API Key is required for all other providers
                  return (
                    <Form.Item 
                      name="apiKey" 
                      label="API Key" 
                      rules={[{ required: true, message: '请输入 API Key' }]}
                    >
                      <Input.Password placeholder="请输入 API Key" />
                    </Form.Item>
                  );
                }}
              </Form.Item>


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

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.serviceType !== curr.serviceType || prev.gatewayId !== curr.gatewayId}
          >
            {({ getFieldValue }) => {
              const currentServiceType = getFieldValue('serviceType');
              const currentGatewayId = getFieldValue('gatewayId');
              if (currentServiceType !== 'GATEWAY' || !currentGatewayId) return null;

              return (
                <Form.Item name="serviceId" label="网关服务" rules={[{ required: true }]}>
                  <Select
                    placeholder="请选择网关服务"
                    onFocus={handleGatewayServiceDropdownOpen}
                    loading={loadingGatewayServices}
                    notFoundContent={loadingGatewayServices ? '加载中...' : '暂无数据'}
                    onChange={(value) => {
                      const selected = gatewayServices.find(s => s.serviceId === value);
                      if (selected) {
                        form.setFieldValue('serviceName', selected.serviceName);
                        // Set TLS enabled based on the service's tlsEnabled property
                        form.setFieldValue('tlsEnabled', selected.tlsEnabled || false);
                      }
                    }}
                  >
                    {gatewayServices.map(service => (
                      <Select.Option key={service.serviceId} value={service.serviceId}>
                        {service.serviceName} ({service.serviceId})
                      </Select.Option>
                    ))}
                  </Select>
                </Form.Item>
              );
            }}
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.serviceType !== curr.serviceType}
          >
            {({ getFieldValue }) => {
              const currentServiceType = getFieldValue('serviceType');
              if (currentServiceType !== 'GATEWAY') return null;
              return (
                <Form.Item name="serviceName" hidden>
                  <Input />
                </Form.Item>
              );
            }}
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.serviceType !== curr.serviceType}
          >
            {({ getFieldValue }) => {
              const currentServiceType = getFieldValue('serviceType');
              if (apiDefinition?.type === 'MODEL_API' || currentServiceType === 'GATEWAY') {
                return null;
              }
              return (
                <Form.Item name="tlsEnabled" valuePropName="checked">
                  <Checkbox>后端服务是否使用 TLS</Checkbox>
                </Form.Item>
              );
            }}
          </Form.Item>

          {/* Conditional usage of mcpProtocol and mcpPath for DIRECT MCP Servers - Moved before comment */}
          {apiDefinition?.metadata?.mcpBridgeType === 'DIRECT' && (
            <>
              <Form.Item
                name="mcpProtocol"
                label="MCP 协议"
                rules={[{ required: true, message: '请选择 MCP 协议' }]}
                initialValue="SSE"
              >
                <Select placeholder="请选择 MCP 协议">
                  <Select.Option value="SSE">SSE</Select.Option>
                  <Select.Option value="HTTP">HTTP</Select.Option>
                </Select>
              </Form.Item>

              <Form.Item
                name="mcpPath"
                label="MCP 后端路径"
                rules={[{ required: true, message: '请输入 MCP 后端路径' }]}
                tooltip="后端服务暴露的 MCP 路径 (例如: /sse)"
              >
                <Input placeholder="请输入 MCP 后端路径" />
              </Form.Item>
            </>
          )}

          <Divider style={{ marginTop: 24, marginBottom: 16 }} />

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

            {currentSnapshot.publishConfig && (
              <div className="mt-4">
                <h3 className="text-base font-medium mb-2">发布配置</h3>
                <div className="bg-gray-50 p-2 rounded border border-gray-200">
                  <pre className="text-xs overflow-x-auto whitespace-pre-wrap m-0">
                    {JSON.stringify(currentSnapshot.publishConfig, null, 2)}
                  </pre>
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
