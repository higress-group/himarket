import { useState, useEffect } from 'react'
import { Modal, Form, Input, Button, Space, Select, Switch, message, Tag, Tooltip, Badge } from 'antd'
import { PlusOutlined, DeleteOutlined, ToolOutlined, ApiOutlined } from '@ant-design/icons'
import { mcpServerApi } from '@/lib/api'

interface ToolParam {
  name: string
  type: string
  description: string
  required: boolean
}

interface ToolItem {
  name: string
  description: string
  params: ToolParam[]
}

interface ToolsConfigEditorModalProps {
  open: boolean
  mcpServerId: string
  initialValue: string
  onSave: () => void
  onCancel: () => void
}

const PARAM_TYPES = ['string', 'number', 'integer', 'boolean', 'array', 'object']


/** 将 McpSchema.Tool[] JSON 解析为表单数据 */
function parseToolsConfig(raw: string): ToolItem[] {
  if (!raw?.trim()) return []
  try {
    let arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr.map((t: any) => {
      const props = t.inputSchema?.properties || {}
      const required: string[] = t.inputSchema?.required || []
      return {
        name: t.name || '',
        description: t.description || '',
        params: Object.entries(props).map(([key, val]: [string, any]) => ({
          name: key,
          type: val.type || 'string',
          description: val.description || '',
          required: required.includes(key),
        })),
      }
    })
  } catch {
    return []
  }
}

/** 将表单数据转为 McpSchema.Tool[] JSON 字符串 */
function toToolsConfigJson(tools: ToolItem[]): string {
  return JSON.stringify(
    tools.map((t) => {
      const properties: Record<string, any> = {}
      const required: string[] = []
      for (const p of t.params) {
        if (!p.name.trim()) continue
        properties[p.name.trim()] = {
          type: p.type,
          ...(p.description ? { description: p.description } : {}),
        }
        if (p.required) required.push(p.name.trim())
      }
      return {
        name: t.name,
        description: t.description,
        inputSchema: {
          type: 'object',
          properties,
          ...(required.length > 0 ? { required } : {}),
        },
      }
    })
  )
}

