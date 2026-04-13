import { useMemo, useCallback } from 'react'
import { Tag, Checkbox, Pagination, Empty, Spin, message } from 'antd'
import type { RemoteMcpItemResult } from '@/types/mcp-vendor'

interface RemoteMcpTableProps {
  items: RemoteMcpItemResult[]
  loading: boolean
  selectedKeys: string[]
  onSelectionChange: (keys: string[], items: RemoteMcpItemResult[]) => void
  maxSelection?: number
  pagination: {
    current: number
    pageSize: number
    total: number
    onChange: (page: number, size: number) => void
  }
}

function parseIconUrl(icon: string | null): string | null {
  if (!icon) return null
  try {
    const parsed = JSON.parse(icon)
    return parsed.value || parsed.url || parsed.data || null
  } catch {
    return null
  }
}

function parseTags(tags: string | null): string[] {
  if (!tags) return []
  try {
    const parsed = JSON.parse(tags)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export default function RemoteMcpTable({
  items,
  loading,
  selectedKeys,
  onSelectionChange,
  maxSelection = 50,
  pagination,
}: RemoteMcpTableProps) {
  const selectedSet = useMemo(() => new Set(selectedKeys), [selectedKeys])
  const isAtLimit = selectedKeys.length >= maxSelection

  const handleToggle = useCallback(
    (item: RemoteMcpItemResult) => {
      if (item.existsInPlatform) return
      const key = item.remoteId
      const isDeselecting = selectedSet.has(key)
      // If at limit and trying to select a new item, block it
      if (!isDeselecting && isAtLimit) {
        message.warning('最多选择 50 条')
        return
      }
      const newKeys = isDeselecting
        ? selectedKeys.filter((k) => k !== key)
        : [...selectedKeys, key]
      const newItems = items.filter((i) => newKeys.includes(i.remoteId))
      onSelectionChange(newKeys, newItems)
    },
    [selectedKeys, selectedSet, items, onSelectionChange, isAtLimit],
  )

  const selectableItems = useMemo(() => items.filter((i) => !i.existsInPlatform), [items])
  const allSelected = selectableItems.length > 0 && selectableItems.every((i) => selectedSet.has(i.remoteId))

  const handleSelectAll = useCallback(() => {
    if (allSelected) {
      onSelectionChange([], [])
    } else {
      const keys = selectableItems.map((i) => i.remoteId)
      onSelectionChange(keys, selectableItems)
    }
  }, [allSelected, selectableItems, onSelectionChange])

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spin size="large" />
      </div>
    )
  }

  if (items.length === 0) {
    return <Empty description="暂无数据" className="py-12" />
  }

  return (
    <div>
      {/* Select all + count */}
      <div className="flex items-center justify-between mb-3">
        <Checkbox
          checked={allSelected}
          indeterminate={selectedKeys.length > 0 && !allSelected}
          onChange={handleSelectAll}
          disabled={selectableItems.length === 0}
        >
          <span className="text-sm text-gray-500">
            全选本页（{selectableItems.length} 项可选）
          </span>
        </Checkbox>
        {selectedKeys.length > 0 && (
          <span className="text-sm text-blue-600">已选 {selectedKeys.length} 项</span>
        )}
      </div>

      {/* Card grid */}
      <div className="grid grid-cols-2 gap-3 max-h-[420px] overflow-y-auto pr-1">
        {items.map((item) => {
          const iconUrl = parseIconUrl(item.icon)
          const tags = parseTags(item.tags)
          const isSelected = selectedSet.has(item.remoteId)
          const isDisabled = item.existsInPlatform || (!isSelected && isAtLimit)

          return (
            <div
              key={item.remoteId}
              onClick={() => handleToggle(item)}
              className={`
                relative rounded-lg border-2 p-3.5 transition-all duration-150 cursor-pointer
                ${isDisabled
                  ? 'border-gray-100 bg-gray-50/50 opacity-60 cursor-not-allowed'
                  : isSelected
                    ? 'border-blue-400 bg-blue-50/40 shadow-sm'
                    : 'border-gray-100 hover:border-blue-200 hover:shadow-sm'
                }
              `}
            >
              {/* Checkbox */}
              <div className="absolute top-3 right-3">
                <Checkbox
                  checked={isSelected}
                  disabled={isDisabled}
                  onClick={(e) => e.stopPropagation()}
                  onChange={() => handleToggle(item)}
                />
              </div>

              {/* Header: icon + name */}
              <div className="flex items-start gap-2.5 mb-2 pr-8">
                {iconUrl ? (
                  <img
                    src={iconUrl}
                    alt=""
                    className="w-9 h-9 rounded-lg shrink-0 object-cover bg-gray-50"
                    onError={(e) => {
                      ;(e.target as HTMLImageElement).style.display = 'none'
                    }}
                  />
                ) : (
                  <div className="w-9 h-9 rounded-lg shrink-0 bg-gradient-to-br from-gray-100 to-gray-200 flex items-center justify-center text-sm font-medium text-gray-400">
                    {(item.displayName || item.mcpName || 'M').charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <div className="font-medium text-sm text-gray-900 truncate leading-tight">
                    {item.displayName || item.mcpName}
                  </div>
                  {item.existsInPlatform && (
                    <Tag color="default" className="mt-0.5 text-xs" style={{ fontSize: 10 }}>
                      已存在
                    </Tag>
                  )}
                </div>
              </div>

              {/* Description */}
              <p className="text-xs text-gray-500 line-clamp-2 mb-2 leading-relaxed min-h-[2.5em]">
                {item.description || '暂无描述'}
              </p>

              {/* Footer: protocol + tags */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <Tag color="blue" className="text-xs m-0" style={{ fontSize: 10 }}>
                  {item.protocolType || 'stdio'}
                </Tag>
                {tags.slice(0, 2).map((tag) => (
                  <Tag key={tag} className="text-xs m-0" style={{ fontSize: 10 }}>
                    {tag}
                  </Tag>
                ))}
                {tags.length > 2 && (
                  <span className="text-xs text-gray-400">+{tags.length - 2}</span>
                )}
              </div>
            </div>
          )
        })}
      </div>

      {/* Pagination */}
      <div className="flex justify-end mt-4">
        <Pagination
          current={pagination.current}
          pageSize={pagination.pageSize}
          total={pagination.total}
          onChange={pagination.onChange}
          showSizeChanger
          showTotal={(total) => `共 ${total} 条`}
          pageSizeOptions={['10', '20', '50']}
          size="small"
        />
      </div>
    </div>
  )
}
