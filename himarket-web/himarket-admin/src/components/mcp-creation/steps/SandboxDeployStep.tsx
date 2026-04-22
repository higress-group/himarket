import { Form, Select, Radio, Input, Tag, message } from 'antd';
import { useState, useEffect } from 'react';

import { sandboxApi } from '@/lib/api';

import type { ExtraParam } from '../types';

interface SandboxItem {
  sandboxId: string;
  sandboxName: string;
}

/**
 * SandboxDeployStep — 沙箱部署配置步骤。
 *
 * 仅当 McpConfigStep 中 sandboxRequired === true 时展示。
 * 渲染在父级 Form 内部，使用 Form.useFormInstance() 获取表单实例。
 */
export default function SandboxDeployStep() {
  const form = Form.useFormInstance();

  // ── 沙箱实例列表 ──
  const [sandboxList, setSandboxList] = useState<SandboxItem[]>([]);
  const [sandboxLoading, setSandboxLoading] = useState(false);

  // ── Namespace 列表 ──
  const [namespaceList, setNamespaceList] = useState<string[]>([]);
  const [namespaceLoading, setNamespaceLoading] = useState(false);

  // ── 资源规格预设 ──
  const resourcePreset = Form.useWatch('resourcePreset', form);

  // ── 额外参数（从 McpConfigStep 读取） ──
  const extraParams: ExtraParam[] = Form.useWatch('extraParams', form) || [];

  // 加载沙箱实例列表
  useEffect(() => {
    setSandboxLoading(true);
    sandboxApi
      .getActiveSandboxes()
      .then((res: unknown) => {
        const list = (res as Record<string, unknown>).data ?? [];
        setSandboxList(Array.isArray(list) ? (list as SandboxItem[]) : []);
      })
      .catch(() => {
        message.error('获取沙箱实例列表失败');
        setSandboxList([]);
      })
      .finally(() => setSandboxLoading(false));
  }, []);

  // 选择沙箱后加载 Namespace
  const handleSandboxChange = async (sandboxId: string) => {
    setNamespaceList([]);
    form.setFieldsValue({ namespace: undefined });
    setNamespaceLoading(true);
    try {
      const res: unknown = await sandboxApi.listNamespaces(sandboxId);
      const list = (res as Record<string, unknown>).data ?? (Array.isArray(res) ? res : []);
      setNamespaceList(Array.isArray(list) ? (list as string[]) : []);
    } catch {
      message.error('获取 Namespace 列表失败');
      setNamespaceList([]);
    } finally {
      setNamespaceLoading(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* ── 部署目标 ── */}
      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
          <span className="text-xs font-medium text-gray-600">部署目标</span>
        </div>
        <div className="p-4 space-y-3">
          <Form.Item
            className="mb-0"
            label="沙箱实例"
            name="sandboxId"
            rules={[{ message: '请选择沙箱实例', required: true }]}
          >
            <Select
              loading={sandboxLoading}
              onChange={handleSandboxChange}
              options={sandboxList.map((s) => ({
                label: s.sandboxName,
                value: s.sandboxId,
              }))}
              placeholder="选择沙箱实例"
            />
          </Form.Item>

          <Form.Item
            className="mb-0"
            extra={
              !form.getFieldValue('sandboxId') ? (
                <span className="text-[10px] text-gray-400">请先选择沙箱实例</span>
              ) : undefined
            }
            label="Namespace"
            name="namespace"
            rules={[{ message: '请选择 Namespace', required: true }]}
          >
            <Select
              disabled={!form.getFieldValue('sandboxId')}
              loading={namespaceLoading}
              options={namespaceList.map((ns) => ({ label: ns, value: ns }))}
              placeholder={namespaceLoading ? '加载中...' : '选择 Namespace'}
              showSearch
            />
          </Form.Item>

          <Form.Item className="mb-0" initialValue="sse" label="传输协议" name="transportType">
            <Radio.Group buttonStyle="solid" optionType="button" size="small">
              <Radio.Button value="sse">SSE</Radio.Button>
              <Radio.Button value="http">Streamable HTTP</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item className="mb-0" initialValue="none" label="鉴权方式" name="authType">
            <Radio.Group buttonStyle="solid" optionType="button" size="small">
              <Radio.Button value="none">无鉴权</Radio.Button>
              <Radio.Button value="bearer">Bearer Token</Radio.Button>
            </Radio.Group>
          </Form.Item>
        </div>
      </div>

      {/* ── 资源规格 ── */}
      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
          <span className="text-xs font-medium text-gray-600">资源规格</span>
        </div>
        <div className="p-4">
          <Form.Item
            className={resourcePreset === 'custom' ? 'mb-3' : 'mb-0'}
            initialValue="small"
            name="resourcePreset"
          >
            <Radio.Group
              className="w-full"
              onChange={(e) => {
                const presets: Record<string, Record<string, string>> = {
                  large: {
                    cpuLimit: '2',
                    cpuRequest: '1',
                    ephemeralStorage: '4Gi',
                    memoryLimit: '2Gi',
                    memoryRequest: '1Gi',
                  },
                  medium: {
                    cpuLimit: '1',
                    cpuRequest: '500m',
                    ephemeralStorage: '2Gi',
                    memoryLimit: '1Gi',
                    memoryRequest: '512Mi',
                  },
                  small: {
                    cpuLimit: '500m',
                    cpuRequest: '250m',
                    ephemeralStorage: '1Gi',
                    memoryLimit: '512Mi',
                    memoryRequest: '256Mi',
                  },
                };
                const p = presets[e.target.value];
                if (p) form.setFieldsValue(p);
              }}
            >
              <div className="grid grid-cols-4 gap-2">
                {[
                  { desc: '0.5C / 512Mi', label: '小型', value: 'small' },
                  { desc: '1C / 1Gi', label: '中型', value: 'medium' },
                  { desc: '2C / 2Gi', label: '大型', value: 'large' },
                  { desc: '手动配置', label: '自定义', value: 'custom' },
                ].map((item) => (
                  <Radio.Button
                    className="h-auto text-center flex-1"
                    key={item.value}
                    style={{ lineHeight: 1.3, padding: '8px 0' }}
                    value={item.value}
                  >
                    <div className="text-xs font-medium">{item.label}</div>
                    <div className="text-[10px] text-gray-400 mt-0.5">{item.desc}</div>
                  </Radio.Button>
                ))}
              </div>
            </Radio.Group>
          </Form.Item>

          {resourcePreset === 'custom' ? (
            <div className="grid grid-cols-2 gap-x-3 gap-y-2 pt-1 border-t border-gray-100">
              <Form.Item className="mb-0" initialValue="250m" label="CPU Request" name="cpuRequest">
                <Input className="font-mono text-xs" size="small" />
              </Form.Item>
              <Form.Item className="mb-0" initialValue="500m" label="CPU Limit" name="cpuLimit">
                <Input className="font-mono text-xs" size="small" />
              </Form.Item>
              <Form.Item
                className="mb-0"
                initialValue="256Mi"
                label="Memory Request"
                name="memoryRequest"
              >
                <Input className="font-mono text-xs" size="small" />
              </Form.Item>
              <Form.Item
                className="mb-0"
                initialValue="512Mi"
                label="Memory Limit"
                name="memoryLimit"
              >
                <Input className="font-mono text-xs" size="small" />
              </Form.Item>
              <Form.Item
                className="mb-0"
                initialValue="1Gi"
                label="临时存储"
                name="ephemeralStorage"
              >
                <Input className="font-mono text-xs" size="small" />
              </Form.Item>
            </div>
          ) : (
            <>
              <Form.Item hidden initialValue="250m" name="cpuRequest">
                <Input />
              </Form.Item>
              <Form.Item hidden initialValue="500m" name="cpuLimit">
                <Input />
              </Form.Item>
              <Form.Item hidden initialValue="256Mi" name="memoryRequest">
                <Input />
              </Form.Item>
              <Form.Item hidden initialValue="512Mi" name="memoryLimit">
                <Input />
              </Form.Item>
              <Form.Item hidden initialValue="1Gi" name="ephemeralStorage">
                <Input />
              </Form.Item>
            </>
          )}
        </div>
      </div>

      {/* ── 参数值配置 ── */}
      {extraParams.length > 0 && (
        <div className="rounded-lg border border-gray-200 overflow-hidden">
          <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
            <span className="text-xs font-medium text-gray-600">参数值配置</span>
            <span className="text-[10px] text-gray-400 ml-2">部署时注入的环境变量 / 请求参数</span>
          </div>
          <div className="p-4 space-y-3">
            {extraParams.map((p) => (
              <div key={p.key}>
                <div className="flex items-center gap-1.5 mb-1">
                  <span className="text-xs font-mono text-gray-700">{p.name}</span>
                  {p.required && <span className="text-red-400 text-[10px]">*</span>}
                  <Tag className="m-0 border-0 bg-gray-100 text-gray-500 text-[10px] leading-tight px-1.5 py-0">
                    {p.position}
                  </Tag>
                </div>
                {p.description && (
                  <div className="text-[10px] text-gray-400 mb-1">{p.description}</div>
                )}
                <Form.Item
                  className="mb-0"
                  name={['paramValues', p.name]}
                  rules={p.required ? [{ message: `请输入 ${p.name}`, required: true }] : undefined}
                >
                  <Input
                    className="font-mono text-xs"
                    placeholder={p.example || `请输入 ${p.name}`}
                    size="small"
                  />
                </Form.Item>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