export default function ToolsConfigEditorModal({
  open,
  mcpServerId,
  initialValue,
  onSave,
  onCancel,
}: ToolsConfigEditorModalProps) {
  const [tools, setTools] = useState<ToolItem[]>([])
  const [saving, setSaving] = useState(false)
  const [expandedTool, setExpandedTool] = useState<number | null>(null)

  useEffect(() => {
    if (open) {
      const parsed = parseToolsConfig(initialValue)
      setTools(parsed.length > 0 ? parsed : [])
      setExpandedTool(parsed.length > 0 ? 0 : null)
    }
  }, [open, initialValue])

  const addTool = () => {
    const newIndex = tools.length
    setTools([...tools, { name: '', description: '', params: [] }])
    setExpandedTool(newIndex)
  }

  const removeTool = (index: number) => {
    const updated = tools.filter((_, i) => i !== index)
    setTools(updated)
    if (expandedTool === index) setExpandedTool(updated.length > 0 ? Math.min(index, updated.length - 1) : null)
    else if (expandedTool !== null && expandedTool > index) setExpandedTool(expandedTool - 1)
  }

  const updateTool = (index: number, field: keyof ToolItem, value: any) => {
    const updated = [...tools]
    ;(updated[index] as any)[field] = value
    setTools(updated)
  }

  const addParam = (toolIndex: number) => {
    const updated = [...tools]
    updated[toolIndex].params.push({ name: '', type: 'string', description: '', required: false })
    setTools(updated)
  }

  const removeParam = (toolIndex: number, paramIndex: number) => {
    const updated = [...tools]
    updated[toolIndex].params = updated[toolIndex].params.filter((_, i) => i !== paramIndex)
    setTools(updated)
  }

  const updateParam = (toolIndex: number, paramIndex: number, field: keyof ToolParam, value: any) => {
    const updated = [...tools]
    ;(updated[toolIndex].params[paramIndex] as any)[field] = value
    setTools(updated)
  }

  const handleSave = async () => {
    for (const tool of tools) {
      if (!tool.name.trim()) {
        message.error('每个工具必须填写名称')
        return
      }
      if (!tool.description.trim()) {
        message.error(`工具「${tool.name || '未命名'}」必须填写描述`)
        return
      }
      for (const p of tool.params) {
        if (!p.name.trim()) {
          message.error(`工具「${tool.name}」中存在未填写名称的参数`)
          return
        }
      }
    }

    setSaving(true)
    try {
      const json = toToolsConfigJson(tools)
      await mcpServerApi.updateToolsConfig(mcpServerId, json)
      message.success('工具配置保存成功')
      onSave()
    } catch {
      message.error('保存失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ToolOutlined style={{ color: '#1677ff' }} />
          <span>编辑工具配置</span>
          {tools.length > 0 && (
            <Tag color="blue" style={{ marginLeft: 4, fontWeight: 'normal' }}>{tools.length} 个工具</Tag>
          )}
        </div>
      }
      open={open}
      onOk={handleSave}
      onCancel={onCancel}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      width={760}
      destroyOnClose
      styles={{ body: { maxHeight: '65vh', overflowY: 'auto', padding: '16px 24px' } }}
    >
      {tools.length === 0 ? (
        <div style={{
          textAlign: 'center', padding: '48px 0', color: '#bbb',
          background: '#fafafa', borderRadius: 8, border: '1px dashed #e0e0e0',
        }}>
          <ApiOutlined style={{ fontSize: 36, color: '#d9d9d9', marginBottom: 12, display: 'block' }} />
          <div style={{ marginBottom: 4 }}>暂无工具定义</div>
          <div style={{ fontSize: 12 }}>点击下方按钮添加第一个工具</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {tools.map((tool, toolIdx) => {
            const isExpanded = expandedTool === toolIdx
            const reqCount = tool.params.filter(p => p.required).length
            return (
              <div
                key={toolIdx}
                style={{
                  border: isExpanded ? '1px solid #91caff' : '1px solid #f0f0f0',
                  borderRadius: 8,
                  background: isExpanded ? '#f6faff' : '#fff',
                  transition: 'all 0.2s',
                }}
              >
                {/* 工具头部 */}
                <div
                  onClick={() => setExpandedTool(isExpanded ? null : toolIdx)}
                  style={{
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    padding: '10px 16px', cursor: 'pointer', userSelect: 'none',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                    <ToolOutlined style={{ color: isExpanded ? '#1677ff' : '#999', fontSize: 14 }} />
                    <span style={{
                      fontWeight: 500, fontSize: 14,
                      color: tool.name ? '#333' : '#bbb',
                      overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                    }}>
                      {tool.name || '未命名工具'}
                    </span>
                    {tool.params.length > 0 && (
                      <Tag color="default" style={{ fontSize: 11, lineHeight: '18px', padding: '0 6px' }}>
                        {tool.params.length} 参数{reqCount > 0 ? `（${reqCount} 必填）` : ''}
                      </Tag>
                    )}
                  </div>
                  <Space size={4}>
                    <Tooltip title="删除工具">
                      <Button
                        type="text" danger size="small"
                        icon={<DeleteOutlined />}
                        onClick={(e) => { e.stopPropagation(); removeTool(toolIdx) }}
                      />
                    </Tooltip>
                  </Space>
                </div>

                {/* 展开内容 */}
                {isExpanded && (
                  <div style={{ padding: '0 16px 16px', borderTop: '1px solid #f0f0f0' }}>
                    <Form layout="vertical" size="small" style={{ marginTop: 12 }}>
                      <div style={{ display: 'flex', gap: 12 }}>
                        <Form.Item label="工具名称" required style={{ flex: 1, marginBottom: 12 }}>
                          <Input
                            placeholder="如 get_weather"
                            value={tool.name}
                            onChange={(e) => updateTool(toolIdx, 'name', e.target.value)}
                          />
                        </Form.Item>
                      </div>
                      <Form.Item label="工具描述" required style={{ marginBottom: 16 }}>
                        <Input.TextArea
                          rows={2}
                          placeholder="描述工具的功能，帮助 AI 理解何时调用此工具"
                          value={tool.description}
                          onChange={(e) => updateTool(toolIdx, 'description', e.target.value)}
                        />
                      </Form.Item>

                      {/* 参数区域 */}
                      <div style={{
                        background: '#fff', borderRadius: 6, border: '1px solid #f0f0f0',
                        padding: 12,
                      }}>
                        <div style={{
                          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                          marginBottom: tool.params.length > 0 ? 10 : 0,
                        }}>
                          <span style={{ fontSize: 12, color: '#666', fontWeight: 500 }}>
                            输入参数
                            {tool.params.length > 0 && (
                              <Badge count={tool.params.length} style={{ marginLeft: 6, backgroundColor: '#e6f4ff', color: '#1677ff', boxShadow: 'none' }} />
                            )}
                          </span>
                          <Button
                            type="link" size="small"
                            icon={<PlusOutlined />}
                            onClick={() => addParam(toolIdx)}
                            style={{ fontSize: 12, padding: 0, height: 'auto' }}
                          >
                            添加
                          </Button>
                        </div>

                        {tool.params.length === 0 ? (
                          <div style={{ textAlign: 'center', padding: '12px 0', color: '#ccc', fontSize: 12 }}>
                            暂无参数，点击右上角添加
                          </div>
                        ) : (
                          <>
                            {/* 表头 */}
                            <div style={{
                              display: 'flex', gap: 8, marginBottom: 6, padding: '0 4px',
                              fontSize: 11, color: '#999', fontWeight: 500,
                            }}>
                              <span style={{ width: 120 }}>参数名</span>
                              <span style={{ width: 90 }}>类型</span>
                              <span style={{ flex: 1 }}>描述</span>
                              <span style={{ width: 100, textAlign: 'center' }}>操作</span>
                            </div>
                            {tool.params.map((param, paramIdx) => (
                              <div key={paramIdx} style={{
                                display: 'flex', gap: 8, marginBottom: 6, alignItems: 'center',
                                padding: '4px', borderRadius: 4,
                                background: paramIdx % 2 === 0 ? '#fafafa' : 'transparent',
                              }}>
                                <Input
                                  placeholder="参数名"
                                  value={param.name}
                                  onChange={(e) => updateParam(toolIdx, paramIdx, 'name', e.target.value)}
                                  style={{ width: 120 }}
                                  size="small"
                                />
                                <Select
                                  value={param.type}
                                  onChange={(v) => updateParam(toolIdx, paramIdx, 'type', v)}
                                  style={{ width: 90 }}
                                  size="small"
                                  options={PARAM_TYPES.map((t) => ({ label: t, value: t }))}
                                />
                                <Input
                                  placeholder="参数描述"
                                  value={param.description}
                                  onChange={(e) => updateParam(toolIdx, paramIdx, 'description', e.target.value)}
                                  style={{ flex: 1 }}
                                  size="small"
                                />
                                <Space size={2} style={{ width: 100, justifyContent: 'center' }}>
                                  <Switch
                                    size="small"
                                    checked={param.required}
                                    onChange={(v) => updateParam(toolIdx, paramIdx, 'required', v)}
                                    checkedChildren="必填"
                                    unCheckedChildren="选填"
                                  />
                                  <Button
                                    type="text" danger size="small"
                                    icon={<DeleteOutlined style={{ fontSize: 12 }} />}
                                    onClick={() => removeParam(toolIdx, paramIdx)}
                                    style={{ padding: '0 4px' }}
                                  />
                                </Space>
                              </div>
                            ))}
                          </>
                        )}
                      </div>
                    </Form>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      <Button
        type="dashed"
        icon={<PlusOutlined />}
        onClick={addTool}
        style={{ width: '100%', marginTop: 16, borderRadius: 8, height: 40 }}
      >
        添加工具
      </Button>
    </Modal>
  )
}
