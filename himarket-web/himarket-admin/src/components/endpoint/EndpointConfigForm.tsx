import { Form, Input, Select, Button, Space, Collapse } from 'antd';
import { PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { useEffect } from 'react';
import type { EndpointType, EndpointConfig } from '@/types/endpoint';

const { TextArea } = Input;
const { Panel } = Collapse;

interface EndpointConfigFormProps {
  type: EndpointType;
  value?: EndpointConfig;
  onChange?: (value: EndpointConfig) => void;
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

export default function EndpointConfigForm({ type, value, onChange }: EndpointConfigFormProps) {
  const [form] = Form.useForm();

  // 监听 value 变化，更新表单
  useEffect(() => {
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
    const values = form.getFieldsValue();
    onChange?.(values);
  };

  // 渲染 MCP Tool 配置表单
  const renderMCPToolConfig = () => {
    return (
      <>
        <Collapse defaultActiveKey={['schema']} className="mb-4">
          <Panel header="Schema 定义" key="schema">
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
          </Panel>

          <Panel header="请求模板 (Request Template) - 高级设置" key="request">
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
                  <>
                    {fields.map((field) => (
                      <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'key']}
                          noStyle
                        >
                          <Input placeholder="Header 名称" style={{ width: 200 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
                          name={[field.name, 'value']}
                          noStyle
                        >
                          <Input placeholder="Header 值" style={{ width: 300 }} />
                        </Form.Item>
                        <MinusCircleOutlined onClick={() => remove(field.name)} />
                      </Space>
                    ))}
                    <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                      添加请求头
                    </Button>
                  </>
                )}
              </Form.List>
            </Form.Item>

            <Form.Item label="请求体模板 (Body)" name={['requestTemplate', 'body']}>
              <TextArea rows={4} placeholder='{"key": "value"}' />
            </Form.Item>
          </Panel>

          <Panel header="响应模板 (Response Template) - 高级设置" key="response">
            <Form.Item label="响应体模板" name={['responseTemplate', 'body']}>
              <TextArea rows={4} placeholder="响应处理逻辑" />
            </Form.Item>
          </Panel>
        </Collapse>
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

        <Collapse defaultActiveKey={['params']} className="mb-4">
          <Panel header="请求参数 (Query Parameters)" key="params">
            <Form.List name="parameters">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <div key={field.key} className="border p-4 mb-4 rounded">
                      <Space style={{ display: 'flex', marginBottom: 8 }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'name']}
                          label="参数名"
                          noStyle
                        >
                          <Input placeholder="参数名" style={{ width: 150 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
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
                        {...field}
                        name={[field.name, 'description']}
                        label="描述"
                      >
                        <Input placeholder="参数描述" />
                      </Form.Item>
                    </div>
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    添加 Query 参数
                  </Button>
                </>
              )}
            </Form.List>
          </Panel>

          <Panel header="请求头 (Headers)" key="headers">
            <Form.List name="headers">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <div key={field.key} className="border p-4 mb-4 rounded">
                      <Space style={{ display: 'flex', marginBottom: 8 }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'name']}
                          label="Header 名"
                          noStyle
                        >
                          <Input placeholder="Header 名" style={{ width: 150 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
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
                        {...field}
                        name={[field.name, 'description']}
                        label="描述"
                      >
                        <Input placeholder="Header 描述" />
                      </Form.Item>
                    </div>
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    添加 Header
                  </Button>
                </>
              )}
            </Form.List>
          </Panel>

          <Panel header="路径参数 (Path Parameters)" key="pathParams">
            <Form.List name="pathParams">
              {(fields, { add, remove }) => (
                <>
                  {fields.map((field) => (
                    <div key={field.key} className="border p-4 mb-4 rounded">
                      <Space style={{ display: 'flex', marginBottom: 8 }}>
                        <Form.Item
                          {...field}
                          name={[field.name, 'name']}
                          label="参数名"
                          noStyle
                        >
                          <Input placeholder="参数名" style={{ width: 150 }} />
                        </Form.Item>
                        <Form.Item
                          {...field}
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
                        {...field}
                        name={[field.name, 'description']}
                        label="描述"
                      >
                        <Input placeholder="参数描述" />
                      </Form.Item>
                    </div>
                  ))}
                  <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                    添加 Path 参数
                  </Button>
                </>
              )}
            </Form.List>
          </Panel>

          <Panel header="请求体定义 (Request Body) - JSON Schema" key="requestBody">
            <Form.Item name="requestBody">
              <TextArea
                rows={6}
                placeholder='{"type": "object", "properties": {...}}'
              />
            </Form.Item>
          </Panel>
        </Collapse>
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
    return (
      <>
        <Form.Item
          label="模型名称"
          name="modelName"
          rules={[{ required: true, message: '请输入模型名称' }]}
        >
          <Input placeholder="例如：gpt-4, qwen-max" />
        </Form.Item>

        <Form.Item label="模型类别" name="modelCategory">
          <Input placeholder="例如：LLM, Embedding, Text2Image" />
        </Form.Item>

        <Form.Item
          label="支持的 AI 协议"
          name="aiProtocols"
        >
          <Select
            mode="tags"
            placeholder="输入协议名称，如: openai, claude"
            options={[
              { label: 'OpenAI', value: 'openai' },
              { label: 'Claude', value: 'claude' },
              { label: 'Gemini', value: 'gemini' }
            ]}
          />
        </Form.Item>

        <Collapse defaultActiveKey={['match']} className="mt-4">
          <Panel header="路由匹配配置 (Match Config)" key="match">
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
          </Panel>
        </Collapse>
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
    >
      {renderConfigForm()}
    </Form>
  );
}
