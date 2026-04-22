import { PlusOutlined, DeleteOutlined, ToolOutlined, ApiOutlined } from '@ant-design/icons';
import {
  Modal,
  Form,
  Input,
  Button,
  Space,
  Select,
  Switch,
  message,
  Tag,
  Tooltip,
  Badge,
} from 'antd';
import { useState, useEffect } from 'react';

import { mcpServerApi } from '@/lib/api';

interface ToolParam {
  name: string;
  type: string;
  description: string;
  required: boolean;
}

interface ToolItem {
  name: string;
  description: string;
  params: ToolParam[];
}

interface ToolsConfigEditorModalProps {
  open: boolean;
  mcpServerId: string;
  initialValue: string;
  onSave: () => void;
  onCancel: () => void;
}

const PARAM_TYPES = ['string', 'number', 'integer', 'boolean', 'array', 'object'];

/** 将 McpSchema.Tool[] JSON 解析为表单数据 */
function parseToolsConfig(raw: string): ToolItem[] {
  if (!raw?.trim()) return [];
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!Array.isArray(arr)) return [];
    return arr.map((t: Record<string, unknown>) => {
      const inputSchema = (t.inputSchema as Record<string, unknown>) || {};
      const props = (inputSchema.properties as Record<string, Record<string, unknown>>) || {};
      const required: string[] = (inputSchema.required as string[]) || [];
      return {
        description: (t.description as string) || '',
        name: (t.name as string) || '',
        params: Object.entries(props).map(([key, val]: [string, Record<string, unknown>]) => ({
          description: (val.description as string) || '',
          name: key,
          required: required.includes(key),
          type: (val.type as string) || 'string',
        })),
      };
    });
  } catch {
    return [];
  }
}

/** 将表单数据转为 McpSchema.Tool[] JSON 字符串 */
function toToolsConfigJson(tools: ToolItem[]): string {
  return JSON.stringify(
    tools.map((t) => {
      const properties: Record<string, unknown> = {};
      const required: string[] = [];
      for (const p of t.params) {
        if (!p.name.trim()) continue;
        properties[p.name.trim()] = {
          type: p.type,
          ...(p.description ? { description: p.description } : {}),
        };
        if (p.required) required.push(p.name.trim());
      }
      return {
        description: t.description,
        inputSchema: {
          properties,
          type: 'object',
          ...(required.length > 0 ? { required } : {}),
        },
        name: t.name,
      };
    }),
  );
}

