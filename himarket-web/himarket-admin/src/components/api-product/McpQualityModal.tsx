import { Modal, Progress, Tag, Collapse, Empty, Spin } from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  RocketOutlined,
} from '@ant-design/icons'

export interface McpQualityIssue {
  level: 'CRITICAL' | 'WARNING'
  field: string
  message: string
  standard: string
}

export interface McpToolQuality {
  name: string
  paramCount: number
  issues: McpQualityIssue[]
}

export interface McpQualityResult {
  score: number
  grade: string
  passedChecks: number
  totalChecks: number
  toolCount: number
  issues: McpQualityIssue[]
  tools: McpToolQuality[]
}

interface McpQualityModalProps {
  open: boolean
  loading: boolean
  result: McpQualityResult | null
  onConfirm: () => void
  onCancel: () => void
  confirmLoading?: boolean
}

const GRADE_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  S: { color: '#10b981', bg: '#ecfdf5', label: '优秀' },
  A: { color: '#3b82f6', bg: '#eff6ff', label: '良好' },
  B: { color: '#f59e0b', bg: '#fffbeb', label: '一般' },
  C: { color: '#f97316', bg: '#fff7ed', label: '较差' },
  D: { color: '#ef4444', bg: '#fef2f2', label: '很差' },
}

const STANDARDS = [
  { icon: '📝', title: '产品描述', desc: '不为空，且不少于 20 个字符' },
  { icon: '🔧', title: '工具名称', desc: '不为空，格式：字母/下划线开头，仅含字母、数字、下划线、连字符' },
  { icon: '💬', title: '工具描述', desc: '不为空，且不少于 10 个字符' },
  { icon: '📋', title: '参数 type', desc: '为合法 JSON Schema 类型：string / number / integer / boolean / array / object / null' },
  { icon: '🏷️', title: '参数 description', desc: '每个参数都应有 description，说明含义、取值范围和示例' },
]

function getProgressColor(score: number): string {
  if (score >= 90) return '#10b981'
  if (score >= 75) return '#3b82f6'
  if (score >= 60) return '#f59e0b'
  if (score >= 45) return '#f97316'
  return '#ef4444'
}

function IssueCard({ issue }: { issue: McpQualityIssue }) {
  const isCritical = issue.level === 'CRITICAL'
  return (
    <div
      className={`rounded-lg p-3 ${
        isCritical ? 'bg-red-50 border border-red-100' : 'bg-amber-50 border border-amber-100'
      }`}
    >
      <div className="flex items-start gap-2">
        {isCritical ? (
          <CloseCircleOutlined className="text-red-500 mt-0.5 flex-shrink-0" />
        ) : (
          <WarningOutlined className="text-amber-500 mt-0.5 flex-shrink-0" />
        )}
        <div className="min-w-0">
          <div className="text-sm font-medium text-gray-800">{issue.message}</div>
          <div className="text-xs text-gray-400 font-mono mt-0.5 truncate">{issue.field}</div>
          <div className="text-xs text-gray-500 mt-1">📌 规范：{issue.standard}</div>
        </div>
      </div>
    </div>
  )
}

