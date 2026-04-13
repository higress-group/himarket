import { useState, useEffect } from 'react'
import { Form, Select, Radio, Input, Tag, message } from 'antd'
import { sandboxApi } from '@/lib/api'
import type { ExtraParam } from '../types'

/**
 * SandboxDeployStep — 沙箱部署配置步骤。
 *
 * 仅当 McpConfigStep 中 sandboxRequired === true 时展示。
 * 渲染在父级 Form 内部，使用 Form.useFormInstance() 获取表单实例。
 */
export default function SandboxDeployStep() {
  const form = Form.useFormInstance()

  // ── 沙箱实例列表 ──
  const [sandboxList, setSandboxList] = useState<any[]>([])
  const [sandboxLoading, setSandboxLoading] = useState(false)

  // ── Namespace 列表 ──
  const [namespaceList, setNamespaceList] = useState<string[]>([])
  const [namespaceLoading, setNamespaceLoading] = useState(false)

  // ── 资源规格预设 ──
  const resourcePreset = Form.useWatch('resourcePreset', form)

  // ── 额外参数（从 McpConfigStep 读取） ──
  const extraParams: ExtraParam[] = Form.useWatch('extraParams', form) || []

  // 加载沙箱实例列表
  useEffect(() => {
    setSandboxLoading(true)
    sandboxApi
      .getActiveSandboxes()
      .then((res: any) => {
        const list = res?.data || []
        setSandboxList(Array.isArray(list) ? list : [])
      })
      .catch(() => {
        message.error('获取沙箱实例列表失败')
        setSandboxList([])
      })
      .finally(() => setSandboxLoading(false))
  }, [])

  // 选择沙箱后加载 Namespace
  const handleSandboxChange = async (sandboxId: string) => {
    setNamespaceList([])
    form.setFieldsValue({ namespace: undefined })
    setNamespaceLoading(true)
    try {
      const res: any = await sandboxApi.listNamespaces(sandboxId)
      const list = res?.data || res || []
      setNamespaceList(Array.isArray(list) ? list : [])
    } catch {
      message.error('获取 Namespace 列表失败')
      setNamespaceList([])
    } finally {
      setNamespaceLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      {/* ── 部署目标 ── */}
      <div className="rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
          <span className="text-xs font-medium text-gray-600">部署目标</span>
        </div>
        <div className="p-4 space-y-3">
          <Form.Item
            name="sandboxId"
            label="沙箱实例"
            className="mb-0"
            rules={[{ required: true, message: '请选择沙箱实例' }]}
          >
            <Select
              placeholder="选择沙箱实例"
              loading={sandboxLoading}
              onChange={handleSandboxChange}
              options={sandboxList.map((s: any) => ({
                value: s.sandboxId,
                label: s.sandboxName,
              }))}
            />
          </Form.Item>

          <Form.Item
            name="namespace"
            label="Namespace"
            className="mb-0"
            rules={[{ required: true, message: '请选择 Namespace' }]}
            extra={
              !form.getFieldValue('sandboxId') ? (
                <span className="text-[10px] text-gray-400">请先选择沙箱实例</span>
              ) : undefined
            }
          >
            <Select
              placeholder={namespaceLoading ? '加载中...' : '选择 Namespace'}
              loading={namespaceLoading}
              disabled={!form.getFieldValue('sandboxId')}
              showSearch
              options={namespaceList.map((ns) => ({ value: ns, label: ns }))}
            />
          </Form.Item>

          <Form.Item name="transportType" label="传输协议" initialValue="sse" className="mb-0">
            <Radio.Group size="small" optionType="button" buttonStyle="solid">
              <Radio.Button value="sse">SSE</Radio.Button>
              <Radio.Button value="http">Streamable HTTP</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <Form.Item name="authType" label="鉴权方式" initialValue="none" className="mb-0">
            <Radio.Group size="small" optionType="button" buttonStyle="solid">
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
            name="resourcePreset"
            initialValue="small"
            className={resourcePreset === 'custom' ? 'mb-3' : 'mb-0'}
          >
            <Radio.Group
              className="w-full"
              onChange={(e) => {
                const presets: Record<string, any> = {
                  small: { cpuRequest: '250m', cpuLimit: '500m', memoryRequest: '256Mi', memoryLimit: '512Mi', ephemeralStorage: '1Gi' },
                  medium: { cpuRequest: '500m', cpuLimit: '1', memoryRequest: '512Mi', memoryLimit: '1Gi', ephemeralStorage: '2Gi' },
                  large: { cpuRequest: '1', cpuLimit: '2', memoryRequest: '1Gi', memoryLimit: '2Gi', ephemeralStorage: '4Gi' },
                }
                const p = presets[e.target.value]
                if (p) form.setFieldsValue(p)
              }}
            >
              <div className="grid grid-cols-4 gap-2">
                {[
                  { value: 'small', label: '小型', desc: '0.5C / 512Mi' },
                  { value: 'medium', label: '中型', desc: '1C / 1Gi' },
                  { value: 'large', label: '大型', desc: '2C / 2Gi' },
                  { value: 'custom', label: '自定义', desc: '手动配置' },
                ].map((item) => (
                  <Radio.Button
                    key={item.value}
                    value={item.value}
                    className="h-auto text-center flex-1"
                    style={{ padding: '8px 0', lineHeight: 1.3 }}
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
              <Form.Item name="cpuRequest" label="CPU Request" className="mb-0" initialValue="250m">
                <Input size="small" className="font-mono text-xs" />
              </Form.Item>
              <Form.Item name="cpuLimit" label="CPU Limit" className="mb-0" initialValue="500m">
                <Input size="small" className="font-mono text-xs" />
              </Form.Item>
              <Form.Item name="memoryRequest" label="Memory Request" className="mb-0" initialValue="256Mi">
                <Input size="small" className="font-mono text-xs" />
              </Form.Item>
              <Form.Item name="memoryLimit" label="Memory Limit" className="mb-0" initialValue="512Mi">
                <Input size="small" className="font-mono text-xs" />
              </Form.Item>
              <Form.Item name="ephemeralStorage" label="临时存储" className="mb-0" initialValue="1Gi">
                <Input size="small" className="font-mono text-xs" />
              </Form.Item>
            </div>
          ) : (
            <>
              <Form.Item name="cpuRequest" hidden initialValue="250m"><Input /></Form.Item>
              <Form.Item name="cpuLimit" hidden initialValue="500m"><Input /></Form.Item>
              <Form.Item name="memoryRequest" hidden initialValue="256Mi"><Input /></Form.Item>
              <Form.Item name="memoryLimit" hidden initialValue="512Mi"><Input /></Form.Item>
              <Form.Item name="ephemeralStorage" hidden initialValue="1Gi"><Input /></Form.Item>
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
                  name={['paramValues', p.name]}
                  className="mb-0"
                  rules={p.required ? [{ required: true, message: `请输入 ${p.name}` }] : undefined}
                >
                  <Input
                    size="small"
                    placeholder={p.example || `请输入 ${p.name}`}
                    className="font-mono text-xs"
                  />
                </Form.Item>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