export default function ToolsConfigEditorModal({
  initialValue,
  mcpServerId,
  onCancel,
  onSave,
  open,
}: ToolsConfigEditorModalProps) {
  const [tools, setTools] = useState<ToolItem[]>([]);
  const [saving, setSaving] = useState(false);
  const [expandedTool, setExpandedTool] = useState<number | null>(null);

  useEffect(() => {
    if (open) {
      const parsed = parseToolsConfig(initialValue);
      setTools(parsed.length > 0 ? parsed : []);
      setExpandedTool(parsed.length > 0 ? 0 : null);
    }
  }, [open, initialValue]);

  const addTool = () => {
    const newIndex = tools.length;
    setTools([...tools, { description: '', name: '', params: [] }]);
    setExpandedTool(newIndex);
  };

  const removeTool = (index: number) => {
    const updated = tools.filter((_, i) => i !== index);
    setTools(updated);
    if (expandedTool === index)
      setExpandedTool(updated.length > 0 ? Math.min(index, updated.length - 1) : null);
    else if (expandedTool !== null && expandedTool > index) setExpandedTool(expandedTool - 1);
  };

  const updateTool = (index: number, field: keyof ToolItem, value: unknown) => {
    const updated = [...tools];
    if (updated[index]) (updated[index] as Record<keyof ToolItem, unknown>)[field] = value;
    setTools(updated);
  };

  const addParam = (toolIndex: number) => {
    const updated = [...tools];
    const tool = updated[toolIndex];
    if (!tool) return;
    tool.params.push({ description: '', name: '', required: false, type: 'string' });
    setTools(updated);
  };

  const removeParam = (toolIndex: number, paramIndex: number) => {
    const updated = [...tools];
    const tool = updated[toolIndex];
    if (!tool) return;
    tool.params = tool.params.filter((_, i) => i !== paramIndex);
    setTools(updated);
  };

  const updateParam = (
    toolIndex: number,
    paramIndex: number,
    field: keyof ToolParam,
    value: unknown,
  ) => {
    const updated = [...tools];
    if (updated[toolIndex] && updated[toolIndex].params[paramIndex])
      (updated[toolIndex].params[paramIndex] as Record<keyof ToolParam, unknown>)[field] = value;
    setTools(updated);
  };

  const handleSave = async () => {
    for (const tool of tools) {
      if (!tool.name.trim()) {
        message.error('每个工具必须填写名称');
        return;
      }
      if (!tool.description.trim()) {
        message.error(`工具「${tool.name || '未命名'}」必须填写描述`);
        return;
      }
      for (const p of tool.params) {
        if (!p.name.trim()) {
          message.error(`工具「${tool.name}」中存在未填写名称的参数`);
          return;
        }
      }
    }

    setSaving(true);
    try {
      const json = toToolsConfigJson(tools);
      await mcpServerApi.updateToolsConfig(mcpServerId, json);
      message.success('工具配置保存成功');
      onSave();
    } catch {
      message.error('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      cancelText="取消"
      confirmLoading={saving}
      destroyOnClose
      okText="保存"
      onCancel={onCancel}
      onOk={handleSave}
      open={open}
      styles={{ body: { maxHeight: '65vh', overflowY: 'auto', padding: '16px 24px' } }}
      title={
        <div style={{ alignItems: 'center', display: 'flex', gap: 8 }}>
          <ToolOutlined style={{ color: '#1677ff' }} />
          <span>编辑工具配置</span>
          {tools.length > 0 && (
            <Tag color="blue" style={{ fontWeight: 'normal', marginLeft: 4 }}>
              {tools.length} 个工具
            </Tag>
          )}
        </div>
      }
      width={760}
    >
      {tools.length === 0 ? (
        <div
          style={{
            background: '#fafafa',
            border: '1px dashed #e0e0e0',
            borderRadius: 8,
            color: '#bbb',
            padding: '48px 0',
            textAlign: 'center',
          }}
        >
          <ApiOutlined
            style={{ color: '#d9d9d9', display: 'block', fontSize: 36, marginBottom: 12 }}
          />
          <div style={{ marginBottom: 4 }}>暂无工具定义</div>
          <div style={{ fontSize: 12 }}>点击下方按钮添加第一个工具</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {tools.map((tool, toolIdx) => {
            const isExpanded = expandedTool === toolIdx;
            const reqCount = tool.params.filter((p) => p.required).length;
            return (
              <div
                key={toolIdx}
                style={{
                  background: isExpanded ? '#f6faff' : '#fff',
                  border: isExpanded ? '1px solid #91caff' : '1px solid #f0f0f0',
                  borderRadius: 8,
                  transition: 'all 0.2s',
                }}
              >
                {/* 工具头部 */}
                <div
                  onClick={() => setExpandedTool(isExpanded ? null : toolIdx)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setExpandedTool(isExpanded ? null : toolIdx);
                    }
                  }}
                  role="button"
                  style={{
                    alignItems: 'center',
                    cursor: 'pointer',
                    display: 'flex',
                    justifyContent: 'space-between',
                    padding: '10px 16px',
                    userSelect: 'none',
                  }}
                  tabIndex={0}
                >
                  <div
                    style={{ alignItems: 'center', display: 'flex', flex: 1, gap: 8, minWidth: 0 }}
                  >
                    <ToolOutlined
                      style={{ color: isExpanded ? '#1677ff' : '#999', fontSize: 14 }}
                    />
                    <span
                      style={{
                        color: tool.name ? '#333' : '#bbb',
                        fontSize: 14,
                        fontWeight: 500,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {tool.name || '未命名工具'}
                    </span>
                    {tool.params.length > 0 && (
                      <Tag
                        color="default"
                        style={{ fontSize: 11, lineHeight: '18px', padding: '0 6px' }}
                      >
                        {tool.params.length} 参数{reqCount > 0 ? `（${reqCount} 必填）` : ''}
                      </Tag>
                    )}
                  </div>
                  <Space size={4}>
                    <Tooltip title="删除工具">
                      <Button
                        danger
                        icon={<DeleteOutlined />}
                        onClick={(e) => {
                          e.stopPropagation();
                          removeTool(toolIdx);
                        }}
                        size="small"
                        type="text"
                      />
                    </Tooltip>
                  </Space>
                </div>

                {/* 展开内容 */}
                {isExpanded && (
                  <div style={{ borderTop: '1px solid #f0f0f0', padding: '0 16px 16px' }}>
                    <Form layout="vertical" size="small" style={{ marginTop: 12 }}>
                      <div style={{ display: 'flex', gap: 12 }}>
                        <Form.Item label="工具名称" required style={{ flex: 1, marginBottom: 12 }}>
                          <Input
                            onChange={(e) => updateTool(toolIdx, 'name', e.target.value)}
                            placeholder="如 get_weather"
                            value={tool.name}
                          />
                        </Form.Item>
                      </div>
                      <Form.Item label="工具描述" required style={{ marginBottom: 16 }}>
                        <Input.TextArea
                          onChange={(e) => updateTool(toolIdx, 'description', e.target.value)}
                          placeholder="描述工具的功能，帮助 AI 理解何时调用此工具"
                          rows={2}
                          value={tool.description}
                        />
                      </Form.Item>

                      {/* 参数区域 */}
                      <div
                        style={{
                          background: '#fff',
                          border: '1px solid #f0f0f0',
                          borderRadius: 6,
                          padding: 12,
                        }}
                      >
                        <div
                          style={{
                            alignItems: 'center',
                            display: 'flex',
                            justifyContent: 'space-between',
                            marginBottom: tool.params.length > 0 ? 10 : 0,
                          }}
                        >
                          <span style={{ color: '#666', fontSize: 12, fontWeight: 500 }}>
                            输入参数
                            {tool.params.length > 0 && (
                              <Badge
                                count={tool.params.length}
                                style={{
                                  backgroundColor: '#e6f4ff',
                                  boxShadow: 'none',
                                  color: '#1677ff',
                                  marginLeft: 6,
                                }}
                              />
                            )}
                          </span>
                          <Button
                            icon={<PlusOutlined />}
                            onClick={() => addParam(toolIdx)}
                            size="small"
                            style={{ fontSize: 12, height: 'auto', padding: 0 }}
                            type="link"
                          >
                            添加
                          </Button>
                        </div>

                        {tool.params.length === 0 ? (
                          <div
                            style={{
                              color: '#ccc',
                              fontSize: 12,
                              padding: '12px 0',
                              textAlign: 'center',
                            }}
                          >
                            暂无参数，点击右上角添加
                          </div>
                        ) : (
                          <>
                            {/* 表头 */}
                            <div
                              style={{
                                color: '#999',
                                display: 'flex',
                                fontSize: 11,
                                fontWeight: 500,
                                gap: 8,
                                marginBottom: 6,
                                padding: '0 4px',
                              }}
                            >
                              <span style={{ width: 120 }}>参数名</span>
                              <span style={{ width: 90 }}>类型</span>
                              <span style={{ flex: 1 }}>描述</span>
                              <span style={{ textAlign: 'center', width: 100 }}>操作</span>
                            </div>
                            {tool.params.map((param, paramIdx) => (
                              <div
                                key={paramIdx}
                                style={{
                                  alignItems: 'center',
                                  background: paramIdx % 2 === 0 ? '#fafafa' : 'transparent',
                                  borderRadius: 4,
                                  display: 'flex',
                                  gap: 8,
                                  marginBottom: 6,
                                  padding: '4px',
                                }}
                              >
                                <Input
                                  onChange={(e) =>
                                    updateParam(toolIdx, paramIdx, 'name', e.target.value)
                                  }
                                  placeholder="参数名"
                                  size="small"
                                  style={{ width: 120 }}
                                  value={param.name}
                                />
                                <Select
                                  onChange={(v) => updateParam(toolIdx, paramIdx, 'type', v)}
                                  options={PARAM_TYPES.map((t) => ({ label: t, value: t }))}
                                  size="small"
                                  style={{ width: 90 }}
                                  value={param.type}
                                />
                                <Input
                                  onChange={(e) =>
                                    updateParam(toolIdx, paramIdx, 'description', e.target.value)
                                  }
                                  placeholder="参数描述"
                                  size="small"
                                  style={{ flex: 1 }}
                                  value={param.description}
                                />
                                <Space size={2} style={{ justifyContent: 'center', width: 100 }}>
                                  <Switch
                                    checked={param.required}
                                    checkedChildren="必填"
                                    onChange={(v) => updateParam(toolIdx, paramIdx, 'required', v)}
                                    size="small"
                                    unCheckedChildren="选填"
                                  />
                                  <Button
                                    danger
                                    icon={<DeleteOutlined style={{ fontSize: 12 }} />}
                                    onClick={() => removeParam(toolIdx, paramIdx)}
                                    size="small"
                                    style={{ padding: '0 4px' }}
                                    type="text"
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
            );
          })}
        </div>
      )}

      <Button
        icon={<PlusOutlined />}
        onClick={addTool}
        style={{ borderRadius: 8, height: 40, marginTop: 16, width: '100%' }}
        type="dashed"
      >
        添加工具
      </Button>
    </Modal>
  );
}
