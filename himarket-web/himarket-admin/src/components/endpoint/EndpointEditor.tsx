import { useState } from 'react';
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
  Radio
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
import { apiDefinitionApi } from '@/lib/api';

const { TextArea } = Input;

interface EndpointEditorProps {
  value?: Endpoint[];
  onChange?: (value: Endpoint[]) => void;
  disabled?: boolean;
  apiType?: string; // API Definition 的类型
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
  MODEL: 'orange'
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
  apiType
}: EndpointEditorProps) {
  // 根据 API 类型推断 Endpoint 类型
  const inferredEndpointType = apiType ? API_TYPE_TO_ENDPOINT_TYPE[apiType] : undefined;
  // 获取显示术语
  const terminology = getTerminology(apiType);
  
  const [modalVisible, setModalVisible] = useState(false);
  const [editingEndpoint, setEditingEndpoint] = useState<Endpoint | null>(null);
  const [selectedType, setSelectedType] = useState<EndpointType | undefined>();
  const [currentConfig, setCurrentConfig] = useState<any>({});
  const [form] = Form.useForm();

  // Swagger Import State
  const [swaggerContent, setSwaggerContent] = useState('');
  const [importLoading, setImportLoading] = useState(false);

  // Creation Method Selection State
  const [creationMethod, setCreationMethod] = useState<'MANUAL' | 'SWAGGER'>('MANUAL');

  const handleAddClick = () => {
    setEditingEndpoint(null);
    setCreationMethod('MANUAL');
    // 自动设置推断出的类型
    setSelectedType(inferredEndpointType);
    setCurrentConfig({});
    form.resetFields();
    // 如果有推断的类型，自动设置到表单
    if (inferredEndpointType) {
      form.setFieldsValue({ type: inferredEndpointType });
    }
    setModalVisible(true);
  };

  const handleEdit = (endpoint: Endpoint) => {
    setEditingEndpoint(endpoint);
    setCreationMethod('MANUAL');
    setSelectedType(endpoint.type);
    setCurrentConfig(endpoint.config || {});
    form.setFieldsValue({
      name: endpoint.name,
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
          <p className="text-sm text-gray-500">
            {apiType === 'MCP_SERVER' 
              ? '配置 MCP Server 的工具（Tools）' 
              : '配置 API 的端点信息，包括 MCP Tool、REST Route 等'}
          </p>
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
        rowKey={(record, index) => `${record.name}-${index}`}
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
        okText={creationMethod === 'SWAGGER' && !editingEndpoint ? "解析并导入" : "确定"}
        cancelText="取消"
        confirmLoading={importLoading}
      >
        {/* 创建方式选择 - 仅在新增且为 MCP Server 时显示 */}
        {!editingEndpoint && apiType === 'MCP_SERVER' && (
          <div className="mb-6">
            <div className="mb-2 font-medium">创建方式</div>
            <Radio.Group 
              value={creationMethod} 
              onChange={(e) => {
                setCreationMethod(e.target.value);
                // 切换时清空 Swagger 内容
                if (e.target.value === 'MANUAL') {
                  setSwaggerContent('');
                }
              }}
              optionType="button"
              buttonStyle="solid"
              className="w-full flex"
            >
              <Radio.Button value="MANUAL" className="flex-1 text-center">手动创建</Radio.Button>
              <Radio.Button value="SWAGGER" className="flex-1 text-center">从 Swagger/OpenAPI 导入</Radio.Button>
            </Radio.Group>
          </div>
        )}

        {/* Swagger 导入界面 */}
        {creationMethod === 'SWAGGER' && !editingEndpoint ? (
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
        ) : (
          /* 手动创建/编辑界面 */
          <Form form={form} layout="vertical" className="mt-4">
            <Form.Item
              label={`${terminology.singular} 名称`}
              name="name"
              rules={[
                { required: true, message: `请输入 ${terminology.singular} 名称` },
                { max: 100, message: '名称不能超过100个字符' }
              ]}
            >
              <Input placeholder={apiType === 'MCP_SERVER' ? '例如：get_user_info' : '例如：getUserInfo'} />
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
                />
              </div>
            )}
          </Form>
        )}
      </Modal>
    </div>
  );
}
