import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Form,
  Input,
  Select,
  Button,
  message,
  Steps,
  Divider,
  Upload,
  Radio
} from 'antd';
import {
  ArrowLeftOutlined,
  SaveOutlined,
  UploadOutlined
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { apiDefinitionApi } from '@/lib/api';
import EndpointEditor from '@/components/endpoint/EndpointEditor';
import type { Endpoint } from '@/types/endpoint';

const { TextArea } = Input;

// API 类型选项
const API_TYPE_OPTIONS = [
  { label: 'REST API', value: 'REST_API' },
  { label: 'MCP Server', value: 'MCP_SERVER' },
  { label: 'Agent API', value: 'AGENT_API' },
  { label: 'Model API', value: 'MODEL_API' }
];

// 创建方式
const CREATE_METHOD_OPTIONS = [
  { label: '导入 Swagger/OpenAPI', value: 'SWAGGER' },
  { label: '手动创建', value: 'MANUAL' }
];

export default function ApiDefinitionForm() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);
  const [createMethod, setCreateMethod] = useState('MANUAL');
  const [swaggerContent, setSwaggerContent] = useState('');
  const [endpoints, setEndpoints] = useState<Endpoint[]>([]);
  const isEdit = searchParams.get('id') !== null;
  const apiDefinitionId = searchParams.get('id');

  useEffect(() => {
    if (isEdit && apiDefinitionId) {
      fetchApiDefinition(apiDefinitionId);
    }
  }, [isEdit, apiDefinitionId]);

  const fetchApiDefinition = async (id: string) => {
    setLoading(true);
    try {
      const response: any = await apiDefinitionApi.getApiDefinitionDetail(id);
      // 处理后端返回的数据格式
      const apiData = response && response.data ? response.data : response;

      form.setFieldsValue({
        name: apiData.name,
        description: apiData.description,
        type: apiData.type,
        status: apiData.status,
        version: apiData.version
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

      if (isEdit && apiDefinitionId) {
        // 准备 endpoints 数据
        const endpointsData = endpoints.map(endpoint => ({
          name: endpoint.name,
          description: endpoint.description,
          type: endpoint.type,
          sortOrder: endpoint.sortOrder,
          config: {
            ...endpoint.config,
            type: endpoint.type  // 添加 type 字段，用于 Jackson 多态反序列化
          }
        }));

        // 更新 API，将 endpoints 一起提交
        await apiDefinitionApi.updateApiDefinition(apiDefinitionId, {
          ...values,
          endpoints: endpointsData
        });

        message.success('API 更新成功');
      } else {
        // 创建 API
        if (createMethod === 'SWAGGER' && swaggerContent) {
          // 通过 Swagger 导入创建
          await apiDefinitionApi.importSwagger({
            swaggerContent,
            name: values.name,
            description: values.description,
            version: values.version
          });
        } else {
          // 准备 endpoints 数据
          const endpointsData = endpoints.map(endpoint => ({
            name: endpoint.name,
            description: endpoint.description,
            type: endpoint.type,
            sortOrder: endpoint.sortOrder,
            config: {
              ...endpoint.config,
              type: endpoint.type  // 添加 type 字段，用于 Jackson 多态反序列化
            }
          }));

          // 手动创建，将 endpoints 作为参数一起传入
          await apiDefinitionApi.createApiDefinition({
            ...values,
            endpoints: endpointsData
          });
        }

        message.success('API 创建成功');
      }

      navigate('/api-definitions');
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

  const renderStepContent = () => {
    switch (currentStep) {
      case 0:
        // 第一步：选择创建方式（仅在创建时显示）
        if (isEdit) {
          const apiType = form.getFieldValue('type');
          return (
            <div>
              {renderBasicInfoForm()}
              <Divider className="my-6" />
              <div className="py-4">
                <h3 className="text-lg font-semibold mb-4">端点配置</h3>
                <EndpointEditor
                  value={endpoints}
                  onChange={setEndpoints}
                  apiType={apiType}
                />
              </div>
            </div>
          );
        }
        return (
          <div className="py-8">
            <div className="text-center mb-6">
              <h3 className="text-lg font-semibold mb-2">选择创建方式</h3>
              <p className="text-gray-500">选择如何创建你的 API Definition</p>
            </div>
            <Radio.Group
              value={createMethod}
              onChange={(e) => setCreateMethod(e.target.value)}
              className="w-full"
            >
              <div className="flex w-full gap-4">
                {CREATE_METHOD_OPTIONS.map((option) => (
                  <Card
                    key={option.value}
                    hoverable
                    className={`flex-1 cursor-pointer ${
                      createMethod === option.value ? 'border-blue-500 border-2' : ''
                    }`}
                    onClick={() => setCreateMethod(option.value)}
                  >
                    <Radio value={option.value}>
                      <div>
                        <div className="font-semibold">{option.label}</div>
                        <div className="text-sm text-gray-500">
                          {option.value === 'MANUAL'
                            ? '从头开始创建一个新的 API Definition'
                            : '通过导入 Swagger/OpenAPI 文档快速创建'}
                        </div>
                      </div>
                    </Radio>
                  </Card>
                ))}
              </div>
            </Radio.Group>
          </div>
        );

      case 1:
        // 第二步：基本信息
        if (!isEdit && createMethod === 'SWAGGER') {
          return (
            <div className="py-4">
              <h3 className="text-lg font-semibold mb-4">上传 Swagger/OpenAPI 文档</h3>
              <Form.Item
                label="Swagger/OpenAPI 文档"
                required
                help="支持 Swagger 2.0 和 OpenAPI 3.0 格式的 JSON 或 YAML 文件"
              >
                <Upload {...uploadProps}>
                  <Button icon={<UploadOutlined />}>选择文件</Button>
                </Upload>
              </Form.Item>
              {swaggerContent && (
                <Form.Item label="文档内容预览">
                  <TextArea
                    value={swaggerContent}
                    rows={10}
                    readOnly
                    style={{ fontFamily: 'monospace' }}
                  />
                </Form.Item>
              )}
              <Divider />
              {renderBasicInfoForm()}
            </div>
          );
        }
        return renderBasicInfoForm();

      case 2:
        // 第三步：配置 Endpoints
        const apiType = form.getFieldValue('type');
        return (
          <div className="py-4">
            <EndpointEditor
              value={endpoints}
              onChange={setEndpoints}
              apiType={apiType}
            />
          </div>
        );

      default:
        return null;
    }
  };

  const renderBasicInfoForm = () => {
    return (
      <div className="py-4">
        <h3 className="text-lg font-semibold mb-4">
          {isEdit ? '编辑 API Definition' : 'API 基本信息'}
        </h3>
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            type: 'REST_API',
            status: 'DRAFT',
            version: '1.0.0'
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
            <Input placeholder="例如：用户管理 API" />
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
              disabled={isEdit}
            />
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
    ? ['编辑信息', '配置 Endpoints']
    : createMethod === 'SWAGGER'
    ? ['选择方式', '导入文档', '配置 Endpoints']
    : ['选择方式', '基本信息', '配置 Endpoints'];

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
      {!isEdit && (
        <Card className="mb-6">
          <Steps
            current={currentStep}
            items={steps.map((step, index) => ({
              key: index.toString(),
              title: step
            }))}
          />
        </Card>
      )}

      {/* 表单内容 */}
      <Card>
        {renderStepContent()}

        {/* 操作按钮 */}
        <div className="flex justify-end space-x-4 mt-6 pt-6 border-t">
          {currentStep > 0 && !isEdit && (
            <Button onClick={handlePrev}>上一步</Button>
          )}
          <Button onClick={handleBack}>取消</Button>
          {(isEdit || currentStep === steps.length - 1) ? (
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
