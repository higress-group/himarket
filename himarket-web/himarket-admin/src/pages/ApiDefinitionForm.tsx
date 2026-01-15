import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Select,
  Button,
  message,
  Steps,
  Divider,
  Upload,
  Radio,
  Switch
} from 'antd';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  UploadOutlined
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { apiDefinitionApi, apiProductApi } from '@/lib/api';
import EndpointEditor from '@/components/endpoint/EndpointEditor';
import type { Endpoint } from '@/types/endpoint';

const { TextArea } = Input;

const DIFY_ENDPOINTS: Endpoint[] = [
  {
    name: 'chat-messages',
    description: '发送对话消息',
    type: 'REST_ROUTE',
    config: {
      path: '/chat-messages',
      method: 'POST',
      headers: [
        {
          name: 'Authorization',
          description: 'Bearer {api_key}',
          required: true
        }
      ],
      requestBody: {
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
      },
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
    type: 'REST_ROUTE',
    config: {
      path: '/workflows/run',
      method: 'POST',
      headers: [
        {
          name: 'Authorization',
          description: 'Bearer {api_key}',
          required: true
        }
      ],
      requestBody: {
        type: 'object',
        properties: {
          inputs: { type: 'object', description: '工作流变量输入', default: {} },
          response_mode: { type: 'string', enum: ['streaming', 'blocking'], description: '响应模式' },
          user: { type: 'string', description: '用户标识' },
          files: { type: 'array', items: { type: 'object' }, description: '文件列表' }
        },
        required: ['inputs', 'response_mode', 'user']
      },
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

interface PropertyField {
  name: string;
  label: string;
  type: string;
  description: string;
  required: boolean;
  options?: string[];
  defaultValue?: any;
}

interface PropertySchema {
  type: string;
  name: string;
  description: string;
  fields: PropertyField[];
}

const SCENARIO_OPTIONS = [
  { label: '文本生成', value: 'text-generation', description: '根据输入提示或指令，自动创作各类文本内容，如文章、故事、邮件或代码，满足多样化内容需求。' }
];

const TEXT_GENERATION_PROTOCOLS = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: '豆包', value: 'doubao' }
];

const IMAGE_GENERATION_PROTOCOLS = [
  { label: '阿里云百炼', value: 'bailian' },
  { label: 'OpenAI', value: 'openai' },
  { label: '豆包', value: 'doubao' },
  { label: 'ComfyUI', value: 'comfyui' }
];

const VIDEO_GENERATION_PROTOCOLS = [
  { label: '阿里云百炼', value: 'bailian' },
  { label: 'OpenAI', value: 'openai' },
  { label: '豆包', value: 'doubao' }
];

const SPEECH_SYNTHESIS_PROTOCOLS = [
  { label: '阿里云百炼', value: 'bailian' },
  { label: 'OpenAI', value: 'openai' }
];

const EMBEDDING_PROTOCOLS = [
  { label: 'OpenAI', value: 'openai' }
];

const DEFAULT_MODEL_PROTOCOLS = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: '豆包', value: 'doubao' }
];

// API 类型选项
const API_TYPE_OPTIONS = [
  { label: 'REST API', value: 'REST_API' },
  { label: 'MCP Server', value: 'MCP_SERVER' },
  { label: 'Agent API', value: 'AGENT_API' },
  { label: 'Model API', value: 'MODEL_API' }
];

