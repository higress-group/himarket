import { ReloadOutlined } from '@ant-design/icons';
import { Form, Select, Alert, Button, Spin } from 'antd';
import { useState, useEffect, useCallback } from 'react';

import { gatewayApi } from '@/lib/api';
import { getGatewayTypeLabel } from '@/lib/constant';
import type { Gateway } from '@/types/gateway';

interface McpServerItem {
  mcpServerName: string;
  type?: string;
  [key: string]: unknown;
}

const MCP_GATEWAY_TYPES = ['HIGRESS', 'APIG_AI', 'ADP_AI_GATEWAY', 'APSARA_GATEWAY'] as const;

export default function GatewayImportStep() {
  const form = Form.useFormInstance();

  const [gateways, setGateways] = useState<Gateway[]>([]);
  const [gatewayLoading, setGatewayLoading] = useState(false);
  const [gatewayError, setGatewayError] = useState<string | null>(null);

  const [selectedGatewayId, setSelectedGatewayId] = useState<string | undefined>();
  const [selectedGatewayType, setSelectedGatewayType] = useState<string | undefined>();
  const [mcpServers, setMcpServers] = useState<McpServerItem[]>([]);
  const [mcpLoading, setMcpLoading] = useState(false);
  const [mcpError, setMcpError] = useState<string | null>(null);

  const fetchGateways = useCallback(async () => {
    setGatewayLoading(true);
    setGatewayError(null);
    try {
      const res = await gatewayApi.getGateways({ page: 1, size: 1000 });
      const all: Gateway[] = res.data?.content || [];
      setGateways(
        all.filter((g) => (MCP_GATEWAY_TYPES as readonly string[]).includes(g.gatewayType)),
      );
    } catch {
      setGatewayError('获取网关列表失败，请重试');
    } finally {
      setGatewayLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchGateways();
  }, [fetchGateways]);

  const fetchMcpServers = useCallback(async (gatewayId: string) => {
    setMcpLoading(true);
    setMcpError(null);
    setMcpServers([]);
    try {
      const res = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 500 });
      setMcpServers(res.data?.content || []);
    } catch {
      setMcpError('获取 MCP Server 列表失败，请重试');
    } finally {
      setMcpLoading(false);
    }
  }, []);

  const handleGatewayChange = (gatewayId: string) => {
    setSelectedGatewayId(gatewayId);
    const gateway = gateways.find((g) => g.gatewayId === gatewayId);
    setSelectedGatewayType(gateway?.gatewayType);
    form.setFieldsValue({ gatewayId, gatewayRefConfig: undefined, mcpName: undefined });
    fetchMcpServers(gatewayId);
  };

  const handleMcpServerChange = (mcpServerName: string) => {
    form.setFieldsValue({
      gatewayRefConfig: { fromGatewayType: selectedGatewayType, mcpServerName },
      mcpName: mcpServerName,
    });
  };

  return (
    <div className="space-y-4">
      {gatewayError && (
        <Alert
          action={
            <Button icon={<ReloadOutlined />} onClick={fetchGateways} size="small">
              重试
            </Button>
          }
          message={gatewayError}
          showIcon
          type="error"
        />
      )}
      {mcpError && (
        <Alert
          action={
            <Button
              icon={<ReloadOutlined />}
              onClick={() => selectedGatewayId && fetchMcpServers(selectedGatewayId)}
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

      <Form.Item label="选择网关实例" required>
        <Select
          className="w-full"
          filterOption={(input, option) =>
            ((option?.label as unknown as string) || '').toLowerCase().includes(input.toLowerCase())
          }
          loading={gatewayLoading}
          notFoundContent={gatewayLoading ? <Spin size="small" /> : '暂无可用网关'}
          onChange={handleGatewayChange}
          optionLabelProp="label"
          placeholder="请选择网关实例"
          showSearch
          value={selectedGatewayId}
        >
          {gateways.map((gw) => (
            <Select.Option key={gw.gatewayId} label={gw.gatewayName} value={gw.gatewayId}>
              <div>
                <div className="font-medium">{gw.gatewayName}</div>
                <div className="text-sm text-gray-500">
                  {gw.gatewayId} - {getGatewayTypeLabel(gw.gatewayType)}
                </div>
              </div>
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item label="选择 MCP Server" required>
        <Select
          className="w-full"
          disabled={!selectedGatewayId}
          filterOption={(input, option) =>
            ((option?.children as unknown as string) || '')
              .toLowerCase()
              .includes(input.toLowerCase())
          }
          loading={mcpLoading}
          notFoundContent={mcpLoading ? <Spin size="small" /> : '暂无 MCP Server'}
          onChange={handleMcpServerChange}
          placeholder={selectedGatewayId ? '请选择 MCP Server' : '请先选择网关实例'}
          showSearch
          value={form.getFieldValue('mcpName')}
        >
          {mcpServers.map((s) => (
            <Select.Option key={s.mcpServerName} value={s.mcpServerName}>
              {s.mcpServerName}
            </Select.Option>
          ))}
        </Select>
      </Form.Item>

      <Form.Item hidden name="gatewayId">
        <input type="hidden" />
      </Form.Item>
      <Form.Item hidden name="gatewayRefConfig">
        <input type="hidden" />
      </Form.Item>
    </div>
  );
}
