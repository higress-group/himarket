import { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  message,
  Space,
  Tag,
  Popconfirm,
  Dropdown,
  Upload,
  Radio,
  Checkbox
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps, UploadProps } from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  DragOutlined,
  DownOutlined,
  UploadOutlined,
  FileTextOutlined,
  FormOutlined
} from '@ant-design/icons';
import type { Endpoint, EndpointType } from '@/types/endpoint';
import EndpointConfigForm from './EndpointConfigForm';
import { apiDefinitionApi, nacosApi } from '@/lib/api';

const { TextArea } = Input;

const DIFY_ENDPOINTS: Endpoint[] = [
  {
    name: 'chat-messages',
    description: '发送对话消息',
    type: 'AGENT',
    config: {
      matchConfig: {
        path: { type: 'Exact', value: '/chat-messages' },
        methods: ['POST']
      },
      headers: [
        {
          name: 'Authorization',
          description: 'Bearer {api_key}',
          required: true
        }
      ],
      requestBody: JSON.stringify({
        type: 'object',
        properties: {
          query: { type: 'string', description: '用户输入/提问内容' },
          inputs: { type: 'object', description: '允许传入 App 定义的各变量值', default: {} },
          response_mode: { type: 'string', enum: ['streaming', 'blocking'], description: '响应模式' },
          user: { type: 'string', description: '用户标识' },
          conversation_id: { type: 'string', description: '会话 ID' },
          files: { type: 'array', items: { type: 'object' }, description: '文件列表' },
          auto_generate_name: { type: 'boolean', default: true, description: '自动生成标题' },
          workflow_id: { type: 'string', description: '工作流ID' }
        },
        required: ['query', 'user']
      }, null, 2),
      responses: {
        '200': {
          description: '成功响应',
          schema: {
            type: 'object',
            properties: {
              event: { type: 'string', description: '事件类型' },
              task_id: { type: 'string', description: '任务 ID' },
              id: { type: 'string', description: '消息 ID' },
              answer: { type: 'string', description: '回答内容' },
              created_at: { type: 'integer', description: '创建时间' }
            }
          }
        }
      }
    }
  },
  {
    name: 'run-workflow',
    description: '执行工作流',
    type: 'AGENT',
    config: {
      matchConfig: {
        path: { type: 'Exact', value: '/workflows/run' },
        methods: ['POST']
      },
      headers: [
        {
          name: 'Authorization',
          description: 'Bearer {api_key}',
          required: true
        }
      ],
      requestBody: JSON.stringify({
        type: 'object',
        properties: {
          inputs: { type: 'object', description: '工作流变量输入', default: {} },
          response_mode: { type: 'string', enum: ['streaming', 'blocking'], description: '响应模式' },
          user: { type: 'string', description: '用户标识' },
          files: { type: 'array', items: { type: 'object' }, description: '文件列表' }
        },
        required: ['inputs', 'response_mode', 'user']
      }, null, 2),
      responses: {
        '200': {
          description: '成功响应',
          schema: {
            type: 'object',
            properties: {
              workflow_run_id: { type: 'string', description: '工作流运行 ID' },
              task_id: { type: 'string', description: '任务 ID' },
              data: { type: 'object', description: '输出数据' },
              status: { type: 'string', description: '运行状态' }
            }
          }
        }
      }
    }
  }
];

const OPENAI_TEMPLATES = [
  { label: 'Chat Completions', value: '/v1/chat/completions', method: 'POST' },
  { label: 'Completions', value: '/v1/completions', method: 'POST' },
  { label: 'Responses', value: '/v1/responses', method: 'POST' },
];

const ANTHROPIC_TEMPLATES = [
  { label: 'Messages', value: '/v1/messages', method: 'POST' },
];

const DOUBAO_TEMPLATES = [
  { label: 'Chat Completions', value: '/api/v3/chat/completions', method: 'POST' },
  { label: 'Create Response', value: '/api/v3/responses', method: 'POST' },
  { label: 'Get Response', value: '/api/v3/responses/{response_id}', method: 'GET' },
  { label: 'Get Response Input Items', value: '/api/v3/responses/{response_id}/input_items', method: 'GET' },
  { label: 'Delete Response', value: '/api/v3/responses/{response_id}', method: 'DELETE' },
];

