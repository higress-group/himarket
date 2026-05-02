import { Modal, Form, Input, Select, message } from 'antd';
import { useState } from 'react';

import { apiDefinitionApi } from '@/lib/api';

interface McpQuickCreateModalProps {
  visible: boolean;
  productId: string;
  onCancel: () => void;
  onSuccess: () => void;
}

function parseLinesToArray(text: string | undefined): string[] | undefined {
  if (!text) return undefined;
  const lines = text
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  return lines.length > 0 ? lines : undefined;
}

function parseEnvLines(text: string | undefined): Record<string, string> | undefined {
  if (!text) return undefined;
  const env: Record<string, string> = {};
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const idx = trimmed.indexOf('=');
    if (idx > 0) {
      env[trimmed.slice(0, idx).trim()] = trimmed.slice(idx + 1).trim();
    }
  }
  return Object.keys(env).length > 0 ? env : undefined;
}

export function McpQuickCreateModal({
  onCancel,
  onSuccess,
  productId,
  visible,
}: McpQuickCreateModalProps) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [protocol, setProtocol] = useState<string>('SSE');

  const isStdio = protocol === 'STDIO';

  const buildConnection = (values: Record<string, unknown>): Record<string, unknown> => {
    if (isStdio) {
      return {
        args: parseLinesToArray(values.argsText as string | undefined),
        command: values.command,
        env: parseEnvLines(values.envText as string | undefined),
        protocol: 'STDIO',
      };
    }
    return {
      protocol: values.protocol,
      url: values.url,
    };
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      await apiDefinitionApi.createApiDefinition({
        description: values.description,
        name: values.name,
        relatedProductId: productId,
        spec: {
          connection: buildConnection(values),
          fromType: 'NATIVE_MCP',
          protocol: values.protocol,
          type: 'MCP_SERVER',
        },
        type: 'MCP_SERVER',
      });

      message.success('MCP创建成功');
      form.resetFields();
      setProtocol('SSE');
      onSuccess();
    } catch (e: unknown) {
      if ((e as { errorFields?: unknown[] }).errorFields) {
        return;
      }
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message || '创建失败',
      );
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    setProtocol('SSE');
    onCancel();
  };

  const handleProtocolChange = (value: string) => {
    setProtocol(value);
    form.setFieldsValue({
      argsText: undefined,
      command: undefined,
      envText: undefined,
      url: undefined,
    });
  };

  return (
    <Modal
      confirmLoading={loading}
      okText="创建"
      onCancel={handleCancel}
      onOk={handleSubmit}
      open={visible}
      title="创建MCP"
      width={640}
    >
      <Form form={form} layout="vertical">
        <Form.Item label="名称" name="name" rules={[{ message: '请输入名称', required: true }]}>
          <Input placeholder="MCP Server名称" />
        </Form.Item>

        <Form.Item label="描述" name="description">
          <Input.TextArea placeholder="可选描述" rows={2} />
        </Form.Item>

        <Form.Item
          initialValue="SSE"
          label="传输协议"
          name="protocol"
          rules={[{ message: '请选择传输协议', required: true }]}
        >
          <Select
            onChange={handleProtocolChange}
            options={[
              { label: 'SSE', value: 'SSE' },
              { label: 'Streamable HTTP', value: 'STREAMABLE_HTTP' },
              { label: 'Stdio', value: 'STDIO' },
            ]}
            placeholder="选择传输协议"
          />
        </Form.Item>

        {!isStdio && (
          <Form.Item label="URL" name="url" rules={[{ message: '请输入URL', required: true }]}>
            <Input
              placeholder={
                protocol === 'SSE'
                  ? '例如:https://www.example.com/mcp/sse'
                  : '例如:https://www.example.com/mcp'
              }
            />
          </Form.Item>
        )}

        {isStdio && (
          <>
            <Form.Item
              label="命令"
              name="command"
              rules={[{ message: '请输入命令', required: true }]}
            >
              <Input placeholder="例如:node" />
            </Form.Item>

            <Form.Item label="参数" name="argsText">
              <Input.TextArea placeholder="每行一个参数" rows={3} />
            </Form.Item>

            <Form.Item label="环境变量" name="envText">
              <Input.TextArea placeholder="每行一个key=value" rows={3} />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}
