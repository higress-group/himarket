import { ReloadOutlined } from '@ant-design/icons';
import { Form, Select, Alert, Button, Spin } from 'antd';
import { useState, useEffect, useCallback } from 'react';

import { nacosApi } from '@/lib/api';

interface NacosInstance {
  nacosId: string;
  nacosName: string;
  nacosType?: 'OPEN_SOURCE' | 'MSE';
  serverUrl?: string;
}

interface Namespace {
  namespaceId: string;
  namespaceName: string;
  namespaceDesc?: string;
}

interface McpServerItem {
  mcpServerName: string;
  version?: string;
}

export default function NacosImportStep() {
  const form = Form.useFormInstance();

  const [nacosInstances, setNacosInstances] = useState<NacosInstance[]>([]);
  const [nacosLoading, setNacosLoading] = useState(false);
  const [nacosError, setNacosError] = useState<string | null>(null);

  const [selectedNacosId, setSelectedNacosId] = useState<string | undefined>();
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const [nsLoading, setNsLoading] = useState(false);
  const [nsError, setNsError] = useState<string | null>(null);

  const [selectedNamespaceId, setSelectedNamespaceId] = useState<string | undefined>();
  const [mcpServers, setMcpServers] = useState<McpServerItem[]>([]);
  const [mcpLoading, setMcpLoading] = useState(false);
  const [mcpError, setMcpError] = useState<string | null>(null);

  const fetchNacosInstances = useCallback(async () => {
    setNacosLoading(true);
    setNacosError(null);
    try {
      const res = await nacosApi.getNacos({ page: 1, size: 1000 });
      setNacosInstances(res.data?.content || []);
    } catch {
      setNacosError('获取 Nacos 实例列表失败');
    } finally {
      setNacosLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchNacosInstances();
  }, [fetchNacosInstances]);

  const fetchNamespaces = useCallback(async (nacosId: string) => {
    setNsLoading(true);
    setNsError(null);
    setNamespaces([]);
    setSelectedNamespaceId(undefined);
    setMcpServers([]);
    try {
      const res = await nacosApi.getNamespaces(nacosId, { page: 1, size: 1000 });
      const content =
        ((res as unknown as Record<string, unknown>).data as Record<string, unknown> | undefined)
          ?.content || [];
      setNamespaces(
        (content as Array<Record<string, unknown>>).map((ns) => ({
          namespaceDesc: String(ns.namespaceDesc ?? ''),
          namespaceId: String(ns.namespaceId ?? ''),
          namespaceName: String((ns.namespaceName || ns.namespaceId) ?? ''),
        })),
      );
    } catch {
      setNsError('获取命名空间列表失败');
    } finally {
      setNsLoading(false);
    }
  }, []);

  const fetchMcpServers = useCallback(async (nacosId: string, namespaceId: string) => {
    setMcpLoading(true);
    setMcpError(null);
    setMcpServers([]);
    try {
      const res = await nacosApi.getNacosMcpServers(nacosId, { namespaceId, page: 1, size: 1000 });
      setMcpServers(res.data?.content || []);
    } catch {
      setMcpError('获取 MCP Server 列表失败');
    } finally {
      setMcpLoading(false);
    }
  }, []);

  const handleNacosChange = (nacosId: string) => {
    setSelectedNacosId(nacosId);
    form.setFieldsValue({ mcpName: undefined, nacosId, nacosRefConfig: undefined });
    fetchNamespaces(nacosId);
  };

  const handleNamespaceChange = (namespaceId: string) => {
    setSelectedNamespaceId(namespaceId);
    form.setFieldsValue({ mcpName: undefined, nacosRefConfig: undefined });
    if (selectedNacosId) fetchMcpServers(selectedNacosId, namespaceId);
  };

  const handleMcpServerChange = (mcpServerName: string) => {
    form.setFieldsValue({
      mcpName: mcpServerName,
      nacosRefConfig: { mcpServerName, namespaceId: selectedNamespaceId },
    });
  };

  return (
    <div className="space-y-4">
      {nacosError && (
        <Alert
          action={
            <Button icon={<ReloadOutlined />} onClick={fetchNacosInstances} size="small">
              重试
            </Button>
          }
          message={nacosError}
          showIcon
          type="error"
        />
      )}
      {nsError && (
        <Alert
          action={
            <Button
              icon={<ReloadOutlined />}
              onClick={() => selectedNacosId && fetchNamespaces(selectedNacosId)}
              size="small"
            >
              重试
            </Button>
          }
          message={nsError}
          showIcon
          type="error"
        />
      )}
      {mcpError && (
        <Alert
          action={
            <Button
              icon={<ReloadOutlined />}
              onClick={() =>
                selectedNacosId &&
                selectedNamespaceId &&
                fetchMcpServers(selectedNacosId, selectedNamespaceId)
              }
              size="small"
            >
              重试
            </Button>
          }
          message={mcpError}
          showIcon
          type="error"
        />
      )}

      <Form.Item label="选择 Nacos 实例" required>
        <Select
          className="w-full"
          filterOption={(input, option) =>
            ((option?.label as unknown as string) || '').toLowerCase().includes(input.toLowerCase())
          }
          loading={nacosLoading}
          notFoundContent={nacosLoading ? <Spin size="small" /> : '暂无可用 Nacos 实例'}
          onChange={handleNacosChange}
          optionLabelProp="label"
          placeholder="请选择 Nacos 实例"
          showSearch
          value={selectedNacosId}
        >
          {nacosInstances.map((n) => (
            <Select.Option key={n.nacosId} label={n.nacosName} value={n.nacosId}>
              <div>
                <div className="font-medium">{n.nacosName}</div>
                <div className="text-sm text-gray-500">
                  {n.serverUrl || n.nacosId}
                  {n.nacosType && ` - ${n.nacosType}`}
                </div>
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item label="选择命名空间" required>
        <Select
          className="w-full"
          disabled={!selectedNacosId}
          filterOption={(input, option) =>
            ((option?.children as unknown as string) || '')
              .toLowerCase()
              .includes(input.toLowerCase())
          }
          loading={nsLoading}
          notFoundContent={nsLoading ? <Spin size="small" /> : '暂无命名空间'}
          onChange={handleNamespaceChange}
          placeholder={selectedNacosId ? '请选择命名空间' : '请先选择 Nacos 实例'}
          showSearch
          value={selectedNamespaceId}
        >
          {namespaces.map((ns) => (
            <Select.Option key={ns.namespaceId} value={ns.namespaceId}>
              {ns.namespaceName}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item label="选择 MCP Server" required>
        <Select
          className="w-full"
          disabled={!selectedNamespaceId}
          filterOption={(input, option) =>
            ((option?.children as unknown as string) || '')
              .toLowerCase()
              .includes(input.toLowerCase())
          }
          loading={mcpLoading}
          notFoundContent={mcpLoading ? <Spin size="small" /> : '暂无 MCP Server'}
          onChange={handleMcpServerChange}
          placeholder={selectedNamespaceId ? '请选择 MCP Server' : '请先选择命名空间'}
          showSearch
          value={form.getFieldValue('mcpName')}
        >
          {mcpServers.map((s) => (
            <Select.Option key={s.mcpServerName} value={s.mcpServerName}>
              <div className="flex items-center justify-between">
                <span>{s.mcpServerName}</span>
                {s.version && <span className="text-xs text-gray-400 ml-2">v{s.version}</span>}
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item hidden name="nacosId">
        <input type="hidden" />
      </Form.Item>
      <Form.Item hidden name="nacosRefConfig">
        <input type="hidden" />
      </Form.Item>
    </div>
  );
}
