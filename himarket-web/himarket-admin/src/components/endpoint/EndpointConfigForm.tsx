import { Form, Input, Select, Button, Space, Collapse, Radio } from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { useEffect, useRef, useState } from 'react';
import type { EndpointType, EndpointConfig } from '@/types/endpoint';

const { TextArea } = Input;

interface EndpointConfigFormProps {
  type: EndpointType;
  value?: EndpointConfig;
  onChange?: (value: EndpointConfig) => void;
  protocol?: string;
}

// HTTP 方法选项
const HTTP_METHODS = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
  { label: 'HEAD', value: 'HEAD' },
  { label: 'OPTIONS', value: 'OPTIONS' }
];

// 匹配类型选项
const MATCH_TYPES = [
  { label: '精确匹配 (Exact)', value: 'Exact' },
  { label: '前缀匹配 (Prefix)', value: 'Prefix' },
  { label: '正则匹配 (Regex)', value: 'Regex' }
];

// 参数位置选项
const PARAMETER_IN_OPTIONS = [
  { label: 'Query 参数', value: 'query' },
  { label: 'Path 参数', value: 'path' },
  { label: 'Header 参数', value: 'header' },
  { label: 'Cookie 参数', value: 'cookie' }
];

export default function EndpointConfigForm({ type, value, onChange, protocol, creationMode = 'MANUAL' }: EndpointConfigFormProps & { creationMode?: 'MANUAL' | 'TEMPLATE' }) {
  const [form] = Form.useForm();
  const isInternalChange = useRef(false);

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

  const getTemplates = () => {
    switch (protocol) {
      case 'openai':
        return OPENAI_TEMPLATES;
      case 'anthropic':
        return ANTHROPIC_TEMPLATES;
      case 'doubao':
        return DOUBAO_TEMPLATES;
      default:
        return [];
    }
  };

  const handleTemplateChange = (templateValue: string) => {
    const templates = getTemplates();
    const template = templates.find(t => t.value === templateValue);
    if (template) {
      form.setFieldsValue({
        matchConfig: {
          path: {
            type: 'Exact',
            value: template.value
          },
          methods: [template.method]
        }
      });
      handleValuesChange();
    }
  };

  // 监听 value 变化，更新表单
  useEffect(() => {
    if (isInternalChange.current) {
      isInternalChange.current = false;
      return;
    }

    if (value) {
      const formValues = { ...value };
      
      // 处理 JSON Schema 字段，如果是对象则转换为字符串显示
      const jsonFields = ['inputSchema', 'outputSchema', 'requestBody', 'configSchema'];
      jsonFields.forEach(field => {
        if (formValues[field] && typeof formValues[field] === 'object') {
          try {
            formValues[field] = JSON.stringify(formValues[field], null, 2);
          } catch (e) {
            console.error(`Failed to stringify ${field}:`, e);
          }
        }
      });

      form.setFieldsValue(formValues);
    } else {
      form.resetFields();
    }
  }, [value, form]);

  // 当配置变化时触发 onChange
  const handleValuesChange = () => {
    isInternalChange.current = true;
    const values = form.getFieldsValue();
    onChange?.(values);
  };

  // 渲染 MCP Tool 配置表单
  const renderMCPToolConfig = () => {
    return (
      <>
        <Collapse 
          defaultActiveKey={['schema']} 
          className="mb-4"
          items={[
            {
              key: 'schema',
              label: 'Schema 定义',
              children: (
                <>
                  <Form.Item
                    label="输入 Schema (Input Schema)"
                    name="inputSchema"
                    help="JSON 格式的输入参数 Schema"
                  >
                    <TextArea
                      rows={4}
                      placeholder='{"type": "object", "properties": {...}}'
                    />
                  </Form.Item>

                  <Form.Item
                    label="输出 Schema (Output Schema)"
                    name="outputSchema"
                    help="JSON 格式的输出结果 Schema"
                  >
                    <TextArea
                      rows={4}
                      placeholder='{"type": "object", "properties": {...}}'
                    />
                  </Form.Item>
                </>
              )
            },
            {
              key: 'request',
              label: '请求模板 (Request Template) - 高级设置',
              children: (
                <>
                  <Form.Item
                    label="请求 URL"
                    name={['requestTemplate', 'url']}
                  >
                    <Input placeholder="https://api.example.com/endpoint" />
                  </Form.Item>

                  <Form.Item
                    label="HTTP 方法"
                    name={['requestTemplate', 'method']}
                  >
                    <Select options={HTTP_METHODS} placeholder="选择方法" />
                  </Form.Item>

                  <Form.Item label="请求头 (Headers)">
                    <Form.List name={['requestTemplate', 'headers']}>
                      {(fields, { add, remove }) => (
                        <div className="flex flex-col gap-2">
                          {fields.map((field) => {
                            const { key, ...restField } = field;
                            return (
                              <Space key={`requestTemplate-header-${key}`} style={{ display: 'flex', marginBottom: 8 }}>
                                <Form.Item
                                  {...restField}
                                  name={[field.name, 'key']}
                                  noStyle
                                >
                                  <Input placeholder="Header 名称" style={{ width: 200 }} />
                                </Form.Item>
                                <Form.Item
                                  {...restField}
                                  name={[field.name, 'value']}
                                  noStyle
                                >
                                  <Input placeholder="Header 值" style={{ width: 300 }} />
                                </Form.Item>
                                <MinusCircleOutlined onClick={() => remove(field.name)} />
                              </Space>
                            );
                          })}
                          <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                            添加请求头
                          </Button>
                        </div>
                      )}
                    </Form.List>
                  </Form.Item>

                  <Form.Item label="请求体模板 (Body)" name={['requestTemplate', 'body']}>
                    <TextArea rows={4} placeholder='{"key": "value"}' />
                  </Form.Item>
                </>
              )
            },
            {
              key: 'response',
              label: '响应模板 (Response Template) - 高级设置',
              children: (
                <Form.Item label="响应体模板" name={['responseTemplate', 'body']}>
                  <TextArea rows={4} placeholder="响应处理逻辑" />
                </Form.Item>
              )
            }
          ]}
        />
      </>
    );
  };

  // 渲染 REST Route 配置表单
  const renderRESTRouteConfig = () => {
    return (
      <>
        <Form.Item
          label="请求路径 (Path)"
          name="path"
          rules={[{ required: true, message: '请输入请求路径' }]}
        >
          <Input placeholder="/api/users/{id}" />
        </Form.Item>

        <Form.Item
          label="HTTP 方法"
          name="method"
          rules={[{ required: true, message: '请选择 HTTP 方法' }]}
        >
          <Select options={HTTP_METHODS} placeholder="选择方法" />
        </Form.Item>

        <Collapse 
          defaultActiveKey={['params']} 
          className="mb-4"
          items={[
            {
              key: 'params',
              label: '请求参数 (Query Parameters)',
              children: (
                <Form.List name="parameters">
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map((field) => {
                        const { key, ...restField } = field;
                        return (
                          <div key={`parameter-${key}`} className="border p-4 mb-4 rounded">
                            <Space style={{ display: 'flex', marginBottom: 8 }}>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'name']}
                                label="参数名"
                                noStyle
                              >
                                <Input placeholder="参数名" style={{ width: 150 }} />
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'required']}
                                valuePropName="checked"
                                noStyle
                              >
                                <Select
                                  options={[
                                    { label: '必填', value: true },
                                    { label: '可选', value: false }
                                  ]}
                                  placeholder="是否必填"
                                  style={{ width: 100 }}
                                />
                              </Form.Item>
                              <MinusCircleOutlined onClick={() => remove(field.name)} />
                            </Space>
                            <Form.Item
                              {...restField}
                              name={[field.name, 'description']}
                              label="描述"
                            >
                              <Input placeholder="参数描述" />
                            </Form.Item>
                          </div>
                        );
                      })}
                      <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                        添加 Query 参数
                      </Button>
                    </>
                  )}
                </Form.List>
              )
            },
            {
              key: 'headers',
              label: '请求头 (Headers)',
              children: (
                <Form.List name="headers">
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map((field) => {
                        const { key, ...restField } = field;
                        return (
                          <div key={`header-${key}`} className="border p-4 mb-4 rounded">
                            <Space style={{ display: 'flex', marginBottom: 8 }}>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'name']}
                                label="Header 名"
                                noStyle
                              >
                                <Input placeholder="Header 名" style={{ width: 150 }} />
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'required']}
                                valuePropName="checked"
                                noStyle
                              >
                                <Select
                                  options={[
                                    { label: '必填', value: true },
                                    { label: '可选', value: false }
                                  ]}
                                  placeholder="是否必填"
                                  style={{ width: 100 }}
                                />
                              </Form.Item>
                              <MinusCircleOutlined onClick={() => remove(field.name)} />
                            </Space>
                            <Form.Item
                              {...restField}
                              name={[field.name, 'description']}
                              label="描述"
                            >
                              <Input placeholder="Header 描述" />
                            </Form.Item>
                          </div>
                        );
                      })}
                      <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                        添加 Header
                      </Button>
                    </>
                  )}
                </Form.List>
              )
            },
            {
              key: 'pathParams',
              label: '路径参数 (Path Parameters)',
              children: (
                <Form.List name="pathParams">
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map((field) => {
                        const { key, ...restField } = field;
                        return (
                          <div key={`pathParam-${key}`} className="border p-4 mb-4 rounded">
                            <Space style={{ display: 'flex', marginBottom: 8 }}>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'name']}
                                label="参数名"
                                noStyle
                              >
                                <Input placeholder="参数名" style={{ width: 150 }} />
                              </Form.Item>
                              <Form.Item
                                {...restField}
                                name={[field.name, 'required']}
                                valuePropName="checked"
                                noStyle
                              >
                                <Select
                                  options={[
                                    { label: '必填', value: true },
                                    { label: '可选', value: false }
                                  ]}
                                  placeholder="是否必填"
                                  style={{ width: 100 }}
                                />
                              </Form.Item>
                              <MinusCircleOutlined onClick={() => remove(field.name)} />
                            </Space>
                            <Form.Item
                              {...restField}
                              name={[field.name, 'description']}
                              label="描述"
                            >
                              <Input placeholder="参数描述" />
                            </Form.Item>
                          </div>
                        );
                      })}
                      <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                        添加 Path 参数
                      </Button>
                    </>
                  )}
                </Form.List>
              )
            },
            {
              key: 'requestBody',
              label: '请求体定义 (Request Body) - JSON Schema',
              children: (
                <Form.Item name="requestBody">
                  <TextArea
                    rows={6}
                    placeholder='{"type": "object", "properties": {...}}'
                  />
                </Form.Item>
              )
            }
          ]}
        />
      </>
    );
  };

  // 渲染 Agent 配置表单
  const renderAgentConfig = () => {
    return (
      <>
        <Form.Item
          label="支持的协议"
          name="protocols"
          rules={[{ required: true, message: '请选择至少一个协议' }]}
        >
          <Select
            mode="tags"
            placeholder="输入协议名称，如: a2a, http, grpc"
            options={[
              { label: 'A2A', value: 'a2a' },
              { label: 'HTTP', value: 'http' },
              { label: 'gRPC', value: 'grpc' },
              { label: 'WebSocket', value: 'websocket' }
            ]}
          />
        </Form.Item>

        <Form.Item
          label="配置 Schema"
          name="configSchema"
          help="JSON 格式的 Agent 配置 Schema"
        >
          <TextArea
            rows={8}
            placeholder='{"type": "object", "properties": {...}}'
          />
        </Form.Item>
      </>
    );
  };

  // 渲染 Model 配置表单
  const renderModelConfig = () => {
    const templates = getTemplates();
    const hasTemplates = templates.length > 0;

    return (
      <>

        {hasTemplates && creationMode === 'TEMPLATE' && (
          <Form.Item label="选择模板">
            <Select 
              options={templates} 
              onChange={handleTemplateChange}
              placeholder="请选择路由模板"
            />
          </Form.Item>
        )}

        <Collapse 
          defaultActiveKey={['match']} 
          className="mt-4"
          items={[
            {
              key: 'match',
              label: '路由匹配配置 (Match Config)',
              children: (
                <>
                  <Form.Item
                    label="路径匹配类型"
                    name={['matchConfig', 'path', 'type']}
                  >
                    <Select options={MATCH_TYPES} placeholder="选择匹配类型" />
                  </Form.Item>

                  <Form.Item
                    label="路径匹配值"
                    name={['matchConfig', 'path', 'value']}
                  >
                    <Input placeholder="/v1/chat/completions" />
                  </Form.Item>

                  <Form.Item
                    label="HTTP 方法"
                    name={['matchConfig', 'methods']}
                  >
                    <Select
                      mode="multiple"
                      options={HTTP_METHODS}
                      placeholder="选择方法（可多选）"
                    />
                  </Form.Item>

                  <Form.List name={['matchConfig', 'headers']}>
                    {(fields, { add, remove }) => (
                      <div className="mt-4">
                        <div className="mb-2 font-medium">Header 匹配</div>
                        {fields.map(({ key, name, ...restField }) => (
                          <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                            <Form.Item
                              {...restField}
                              name={[name, 'name']}
                              rules={[{ required: true, message: '请输入 Header 名称' }]}
                            >
                              <Input placeholder="Header 名称" />
                            </Form.Item>
                            <Form.Item
                              {...restField}
                              name={[name, 'type']}
                              initialValue="Exact"
                            >
                              <Select options={MATCH_TYPES} style={{ width: 120 }} />
                            </Form.Item>
                            <Form.Item
                              {...restField}
                              name={[name, 'value']}
                              rules={[{ required: true, message: '请输入匹配值' }]}
                            >
                              <Input placeholder="匹配值" />
                            </Form.Item>
                            <MinusCircleOutlined onClick={() => remove(name)} />
                          </Space>
                        ))}
                        <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                          添加 Header 匹配规则
                        </Button>
                      </div>
                    )}
                  </Form.List>

                  <Form.List name={['matchConfig', 'queryParams']}>
                    {(fields, { add, remove }) => (
                      <div className="mt-4">
                        <div className="mb-2 font-medium">Query 参数匹配</div>
                        {fields.map(({ key, name, ...restField }) => (
                          <Space key={key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                            <Form.Item
                              {...restField}
                              name={[name, 'name']}
                              rules={[{ required: true, message: '请输入参数名称' }]}
                            >
                              <Input placeholder="参数名称" />
                            </Form.Item>
                            <Form.Item
                              {...restField}
                              name={[name, 'type']}
                              initialValue="Exact"
                            >
                              <Select options={MATCH_TYPES} style={{ width: 120 }} />
                            </Form.Item>
                            <Form.Item
                              {...restField}
                              name={[name, 'value']}
                              rules={[{ required: true, message: '请输入匹配值' }]}
                            >
                              <Input placeholder="匹配值" />
                            </Form.Item>
                            <MinusCircleOutlined onClick={() => remove(name)} />
                          </Space>
                        ))}
                        <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                          添加 Query 参数匹配规则
                        </Button>
                      </div>
                    )}
                  </Form.List>
                </>
              )
            }
          ]}
        />
      </>
    );
  };

  // 根据类型渲染不同的表单
  const renderConfigForm = () => {
    switch (type) {
      case 'MCP_TOOL':
        return renderMCPToolConfig();
      case 'REST_ROUTE':
        return renderRESTRouteConfig();
      case 'AGENT':
        return renderAgentConfig();
      case 'MODEL':
        return renderModelConfig();
      default:
        return (
          <Form.Item label="配置">
            <TextArea
              rows={8}
              placeholder="请输入 JSON 格式的配置"
            />
          </Form.Item>
        );
    }
  };

  return (
    <Form
      form={form}
      layout="vertical"
      onValuesChange={handleValuesChange}
      component={false}
    >
      {renderConfigForm()}
    </Form>
  );
}