export default function ApiDefinitionForm() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { productName, productType, productDescription } = location.state || {};
  const [form] = Form.useForm();
  const apiName = Form.useWatch('name', form);
  const [loading, setLoading] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const [supportedProperties, setSupportedProperties] = useState<PropertySchema[]>([]);
  const isEdit = searchParams.get('id') !== null;
  const apiDefinitionId = searchParams.get('id');

  useEffect(() => {
    fetchSupportedProperties();
    if (isEdit && apiDefinitionId) {
      fetchApiDefinition(apiDefinitionId);
    }
  }, [isEdit, apiDefinitionId]);

  const fetchSupportedProperties = async () => {
    try {
      const response: any = await apiDefinitionApi.getSupportedProperties();
      const data = response && response.data ? response.data : response;
      if (Array.isArray(data)) {
        setSupportedProperties(data);
      }
    } catch (error) {
      console.error('获取支持的属性配置失败:', error);
    }
  };

  const fetchApiDefinition = async (id: string) => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getApiDefinitionDetail(id);
      // 处理后端返回的数据格式
      const apiData = response && response.data ? response.data : response;

      const propertiesObj: Record<string, any> = {};
      if (apiData.properties && Array.isArray(apiData.properties)) {
        apiData.properties.forEach((p: any) => {
          propertiesObj[p.type] = { ...p, enabled: p.enabled !== false };
        });
      }

      form.setFieldsValue({
        name: apiData.name,
        description: apiData.description,
        type: apiData.type,
        status: apiData.status,
        version: apiData.version,
        metadata: apiData.metadata,
        properties: propertiesObj
      });

      // 加载 endpoints
      try {
        const endpointsResponse: any = await apiDefinitionApi.getEndpoints(id);
        const endpointsData = endpointsResponse && endpointsResponse.data ? endpointsResponse.data : endpointsResponse;

        if (Array.isArray(endpointsData)) {
          setEndpoints(endpointsData);
        } else {
          setEndpoints([]);
        }
      } catch (error) {
        console.error('获取 endpoints 失败:', error);
        setEndpoints([]);
      }
    } catch (error) {
      message.error('获取 API 详情失败');
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    navigate(-1);
  };

  const handleNext = () => {
    form.validateFields().then(() => {
      setCurrentStep(currentStep + 1);
    });
  };

  const handlePrev = () => {
    setCurrentStep(currentStep - 1);
  };

  const handleSubmit = async () => {
    try {
      await form.validateFields();
      // 使用 true 参数获取所有字段，包括 disabled 的字段
      const values = form.getFieldsValue(true);

      setLoading(true);

      // 准备 endpoints 数据
      const endpointsData = endpoints.map(endpoint => {
        const config = { ...endpoint.config };
        // Ensure JSON fields are objects
        const jsonFields = ['inputSchema', 'outputSchema', 'requestBody', 'configSchema'];
        jsonFields.forEach(field => {
          if (config[field] && typeof config[field] === 'string') {
            try {
              config[field] = JSON.parse(config[field]);
            } catch (e) {
              console.warn(`Failed to parse ${field} for endpoint ${endpoint.name}`, e);
            }
          }
        });

        return {
          name: values.type === 'MODEL_API' ? values.name : endpoint.name,
          description: endpoint.description,
          type: endpoint.type,
          sortOrder: endpoint.sortOrder,
          config: {
            ...config,
            type: endpoint.type  // 添加 type 字段，用于 Jackson 多态反序列化
          }
        };
      });

      // 转换 properties 对象为列表
      const propertiesList: any[] = [];
      if (values.properties) {
        for (const [type, props] of Object.entries(values.properties)) {
          // 只有当属性开启时才提交
          if (props && typeof props === 'object' && (props as any).enabled === true) {
            propertiesList.push({
              type: type,
              ...(props as object)
            });
          }
        }
      }

      const payload = {
        ...values,
        properties: propertiesList,
        endpoints: endpointsData
      };

      if (isEdit && apiDefinitionId) {
        // 更新 API，将 endpoints 一起提交
        await apiDefinitionApi.updateApiDefinition(apiDefinitionId, payload);

        message.success('API 更新成功');
      } else {
        // 创建 API
        let newApiId: string | undefined;

        // 手动创建，将 endpoints 作为参数一起传入
        const res: any = await apiDefinitionApi.createApiDefinition(payload);
        const data = res && res.data ? res.data : res;
        newApiId = data?.apiDefinitionId;

        message.success('API 创建成功');

        // 如果是从 Product 页面跳转过来的，自动关联
        const { productId } = location.state || {};
        if (productId && newApiId) {
          try {
            await apiProductApi.createApiProductRef(productId, {
              productId: productId,
              sourceType: 'MANAGED',
              apiDefinitionId: newApiId
            });
            message.success('已自动关联到当前产品');
          } catch (linkError) {
            console.error('自动关联失败', linkError);
            message.warning('API创建成功，但自动关联失败，请手动关联');
          }
        }
      }

      navigate(-1);
    } catch (error: any) {
      if (error.errorFields) {
        message.error('请完善表单信息');
      } else {
        message.error(error.message || '操作失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const renderStepContent = () => {
    switch (currentStep) {
      case 0:
        // 第一步：基本信息
        return renderBasicInfoForm();

      case 1:
        // 第二步：配置属性
        return renderPropertiesForm();

      case 2:
        // 第三步：配置 Endpoints
        const apiType = form.getFieldValue('type');
        const protocol = form.getFieldValue(['metadata', 'protocol']);
        const mcpBridgeType = form.getFieldValue(['metadata', 'mcpBridgeType']);
        return (
          <div className="py-4">
            <EndpointEditor
              value={endpoints}
              onChange={setEndpoints}
              apiType={apiType}
              apiName={form.getFieldValue('name')}
              protocol={protocol}
              mcpBridgeType={mcpBridgeType}
            />
          </div>
        );

      default:
        return null;
    }
  };

  const renderFieldInput = (field: PropertyField) => {
    switch (field.type) {
      case 'integer':
        return <InputNumber style={{ width: '100%' }} />;
      case 'boolean':
        return <Select options={[{ label: '是', value: true }, { label: '否', value: false }]} />;
      case 'select':
        return <Select options={field.options?.map(opt => ({ label: opt, value: opt }))} />;
      default:
        return <Input />;
    }
  };

  const renderPropertiesForm = () => {
    return (
      <div className="py-4">
        <h3 className="text-lg font-semibold mb-4">
          {isEdit ? 'API 属性配置' : '配置 API 属性'}
        </h3>
        <Form
          form={form}
          layout="vertical"
        >
          {supportedProperties.map((schema) => (
            <Card
              key={schema.type}
              title={schema.name}
              className="mb-4"
              size="small"
              extra={
                <Form.Item
                  name={['properties', schema.type, 'enabled']}
                  valuePropName="checked"
                  initialValue={false}
                  style={{ marginBottom: 0 }}
                >
                  <Switch checkedChildren="开启" unCheckedChildren="关闭" />
                </Form.Item>
              }
            >
              <p className="text-gray-500 mb-4">{schema.description}</p>
              <Form.Item
                noStyle
                shouldUpdate={(prev, curr) => {
                  return prev.properties?.[schema.type]?.enabled !== curr.properties?.[schema.type]?.enabled;
                }}
              >
                {({ getFieldValue }) => {
                  const enabled = getFieldValue(['properties', schema.type, 'enabled']);
                  if (!enabled) return null;

                  return (
                    <div className="grid grid-cols-2 gap-4">
                      {schema.fields.map((field) => (
                        <Form.Item
                          key={field.name}
                          label={field.label}
                          name={['properties', schema.type, field.name]}
                          rules={[{ required: field.required, message: `请输入${field.label}` }]}
                          help={field.description}
                          initialValue={field.defaultValue}
                        >
                          {renderFieldInput(field)}
                        </Form.Item>
                      ))}
                    </div>
                  );
                }}
              </Form.Item>
            </Card>
          ))}
        </Form>
      </div>
    );
  };

  const renderBasicInfoForm = () => {
    return (
      <div className="py-4">
        <h3 className="text-lg font-semibold mb-4">
          {isEdit ? '基本信息配置' : 'API 基本信息'}
        </h3>
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            name: productName || '',
            description: productDescription || '',
            type: productType || 'REST_API',
            status: 'DRAFT',
            version: '1.0.0'
          }}
          onValuesChange={(changedValues) => {
            // 监听协议变化
          }}
        >
          <Form.Item
            label="API 名称"
            name="name"
            rules={[
              { required: true, message: '请输入 API 名称' },
              { max: 100, message: '名称不能超过100个字符' }
            ]}
          >
            <Input placeholder="例如：用户管理 API" disabled={!!productName} />
          </Form.Item>

          <Form.Item
            label="API 描述"
            name="description"
            rules={[{ max: 500, message: '描述不能超过500个字符' }]}
          >
            <TextArea
              rows={4}
              placeholder="详细描述这个 API 的功能和用途"
            />
          </Form.Item>

          <Form.Item
            label="API 类型"
            name="type"
            rules={[{ required: true, message: '请选择 API 类型' }]}
          >
            <Select
              options={API_TYPE_OPTIONS}
              placeholder="选择 API 类型"
              disabled={isEdit || !!productType}
            />
          </Form.Item>

          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.type !== curr.type}
          >
            {({ getFieldValue }) => {
              const type = getFieldValue('type');
              if (type === 'MCP_SERVER') {
                return (
                  <Form.Item
                    label="协议"
                    name={['metadata', 'mcpBridgeType']}
                    rules={[{ required: true, message: '请选择协议' }]}
                    initialValue="HTTP_TO_MCP"
                  >
                    <Radio.Group>
                      <Radio value="HTTP_TO_MCP">HTTP转MCP</Radio>
                      <Radio value="DIRECT">MCP服务直接代理</Radio>
                    </Radio.Group>
                  </Form.Item>
                );
              }
              if (type === 'AGENT_API') {
                return (
                  <Form.Item
                    label="协议"
                    name={['metadata', 'protocol']}
                    rules={[{ required: true, message: '请选择协议' }]}
                  >
                    <Select
                      placeholder="选择协议"
                      options={[
                        { label: 'Dify', value: 'Dify' },
                        { label: '百炼', value: 'Bailian' }
                      ]}
                    />
                  </Form.Item>
                );
              }
              if (type === 'MODEL_API') {
                return (
                  <>
                    <Form.Item
                      label="场景"
                      name={['metadata', 'scenario']}
                      rules={[{ required: true, message: '请选择场景' }]}
                    >
                      <Select
                        placeholder="选择场景"
                        options={SCENARIO_OPTIONS.map(opt => ({
                          label: (
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                              <span>{opt.label}</span>
                              <span style={{ fontSize: '12px', color: '#888' }}>{opt.description}</span>
                            </div>
                          ),
                          value: opt.value,
                          title: opt.label
                        }))}
                        optionLabelProp="title"
                        onChange={() => {
                          form.setFieldValue(['metadata', 'protocol'], undefined);
                        }}
                      />
                    </Form.Item>
                    <Form.Item
                      noStyle
                      shouldUpdate={(prev, curr) => prev.metadata?.scenario !== curr.metadata?.scenario}
                    >
                      {({ getFieldValue }) => {
                        const scenario = getFieldValue(['metadata', 'scenario']);
                        let protocolOptions = DEFAULT_MODEL_PROTOCOLS;

                        if (scenario === 'text-generation') {
                          protocolOptions = TEXT_GENERATION_PROTOCOLS;
                        } else if (scenario === 'image-generation') {
                          protocolOptions = IMAGE_GENERATION_PROTOCOLS;
                        } else if (scenario === 'video-generation') {
                          protocolOptions = VIDEO_GENERATION_PROTOCOLS;
                        } else if (scenario === 'speech-synthesis') {
                          protocolOptions = SPEECH_SYNTHESIS_PROTOCOLS;
                        } else if (scenario === 'embedding') {
                          protocolOptions = EMBEDDING_PROTOCOLS;
                        }

                        return (
                          <Form.Item
                            label="协议"
                            name={['metadata', 'protocol']}
                            rules={[{ required: true, message: '请选择协议' }]}
                          >
                            <Select
                              placeholder="选择协议"
                              options={protocolOptions}
                            />
                          </Form.Item>
                        );
                      }}
                    </Form.Item>
                  </>
                );
              }
              return null;
            }}
          </Form.Item>

          <Form.Item
            label="版本号"
            name="version"
            rules={[
              { required: true, message: '请输入版本号' },
              {
                pattern: /^\d+\.\d+\.\d+$/,
                message: '版本号格式应为：x.y.z（如：1.0.0）'
              }
            ]}
          >
            <Input placeholder="1.0.0" />
          </Form.Item>
        </Form>
      </div>
    );
  };

  const steps = isEdit
    ? ['编辑信息', '配置属性', '配置 Endpoints']
    : ['基本信息', '配置属性', '配置 Endpoints'];

  return (
    <div>
      {/* 页面头部 */}
      <div className="mb-6">
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={handleBack}
          className="mb-4"
        >
          返回
        </Button>
        <h1 className="text-2xl font-bold">
          {isEdit ? '编辑 API Definition' : '创建 API Definition'}
        </h1>
      </div>

      {/* 步骤指示器 */}
      <Card className="mb-6">
        <Steps
          current={currentStep}
          items={steps.map((step, index) => ({
            key: index.toString(),
            title: step
          }))}
        />
      </Card>

      {/* 表单内容 */}
      <Card>
        {renderStepContent()}

        {/* 操作按钮 */}
        <div className="flex justify-end space-x-4 mt-6 pt-6 border-t">
          {currentStep > 0 && (
            <Button onClick={handlePrev}>上一步</Button>
          )}
          <Button onClick={handleBack}>取消</Button>
          {currentStep === steps.length - 1 ? (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={loading}
              onClick={handleSubmit}
            >
              {isEdit ? '保存' : '创建'}
            </Button>
          ) : (
            <Button type="primary" onClick={handleNext}>
              下一步
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}