export function McpQualityModal({
  open,
  loading,
  result,
  onConfirm,
  onCancel,
  confirmLoading,
}: McpQualityModalProps) {
  const grade = result ? (GRADE_CONFIG[result.grade] ?? GRADE_CONFIG['D']) : null
  const hasCritical = result?.issues.some((i) => i.level === 'CRITICAL') ?? false
  const progressColor = result ? getProgressColor(result.score) : '#e5e7eb'
  const productIssues = result?.issues.filter((i) => i.field.startsWith('product.')) ?? []

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <RocketOutlined className="text-blue-500" />
          <span>MCP 质量评估</span>
        </div>
      }
      open={open}
      onOk={onConfirm}
      onCancel={onCancel}
      okText="继续发布"
      cancelText="取消"
      width={680}
      confirmLoading={confirmLoading}
      okButtonProps={{ danger: hasCritical, disabled: loading }}
      cancelButtonProps={{ disabled: confirmLoading }}
      destroyOnClose
    >
      {loading ? (
        <div className="flex justify-center items-center py-16">
          <Spin size="large" tip="评估中..." />
        </div>
      ) : result ? (
        <div className="space-y-5">
          {/* 评分卡片 */}
          <div
            className="flex items-center gap-6 rounded-xl p-5"
            style={{ background: grade?.bg }}
          >
            <div className="flex-shrink-0">
              <Progress
                type="circle"
                percent={result.score}
                size={88}
                strokeColor={progressColor}
                format={() => (
                  <div className="text-center">
                    <div className="text-2xl font-bold" style={{ color: progressColor }}>
                      {result.score}
                    </div>
                    <div className="text-xs text-gray-500">分</div>
                  </div>
                )}
              />
            </div>

            <div className="flex-1">
              <div className="flex items-center gap-3 mb-2">
                <span className="text-3xl font-extrabold" style={{ color: grade?.color }}>
                  {result.grade}
                </span>
                <span className="text-base font-medium text-gray-700">{grade?.label}</span>
              </div>
              <div className="text-sm text-gray-500 space-y-1">
                <div>
                  检测通过{' '}
                  <span className="font-semibold text-gray-800">
                    {result.passedChecks}/{result.totalChecks}
                  </span>{' '}
                  项 · 共 {result.toolCount} 个工具
                </div>
                {hasCritical && (
                  <div className="flex items-center gap-1 text-red-500 font-medium">
                    <CloseCircleOutlined />
                    <span>存在严重问题，建议修复后再发布</span>
                  </div>
                )}
                {!hasCritical && result.issues.length > 0 && (
                  <div className="flex items-center gap-1 text-amber-500 font-medium">
                    <WarningOutlined />
                    <span>存在 {result.issues.length} 个警告，可继续发布</span>
                  </div>
                )}
                {result.issues.length === 0 && (
                  <div className="flex items-center gap-1 text-green-600 font-medium">
                    <CheckCircleOutlined />
                    <span>所有检测项均通过</span>
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 检测详情（可滚动区域） */}
          <div className="max-h-72 overflow-y-auto space-y-3 pr-1">
            {/* 产品级问题 */}
            {productIssues.length > 0 && (
              <div className="space-y-2">
                <div className="text-xs font-medium text-gray-500">产品级问题</div>
                {productIssues.map((issue) => (
                  <IssueCard key={issue.field} issue={issue} />
                ))}
              </div>
            )}

            {/* 工具级问题 */}
            {result.tools.length > 0 && (
              <div>
                {productIssues.length > 0 && (
                  <div className="text-xs font-medium text-gray-500 mt-3 mb-2">工具检测详情</div>
                )}
                {productIssues.length === 0 && (
                  <div className="text-xs font-medium text-gray-500 mb-2">检测详情</div>
                )}
                <Collapse
                  size="small"
                  ghost
                  className="border border-gray-200 rounded-lg overflow-hidden"
                  items={result.tools.map((tool) => {
                    const toolCritical = tool.issues.filter((i) => i.level === 'CRITICAL').length
                    const toolWarning = tool.issues.filter((i) => i.level === 'WARNING').length
                    return {
                      key: tool.name,
                      label: (
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-sm">{tool.name}</span>
                          <span className="text-xs text-gray-400">({tool.paramCount} 个参数)</span>
                          {toolCritical > 0 && (
                            <Tag color="error" className="text-xs">
                              {toolCritical} 严重
                            </Tag>
                          )}
                          {toolWarning > 0 && (
                            <Tag color="warning" className="text-xs">
                              {toolWarning} 警告
                            </Tag>
                          )}
                          {tool.issues.length === 0 && (
                            <Tag color="success" className="text-xs">
                              通过
                            </Tag>
                          )}
                        </div>
                      ),
                      children:
                        tool.issues.length === 0 ? (
                          <div className="text-sm text-green-600 flex items-center gap-1 py-1">
                            <CheckCircleOutlined />
                            <span>该工具所有检测项均通过</span>
                          </div>
                        ) : (
                          <div className="space-y-2">
                            {tool.issues.map((issue) => (
                              <IssueCard key={issue.field} issue={issue} />
                            ))}
                          </div>
                        ),
                    }
                  })}
                />
              </div>
            )}

            {/* 无工具提示 */}
            {result.toolCount === 0 && (
              <Empty
                description="暂无工具配置，无法评估工具质量"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            )}
          </div>

          {/* 评估标准说明 */}
          <div className="bg-gray-50 rounded-lg p-4">
            <div className="text-xs font-semibold text-gray-600 mb-3">评估标准</div>
            <div className="space-y-2">
              {STANDARDS.map((s) => (
                <div key={s.title} className="flex gap-2 text-xs text-gray-500">
                  <span>{s.icon}</span>
                  <span>
                    <span className="font-medium text-gray-700">{s.title}：</span>
                    {s.desc}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : null}
    </Modal>
  )
}
