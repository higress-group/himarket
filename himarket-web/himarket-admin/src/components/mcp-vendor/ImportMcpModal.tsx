import { useState, useCallback } from 'react'
import { Modal, Input, Button, Alert, message, Space, Tag } from 'antd'
import { SearchOutlined, DownloadOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { mcpVendorApi } from '@/lib/api'
import type {
  McpVendorType,
  RemoteMcpItemResult,
  RemoteMcpItemParam,
  BatchImportResult,
  VendorOption,
} from '@/types/mcp-vendor'
import { VENDOR_OPTIONS } from '@/types/mcp-vendor'
import RemoteMcpTable from './RemoteMcpTable'

interface ImportMcpModalProps {
  open: boolean
  onClose: () => void
  onImportSuccess: () => void
}

export default function ImportMcpModal({ open, onClose, onImportSuccess }: ImportMcpModalProps) {
  const [step, setStep] = useState<'select' | 'list'>('select')
  const [vendorType, setVendorType] = useState<McpVendorType | null>(null)
  const [keyword, setKeyword] = useState('')
  const [items, setItems] = useState<RemoteMcpItemResult[]>([])
  const [selectedKeys, setSelectedKeys] = useState<string[]>([])
  const [selectedItems, setSelectedItems] = useState<RemoteMcpItemResult[]>([])
  const [loading, setLoading] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [importResult, setImportResult] = useState<BatchImportResult | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })

  const selectedVendor = VENDOR_OPTIONS.find((v) => v.value === vendorType)

  const handleSelectVendor = useCallback((vendor: VendorOption) => {
    setVendorType(vendor.value)
    setStep('list')
    setItems([])
    setSelectedKeys([])
    setSelectedItems([])
    setKeyword('')
    setError(null)
    setPagination({ current: 1, pageSize: 20, total: 0 })
  }, [])

  const handleBack = useCallback(() => {
    setStep('select')
    setVendorType(null)
    setItems([])
    setSelectedKeys([])
    setSelectedItems([])
    setKeyword('')
    setError(null)
  }, [])

  const handleQuery = useCallback(
    async (page = 1, size = pagination.pageSize) => {
      if (!vendorType) return
      setLoading(true)
      setError(null)
      try {
        const res: any = await mcpVendorApi.listRemoteMcpItems({
          vendorType,
          keyword: keyword.trim() || undefined,
          page,
          size,
        })
        const data = res.data ?? res
        setItems(data.content ?? [])
        setPagination({
          current: data.number ?? page,
          pageSize: data.size ?? size,
          total: data.totalElements ?? 0,
        })
      } catch (err: any) {
        const msg = err?.response?.data?.message || err?.message || '查询失败'
        setError(msg)
        setItems([])
      } finally {
        setLoading(false)
      }
    },
    [vendorType, keyword, pagination.pageSize],
  )

  const handlePageChange = useCallback(
    (page: number, size: number) => {
      handleQuery(page, size)
    },
    [handleQuery],
  )

  const handleSelectionChange = useCallback((keys: string[], rows: RemoteMcpItemResult[]) => {
    if (keys.length > 50) {
      message.warning('最多选择 50 条')
      return
    }
    setSelectedKeys(keys)
    setSelectedItems(rows)
  }, [])

  const handleImport = useCallback(async () => {
    if (!vendorType || selectedItems.length === 0) {
      message.warning('请先选择要导入的 MCP')
      return
    }
    setImporting(true)
    const hide = message.loading('正在导入，请稍候（每个 MCP 需要获取详情，可能需要一些时间）...', 0)
    try {
      const importItems: RemoteMcpItemParam[] = selectedItems.map((item) => ({
        remoteId: item.remoteId,
        mcpName: item.mcpName,
        displayName: item.displayName,
        description: item.description,
        protocolType: item.protocolType,
        connectionConfig: item.connectionConfig,
        tags: item.tags,
        icon: item.icon,
        repoUrl: item.repoUrl,
        extraParams: item.extraParams,
      }))

      // 使用 AbortController 设置前端超时保护（5 分钟）
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), 300000)

      let res: any
      try {
        res = await mcpVendorApi.batchImport({ vendorType, items: importItems })
      } finally {
        clearTimeout(timeoutId)
      }

      // 后端可能有全局响应包装 {code, message, data} 或直接返回 BatchImportResult
      const result: BatchImportResult | null = res?.data?.successCount !== undefined
        ? res.data
        : res?.successCount !== undefined
          ? res
          : null
      if (result && typeof result.successCount === 'number') {
        setImportResult(result)
        onImportSuccess()
      } else {
        message.success('导入完成')
        onImportSuccess()
      }
    } catch (err: any) {
      if (err?.name === 'AbortError' || err?.code === 'ECONNABORTED') {
        message.warning('导入请求超时，但后台可能已完成导入，请刷新页面查看')
        onImportSuccess()
      } else {
        const msg = err?.response?.data?.message || err?.message || '导入失败'
        message.error(msg)
      }
    } finally {
      hide()
      setImporting(false)
    }
  }, [selectedItems, vendorType, onImportSuccess])

  const handleClose = useCallback(() => {
    setStep('select')
    setVendorType(null)
    setItems([])
    setSelectedKeys([])
    setSelectedItems([])
    setKeyword('')
    setError(null)
    setImportResult(null)
    setPagination({ current: 1, pageSize: 20, total: 0 })
    onClose()
  }, [onClose])

  const handleCloseResult = useCallback(() => {
    setImportResult(null)
    setSelectedKeys([])
    setSelectedItems([])
    handleQuery(pagination.current, pagination.pageSize)
  }, [handleQuery, pagination.current, pagination.pageSize])

  return (
    <>
      <Modal
        title={
          step === 'list' && selectedVendor ? (
            <div className="flex items-center gap-2">
              <Button
                type="text"
                size="small"
                icon={<ArrowLeftOutlined />}
                onClick={handleBack}
                className="text-gray-400 hover:text-gray-600"
              />
              <img src={selectedVendor.iconUrl} alt={selectedVendor.label} className="w-5 h-5 rounded" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }} />
              <span>从 {selectedVendor.label} 导入</span>
            </div>
          ) : (
            '导入 MCP Server'
          )
        }
        open={open}
        onCancel={handleClose}
        width={step === 'list' ? 1000 : 640}
        footer={
          step === 'list' && items.length > 0 ? (
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">
                已选择 <span className="font-medium text-blue-600">{selectedKeys.length}</span> 项
                {selectedKeys.length >= 50 && <span className="text-orange-500 ml-2">（已达上限）</span>}
              </span>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                loading={importing}
                disabled={selectedKeys.length === 0}
                onClick={handleImport}
              >
                导入选中项
              </Button>
            </div>
          ) : null
        }
        destroyOnHidden
      >
        {step === 'select' ? (
          /* ========== Step 1: Vendor Selection Cards ========== */
          <div className="py-2">
            <p className="text-gray-500 mb-5">选择 MCP 数据源，从第三方平台浏览并导入 MCP Server</p>
            <div className="grid grid-cols-1 gap-3">
              {VENDOR_OPTIONS.map((vendor) => (
                <div
                  key={vendor.value}
                  onClick={() => handleSelectVendor(vendor)}
                  className="group cursor-pointer rounded-xl border-2 border-gray-100 hover:border-blue-300 p-5 transition-all duration-200 hover:shadow-md hover:bg-blue-50/30 flex items-center gap-4"
                >
                  <div
                    className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0 transition-transform group-hover:scale-110 overflow-hidden"
                    style={{ backgroundColor: vendor.color + '15' }}
                  >
                    <img src={vendor.iconUrl} alt={vendor.label} className="w-8 h-8 rounded object-contain" onError={(e) => { (e.target as HTMLImageElement).src = '' }} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-gray-800 text-base">{vendor.label}</div>
                    <div className="text-sm text-gray-500 mt-0.5">{vendor.description}</div>
                  </div>
                  <div className="text-gray-300 group-hover:text-blue-400 transition-colors text-lg">→</div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          /* ========== Step 2: MCP List ========== */
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <Input
                placeholder="搜索 MCP Server..."
                prefix={<SearchOutlined className="text-gray-400" />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={() => handleQuery(1)}
                allowClear
                style={{ flex: 1 }}
              />
              <Button type="primary" loading={loading} onClick={() => handleQuery(1)}>
                搜索
              </Button>
            </div>

            {error && (
              <Alert
                message="查询失败"
                description={error}
                type="error"
                showIcon
                closable
                onClose={() => setError(null)}
              />
            )}

            <RemoteMcpTable
              items={items}
              loading={loading}
              selectedKeys={selectedKeys}
              onSelectionChange={handleSelectionChange}
              maxSelection={50}
              pagination={{
                current: pagination.current,
                pageSize: pagination.pageSize,
                total: pagination.total,
                onChange: handlePageChange,
              }}
            />

            {items.length === 0 && !loading && !error && (
              <div className="text-center py-12 text-gray-400">
                输入关键词搜索，或直接点击"搜索"浏览全部 MCP Server
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Import result modal */}
      <Modal
        title="导入结果"
        open={!!importResult}
        onCancel={handleCloseResult}
        onOk={handleCloseResult}
        okText="确定"
        cancelButtonProps={{ style: { display: 'none' } }}
        width={600}
      >
        {importResult && (
          <div className="space-y-4">
            <div className="flex gap-4 justify-center py-2">
              <div className="text-center">
                <div className="text-2xl font-bold text-green-600">{importResult.successCount}</div>
                <div className="text-xs text-gray-500 mt-1">成功</div>
              </div>
              <div className="w-px bg-gray-200" />
              <div className="text-center">
                <div className="text-2xl font-bold text-yellow-500">{importResult.skippedCount}</div>
                <div className="text-xs text-gray-500 mt-1">跳过</div>
              </div>
              <div className="w-px bg-gray-200" />
              <div className="text-center">
                <div className="text-2xl font-bold text-red-500">{importResult.failedCount}</div>
                <div className="text-xs text-gray-500 mt-1">失败</div>
              </div>
            </div>
            {importResult.details.length > 0 && (
              <div className="max-h-60 overflow-y-auto rounded-lg border border-gray-100">
                {importResult.details.map((detail, idx) => (
                  <div
                    key={detail.mcpName}
                    className={`flex items-center justify-between px-4 py-2.5 ${idx < importResult.details.length - 1 ? 'border-b border-gray-50' : ''}`}
                  >
                    <span className="text-sm font-mono text-gray-700 truncate max-w-[300px]">{detail.mcpName}</span>
                    <Space>
                      <Tag
                        color={
                          detail.status === 'SUCCESS' ? 'success' : detail.status === 'SKIPPED' ? 'warning' : 'error'
                        }
                      >
                        {detail.status === 'SUCCESS' ? '成功' : detail.status === 'SKIPPED' ? '已跳过' : '失败'}
                      </Tag>
                      {detail.message && <span className="text-xs text-red-500 max-w-[150px] truncate">{detail.message}</span>}
                    </Space>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </Modal>
    </>
  )
}