interface EndpointEditorProps {
  value?: Endpoint[];
  onChange?: (value: Endpoint[]) => void;
  disabled?: boolean;
  apiType?: string; // API Definition 的类型
  apiName?: string; // API Definition 的名称
  protocol?: string; // API 协议
}

// Endpoint 类型选项
const ENDPOINT_TYPE_OPTIONS = [
  { label: 'MCP Tool', value: 'MCP_TOOL' },
  { label: 'REST Route', value: 'REST_ROUTE' },
  { label: 'Agent', value: 'AGENT' },
  { label: 'Model', value: 'MODEL' }
];

// Endpoint 类型颜色映射
const ENDPOINT_TYPE_COLOR_MAP: Record<EndpointType, string> = {
  MCP_TOOL: 'purple',
  REST_ROUTE: 'blue',
  AGENT: 'green',
  MODEL: 'orange',
  HTTP: 'cyan'
};

// API 类型到 Endpoint 类型的映射
const API_TYPE_TO_ENDPOINT_TYPE: Record<string, EndpointType> = {
  REST_API: 'REST_ROUTE',
  MCP_SERVER: 'MCP_TOOL',
  AGENT_API: 'AGENT',
  MODEL_API: 'MODEL'
};

// 根据 API 类型获取术语
const getTerminology = (apiType?: string) => {
  if (apiType === 'MCP_SERVER') {
    return {
      singular: 'Tool',
      plural: 'Tools',
      singularLower: 'tool',
      pluralLower: 'tools'
    };
  }
  if (apiType === 'MODEL_API') {
    return {
      singular: 'Model Route',
      plural: 'Model Routes',
      singularLower: 'model route',
      pluralLower: 'model routes'
    };
  }
  return {
    singular: 'Endpoint',
    plural: 'Endpoints',
    singularLower: 'endpoint',
    pluralLower: 'endpoints'
  };
};

