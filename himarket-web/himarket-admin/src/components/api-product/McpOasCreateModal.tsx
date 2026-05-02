import { UploadOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { Modal, Button, Upload, message } from 'antd';
import * as yaml from 'js-yaml';
import { useState } from 'react';

import { apiDefinitionApi } from '@/lib/api';

interface McpOasCreateModalProps {
  visible: boolean;
  productId: string;
  onCancel: () => void;
  onSuccess: () => void;
}

interface OpenAPIToolArg {
  name: string;
  description?: string;
  type?: string;
  required?: boolean;
  default?: string;
  enum?: string[];
  position?: string;
  items?: Record<string, unknown>;
  properties?: Record<string, unknown>;
}

interface OpenAPITool {
  name: string;
  description?: string;
  args?: OpenAPIToolArg[];
  requestTemplate?: {
    url: string;
    method: string;
    queryParams?: Record<string, string>;
    headers?: Array<{ key: string; value: string }>;
    body?: string;
  };
  responseTemplate?: {
    prependBody?: string;
    appendBody?: string;
    body?: string;
  };
  errorResponseTemplate?: string;
  outputSchema?: Record<string, unknown>;
}

interface OpenAPIToolsConfig {
  format: 'OPEN_API';
  server: {
    name?: string;
    allowTools?: string[];
    config?: Record<string, unknown>;
    type?: string;
    transport?: string;
    mcpServerURL?: string;
    timeout?: number;
  };
  tools: OpenAPITool[];
}

interface ParseResult {
  name: string;
  description: string;
  url: string;
  toolsConfig: OpenAPIToolsConfig;
  toolCount: number;
}

function parseOasText(text: string): Record<string, unknown> | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  try {
    return JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    try {
      return yaml.load(trimmed) as Record<string, unknown>;
    } catch {
      return null;
    }
  }
}

function generateBodyTemplate(schema: Record<string, unknown>): string | undefined {
  const properties = schema.properties as Record<string, Record<string, unknown>> | undefined;
  if (!properties) return undefined;
  const template: Record<string, string> = {};
  for (const key of Object.keys(properties)) {
    template[key] = `{{${key}}}`;
  }
  return JSON.stringify(template, null, 2);
}

function convertOasToToolsConfig(oas: Record<string, unknown>): ParseResult | null {
  const info = oas.info as Record<string, unknown> | undefined;
  const name = String(info?.title || 'untitled').slice(0, 255);
  const description = String(info?.description || '').slice(0, 512);

  const servers = oas.servers as Array<{ url: string }> | undefined;
  const url = servers?.[0]?.url || '';

  const paths = oas.paths as Record<string, Record<string, unknown>> | undefined;
  if (!paths) return null;

  const tools: OpenAPITool[] = [];
  const allowTools: string[] = [];
  const httpMethods = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options'];

  for (const [path, methods] of Object.entries(paths)) {
    if (typeof methods !== 'object' || methods === null) continue;
    for (const [method, operation] of Object.entries(methods)) {
      if (typeof operation !== 'object' || operation === null) continue;
      if (!httpMethods.includes(method.toLowerCase())) continue;

      const op = operation as Record<string, unknown>;
      const toolName =
        (op.operationId as string) ||
        `${method}_${path.replace(/\//g, '_').replace(/[{}]/g, '').replace(/^_/, '')}`;
      const toolDescription = ((op.summary as string) || (op.description as string) || '').slice(
        0,
        512,
      );

      const args: OpenAPIToolArg[] = [];
      const queryParams: Record<string, string> = {};
      const headers: Array<{ key: string; value: string }> = [];

      const parameters = op.parameters as Array<Record<string, unknown>> | undefined;
      if (parameters) {
        for (const param of parameters) {
          const paramSchema = (param.schema as Record<string, unknown>) || {};
          const arg: OpenAPIToolArg = {
            description: String(param.description || ''),
            name: String(param.name || ''),
            position: String(param.in || 'query'),
            required: param.required === true,
            type: String(paramSchema.type || 'string'),
          };
          if (paramSchema.default !== undefined) arg.default = String(paramSchema.default);
          if (Array.isArray(paramSchema.enum)) arg.enum = paramSchema.enum.map(String);
          args.push(arg);

          if (param.in === 'query') {
            queryParams[String(param.name)] = `{{${param.name}}}`;
          } else if (param.in === 'header') {
            headers.push({ key: String(param.name), value: `{{${param.name}}}` });
          }
        }
      }

      let body: string | undefined;
      const requestBody = op.requestBody as Record<string, unknown>;
      if (requestBody) {
        const content = requestBody.content as Record<string, Record<string, unknown>> | undefined;
        const jsonContent = content?.['application/json'];
        const schema = jsonContent?.schema as Record<string, unknown> | undefined;
        if (schema) {
          body = generateBodyTemplate(schema);
          const properties = schema.properties as
            | Record<string, Record<string, unknown>>
            | undefined;
          const requiredList = schema.required as string[] | undefined;
          if (properties) {
            for (const [propName, propSchema] of Object.entries(properties)) {
              const existing = args.find((a) => a.name === propName);
              if (!existing) {
                args.push({
                  description: String(propSchema.description || ''),
                  name: propName,
                  position: 'body',
                  required: Array.isArray(requiredList) && requiredList.includes(propName),
                  type: String(propSchema.type || 'string'),
                });
              }
            }
          }
        }
      }

      tools.push({
        args: args.length > 0 ? args : undefined,
        description: toolDescription,
        name: toolName,
        requestTemplate: {
          body,
          headers: headers.length > 0 ? headers : undefined,
          method: method.toUpperCase(),
          queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined,
          url: path,
        },
      });
      allowTools.push(toolName);
    }
  }

  const toolsConfig: OpenAPIToolsConfig = {
    format: 'OPEN_API',
    server: {
      allowTools,
      mcpServerURL: url,
      name,
    },
    tools,
  };

  return { description, name, toolCount: tools.length, toolsConfig, url };
}

