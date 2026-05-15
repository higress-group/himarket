import Editor from '@monaco-editor/react';
import { Modal, Button, message } from 'antd';
import { useState } from 'react';

import { apiDefinitionApi } from '@/lib/api';

interface McpJsonImportModalProps {
  visible: boolean;
  productId: string;
  onCancel: () => void;
  onSuccess: () => void;
}

export function McpJsonImportModal({
  onCancel,
  onSuccess,
  productId,
  visible,
}: McpJsonImportModalProps) {
  const [jsonText, setJsonText] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (!jsonText.trim()) {
      message.error('请输入JSON配置');
      return;
    }

    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(jsonText.trim());
    } catch {
      message.error('JSON格式错误，请检查输入');
      return;
    }

    const mcpServers = parsed.mcpServers as Record<string, Record<string, unknown>> | undefined;
    if (!mcpServers || typeof mcpServers !== 'object') {
      message.error('JSON缺少mcpServers字段');
      return;
    }

    const keys = Object.keys(mcpServers);
    if (keys.length === 0) {
      message.error('mcpServers不能为空');
      return;
    }

    const name = keys[0];
    if (!name) {
      message.error('mcpServers不能为空');
      return;
    }
    const config = mcpServers[name];
    if (!config || typeof config !== 'object') {
      message.error('MCP配置格式错误');
      return;
    }

    let protocol: string;
    let connection: Record<string, unknown>;

    if (config.command) {
      protocol = 'STDIO';
      connection = {
        args: config.args,
        command: config.command,
        cwd: config.cwd,
        env: config.env,
        protocol: 'STDIO',
      };
    } else {
      const url = config.url as string | undefined;
      if (!url || typeof url !== 'string') {
        message.error('MCP配置中缺少url字段');
        return;
      }
      if (config.type === 'sse') {
        protocol = 'SSE';
      } else {
        protocol = 'STREAMABLE_HTTP';
      }
      connection = {
        protocol,
        url,
      };
    }

    setLoading(true);
    try {
      await apiDefinitionApi.createApiDefinition({
        meta: {
          source: {
            type: 'MANUAL',
          },
        },
        name,
        relatedProductId: productId,
        spec: {
          connection,
          fromType: 'NATIVE_MCP',
          protocol,
          type: 'MCP_SERVER',
        },
        type: 'MCP_SERVER',
      });

      message.success('JSON导入成功');
      setJsonText('');
      onSuccess();
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message || '导入失败',
      );
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    setJsonText('');
    onCancel();
  };

  return (
    <Modal
      confirmLoading={loading}
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={handleCancel}>取消</Button>
          <Button loading={loading} onClick={handleSubmit} type="primary">
            导入
          </Button>
        </div>
      }
      onCancel={handleCancel}
      open={visible}
      title="从JSON导入MCP"
      width={640}
    >
      <div className="space-y-3">
        <div className="text-xs text-gray-500">请输入JSON配置，系统自动解析并创建MCP Server</div>
        <div className="border rounded overflow-hidden relative" style={{ height: 360 }}>
          <Editor
            height="100%"
            language="json"
            onChange={(value) => setJsonText(value || '')}
            options={{
              automaticLayout: true,
              folding: false,
              fontSize: 13,
              formatOnPaste: true,
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
            value={jsonText}
          />
          {!jsonText && (
            <div className="absolute inset-0 pointer-events-none p-3 text-sm text-gray-400 font-mono whitespace-pre">
              {`{\n  "mcpServers": {\n    "my-mcp": {\n      "type": "sse",\n      "url": "http://localhost:8080/sse"\n    }\n  }\n}`}
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}
