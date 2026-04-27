import { Form, Input, Modal, Radio, Select, message } from 'antd';
import { useEffect, useState } from 'react';

import { mcpServerApi, sandboxApi } from '@/lib/api';
import type { ExtraParamDef, McpMetaItem } from '@/types/api-product';

interface DeployMcpModalProps {
  mcpServerId: string | null;
  productId: string;
  mcpMetaList: McpMetaItem[];
  onCancel: () => void;
  onDeploySuccess: () => void;
}

export function DeployMcpModal({
  mcpMetaList,
  mcpServerId,
  onCancel,
  onDeploySuccess,
  productId,
}: DeployMcpModalProps) {
  const [deployForm] = Form.useForm();
  const deploySandboxIdValue = Form.useWatch('sandboxId', deployForm);
  const [deploying, setDeploying] = useState(false);
  const [deploySandboxList, setDeploySandboxList] = useState<
    Array<{ sandboxId: string; sandboxName?: string }>
  >([]);
  const [deploySandboxLoading, setDeploySandboxLoading] = useState(false);
  const [deployNamespaceList, setDeployNamespaceList] = useState<string[]>([]);
  const [deployNamespaceLoading, setDeployNamespaceLoading] = useState(false);
  const [deployParamValues, setDeployParamValues] = useState<Record<string, string>>({});
  const [deployResourcePreset, setDeployResourcePreset] = useState('small');

  useEffect(() => {
    if (!mcpServerId) return;
    setDeploySandboxLoading(true);
    sandboxApi
      .getActiveSandboxes()
      .then((res) => {
        const list = res?.data || [];
        setDeploySandboxList(Array.isArray(list) ? list : []);
      })
      .catch(() => setDeploySandboxList([]))
      .finally(() => setDeploySandboxLoading(false));
    setDeployNamespaceList([]);
    setDeployNamespaceLoading(false);
    setDeployParamValues({});
  }, [mcpServerId]);

  const getDeployExtraParamDefs = (): ExtraParamDef[] => {
    const meta = mcpMetaList.find((m) => m.mcpServerId === mcpServerId);
    if (!meta?.extraParams) return [];
    try {
      const parsed =
        typeof meta.extraParams === 'string' ? JSON.parse(meta.extraParams) : meta.extraParams;
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  const handleDeploySandboxChange = async (sandboxId: string) => {
    setDeployNamespaceList([]);
    deployForm.setFieldsValue({ namespace: undefined });
    setDeployNamespaceLoading(true);
    try {
      const res = await sandboxApi.listNamespaces(sandboxId);
      const list = res?.data || res || [];
      setDeployNamespaceList(Array.isArray(list) ? list : []);
    } catch {
      message.error('获取 Namespace 列表失败');
      setDeployNamespaceList([]);
    } finally {
      setDeployNamespaceLoading(false);
    }
  };

  const handleDeploySandbox = async () => {
    if (!mcpServerId) return;
    try {
      const values = await deployForm.validateFields();
      const paramDefs = getDeployExtraParamDefs();
      const missingParams = paramDefs
        .filter((p) => p.required && !deployParamValues[p.name]?.trim())
        .map((p) => p.name);
      if (missingParams.length > 0) {
        message.error(`请填写必填参数: ${missingParams.join(', ')}`);
        return;
      }
      setDeploying(true);
      const paramValuesJson =
        paramDefs.length > 0 && Object.keys(deployParamValues).length > 0
          ? JSON.stringify(deployParamValues)
          : undefined;
      const resourceSpec =
        values.cpuRequest ||
        values.cpuLimit ||
        values.memoryRequest ||
        values.memoryLimit ||
        values.ephemeralStorage
          ? JSON.stringify({
              cpuLimit: values.cpuLimit || undefined,
              cpuRequest: values.cpuRequest || undefined,
              ephemeralStorage: values.ephemeralStorage || undefined,
              memoryLimit: values.memoryLimit || undefined,
              memoryRequest: values.memoryRequest || undefined,
            })
          : undefined;
      await mcpServerApi.deploySandbox(mcpServerId, {
        authType: values.authType || 'none',
        namespace: values.namespace,
        paramValues: paramValuesJson,
        resourceSpec,
        sandboxId: values.sandboxId,
        transportType: values.transportType || 'sse',
      });
      message.success('沙箱部署已提交，等待部署完成...');
      const targetMcpServerId = mcpServerId;
      onCancel();
      deployForm.resetFields();
      setDeployParamValues({});
      setDeployResourcePreset('small');
      const maxAttempts = 15;
      let deployed = false;
      for (let i = 0; i < maxAttempts; i++) {
        await new Promise((r) => setTimeout(r, 2000));
        const metaRes = await mcpServerApi.listMetaByProduct(productId);
        const metaList = metaRes?.data || [];
        const activeMeta = (Array.isArray(metaList) ? metaList : []).find(
          (m) => m.mcpServerId === targetMcpServerId,
        );
        if (activeMeta?.endpointStatus === 'ACTIVE' && activeMeta?.endpointUrl) {
          message.success('沙箱部署完成');
          deployed = true;
          break;
        }
      }
      if (!deployed) {
        message.warning('沙箱部署超时，请稍后刷新页面查看状态');
      }
      onDeploySuccess();
    } catch (e: unknown) {
      if ((e as { errorFields?: unknown }).errorFields) return;
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ||
          '沙箱部署失败',
      );
    } finally {
      setDeploying(false);
    }
  };

  const handleCancel = () => {
    onCancel();
    deployForm.resetFields();
    setDeployParamValues({});
    setDeployResourcePreset('small');
  };

  return (
    <Modal
      confirmLoading={deploying}
      destroyOnClose
      maskClosable={false}
      okText="开始部署"
      onCancel={handleCancel}
      onOk={handleDeploySandbox}
      open={!!mcpServerId}
      title="部署到沙箱"
      width={600}
    >
      <Form
        form={deployForm}
        initialValues={{
          authType: 'none',
          cpuLimit: '500m',
          cpuRequest: '250m',
          ephemeralStorage: '1Gi',
          memoryLimit: '512Mi',
          memoryRequest: '256Mi',
          resourcePreset: 'small',
          transportType: 'sse',
        }}
        layout="vertical"
      >
        <div className="space-y-4">
          <div className="rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
              <span className="text-xs font-medium text-gray-600">部署目标</span>
            </div>
            <div className="p-4 space-y-3">
              <Form.Item
                className="mb-0"
                label="沙箱实例"
                name="sandboxId"
                rules={[{ message: '请选择沙箱', required: true }]}
              >
                <Select
                  loading={deploySandboxLoading}
                  onChange={handleDeploySandboxChange}
                  options={deploySandboxList.map((s) => ({
                    label: s.sandboxName || s.sandboxId,
                    value: s.sandboxId,
                  }))}
                  placeholder="选择沙箱实例"
                />
              </Form.Item>
              <Form.Item
                className="mb-0"
                extra={
                  !deploySandboxIdValue ? (
                    <span className="text-[10px] text-gray-400">请先选择沙箱实例</span>
                  ) : undefined
                }
                label="Namespace"
                name="namespace"
                rules={[{ message: '请选择 Namespace', required: true }]}
              >
                <Select
                  disabled={!deploySandboxIdValue}
                  loading={deployNamespaceLoading}
                  options={deployNamespaceList.map((ns) => ({ label: ns, value: ns }))}
                  placeholder={deployNamespaceLoading ? '加载中...' : '选择 Namespace'}
                  showSearch
                />
              </Form.Item>
              <Form.Item className="mb-0" label="传输协议" name="transportType">
                <Radio.Group buttonStyle="solid" optionType="button" size="small">
                  <Radio.Button value="sse">SSE</Radio.Button>
                  <Radio.Button value="http">Streamable HTTP</Radio.Button>
                </Radio.Group>
              </Form.Item>
              <Form.Item className="mb-0" label="鉴权方式" name="authType">
                <Select>
                  <Select.Option value="none">无鉴权</Select.Option>
                  <Select.Option value="apikey">API Key</Select.Option>
                </Select>
              </Form.Item>
            </div>
          </div>

          <div className="rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
              <span className="text-xs font-medium text-gray-600">资源规格</span>
            </div>
            <div className="p-4">
              <Form.Item
                className={deployResourcePreset === 'custom' ? 'mb-3' : 'mb-0'}
                name="resourcePreset"
              >
                <Radio.Group
                  className="w-full"
                  onChange={(e) => {
                    setDeployResourcePreset(e.target.value);
                    const presets = {
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
                    const p = presets[e.target.value as keyof typeof presets];
                    if (p) deployForm.setFieldsValue(p);
                  }}
                  value={deployResourcePreset}
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
              {deployResourcePreset === 'custom' ? (
                <div className="grid grid-cols-2 gap-x-3 gap-y-2 pt-1 border-t border-gray-100">
                  <Form.Item className="mb-0" label="CPU Request" name="cpuRequest">
                    <Input className="font-mono text-xs" size="small" />
                  </Form.Item>
                  <Form.Item className="mb-0" label="CPU Limit" name="cpuLimit">
                    <Input className="font-mono text-xs" size="small" />
                  </Form.Item>
                  <Form.Item className="mb-0" label="Memory Request" name="memoryRequest">
                    <Input className="font-mono text-xs" size="small" />
                  </Form.Item>
                  <Form.Item className="mb-0" label="Memory Limit" name="memoryLimit">
                    <Input className="font-mono text-xs" size="small" />
                  </Form.Item>
                  <Form.Item className="mb-0" label="临时存储" name="ephemeralStorage">
                    <Input className="font-mono text-xs" size="small" />
                  </Form.Item>
                </div>
              ) : (
                <>
                  <Form.Item hidden name="cpuRequest">
                    <Input />
                  </Form.Item>
                  <Form.Item hidden name="cpuLimit">
                    <Input />
                  </Form.Item>
                  <Form.Item hidden name="memoryRequest">
                    <Input />
                  </Form.Item>
                  <Form.Item hidden name="memoryLimit">
                    <Input />
                  </Form.Item>
                  <Form.Item hidden name="ephemeralStorage">
                    <Input />
                  </Form.Item>
                </>
              )}
            </div>
          </div>

          {(() => {
            const paramDefs = getDeployExtraParamDefs();
            if (paramDefs.length === 0) return null;
            return (
              <div className="rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
                  <span className="text-xs font-medium text-gray-600">参数值配置</span>
                  <span className="text-[10px] text-gray-400 ml-2">
                    部署时注入的环境变量 / 请求参数
                  </span>
                </div>
                <div className="p-4 space-y-3">
                  {paramDefs.map((p) => (
                    <div key={p.name}>
                      <div className="flex items-center gap-1.5 mb-1">
                        <span className="text-xs font-mono text-gray-700">{p.name}</span>
                        {p.required && <span className="text-red-400 text-[10px]">*</span>}
                        <span className="text-[10px] text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">
                          {p.position}
                        </span>
                      </div>
                      {p.description && (
                        <div className="text-[10px] text-gray-400 mb-1">{p.description}</div>
                      )}
                      <Input
                        className="font-mono text-xs"
                        onChange={(e) =>
                          setDeployParamValues((prev) => ({ ...prev, [p.name]: e.target.value }))
                        }
                        placeholder={p.example ? String(p.example) : `请输入 ${p.name}`}
                        size="small"
                        value={deployParamValues[p.name] || ''}
                      />
                    </div>
                  ))}
                </div>
              </div>
            );
          })()}
        </div>
      </Form>
    </Modal>
  );
}