export function McpOasCreateModal({
  onCancel,
  onSuccess,
  productId,
  visible,
}: McpOasCreateModalProps) {
  const [oasText, setOasText] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [fileName, setFileName] = useState<string>('');

  const reset = () => {
    setOasText('');
    setFileName('');
  };

  const handleCancel = () => {
    reset();
    onCancel();
  };

  const handleFileRead = (file: File) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = String(e.target?.result || '');
      setOasText(content);
      setFileName(file.name);
      message.success(`已加载文件:${file.name}`);
    };
    reader.readAsText(file);
    return false;
  };

  const handleCreate = async () => {
    if (!oasText.trim()) {
      message.error('请先输入或上传OpenAPI Spec');
      return;
    }

    const oas = parseOasText(oasText);
    if (!oas) {
      message.error('OpenAPI Spec解析失败，请检查JSON/YAML格式');
      return;
    }

    const result = convertOasToToolsConfig(oas);
    if (!result || result.toolCount === 0) {
      message.error('未能从OpenAPI Spec中提取到有效工具，请检查内容');
      return;
    }

    setLoading(true);
    try {
      await apiDefinitionApi.createApiDefinition({
        description: result.description,
        name: result.name,
        relatedProductId: productId,
        spec: {
          connection: {
            protocol: 'STREAMABLE_HTTP',
            url: result.url,
          },
          fromType: 'HTTP_TO_MCP',
          protocol: 'STREAMABLE_HTTP',
          toolsConfig: result.toolsConfig,
          type: 'MCP_SERVER',
        },
        type: 'MCP_SERVER',
      });

      message.success(`HTTP转MCP创建成功，共${result.toolCount}个工具`);
      reset();
      onSuccess();
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message || '创建失败',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      confirmLoading={loading}
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={handleCancel}>取消</Button>
          <Button loading={loading} onClick={handleCreate} type="primary">
            创建
          </Button>
        </div>
      }
      onCancel={handleCancel}
      open={visible}
      title="HTTP转MCP"
      width={640}
    >
      <div className="space-y-3">
        <div className="text-xs text-gray-500">
          编辑或上传OpenAPI文件，系统自动解析并创建MCP Server
        </div>

        <div className="border rounded overflow-hidden relative" style={{ height: 360 }}>
          <Editor
            height="100%"
            language="yaml"
            onChange={(value) => setOasText(value || '')}
            options={{
              automaticLayout: true,
              folding: false,
              fontSize: 13,
              glyphMargin: false,
              lineNumbers: 'off',
              minimap: { enabled: false },
              overviewRulerLanes: 0,
              renderLineHighlight: 'none',
              scrollBeyondLastLine: false,
              tabSize: 2,
              wordWrap: 'on',
            }}
            theme="vs-light"
            value={oasText}
          />
          {!oasText && (
            <div className="absolute inset-0 pointer-events-none p-3 text-sm text-gray-400 font-mono whitespace-pre">
              {`openapi: 3.0.0\ninfo:\n  title: Demo API\n  version: 1.0.0\npaths:\n  /hello:\n    get:\n      summary: Hello\n      operationId: hello\n      responses:\n        '200':\n          description: OK`}
            </div>
          )}
        </div>

        <div className="flex items-center gap-3">
          <Upload
            accept=".json,.yaml,.yml"
            beforeUpload={handleFileRead}
            capture={undefined}
            hasControlInside={false}
            pastable={false}
            showUploadList={false}
          >
            <Button icon={<UploadOutlined />} size="small">
              {fileName ? `已选择:${fileName}` : '上传OpenAPI文件'}
            </Button>
          </Upload>
        </div>
      </div>
    </Modal>
  );
}