export default function EndpointEditor({
  value = [],
  onChange,
  disabled = false,
  apiType,
  apiName,
  protocol
}: EndpointEditorProps) {
  // 根据 API 类型推断 Endpoint 类型
  const inferredEndpointType = apiType ? API_TYPE_TO_ENDPOINT_TYPE[apiType] : undefined;
  // 获取显示术语
  const terminology = getTerminology(apiType);
  
  const [modalVisible, setModalVisible] = useState(false);
  const [editingEndpoint, setEditingEndpoint] = useState<Endpoint | null>(null);
  const [selectedType, setSelectedType] = useState<EndpointType | undefined>();
  const [currentConfig, setCurrentConfig] = useState<any>({});
  const [mcpProtocol, setMcpProtocol] = useState<'sse' | 'http'>('sse');
  const [form] = Form.useForm();

  // Swagger Import State
  const [swaggerContent, setSwaggerContent] = useState('');
  const [importLoading, setImportLoading] = useState(false);

  // Creation Method Selection State
  const [creationMethod, setCreationMethod] = useState<'MANUAL' | 'SWAGGER' | 'NACOS' | 'MCP_SERVER' | 'TEMPLATE'>('MANUAL');

  // Template Import State
  const [selectedTemplateEndpoints, setSelectedTemplateEndpoints] = useState<string[]>([]);

  // Nacos Import State
  const [nacosInstances, setNacosInstances] = useState<any[]>([]);
  const [nacosNamespaces, setNacosNamespaces] = useState<any[]>([]);
  const [nacosServices, setNacosServices] = useState<any[]>([]);
  const [selectedNacosId, setSelectedNacosId] = useState<string>();
  const [selectedNamespaceId, setSelectedNamespaceId] = useState<string>();
  const [selectedMcpService, setSelectedMcpService] = useState<string>();

  // MCP Server Import State
  const [mcpEndpoint, setMcpEndpoint] = useState('');
  const [mcpToken, setMcpToken] = useState('');

  useEffect(() => {
    if (modalVisible && apiType === 'MODEL_API') {
      form.setFieldValue('name', apiName);
    }
  }, [apiName, modalVisible, apiType, form]);

  useEffect(() => {
    if (creationMethod === 'NACOS') {
      fetchNacosInstances();
    }
  }, [creationMethod]);

  const fetchNacosInstances = async () => {
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 1000 });
      setNacosInstances((res.data?.content || []).map((n: any) => ({
        value: n.nacosId,
        label: n.nacosName
      })));
    } catch (error) {
      console.error('Failed to fetch Nacos instances:', error);
    }
  };

  const handleNacosChange = async (nacosId: string) => {
    setSelectedNacosId(nacosId);
    setSelectedNamespaceId(undefined);
    setSelectedMcpService(undefined);
    setNacosNamespaces([]);
    setNacosServices([]);
    
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
      setNacosNamespaces((res.data?.content || []).map((ns: any) => ({
        value: ns.namespaceId,
        label: ns.namespaceName || ns.namespaceId
      })));
    } catch (error) {
      console.error('Failed to fetch namespaces:', error);
    }
  };

  const handleNamespaceChange = async (namespaceId: string) => {
    setSelectedNamespaceId(namespaceId);
    setSelectedMcpService(undefined);
    setNacosServices([]);

    if (!selectedNacosId) return;

    try {
      const res = await nacosApi.getNacosMcpServers(selectedNacosId, {
        page: 1,
        size: 1000,
        namespaceId
      });
      setNacosServices((res.data?.content || []).map((srv: any) => ({
        value: srv.mcpServerName,
        label: srv.mcpServerName
      })));
    } catch (error) {
      console.error('Failed to fetch MCP services:', error);
    }
  };

  const handleAddClick = () => {
    setEditingEndpoint(null);
    const initialMethod = apiType === 'MCP_SERVER' ? 'SWAGGER' : 'MANUAL';
    setCreationMethod(initialMethod);
    setSelectedTemplateEndpoints([]);
    // 自动设置推断出的类型
    setSelectedType(inferredEndpointType);
    setCurrentConfig({});
    
    if (initialMethod === 'MANUAL') {
      form.resetFields();
      // 如果有推断的类型，自动设置到表单
      if (inferredEndpointType) {
        form.setFieldsValue({ 
          type: inferredEndpointType,
          name: apiType === 'MODEL_API' ? apiName : undefined
        });
      }
    }
    setModalVisible(true);
  };

  const handleEdit = (endpoint: Endpoint) => {
    setEditingEndpoint(endpoint);
    setCreationMethod('MANUAL');
    setSelectedType(endpoint.type);
    setCurrentConfig(endpoint.config || {});
    form.setFieldsValue({
      name: apiType === 'MODEL_API' ? apiName : endpoint.name,
      description: endpoint.description,
      type: endpoint.type
    });
    setModalVisible(true);
  };

  const handleDelete = (index: number) => {
    const newEndpoints = [...value];
    newEndpoints.splice(index, 1);
    // 重新计算 sortOrder
    newEndpoints.forEach((ep, idx) => {
      ep.sortOrder = idx;
    });
    onChange?.(newEndpoints);
    message.success(`${terminology.singular} 删除成功`);
  };

  const handleModalOk = async () => {
    if (creationMethod === 'TEMPLATE') {
      let endpointsToAdd: Endpoint[] = [];
      
      if (apiType === 'AGENT_API') {
        endpointsToAdd = DIFY_ENDPOINTS.filter(ep => selectedTemplateEndpoints.includes(ep.name));
      } else if (apiType === 'MODEL_API') {
        const templates = protocol === 'openai' ? OPENAI_TEMPLATES :
                          protocol === 'anthropic' ? ANTHROPIC_TEMPLATES :
                          protocol === 'doubao' ? DOUBAO_TEMPLATES : [];
        
        endpointsToAdd = templates
          .filter(t => selectedTemplateEndpoints.includes(t.label))
          .map(t => ({
            name: t.label,
            description: t.value,
            type: 'MODEL',
            config: {
              matchConfig: {
                path: { type: 'Exact', value: t.value },
                methods: [t.method]
              }
            }
          }));
      }

      if (endpointsToAdd.length === 0) {
        message.warning('请至少选择一个 Endpoint');
        return;
      }
      
      const newEndpointsToAdd = endpointsToAdd.map(ep => {
        const newEp = JSON.parse(JSON.stringify(ep));
        if (newEp.config && newEp.config.requestBody && typeof newEp.config.requestBody === 'string') {
             try {
                 newEp.config.requestBody = JSON.parse(newEp.config.requestBody);
             } catch (e) {
                 console.error('Failed to parse requestBody', e);
             }
        }
        return newEp;
      });

      const updatedValue = [...value, ...newEndpointsToAdd];
      onChange?.(updatedValue);
      setModalVisible(false);
      message.success(`成功添加 ${newEndpointsToAdd.length} 个 Endpoints`);
      return;
    }

    if (creationMethod === 'NACOS' && !editingEndpoint) {
      if (!selectedNacosId || !selectedNamespaceId || !selectedMcpService) {
        message.warning('请完整选择 Nacos 配置');
        return;
      }
      setImportLoading(true);
      try {
        const res: any = await apiDefinitionApi.importFromNacos({
          nacosId: selectedNacosId,
          namespaceId: selectedNamespaceId,
          mcpServerName: selectedMcpService
        });
        
        const data = res && res.data ? res.data : res;
        
        if (data && data.endpoints && Array.isArray(data.endpoints)) {
          const newEndpoints = data.endpoints.map((ep: any) => {
            if (ep.config && typeof ep.config === 'string') {
              try {
                ep.config = JSON.parse(ep.config);
              } catch (e) {
                console.error('Failed to parse endpoint config:', e);
              }
            }
            return ep;
          });
          
          const updatedValue = [...value, ...newEndpoints];
          onChange?.(updatedValue);
          
          message.success(`成功导入 ${newEndpoints.length} 个 ${terminology.pluralLower}`);
          setModalVisible(false);
        } else {
          message.warning('未解析到任何 Endpoints');
        }
      } catch (error) {
        console.error('Nacos 导入失败', error);
        message.error('导入失败');
      } finally {
        setImportLoading(false);
      }
      return;
    }

    if (creationMethod === 'MCP_SERVER' && !editingEndpoint) {
      if (!mcpEndpoint) {
        message.warning('请输入 MCP Server Endpoint');
        return;
      }
      setImportLoading(true);
      try {
        const res: any = await apiDefinitionApi.importFromMcpServer({
          endpoint: mcpEndpoint,
          token: mcpToken,
          type: mcpProtocol
        });
        
        const data = res && res.data ? res.data : res;
        
        if (data && data.endpoints && Array.isArray(data.endpoints)) {
          const newEndpoints = data.endpoints.map((ep: any) => {
            if (ep.config && typeof ep.config === 'string') {
              try {
                ep.config = JSON.parse(ep.config);
              } catch (e) {
                console.error('Failed to parse endpoint config:', e);
              }
            }
            return ep;
          });
          
          const updatedValue = [...value, ...newEndpoints];
          onChange?.(updatedValue);
          
          message.success(`成功导入 ${newEndpoints.length} 个 ${terminology.pluralLower}`);
          setModalVisible(false);
        } else {
          message.warning('未解析到任何 Endpoints');
        }
      } catch (error) {
        console.error('MCP Server 导入失败', error);
        message.error('导入失败');
      } finally {
        setImportLoading(false);
      }
      return;
    }

    if (creationMethod === 'SWAGGER' && !editingEndpoint) {
      // Swagger 导入逻辑
      if (!swaggerContent) {
        message.warning('请先上传 Swagger 内容');
        return;
      }

      setImportLoading(true);
      try {
        const res: any = await apiDefinitionApi.convertSwagger({
          swaggerContent,
          name: 'temp',
          version: '1.0.0',
          type: apiType === 'MCP_SERVER' ? 'MCP_SERVER' : 'REST_API'
        });
        
        const data = res && res.data ? res.data : res;
        
        if (data && data.endpoints && Array.isArray(data.endpoints)) {
          const newEndpoints = data.endpoints.map((ep: any) => {
            // Parse config string if it exists
            if (ep.config && typeof ep.config === 'string') {
              try {
                ep.config = JSON.parse(ep.config);
              } catch (e) {
                console.error('Failed to parse endpoint config:', e);
              }
            }
            return ep;
          });
          
          // Append new endpoints to existing ones
          const updatedValue = [...value, ...newEndpoints];
          onChange?.(updatedValue);
          
          message.success(`成功导入 ${newEndpoints.length} 个 ${terminology.pluralLower}`);
          setModalVisible(false);
          setSwaggerContent('');
        } else {
          message.warning('未解析到任何 Endpoints');
        }
      } catch (error) {
        console.error('Swagger 解析失败', error);
        message.error('Swagger 解析失败，请检查内容格式');
      } finally {
        setImportLoading(false);
      }
      return;
    }

    // 手动创建/编辑逻辑
    try {
      const values = await form.validateFields();
      
      // 确定最终的 type 值：优先使用表单中的 type，如果没有则使用推断的类型或当前选中的类型
      const finalType = values.type || inferredEndpointType || selectedType;

      if (!finalType) {
        message.error('Endpoint 类型不能为空');
        return;
      }

      // 处理配置中的 JSON 字符串
      const processedConfig = { ...currentConfig };
      const jsonFields = ['inputSchema', 'outputSchema', 'requestBody', 'configSchema'];
      
      for (const field of jsonFields) {
        if (processedConfig[field] && typeof processedConfig[field] === 'string') {
          try {
            if (processedConfig[field].trim()) {
               processedConfig[field] = JSON.parse(processedConfig[field]);
            } else {
               delete processedConfig[field];
            }
          } catch (e) {
            message.error(`${field} JSON 格式错误`);
            return;
          }
        }
      }

      // 使用当前配置
      const newEndpoint: Endpoint = {
        ...values,
        type: finalType,  // 确保 type 字段存在
        config: processedConfig,
        sortOrder: editingEndpoint ? editingEndpoint.sortOrder : value.length
      };

      let newEndpoints: Endpoint[];
      if (editingEndpoint) {
        // 编辑模式：查找并更新
        const index = value.findIndex(
          (ep) => ep.sortOrder === editingEndpoint.sortOrder
        );
        newEndpoints = [...value];
        newEndpoints[index] = {
          ...editingEndpoint,
          ...newEndpoint
        };
      } else {
        // 新增模式
        newEndpoints = [...value, newEndpoint];
      }

      onChange?.(newEndpoints);
      setModalVisible(false);
      message.success(editingEndpoint ? `${terminology.singular} 更新成功` : `${terminology.singular} 添加成功`);
    } catch (error) {
      // 表单验证失败
    }
  };

  const handleModalCancel = () => {
    setModalVisible(false);
  };

  const handleMoveUp = (index: number) => {
    if (index === 0) return;
    const newEndpoints = [...value];
    [newEndpoints[index - 1], newEndpoints[index]] = [
      newEndpoints[index],
      newEndpoints[index - 1]
    ];
    // 更新 sortOrder
    newEndpoints.forEach((ep, idx) => {
      ep.sortOrder = idx;
    });
    onChange?.(newEndpoints);
  };

  const handleMoveDown = (index: number) => {
    if (index === value.length - 1) return;
    const newEndpoints = [...value];
    [newEndpoints[index], newEndpoints[index + 1]] = [
      newEndpoints[index + 1],
      newEndpoints[index]
    ];
    // 更新 sortOrder
    newEndpoints.forEach((ep, idx) => {
      ep.sortOrder = idx;
    });
    onChange?.(newEndpoints);
  };

  // Swagger Import Handlers
  const handleImportSwaggerClick = () => {
    setSwaggerContent('');
    setCreationMethod('SWAGGER');
    setModalVisible(true);
  };

  const handleSwaggerImport = async () => {
    if (!swaggerContent) {
      message.warning('请先输入或上传 Swagger 内容');
      return;
    }

    setImportLoading(true);
    try {
      const res: any = await apiDefinitionApi.convertSwagger({
        swaggerContent,
        name: 'temp', // Name is required by backend but not used for just getting endpoints
        version: '1.0.0',
        type: apiType === 'MCP_SERVER' ? 'MCP' : 'REST'
      });
      
      const data = res && res.data ? res.data : res;
      
      if (data && data.endpoints && Array.isArray(data.endpoints)) {
        const newEndpoints = data.endpoints.map((ep: any) => {
          // Parse config string if it exists
          if (ep.config && typeof ep.config === 'string') {
            try {
              ep.config = JSON.parse(ep.config);
            } catch (e) {
              console.error('Failed to parse endpoint config:', e);
            }
          }
          return ep;
        });
        
        // Append new endpoints to existing ones
        const updatedValue = [...value, ...newEndpoints];
        onChange?.(updatedValue);
        
        message.success(`成功导入 ${newEndpoints.length} 个 ${terminology.pluralLower}`);
        setModalVisible(false);
        setSwaggerContent('');
      } else {
        message.warning('未解析到任何 Endpoints');
      }
    } catch (error) {
      console.error('Swagger 解析失败', error);
      message.error('Swagger 解析失败，请检查内容格式');
    } finally {
      setImportLoading(false);
    }
  };

  const uploadProps: UploadProps = {
    beforeUpload: (file) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        setSwaggerContent(content);
        message.success(`${file.name} 文件上传成功`);
      };
      reader.readAsText(file);
      return false;
    },
    maxCount: 1
  };

  const importMenu: MenuProps['items'] = [
    {
      key: 'swagger',
      label: '从 Swagger/OpenAPI 导入',
      icon: <UploadOutlined />,
      onClick: handleImportSwaggerClick
    }
  ];

  const columns: ColumnsType<Endpoint> = [
    {
      title: `${terminology.singular} 名称`,
      dataIndex: 'name',
      key: 'name',
      width: 200,
      ellipsis: true
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_, record, index) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            disabled={disabled}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除"
            description={`确定要删除此 ${terminology.singular} 吗？`}
            onConfirm={() => handleDelete(index)}
            okText="确认"
            cancelText="取消"
            disabled={disabled}
          >
            <Button
              type="link"
              danger
              size="small"
              icon={<DeleteOutlined />}
              disabled={disabled}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <div>
          <h4 className="text-base font-semibold">{terminology.singular} 配置</h4>
          {apiType === 'MCP_SERVER' && (
            <p className="text-sm text-gray-500">
              配置 MCP Server 的工具（Tools）
            </p>
          )}
        </div>
        <Space>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddClick}
            disabled={disabled}
          >
            添加 {terminology.singular}
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={value}
        rowKey={(record) => `${record.name}-${record.sortOrder}`}
        pagination={false}
        locale={{
          emptyText: `暂无 ${terminology.singular}，点击"添加 ${terminology.singular}"开始配置`
        }}
      />

      <Modal
        title={editingEndpoint ? `编辑 ${terminology.singular}` : `添加 ${terminology.singular}`}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        width={700}
        okText={
          (creationMethod === 'SWAGGER' || creationMethod === 'NACOS' || creationMethod === 'MCP_SERVER' || creationMethod === 'TEMPLATE') && !editingEndpoint 
            ? "导入" 
            : "确定"
        }
        cancelText="取消"
        confirmLoading={importLoading}
      >
        {/* 创建方式选择 - 仅在新增且为 MCP Server 或 Agent API 或 Model API 时显示 */}
        {!editingEndpoint && (apiType === 'MCP_SERVER' || apiType === 'AGENT_API' || apiType === 'MODEL_API') && (
          <div className="mb-6">
            <div className="mb-2 font-medium">创建方式</div>
            {apiType === 'MCP_SERVER' ? (
              <Select
                value={creationMethod}
                onChange={(value) => {
                  setCreationMethod(value);
                  // 切换时清空 Swagger 内容
                  if (value === 'MANUAL') {
                    setSwaggerContent('');
                  }
                }}
                className="w-full"
                options={[
                  { label: 'Swagger 导入', value: 'SWAGGER' },
                  { label: 'Nacos 导入', value: 'NACOS' },
                  { label: 'MCP Server 导入', value: 'MCP_SERVER' },
                  { label: '手动创建', value: 'MANUAL' },
                ]}
              />
            ) : (
              <Radio.Group
                value={creationMethod}
                onChange={(e) => {
                  const value = e.target.value;
                  setCreationMethod(value);
                  // 切换时清空 Swagger 内容
                  if (value === 'MANUAL') {
                    setSwaggerContent('');
                  }
                }}
                optionType="button"
                buttonStyle="solid"
                className="w-full flex"
              >
                {apiType === 'AGENT_API' && (
                  <Radio.Button value="TEMPLATE" className="flex-1 text-center">从模板添加</Radio.Button>
                )}
                {apiType === 'MODEL_API' && (
                  <Radio.Button value="TEMPLATE" className="flex-1 text-center">从模板添加</Radio.Button>
                )}
                <Radio.Button value="MANUAL" className="flex-1 text-center">手动创建</Radio.Button>
              </Radio.Group>
            )}
          </div>
        )}

        {/* 模板选择界面 */}
        {creationMethod === 'TEMPLATE' && !editingEndpoint && (
             <div className="mb-4">
                 <div className="mb-2 font-medium">选择模板 ({apiType === 'AGENT_API' ? 'Dify' : protocol})</div>
                 <div className="border rounded p-4 max-h-60 overflow-y-auto">
                     <Checkbox.Group 
                        className="flex flex-col gap-2"
                        value={selectedTemplateEndpoints}
                        onChange={(checkedValues) => setSelectedTemplateEndpoints(checkedValues as string[])}
                     >
                         {(apiType === 'AGENT_API' ? DIFY_ENDPOINTS : (
                            protocol === 'openai' ? OPENAI_TEMPLATES :
                            protocol === 'anthropic' ? ANTHROPIC_TEMPLATES :
                            protocol === 'doubao' ? DOUBAO_TEMPLATES : []
                         ).map(t => ({
                            name: 'label' in t ? t.label : t.name,
                            description: 'value' in t ? t.value : t.description
                         }))).map(ep => {
                             const isExist = value.some(existingEp => existingEp.name === ep.name);
                             return (
                                 <Checkbox key={ep.name} value={ep.name} disabled={isExist}>
                                     <span className="font-medium">{ep.name}</span>
                                     <span className="text-gray-500 ml-2">- {ep.description}</span>
                                     {isExist && <span className="text-orange-500 ml-2">(已存在)</span>}
                                 </Checkbox>
                             );
                         })}
                     </Checkbox.Group>
                 </div>
             </div>
        )}

        {/* Nacos 导入界面 */}
        {creationMethod === 'NACOS' && !editingEndpoint && (
          <div className="mb-4">
            <Form layout="vertical">
              <Form.Item label="Nacos 实例" required>
                <Select
                  placeholder="请选择 Nacos 实例"
                  options={nacosInstances}
                  value={selectedNacosId}
                  onChange={handleNacosChange}
                />
              </Form.Item>
              <Form.Item label="命名空间" required>
                <Select
                  placeholder="请选择命名空间"
                  options={nacosNamespaces}
                  value={selectedNamespaceId}
                  onChange={handleNamespaceChange}
                  disabled={!selectedNacosId}
                />
              </Form.Item>
              <Form.Item label="MCP 服务" required>
                <Select
                  placeholder="请选择 MCP 服务"
                  options={nacosServices}
                  value={selectedMcpService}
                  onChange={setSelectedMcpService}
                  disabled={!selectedNamespaceId}
                  showSearch
                />
              </Form.Item>
            </Form>
          </div>
        )}

        {/* MCP Server 导入界面 */}
        {creationMethod === 'MCP_SERVER' && !editingEndpoint && (
          <div className="mb-4">
            <Form layout="vertical">
              <Form.Item label="协议类型" required>
                <Radio.Group value={mcpProtocol} onChange={(e) => setMcpProtocol(e.target.value)}>
                  <Radio value="sse">SSE</Radio>
                  <Radio value="http">HTTP</Radio>
                </Radio.Group>
              </Form.Item>
              <Form.Item label="MCP Server Endpoint" required help="例如: http://localhost:8080/mcp">
                <Input
                  placeholder="请输入 MCP Server Endpoint"
                  value={mcpEndpoint}
                  onChange={(e) => setMcpEndpoint(e.target.value)}
                />
              </Form.Item>
              <Form.Item label="Token (可选)">
                <Input.Password
                  placeholder="请输入访问 Token"
                  value={mcpToken}
                  onChange={(e) => setMcpToken(e.target.value)}
                />
              </Form.Item>
            </Form>
          </div>
        )}

        {/* Swagger 导入界面 */}
        {creationMethod === 'SWAGGER' && !editingEndpoint && (
          <div className="mt-4">
            <div className="mb-4 p-4 bg-gray-50 rounded border border-dashed border-gray-300 text-center">
              <div className="mb-4">
                <FileTextOutlined style={{ fontSize: '32px', color: '#1890ff' }} />
              </div>
              <div className="mb-4 text-gray-500">
                支持上传 JSON 或 YAML 格式的 Swagger/OpenAPI 定义文件
              </div>
              <Upload {...uploadProps} showUploadList={false}>
                <Button icon={<UploadOutlined />}>点击上传文件</Button>
              </Upload>
              {swaggerContent && (
                <div className="mt-2 text-green-600">
                  <FileTextOutlined className="mr-1" /> 文件已读取，点击"解析并导入"完成操作
                </div>
              )}
            </div>
          </div>
        )}

        {/* 手动创建/编辑界面 */}
        <Form 
          form={form} 
          layout="vertical" 
          className="mt-4"
          style={{ display: (creationMethod === 'MANUAL' || editingEndpoint) ? 'block' : 'none' }}
        >
          <Form.Item
            label={`${terminology.singular} 名称`}
            name="name"
            rules={[
              { required: true, message: `请输入 ${terminology.singular} 名称` },
              { max: 100, message: '名称不能超过100个字符' }
            ]}
          >
            <Input 
              placeholder={apiType === 'MCP_SERVER' ? '例如：get_user_info' : '例如：getUserInfo'} 
            />
          </Form.Item>

          {!inferredEndpointType && (
            <Form.Item
              label="Endpoint 类型"
              name="type"
              rules={[{ required: true, message: '请选择 Endpoint 类型' }]}
            >
              <Select
                options={ENDPOINT_TYPE_OPTIONS}
                placeholder="选择 Endpoint 类型"
                disabled={!!editingEndpoint}
                onChange={(type: EndpointType) => {
                  setSelectedType(type);
                  setCurrentConfig({});
                }}
              />
            </Form.Item>
          )}

          <Form.Item
            label="描述"
            name="description"
            rules={[{ max: 500, message: '描述不能超过500个字符' }]}
          >
            <TextArea
              rows={3}
              placeholder={`详细描述这个 ${terminology.singular} 的功能`}
            />
          </Form.Item>

          {selectedType && (
            <div className="border-t pt-4 mt-4">
              <h4 className="text-sm font-semibold mb-4">{terminology.singular} 配置</h4>
              <EndpointConfigForm
                type={selectedType}
                value={currentConfig}
                onChange={setCurrentConfig}
                protocol={protocol}
                creationMode={creationMethod === 'TEMPLATE' ? 'TEMPLATE' : 'MANUAL'}
              />
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
}
